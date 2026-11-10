<script lang="ts" setup>
import type {
  TaskConfig,
  TaskConfigBatchAction,
} from '#/api/system/task-config';

import { onMounted, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  batchTaskConfigApi,
  deleteTaskConfigApi,
  fetchTaskConfigListApi,
  fetchTaskQueuesApi,
  fetchTaskWorkflowTypesApi,
  triggerTaskConfigApi,
  updateTaskConfigApi,
} from '#/api/system/task-config';
import { $t } from '#/locales';
import {
  useConfigColumns,
  useConfigSearchSchema,
} from '#/views/task/config/data';
import ConfigForm from '#/views/task/config/modules/form.vue';
import { notifyTaskExecutionChanged } from '#/views/task/modules/events';

defineOptions({ name: 'TaskConfigPanel' });

const selectedIds = ref<Set<number>>(new Set());
const bulkLoading = ref(false);
const rowLoadingId = ref<null | number>(null);

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: ConfigForm,
  destroyOnClose: true,
});

function clearSelection() {
  selectedIds.value = new Set();
  gridApi.grid?.clearCheckboxRow?.();
}

const [Grid, gridApi] = useVbenVxeGrid<TaskConfig>({
  formOptions: {
    collapsed: false,
    schema: useConfigSearchSchema(),
    showCollapseButton: false,
  },
  gridEvents: {
    checkboxChange: ({
      row,
      checked,
    }: {
      checked: boolean;
      row: TaskConfig;
    }) => {
      if (!row) return;
      const next = new Set(selectedIds.value);
      if (checked) next.add(row.id);
      else next.delete(row.id);
      selectedIds.value = next;
    },
    checkboxAll: ({ checked }: { checked: boolean }) => {
      const records = (gridApi.grid?.getCheckboxRecords?.() ??
        []) as TaskConfig[];
      selectedIds.value = checked
        ? new Set(records.map((r) => r.id))
        : new Set();
    },
  },
  gridOptions: {
    checkboxConfig: { highlight: true },
    columns: useConfigColumns(),
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
          return await fetchTaskConfigListApi({
            page: page.currentPage,
            pageSize: page.pageSize,
            code: formValues.code || undefined,
            name: formValues.name || undefined,
            status: formValues.status ?? undefined,
            workflowType: formValues.workflowType || undefined,
            taskQueue: formValues.taskQueue || undefined,
          });
        },
      },
    },
  } as never,
});

onMounted(async () => {
  try {
    const [wf, queues] = await Promise.all([
      fetchTaskWorkflowTypesApi(),
      fetchTaskQueuesApi(),
    ]);
    gridApi.formApi?.updateSchema?.([
      {
        fieldName: 'workflowType',
        componentProps: {
          options: wf ?? [],
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
          placeholder: $t('task.config.workflowTypeFilterPlaceholder'),
        },
      },
      {
        fieldName: 'taskQueue',
        componentProps: {
          options: queues ?? [],
          allowClear: true,
          showSearch: true,
          optionFilterProp: 'label',
          placeholder: $t('task.config.taskQueueFilterPlaceholder'),
        },
      },
    ]);
  } catch {
    // 下拉失败不影响列表
  }
});

function openCreate() {
  formDrawerApi.setData({ mode: 'create' }).open();
}

function openEdit(row: TaskConfig) {
  formDrawerApi.setData({ mode: 'edit', row }).open();
}

async function handleDelete(row: TaskConfig) {
  try {
    await deleteTaskConfigApi(row.id);
    message.success($t('task.config.deleteOk'));
    clearSelection();
    await gridApi.reload();
  } catch {
    // interceptor 已提示
  }
}

async function handleTrigger(row: TaskConfig) {
  if (row.isEnabled !== 1) {
    message.error($t('task.config.triggerDisabledHint'));
    return;
  }
  rowLoadingId.value = row.id;
  try {
    await triggerTaskConfigApi(row.id);
    message.success($t('task.config.triggerOk'));
    // 通知执行记录 Tab 重新拉数（force-render keep-alive 下不会自动刷新）
    notifyTaskExecutionChanged();
    await gridApi.reload();
  } catch {
    // 禁用触发等业务错误由 interceptor 展示 mock 文案
  } finally {
    rowLoadingId.value = null;
  }
}

