import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { RoleListItem } from '#/api/system/role';

/* ============================================================
 * 角色状态 常量
 * ============================================================ */
export const ROLE_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/* ============================================================
 * 角色列表列定义
 * ============================================================ */
export function useRoleColumns(): VxeTableGridOptions['columns'] {
  return [
    { field: 'id', title: 'ID', width: 70 },
    { field: 'code', title: '编码', minWidth: 140 },
    { field: 'name', title: '角色名', minWidth: 140 },
    { field: 'parentName', title: '父角色', minWidth: 120 },
    { field: 'sort', title: '排序', width: 70 },
    { field: 'userCount', title: '用户数', width: 80 },
    { field: 'isEnabled', title: '状态', width: 80 },
    { title: '操作', fixed: 'right', width: 200, slots: { default: 'action' } },
  ];
}

/* ============================================================
 * 搜索 schema
 * ========================================================= */
export function useRoleSearchSchema(): Array<{
  component: string;
  componentProps?: Record<string, any>;
  fieldName: string;
  label: string;
}> {
  return [
    {
      component: 'Input',
      fieldName: 'code',
      label: '编码',
      componentProps: { placeholder: '如 operator' },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '角色名',
      componentProps: { placeholder: '如 超级管理员' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: ROLE_STATUS_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
  ];
}

/* ============================================================
 * 父角色下拉（排除自己，避免成环）
 * ============================================================ */
export function buildRoleParentOptions(
  roles: RoleListItem[],
  excludeId?: number,
): Array<{ label: string; value: number }> {
  return roles
    .filter((r) => r.id !== excludeId)
    .toSorted((a, b) => a.sort - b.sort || a.id - b.id)
    .map((r) => ({ label: r.name, value: r.id }));
}
