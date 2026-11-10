/**
 * 菜单管理 类型定义
 * 字段对齐 backend-mock-template 的 sys_menu；软删 deletedAt: 0=未删
 */

export type MenuType = 'BUTTON' | 'DIR' | 'MENU';

export interface SysMenu {
  id: number;
  parentId: null | number;
  name: string;
  type: MenuType;
  path: null | string;
  component: null | string;
  icon: string;
  redirect: string;
  permissionCode: null | string;
  /** 物化路径，如 /1/11/ */
  treePath: string;
  /** 前端扩展 JSON 字符串（badge/hideInBreadcrumb/keepAlive/affix/activeMenu） */
  metadata: null | string;
  sort: number;
  isHidden: 0 | 1;
  isEnabled: 0 | 1;
  deletedAt: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface MenuListQuery {
  page?: number;
  pageSize?: number;
  name?: string;
  type?: MenuType;
  permissionCode?: string;
  status?: 0 | 1;
}

export interface CreateMenuRequest {
  parentId?: null | number;
  name: string;
  type: MenuType;
  path?: null | string;
  component?: null | string;
  icon?: string;
  redirect?: string;
  permissionCode?: null | string;
  metadata?: null | string;
  sort?: number;
  isHidden?: 0 | 1;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateMenuRequest {
  id: number;
  data: Partial<CreateMenuRequest>;
}

export interface MenuBatchRequest {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}

export interface MenuBindApiItem {
  id: number;
  name: string;
  method: string;
  path: string;
  permissionCode: string;
  apiGroup: string;
  isEnabled: 0 | 1;
  /** 是否已绑定到当前菜单 */
  bound: boolean;
}

export interface PageResult<T> {
  items: T[];
  /** 分页总数：菜单管理为「最外层根节点数」 */
  total: number;
  /**
   * 菜单条数（筛选范围内完整子树合计）。
   * 仅按根分页接口会返回；普通分页可不传。
   */
  itemTotal?: number;
}
