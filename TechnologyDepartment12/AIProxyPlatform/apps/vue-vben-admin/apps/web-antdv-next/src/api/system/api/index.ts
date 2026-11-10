import type {
  ApiBatchRequest,
  ApiListQuery,
  ApiSyncResult,
  CreateApiRequest,
  PageResult,
  SysApi,
  UpdateApiRequest,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 接口管理（sys_api）
 * ============================================================ */

/** 分页列出接口 */
export function fetchApiListApi(params: ApiListQuery = {}) {
  return requestClient.get<PageResult<SysApi>>('/system/api/list', {
    params,
  });
}

/** 全量接口 */
export function fetchAllApisApi() {
  return requestClient.get<SysApi[]>('/system/api/all');
}

/** 去重分组列表（供分组下拉） */
export function fetchApiGroupsApi() {
  return requestClient.get<string[]>('/system/api/groups');
}

/** 新建接口 */
export function createApiApi(body: CreateApiRequest) {
  return requestClient.post<SysApi>('/system/api', body);
}

/** 更新接口 */
export function updateApiApi({ id, data }: UpdateApiRequest) {
  return requestClient.put<SysApi>(`/system/api/${id}`, data);
}

/** 删除接口 */
export function deleteApiApi(id: number) {
  return requestClient.delete<unknown>(`/system/api/${id}`);
}

/** 批量操作接口 */
export function batchApiApi(body: ApiBatchRequest) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/api/batch', body);
}

/** 同步接口（按后端路由清单 upsert） */
export function syncApisApi() {
  return requestClient.post<ApiSyncResult>('/system/api/sync');
}

export type * from './types';
