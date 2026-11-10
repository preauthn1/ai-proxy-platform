import type {
  CreateRoleRequest,
  PageResult,
  RoleApiBindItem,
  RoleListItem,
  RoleListQuery,
  RoleMenuBindItem,
  SysRole,
  UpdateRoleRequest,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 角色管理（sys_role）
 * ============================================================ */

/** 分页列出角色 */
export function fetchRoleListApi(params: RoleListQuery = {}) {
  return requestClient.get<PageResult<RoleListItem>>('/system/role/list', {
    params,
  });
}

/** 全量角色（供用户表单的角色下拉用） */
export function fetchAllRolesApi(params?: { status?: 0 | 1 }) {
  return requestClient.get<SysRole[]>('/system/role/all', {
    params: params ?? {},
  });
}

/** 新建角色 */
export function createRoleApi(body: CreateRoleRequest) {
  return requestClient.post<SysRole>('/system/role', body);
}

/** 更新角色 */
export function updateRoleApi({ id, data }: UpdateRoleRequest) {
  return requestClient.put<SysRole>(`/system/role/${id}`, data);
}

/** 删除角色（软删） */
export function deleteRoleApi(id: number) {
  return requestClient.delete<unknown>(`/system/role/${id}`);
}

/** 读取角色已绑定菜单（带 bound 标记） */
export function fetchRoleMenusApi(id: number) {
  return requestClient.get<RoleMenuBindItem[]>(`/system/role/${id}/menus`);
}

/** 全量替换角色菜单授权 */
export function setRoleMenusApi(id: number, menuIds: number[]) {
  return requestClient.post<{ menuIds: number[]; roleId: number }>(
    `/system/role/${id}/menus`,
    { menuIds },
  );
}

/** 读取角色已绑定接口（带 bound 标记） */
export function fetchRoleApisApi(id: number) {
  return requestClient.get<RoleApiBindItem[]>(`/system/role/${id}/apis`);
}

/** 全量替换角色接口授权 */
export function setRoleApisApi(id: number, apiIds: number[]) {
  return requestClient.post<{ apiIds: number[]; roleId: number }>(
    `/system/role/${id}/apis`,
    { apiIds },
  );
}

export type * from './types';
