import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { SysMenu } from '#/api/system/menu';

/* ============================================================
 * 菜单类型 / 状态 常量
 * ============================================================ */
export const MENU_TYPE_OPTIONS = [
  { label: 'DIR — 目录', value: 'DIR' },
  { label: 'MENU — 菜单/路由', value: 'MENU' },
  { label: 'BUTTON — 按钮', value: 'BUTTON' },
];

export const MENU_TYPE_TAG: Record<string, string> = {
  DIR: 'blue',
  MENU: 'green',
  BUTTON: 'orange',
};

export const MENU_STATUS_OPTIONS = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
];

/* ============================================================
 * 菜单列表列定义（vxe-grid 树形）
 * ============================================================ */
export function useMenuColumns(): VxeTableGridOptions['columns'] {
  return [
    // 树形展开列：treeNode 由 vxe-tree 自动渲染，需保留为第一列
    { field: 'name', title: '菜单名', treeNode: true, minWidth: 200 },
    { field: 'id', title: 'ID', width: 70 },
    { field: 'type', title: '类型', width: 90 },
    { field: 'path', title: '路由路径', minWidth: 160 },
    { field: 'permissionCode', title: '权限码', minWidth: 160 },
    { field: 'icon', title: '图标', width: 80 },
    { field: 'sort', title: '排序', width: 70 },
    { field: 'isEnabled', title: '状态', width: 80 },
    { title: '操作', fixed: 'right', width: 250, slots: { default: 'action' } },
  ];
}

/* ============================================================
 * 搜索 schema
 * ============================================================ */
export function useMenuSearchSchema(): Array<{
  component: string;
  componentProps?: Record<string, any>;
  fieldName: string;
  label: string;
}> {
  return [
    {
      component: 'Input',
      fieldName: 'name',
      label: '菜单名',
      componentProps: { placeholder: '如 用户管理' },
    },
    {
      component: 'Select',
      fieldName: 'type',
      label: '类型',
      componentProps: {
        options: MENU_TYPE_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
    {
      component: 'Input',
      fieldName: 'permissionCode',
      label: '权限码',
      componentProps: { placeholder: '如 admin:user:list' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      componentProps: {
        options: MENU_STATUS_OPTIONS,
        clearable: true,
        placeholder: '全部',
      },
    },
  ];
}

/* ============================================================
 * 把扁平菜单组树（供父菜单下拉用 label 带缩进）
 * ============================================================ */
/** 把扁平菜单组树（供父菜单下拉用 label 带缩进） */
export function buildParentOptions(
  menus: SysMenu[],
  excludeId?: number,
): Array<{ label: string; value: number }> {
  const byParent = new Map<null | number, SysMenu[]>();
  menus.forEach((m) => {
    const k = m.parentId;
    const arr = byParent.get(k) ?? [];
    arr.push(m);
    byParent.set(k, arr);
  });
  const out: Array<{ label: string; value: number }> = [];
  const walk = (parentId: null | number, depth: number) => {
    const children = (byParent.get(parentId) ?? []).filter(
      (m) => m.type !== 'BUTTON' && m.id !== excludeId,
    );
    [...children]
      .toSorted((a, b) => a.sort - b.sort || a.id - b.id)
      .forEach((m) => {
        const indent = depth === 0 ? '' : `${'　'.repeat(depth)}└ `;
        out.push({ label: `${indent}${m.name}（${m.type}）`, value: m.id });
        walk(m.id, depth + 1);
      });
  };
  walk(null, 0);
  return out;
}

/** 把扁平菜单组树为 vxe-grid 树形数据 */
export function buildMenuTree(
  list: SysMenu[],
): Array<SysMenu & { children?: SysMenu[] }> {
  const byId = new Map<number, SysMenu & { children?: SysMenu[] }>();
  list.forEach((m) => byId.set(m.id, { ...m }));
  const roots: Array<SysMenu & { children?: SysMenu[] }> = [];
  for (const node of byId.values()) {
    if (node.parentId === null || node.parentId === undefined) {
      roots.push(node);
    } else {
      const parent = byId.get(node.parentId);
      if (parent) {
        parent.children = parent.children ?? [];
        parent.children.push(node);
      } else {
        roots.push(node);
      }
    }
  }
  const sortRec = (arr: Array<SysMenu & { children?: SysMenu[] }>) => {
    arr.sort((a, b) => a.sort - b.sort || a.id - b.id);
    arr.forEach((n) => {
      if (n.children?.length) sortRec(n.children);
    });
  };
  sortRec(roots);
  return roots;
}
