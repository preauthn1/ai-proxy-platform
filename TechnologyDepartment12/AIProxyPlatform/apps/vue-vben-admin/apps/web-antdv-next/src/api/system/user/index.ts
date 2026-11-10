import type {
  CreateUserRequest,
  PageResult,
  ResetPasswordRequest,
  ToggleUserStatusRequest,
  UpdateUserRequest,
  UserListItem,
  UserListQuery,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 用户管理（sys_user）
 * ============================================================ */

/** 分页列出用户 */
export function fetchUserListApi(params: UserListQuery = {}) {
  return requestClient.get<PageResult<UserListItem>>('/system/user/list', {
    params,
  });
}

/** 新建用户 */
export function createUserApi(body: CreateUserRequest) {
  return requestClient.post<UserListItem>('/system/user', body);
}

/** 更新用户 */
export function updateUserApi({ id, data }: UpdateUserRequest) {
  return requestClient.put<UserListItem>(`/system/user/${id}`, data);
}

/** 删除用户（软删） */
export function deleteUserApi(id: number) {
  return requestClient.delete<unknown>(`/system/user/${id}`);
}

/** 启停用户 */
export function toggleUserStatusApi({ id, status }: ToggleUserStatusRequest) {
  return requestClient.put<UserListItem>(`/system/user/${id}/status`, {
    status,
  });
}

/** 重置用户密码 */
export function resetUserPasswordApi({ id, password }: ResetPasswordRequest) {
  return requestClient.post<{ id: number }>(`/system/user/${id}/password`, {
    password,
  });
}

export type * from './types';
