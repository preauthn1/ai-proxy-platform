<script lang="ts" setup>
import type { SysMenu } from '#/api/system/menu';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { Page, useVbenDrawer } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchMenuListApi } from '#/api/system/menu';
import { useDeleteMenu, useUpdateMenu } from '#/api/system/menu/hooks';
import { refreshAccess } from '#/router/refresh-access';
import {
  buildMenuTree,
  MENU_TYPE_TAG,
  useMenuColumns,
  useMenuSearchSchema,
} from '#/views/system/menu/data';
import MenuForm from '#/views/system/menu/modules/form.vue';

defineOptions({ name: 'SystemMenu' });

const router = useRouter();

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: MenuForm,
  destroyOnClose: true,
});

// 编辑态传递给抽屉的数据
const drawerData = ref<{
  mode: 'create' | 'edit';
  presetParentId?: null | number;
  row?: SysMenu;
}>({ mode: 'create' });

const treeData = ref<Array<SysMenu & { children?: SysMenu[] }>>([]);
/** 最外层根节点数（驱动分页） */
const total = ref(0);
/** 筛选范围内的菜单条数（仅展示） */
const itemTotal = ref(0);
const isExpanded = ref(false);

async function loadTree(
  page: { currentPage: number; pageSize: number },
  formValues: Record<string, any> = {},
) {
  // 后端按「最外层根」分页：pageSize=20 表示 20 个根；items 为这些根下的完整子树
  const res = await fetchMenuListApi({
    page: page.currentPage,
    pageSize: page.pageSize,
    name: formValues.name || undefined,
    type: formValues.type || undefined,
    permissionCode: formValues.permissionCode || undefined,
    status: formValues.status ?? undefined,
  });
  treeData.value = buildMenuTree(res.items);
  total.value = res.total;
  itemTotal.value = res.itemTotal ?? 0;
  isExpanded.value = false;
}

const deleteMut = useDeleteMenu({
  onSuccess: async () => {
    message.success('删除成功');
    gridApi.query();
    await refreshAccess(router);
  },
  onError: (err: Error) =>
    message.error(`删除失败：${err.message ?? '未知错误'}`),
});

const toggleMut = useUpdateMenu({
  onSuccess: async (_data, vars) => {
    message.success(vars.data.isEnabled === 1 ? '已启用' : '已禁用');
    gridApi.query();
    await refreshAccess(router);
  },
  onError: (err: Error) =>
    message.error(`操作失败：${err.message ?? '未知错误'}`),
});

async function toggleExpandAll() {
  if (isExpanded.value) {
    await gridApi.grid.clearTreeExpand();
    isExpanded.value = false;
  } else {
    await gridApi.grid.setAllTreeExpand(true);
    isExpanded.value = true;
  }
}

const [Grid, gridApi] = useVbenVxeGrid<SysMenu>({
  formOptions: {
    collapsed: false,
    schema: useMenuSearchSchema(),
    showCollapseButton: false,
  },
  gridOptions: {
    columns: useMenuColumns(),
    keepSource: true,
    rowConfig: { isHover: true },
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
    // 启用分页；total 为根节点数。Total 区展示「根 + 条数」
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
        total: () => `共 ${total.value} 个根菜单，${itemTotal.value} 条数据`,
      },
    },
    proxyConfig: {
      ajax: {
        query: async (
          { page }: { page: { currentPage: number; pageSize: number } },
          formValues: Record<string, any>,
        ) => {
          await loadTree(page, formValues);
          return { items: treeData.value, total: total.value };
        },
      },
    },
  } as never,
});

function openCreate(parentId: null | number = null) {
  drawerData.value = { mode: 'create', presetParentId: parentId };
  formDrawerApi.setData(drawerData.value).open();
}

function openEdit(row: SysMenu) {
  drawerData.value = { mode: 'edit', row };
  formDrawerApi.setData(drawerData.value).open();
}

function toggleStatus(row: SysMenu) {
  toggleMut.mutate({
    id: row.id,
    data: { isEnabled: row.isEnabled === 1 ? 0 : 1 },
  });
}

async function onMenuSaved() {
  gridApi.query();
  await refreshAccess(router);
}
</script>

<template>
  <Page>
    <Grid>
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <Button type="primary" @click="openCreate(null)"> + 新增菜单 </Button>
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
      <template #action="{ row }">
        <Space>
          <a @click="openEdit(row)">编辑</a>
          <a v-if="row.type !== 'BUTTON'" @click="openCreate(row.id)">添加子项</a>
          <a @click="toggleStatus(row)">
            {{ row.isEnabled === 1 ? '禁用' : '启用' }}
          </a>
          <Popconfirm title="确认删除" @confirm="deleteMut.mutate(row.id)">
            <a style="color: #ff4d4f">删除</a>
          </Popconfirm>
        </Space>
      </template>
      <template #type="{ row }">
        <Tag :color="MENU_TYPE_TAG[row.type]">{{ row.type }}</Tag>
      </template>
      <template #isEnabled="{ row }">
        <Tag :color="row.isEnabled === 1 ? 'success' : 'default'">
          {{ row.isEnabled === 1 ? '启用' : '禁用' }}
        </Tag>
      </template>
    </Grid>
    <FormDrawer @success="onMenuSaved" />
  </Page>
</template>