async function handleToggle(row: TaskConfig) {
  const next: 0 | 1 = row.isEnabled === 1 ? 0 : 1;
  rowLoadingId.value = row.id;
  try {
    await updateTaskConfigApi({ id: row.id, data: { isEnabled: next } });
    message.success(
      next === 1 ? $t('task.config.enableOk') : $t('task.config.disableOk'),
    );
    await gridApi.reload();
  } catch {
    // interceptor
  } finally {
    rowLoadingId.value = null;
  }
}

async function bulkAction(action: TaskConfigBatchAction) {
  const ids = [...selectedIds.value];
  if (ids.length === 0) {
    message.warning($t('task.config.selectFirst'));
    return;
  }
  bulkLoading.value = true;
  try {
    const res = await batchTaskConfigApi({ action, ids });
    if (action === 'trigger') {
      const skipped = res.skippedDisabled?.length ?? 0;
      if (skipped > 0) {
        message.success(
          $t('task.config.bulkTriggerPartial', {
            triggered: res.affected,
            skipped,
          }),
        );
      } else {
        message.success($t('task.config.bulkOk'));
      }
      notifyTaskExecutionChanged();
    } else {
      message.success($t('task.config.bulkOk'));
    }
    clearSelection();
    await gridApi.reload();
  } catch {
    // interceptor：全禁用触发等业务错误
  } finally {
    bulkLoading.value = false;
  }
}
</script>

<template>
  <div>
    <Grid :table-title="$t('task.config.title')">
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <span
            v-if="selectedIds.size > 0"
            class="text-xs"
            style="color: var(--ant-color-text-secondary)"
          >
            {{ $t('task.config.selectedCount', { n: selectedIds.size }) }}
          </span>
          <template v-if="selectedIds.size > 0">
            <Button
              size="small"
              :loading="bulkLoading"
              @click="bulkAction('enable')"
            >
              {{ $t('task.config.bulkEnable') }}
            </Button>
            <Button
              size="small"
              :loading="bulkLoading"
              @click="bulkAction('disable')"
            >
              {{ $t('task.config.bulkDisable') }}
            </Button>
            <Button
              size="small"
              :loading="bulkLoading"
              @click="bulkAction('trigger')"
            >
              {{ $t('task.config.bulkTrigger') }}
            </Button>
            <Popconfirm
              :title="$t('task.config.confirmBulkDelete')"
              @confirm="bulkAction('delete')"
            >
              <Button size="small" danger ghost :loading="bulkLoading">
                {{ $t('task.config.bulkDelete') }}
              </Button>
            </Popconfirm>
          </template>
          <Button type="primary" @click="openCreate">
            {{ $t('task.config.create') }}
          </Button>
        </Space>
      </template>

      <template #cron="{ row }">
        <span v-if="row.cronExpr">{{ row.cronExpr }}</span>
        <span v-else style="color: var(--ant-color-text-secondary)">
          {{ $t('task.config.cronManualOnly') }}
        </span>
      </template>

      <template #status="{ row }">
        <Tag :color="row.isEnabled === 1 ? 'success' : 'default'">
          {{
            row.isEnabled === 1
              ? $t('task.config.enabled')
              : $t('task.config.disabled')
          }}
        </Tag>
      </template>

      <template #action="{ row }">
        <Space>
          <Button type="link" size="small" @click="openEdit(row)">
            {{ $t('task.config.edit') }}
          </Button>
          <Button
            type="link"
            size="small"
            :loading="rowLoadingId === row.id"
            @click="handleTrigger(row)"
          >
            {{ $t('task.config.trigger') }}
          </Button>
          <Button
            type="link"
            size="small"
            :loading="rowLoadingId === row.id"
            @click="handleToggle(row)"
          >
            {{
              row.isEnabled === 1
                ? $t('task.config.disabled')
                : $t('task.config.enabled')
            }}
          </Button>
          <Popconfirm
            :title="$t('task.config.confirmDelete')"
            @confirm="handleDelete(row)"
          >
            <Button type="link" size="small" danger>
              {{ $t('task.config.delete') }}
            </Button>
          </Popconfirm>
        </Space>
      </template>
    </Grid>
    <FormDrawer @success="gridApi.reload()" />
  </div>
</template>
