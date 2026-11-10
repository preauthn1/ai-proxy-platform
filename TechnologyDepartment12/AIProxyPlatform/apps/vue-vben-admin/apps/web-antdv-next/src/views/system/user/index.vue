<script lang="ts" setup>
import type { UserListItem } from '#/api/system/user';

import { ref } from 'vue';

import { Page, useVbenDrawer } from '@vben/common-ui';

import { Button, message, Popconfirm, Space, Tag } from 'antdv-next';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { fetchUserListApi } from '#/api/system/user';
import { useDeleteUser, useToggleUserStatus } from '#/api/system/user/hooks';
import { useUserColumns, useUserSearchSchema } from '#/views/system/user/data';
import UserForm from '#/views/system/user/modules/form.vue';
import ResetPassword from '#/views/system/user/modules/reset-password.vue';

defineOptions({ name: 'SystemUser' });

const [FormDrawer, formDrawerApi] = useVbenDrawer({
  connectedComponent: UserForm,
  destroyOnClose: true,
});

const [ResetDrawer, resetDrawerApi] = useVbenDrawer({
  connectedComponent: ResetPassword,
  destroyOnClose: true,
});

// 编辑态传递给抽屉的数据
const drawerData = ref<{
  mode: 'create' | 'edit';
  row?: UserListItem;
}>({ mode: 'create' });

const resetData = ref<{ id: number; username: string }>({
  id: 0,
  username: '',
});

const deleteMut = useDeleteUser({
  onSuccess: () => {
    message.success('删除成功');
    gridApi.query();
  },
  onError: (err: Error) =>
    message.error(`删除失败：${err.message ?? '未知错误'}`),
});

const toggleMut = useToggleUserStatus({
  onSuccess: (_data, vars) => {
    message.success(vars.status === 1 ? '已启用' : '已禁用');
    gridApi.query();
  },
  onError: (err: Error) =>
    message.error(`操作失败：${err.message ?? '未知错误'}`),
});

const [Grid, gridApi] = useVbenVxeGrid<UserListItem>({
  formOptions: {
    collapsed: false,
    schema: useUserSearchSchema(),
    showCollapseButton: false,
  },
  gridOptions: {
    columns: useUserColumns(),
    keepSource: true,
    rowConfig: { isHover: true },
    size: 'small',
    stripe: true,
    proxyConfig: {
      ajax: {
        query: async (_params: unknown, formValues: Record<string, any>) => {
          const res = await fetchUserListApi({
            page: 1,
            pageSize: 100,
            username: formValues.username || undefined,
            nickname: formValues.nickname || undefined,
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

function openEdit(row: UserListItem) {
  drawerData.value = { mode: 'edit', row };
  formDrawerApi.setData(drawerData.value).open();
}

function toggleStatus(row: UserListItem) {
  toggleMut.mutate({ id: row.id, status: row.isEnabled === 1 ? 0 : 1 });
}

function openReset(row: UserListItem) {
  resetData.value = { id: row.id, username: row.username };
  resetDrawerApi.setData(resetData.value).open();
}
</script>

<template>
  <Page>
    <Grid>
      <template #toolbar-tools>
        <Space :size="8" align="center">
          <Button type="primary" @click="openCreate"> + 新建用户 </Button>
        </Space>
      </template>
      <template #action="{ row }">
        <Space>
          <a @click="openEdit(row)">编辑</a>
          <a @click="toggleStatus(row as UserListItem)">
            {{ (row as UserListItem).isEnabled === 1 ? '禁用' : '启用' }}
          </a>
          <a @click="openReset(row as UserListItem)">重置密码</a>
          <Popconfirm title="确认删除" @confirm="deleteMut.mutate(row.id)">
            <a style="color: #ff4d4f">删除</a>
          </Popconfirm>
        </Space>
      </template>
      <template #roleNames="{ row }">
        <Space :size="4" wrap>
          <Tag
            v-for="name in (row as UserListItem).roleNames"
            :key="name"
            color="blue"
          >
            {{ name }}
          </Tag>
          <span
            v-if="!(row as UserListItem).roleNames.length"
            style="color: #999"
            >—</span>
        </Space>
      </template>
      <template #isEnabled="{ row }">
        <Tag
          :color="(row as UserListItem).isEnabled === 1 ? 'success' : 'default'"
        >
          {{ (row as UserListItem).isEnabled === 1 ? '启用' : '禁用' }}
        </Tag>
      </template>
    </Grid>
    <FormDrawer @success="gridApi.query()" />
    <ResetDrawer @success="gridApi.query()" />
  </Page>
</template>
