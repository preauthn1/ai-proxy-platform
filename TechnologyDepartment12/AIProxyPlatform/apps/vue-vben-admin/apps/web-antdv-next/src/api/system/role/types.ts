/**
 * 角色管理 类型定义
 * 字段对齐 backend-mock-template 的 sys_role；软删 deletedAt: 0=未删
 */

import type { SysApi } from '#/api/system/api/types';
import type { SysMenu } from '#/api/system/menu/types';

export interface SysRole {
  id: number;
  code: string;
  name: string;
  parentId: null | number;
  sort: number;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

/** 角色列表行（含 userCount/parentName join 字段） */
export interface RoleListItem extends SysRole {
  /** 关联用户数 */
  userCount: number;
  /** 父角色名 */
  parentName: null | string;
}

export interface RoleListQuery {
  page?: number;
  pageSize?: number;
  code?: string;
  name?: string;
  /** 0=禁用 1=启用 */
  status?: 0 | 1;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  parentId?: null | number;
  sort?: number;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateRoleRequest {
  id: number;
  data: {
    isEnabled?: 0 | 1;
    name?: string;
    parentId?: null | number;
    remark?: string;
    sort?: number;
  };
}

/** 角色下拉选项 */
export interface RoleOption {
  label: string;
  value: number;
}

/** 菜单授权项：全量菜单 + bound 标记 */
export interface RoleMenuBindItem extends SysMenu {
  /** 是否已绑定到当前角色 */
  bound: boolean;
}

/** 接口授权项：全量接口 + bound 标记 */
export interface RoleApiBindItem extends SysApi {
  /** 是否已绑定到当前角色 */
  bound: boolean;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
