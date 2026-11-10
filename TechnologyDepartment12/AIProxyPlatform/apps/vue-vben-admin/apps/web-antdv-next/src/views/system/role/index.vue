<script lang="ts" setup>
import type { RoleListItem } from '#/api/system/role';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchRoleListApi } from '#/api/system/role';
import { useDeleteRole } from '#/api/system/role/hooks';
import { useRoleColumns, useRoleSearchSchema } from '#/views/system/role/data';
import RoleForm from '#/views/system/role/modules/form.vue';
import RolePermission from '#/views/system/role/modules/permission.vue';

defineOptions({ name: 'SystemRole' });

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: RoleForm,
  destroyOnClose: true,
});

const [PermDrawer, permDrawerApi] = useVbenDrawer({
  connectedComponent: RolePermission,
  destroyOnClose: true,
});

const drawerData = ref<{
  mode: 'create' | 'edit';
  row?: RoleListItem;
}>({ mode: 'create' });

const permData = ref<{ id: number; name: string }>({ id: 0, name: '' });

const deleteMut = useDeleteRole({
  onSuccess: () => {
    message.success('删除成功');
    gridApi.query();
  },
  onError: (err: Error) =>
    message.error(`删除失败：${err.message ?? '未知错误'}`),
});

const [Grid, gridApi] = useVbenVxeGrid<RoleListItem>({
  formOptions: {
    collapsed: false,
    schema: useRoleSearchSchema(),
    showCollapseButton: false,
  },
  gridOptions: {
    columns: useRoleColumns(),
    keepSource: true,
    rowConfig: { isHover: true },
    size: 'small',
    stripe: true,
    proxyConfig: {
      ajax: {
        query: async (_params: unknown, formValues: Record<string, any>) => {
          const res = await fetchRoleListApi({
            page: 1,
            pageSize: 100,
            code: formValues.code || undefined,
            name: formValues.name || undefined,
            status: formValues.status ?? undefined,
          });
          return { items: res.items, total: res.total };
        },
      },
    },
    toolbarConfig: {
      custom: true,
      refresh: true,
      zoom: true,
    },
  } as never,
});

function openCreate() {
  drawerData.value = { mode: 'create' };
  formDrawerApi.setData(drawerData.value).open();
}

function openEdit(row: RoleListItem) {
  drawerData.value = { mode: 'edit', row };
  formDrawerApi.setData(drawerData.value).open();
}

function openPermission(row: RoleListItem) {
  permData.value = { id: row.id, name: row.name };
  permDrawerApi.setData(permData.value).open();
}
</script>

<template>
  <Page>
    <Grid>
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <Button type="primary" @click="openCreate"> + 新增角色 </Button>
        </Space>
      </template>
      <template #action="{ row }">
        <Space>
          <a @click="openEdit(row)">编辑</a>
          <a @click="openPermission(row as RoleListItem)">分配权限</a>
          <Popconfirm title="确认删除" @confirm="deleteMut.mutate(row.id)">
            <a style="color: #ff4d4f">删除</a>
          </Popconfirm>
        </Space>
      </template>
      <template #isEnabled="{ row }">
        <Tag
          :color="(row as RoleListItem).isEnabled === 1 ? 'success' : 'default'"
        >
          {{ (row as RoleListItem).isEnabled === 1 ? '启用' : '禁用' }}
        </Tag>
      </template>
    </Grid>
    <FormDrawer @success="gridApi.query()" />
    <PermDrawer @success="gridApi.query()" />
  </Page>
</template>
