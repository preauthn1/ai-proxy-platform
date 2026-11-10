import type { VbenFormProps } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SysApi } from '#/api/system/api';

import { z } from '#/adapter/form';

/* ============================================================
 * HTTP 方法 / 状态 常量
 * ============================================================ */
export const HTTP_METHOD_OPTIONS = [
  'GET',
  'POST',
  'PUT',
  'DELETE',
  'PATCH',
  'OPTIONS',
  'HEAD',
].map((m) => ({ label: m, value: m }));

export const METHOD_COLOR: Record<string, string> = {
  GET: 'blue',
  POST: 'green',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
  OPTIONS: 'default',
  HEAD: 'default',
};

export const API_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/** 分组树节点：父行为合成分组，子行为真实接口 */
export type ApiTreeNode = {
  children?: ApiTreeNode[];
  isGroup?: boolean;
  /** 稳定行 key：分组用 group:xxx，叶子用数字 id */
  rowKey: string;
} & SysApi;

/* ============================================================
 * 接口列表列定义（vxe-grid 分组树）
 * ============================================================ */
export function useApiColumns(): VxeTableGridOptions['columns'] {
  return [
    // 树形展开列：分组父行展示组名，子行展示接口名（勿自定义 default slot，以免丢展开图标）
    { field: 'name', title: '接口名', treeNode: true, minWidth: 200 },
    { field: 'id', title: 'ID', width: 70, slots: { default: 'id' } },
    { field: 'method', title: '方法', width: 90, slots: { default: 'method' } },
    { field: 'path', title: '路径', minWidth: 220, slots: { default: 'path' } },
    {
      field: 'permissionCode',
      title: '权限码',
      minWidth: 160,
      slots: { default: 'permissionCode' },
    },
    {
      field: 'isEnabled',
      title: '状态',
      width: 80,
      slots: { default: 'isEnabled' },
    },
    {
      field: 'createdAt',
      title: '创建时间',
      width: 170,
      slots: { default: 'createdAt' },
    },
    { title: '操作', fixed: 'right', width: 140, slots: { default: 'action' } },
  ];
}

/* ============================================================
 * 搜索 schema
 * ============================================================ */
export function useApiSearchSchema(): VbenFormProps['schema'] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: '接口名',
      componentProps: { placeholder: '请输入' },
    },
    {
      component: 'Input',
      fieldName: 'path',
      label: '路径',
      componentProps: { placeholder: '如 /api/system/user/list' },
    },
    {
      component: 'Select',
      fieldName: 'method',
      label: '方法',
      componentProps: {
        options: HTTP_METHOD_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
    {
      component: 'Input',
      fieldName: 'group',
      label: '分组',
      componentProps: { placeholder: '如 用户管理' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: API_STATUS_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
  ];
}

/* ============================================================
 * 接口 form schema（抽屉内表单）
 * ============================================================ */
export function useApiFormSchema(): VbenFormProps['schema'] {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: '接口名',
      rules: z.string().min(1, '请输入接口名').max(64, '最长 64 字符'),
      componentProps: { placeholder: '展示用，如 用户分页列表' },
    },
    {
      component: 'Select',
      fieldName: 'method',
      label: 'HTTP 方法',
      defaultValue: 'GET',
      rules: z.string().min(1, '请选择 HTTP 方法'),
      componentProps: { options: HTTP_METHOD_OPTIONS },
    },
    {
      component: 'Input',
      fieldName: 'apiGroup',
      label: '分组',
      componentProps: { placeholder: '选已有或输入新分组，如 用户管理' },
    },
    {
      component: 'Input',
      fieldName: 'path',
      label: '路径',
      rules: z.string().min(1, '请输入路径'),
      componentProps: { placeholder: '如 /api/system/user/:id' },
    },
    {
      component: 'Input',
      fieldName: 'permissionCode',
      label: '权限码',
      rules: z.string().min(1, '请输入权限码'),
      componentProps: { placeholder: '如 system:user:list' },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: { placeholder: '选填', rows: 3 },
    },
    {
      component: 'Switch',
      fieldName: 'isEnabled',
      label: '启用',
      // Switch checked 用 boolean；提交时再映射 0|1
      defaultValue: true,
    },
  ];
}

/* ============================================================
 * 按 apiGroup 合成分组树（供 vxe-grid 树形折叠）
 * 空分组名归为「未分组」。
 * 列表接口按「分组」分页：list 应已是当前页各组的完整接口集合。
 * ============================================================ */
export function buildApiGroupTree(list: SysApi[]): ApiTreeNode[] {
  const groups = new Map<string, SysApi[]>();
  list.forEach((a) => {
    const g = a.apiGroup?.trim() || '未分组';
    const arr = groups.get(g) ?? [];
    arr.push(a);
    groups.set(g, arr);
  });

  // 分组父节点使用负 id，避免与真实接口 id 冲突
  let groupSeq = -1;

  return [...groups.entries()]
    .toSorted((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
    .map(([groupName, apis]) => {
      const children: ApiTreeNode[] = [...apis]
        .toSorted((a, b) => a.id - b.id)
        .map((a) => ({
          ...a,
          rowKey: String(a.id),
        }));

      const parentId = groupSeq--;
      return {
        id: parentId,
        name: `${groupName}（${children.length}）`,
        method: 'GET',
        path: '',
        permissionCode: '',
        apiGroup: groupName,
        remark: '',
        isEnabled: 1,
        deletedAt: 0,
        createdAt: '',
        updatedAt: '',
        isGroup: true,
        rowKey: `group:${groupName}`,
        children,
      } satisfies ApiTreeNode;
    });
}
