import type { VxeTableGridOptions } from '#/adapter/vxe-table';

/* ============================================================
 * 用户状态 常量
 * ============================================================ */
export const USER_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/* ============================================================
 * 用户列表列定义
 * ============================================================ */
export function useUserColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: 'ID', width: 70 },
    { field: 'username', title: '用户名', minWidth: 120 },
    { field: 'nickname', title: '昵称', minWidth: 120 },
    { field: 'email', title: '邮箱', minWidth: 160 },
    { field: 'phone', title: '手机号', width: 130 },
    { field: 'roleNames', title: '角色', minWidth: 160 },
    { field: 'isEnabled', title: '状态', width: 80 },
    { field: 'lastLoginAt', title: '最后登录', minWidth: 160, formatter: 'formatDateTime' },
    { title: '操作', fixed: 'right', width: 220, slots: { default: 'action' } },
  ];
}

/* ============================================================
 * 搜索 schema
 * ========================================================= */
export function useUserSearchSchema(): Array<{
  component: string;
  componentProps?: Record<string, any>;
  fieldName: string;
  label: string;
}> {
  return [
    {
      component: 'Input',
      fieldName: 'username',
      label: '用户名',
      componentProps: { placeholder: '如 admin' },
    },
    {
      component: 'Input',
      fieldName: 'nickname',
      label: '昵称',
      componentProps: { placeholder: '如 管理员' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: USER_STATUS_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
  ];
}
