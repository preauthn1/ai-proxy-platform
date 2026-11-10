/**
 * 用户管理 类型定义
 * 字段对齐 backend-mock-template 的 sys_user；软删 deletedAt: 0=未删
 */

export interface UserListItem {
  id: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  avatar: string;
  /** 默认语言 code，如 zh-CN */
  languageCode: null | string;
  lastLoginAt: null | string;
  lastLoginIp: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  /** 关联角色 id 列表 */
  roleIds: number[];
  /** 关联角色名列表（join 后返回，仅展示用） */
  roleNames: string[];
}

export interface UserListQuery {
  page?: number;
  pageSize?: number;
  username?: string;
  nickname?: string;
  /** 0=禁用 1=启用 */
  status?: 0 | 1;
  roleId?: number;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  nickname: string;
  email?: string;
  phone?: string;
  avatar?: string;
  languageCode?: string;
  isEnabled?: 0 | 1;
  roleIds?: number[];
  remark?: string;
}

export interface UpdateUserRequest {
  id: number;
  data: {
    avatar?: string;
    email?: string;
    isEnabled?: 0 | 1;
    languageCode?: string;
    nickname?: string;
    phone?: string;
    remark?: string;
    roleIds?: number[];
  };
}

export interface ToggleUserStatusRequest {
  id: number;
  status: 0 | 1;
}

export interface ResetPasswordRequest {
  id: number;
  password: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
