<script lang="ts" setup>
import type { SysApi } from '#/api/system/api';
import type { ApiTreeNode } from '#/views/system/api/data';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchApiListApi } from '#/api/system/api';
import { useDeleteApi, useSyncApisApi } from '#/api/system/api/hooks';
import {
  buildApiGroupTree,
  METHOD_COLOR,
  useApiColumns,
  useApiSearchSchema,
} from '#/views/system/api/data';
import ApiForm from '#/views/system/api/modules/form.vue';

defineOptions({ name: 'SystemApi' });

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: ApiForm,
  destroyOnClose: true,
});

const drawerData = ref<{ row?: SysApi }>({});
const syncLoading = ref(false);

const treeData = ref<ApiTreeNode[]>([]);
/** 分组总数（驱动分页） */
const total = ref(0);
/** 筛选后的接口条数（仅展示） */
const itemTotal = ref(0);
const isExpanded = ref(false);

async function loadTree(
  page: { currentPage: number; pageSize: number },
  formValues: Record<string, any> = {},
) {
  // 后端按「分组」分页：pageSize=20 表示 20 个分组；items 为这些组下的全部接口
  const res = await fetchApiListApi({
    page: page.currentPage,
    pageSize: page.pageSize,
    name: formValues.name || undefined,
    path: formValues.path || undefined,
    method: formValues.method || undefined,
    group: formValues.group || undefined,
    status: formValues.status ?? undefined,
  });
  treeData.value = buildApiGroupTree(res.items);
  total.value = res.total;
  itemTotal.value = res.itemTotal ?? 0;
  isExpanded.value = false;
}

const deleteMut = useDeleteApi({
  onSuccess: () => {
    message.success('删除成功');
    gridApi.query();
  },
  onError: (err: Error) =>
    message.error(`删除失败：${err.message ?? '未知错误'}`),
});

const syncMut = useSyncApisApi({
  onSuccess: (res) => {
    message.success(
      `同步成功：新增 ${res.added}，跳过 ${res.skipped}，共 ${res.total}`,
    );
    gridApi.query();
  },
  onError: (err: Error) =>
    message.error(`同步失败：${err.message ?? '未知错误'}`),
  onSettled: () => {
    syncLoading.value = false;
  },
});

async function handleSync() {
  syncLoading.value = true;
  await syncMut.mutate();
}

async function toggleExpandAll() {
  if (isExpanded.value) {
    await gridApi.grid.clearTreeExpand();
    isExpanded.value = false;
  } else {
    await gridApi.grid.setAllTreeExpand(true);
    isExpanded.value = true;
  }
}

const [Grid, gridApi] = useVbenVxeGrid<ApiTreeNode>({
  formOptions: {
    collapsed: false,
    schema: useApiSearchSchema(),
    showCollapseButton: false,
  },
  gridOptions: {
    columns: useApiColumns(),
    keepSource: true,
    rowConfig: { isHover: true, keyField: 'rowKey' },
    size: 'small',
    stripe: true,
    treeConfig: {
      transform: false,
      expandAll: false,
    },
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
    // 启用分页；total 为分组数。Total 区展示「分组 + 条数」（与 Sizes 同处分页条）
    pagerConfig: {
      layouts: [
        'Total',
        'Sizes',
        'PrevJump',
        'PrevPage',
        'Number',
        'NextPage',
        'NextJump',
      ],
      slots: {
        total: () => `共 ${total.value} 个分组，${itemTotal.value} 条数据`,
      },
    },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          await loadTree(page, formValues);
          // total 为分组数；items 为当前页分组树节点
          return { items: treeData.value, total: total.value };
        },
      },
    },
  } as never,
});

function openCreate() {
  drawerData.value = {};
  formDrawerApi.setData(drawerData.value).open();
}

function openEdit(row: SysApi) {
  drawerData.value = { row };
  formDrawerApi.setData(drawerData.value).open();
}

function isGroupRow(row: ApiTreeNode) {
  return Boolean(row.isGroup);
}
</script>

<template>
  <Page>
    <Grid>
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <Popconfirm
            title="确认同步"
            description="将从后端路由清单重新扫描并登记接口（命中则跳过）"
            @confirm="handleSync"
          >
            <Button :loading="syncLoading">同步接口</Button>
          </Popconfirm>
          <Button type="primary" @click="openCreate"> + 新增接口 </Button>
          <Button @click="toggleExpandAll">
            <IconifyIcon
              :icon="
                isExpanded
                  ? 'ant-design:up-outlined'
                  : 'ant-design:down-outlined'
              "
            />
            {{ isExpanded ? '折叠全部' : '展开全部' }}
          </Button>
        </Space>
      </template>
      <template #id="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <span v-else>{{ row.id }}</span>
      </template>
      <template #method="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <Tag v-else :color="METHOD_COLOR[row.method]">{{ row.method }}</Tag>
      </template>
      <template #path="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <span v-else style="font-family: monospace">{{ row.path }}</span>
      </template>
      <template #permissionCode="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <Tag v-else>{{ row.permissionCode }}</Tag>
      </template>
      <template #isEnabled="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <Tag v-else :color="row.isEnabled === 1 ? 'success' : 'default'">
          {{ row.isEnabled === 1 ? '启用' : '禁用' }}
        </Tag>
      </template>
      <template #createdAt="{ row }">
        <span v-if="isGroupRow(row)" style="color: #999">-</span>
        <span v-else>{{ formatDateTime(row.createdAt) }}</span>
      </template>
      <template #action="{ row }">
        <Space v-if="!isGroupRow(row)">
          <a @click="openEdit(row)">编辑</a>
          <Popconfirm title="确认删除" @confirm="deleteMut.mutate(row.id)">
            <a style="color: #ff4d4f">删除</a>
          </Popconfirm>
        </Space>
      </template>
    </Grid>
    <FormDrawer @success="gridApi.query()" />
  </Page>
</template>
