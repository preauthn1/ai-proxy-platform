import type {
  CreateMenuRequest,
  MenuBatchRequest,
  MenuBindApiItem,
  MenuListQuery,
  PageResult,
  SysMenu,
  UpdateMenuRequest,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 菜单管理（sys_menu）
 * ============================================================ */

/** 分页列出菜单 */
export function fetchMenuListApi(params: MenuListQuery = {}) {
  return requestClient.get<PageResult<SysMenu>>('/system/menu/list', {
    params,
  });
}

/** 全量菜单（供父菜单下拉与前端组树） */
export function fetchAllMenusApi(params?: { status?: 0 | 1; type?: string }) {
  return requestClient.get<SysMenu[]>('/system/menu/all', {
    params: params ?? {},
  });
}

/** 新建菜单 */
export function createMenuApi(body: CreateMenuRequest) {
  return requestClient.post<SysMenu>('/system/menu', body);
}

/** 更新菜单 */
export function updateMenuApi({ id, data }: UpdateMenuRequest) {
  return requestClient.put<SysMenu>(`/system/menu/${id}`, data);
}

/** 删除菜单 */
export function deleteMenuApi(id: number) {
  return requestClient.delete<unknown>(`/system/menu/${id}`);
}

/** 批量操作菜单 */
export function batchMenuApi(body: MenuBatchRequest) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/menu/batch', body);
}

/** 读取某菜单已绑定接口（带 bound 标记） */
export function fetchMenuApisApi(id: number) {
  return requestClient.get<MenuBindApiItem[]>(`/system/menu/${id}/apis`);
}

/** 全量替换某菜单的接口绑定 */
export function setMenuApisApi(id: number, apiIds: number[]) {
  return requestClient.post<{ apiIds: number[]; menuId: number }>(
    `/system/menu/${id}/apis`,
    { apiIds },
  );
}

/**
 * 按菜单 ID 聚合 sys_menu_api → 去重 apiIds。
 * 角色授权「从已选菜单带出接口」用。
 */
export function fetchApisByMenusApi(menuIds: number[]) {
  return requestClient.post<{ apiIds: number[]; menuIds: number[] }>(
    '/system/menu/apis-by-menus',
    { menuIds },
  );
}

export type * from './types';
