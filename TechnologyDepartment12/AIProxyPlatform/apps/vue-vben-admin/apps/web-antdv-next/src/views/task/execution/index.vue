<script lang="ts" setup>
import type { TaskExecution } from '#/api/system/task-execution';

import { onBeforeUnmount, onMounted, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  fetchTaskConfigListApi,
  fetchTaskWorkflowTypesApi,
} from '#/api/system/task-config';
import { fetchTaskExecutionListApi } from '#/api/system/task-execution';
import { $t } from '#/locales';
import {
  executionStatusLabel,
  formatDuration,
  STATUS_TAG_COLOR,
  useExecutionColumns,
  useExecutionSearchSchema,
} from '#/views/task/execution/data';
import ExecutionDetail from '#/views/task/execution/modules/detail.vue';
import { onTaskExecutionChanged } from '#/views/task/modules/events';

defineOptions({ name: 'TaskExecutionPanel' });

const configOptions = ref<Array<{ label: string; value: number }>>([]);

const [DetailDrawer, detailDrawerApi] = useVbenDrawer({
  connectedComponent: ExecutionDetail,
  destroyOnClose: true,
});

function openDetail(row: TaskExecution) {
  detailDrawerApi.setData({ row }).open();
}

const [Grid, gridApi] = useVbenVxeGrid<TaskExecution>({
  formOptions: {
    collapsed: false,
    schema: useExecutionSearchSchema(),
    showCollapseButton: false,
  },
  // 与日志审计一致：点击行打开详情
  gridEvents: {
    cellClick: ({ row }: { row: TaskExecution }) => {
      openDetail(row);
    },
  },
  gridOptions: {
    columns: useExecutionColumns(),
    keepSource: true,
    rowConfig: { isHover: true },
    size: 'small',
    stripe: true,
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          const range = formValues.startedAtRange as
            | [unknown, unknown]
            | undefined;
          const startedAtFrom =
            range?.[0] === undefined || range[0] === null
              ? undefined
              : String(range[0]);
          const startedAtTo =
            range?.[1] === undefined || range[1] === null
              ? undefined
              : String(range[1]);
          const rawConfigId = formValues.configId;
          const configId =
            rawConfigId === undefined ||
            rawConfigId === null ||
            rawConfigId === ''
              ? undefined
              : Number(rawConfigId);
          return await fetchTaskExecutionListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            configId,
            status: formValues.status || undefined,
            workflowType: formValues.workflowType || undefined,
            startedAtFrom,
            startedAtTo,
          });
        },
      },
    },
  } as never,
});

let stopExecutionListener: (() => void) | undefined;

onMounted(async () => {
  try {
    const [res, workflowTypes] = await Promise.all([
      fetchTaskConfigListApi({ page: 1, pageSize: 200 }),
      fetchTaskWorkflowTypesApi(),
    ]);
    configOptions.value = res.items.map((c) => ({
      label: `${c.name}（${c.code}）`,
      value: c.id,
    }));
    // 局部更新 configId / workflowType 下拉 options（schema 在 grid 初始化时已固化）
    gridApi.formApi?.updateSchema?.([
      {
        fieldName: 'configId',
        componentProps: {
          options: configOptions.value,
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
        },
      },
      {
        fieldName: 'workflowType',
        componentProps: {
          options: workflowTypes ?? [],
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
          placeholder: $t('task.execution.filterWorkflowPlaceholder'),
        },
      },
    ]);
  } catch {
    // 下拉失败不影响列表
  }

  // 配置 Tab 触发成功后刷新本表（双 Tab keep-alive）
  stopExecutionListener = onTaskExecutionChanged(() => {
    void gridApi.reload();
  });
});

onBeforeUnmount(() => {
  stopExecutionListener?.();
  stopExecutionListener = undefined;
});
</script>

<template>
  <div>
    <Grid :table-title="$t('task.execution.title')">
      <template #configName="{ row }">
        {{ row.configName || $t('task.execution.unknownConfig') }}
      </template>
      <template #status="{ row }">
        <Tag :color="STATUS_TAG_COLOR[row.status] ?? 'default'">
          {{ executionStatusLabel(row.status) }}
        </Tag>
      </template>
      <template #waitDuration="{ row }">
        {{ formatDuration(row.pendingAt, row.startedAt) }}
      </template>
      <template #duration="{ row }">
        {{ formatDuration(row.startedAt, row.closedAt) }}
      </template>
      <template #failure="{ row }">
        <span v-if="row.failureReason">{{ row.failureReason }}</span>
        <span v-else style="color: var(--ant-color-text-secondary)">—</span>
      </template>
    </Grid>
    <DetailDrawer />
  </div>
</template>
