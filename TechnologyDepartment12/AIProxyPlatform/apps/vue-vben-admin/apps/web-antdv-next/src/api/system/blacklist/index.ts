import type {
  BlacklistBatchRequest,
  BlacklistListQuery,
  CreateBlacklistRequest,
  PageResult,
  SysBlacklist,
  UpdateBlacklistRequest,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 访问黑名单（sys_blacklist）
 * ============================================================ */

/** 分页列出黑名单 */
export function fetchBlacklistListApi(params: BlacklistListQuery = {}) {
  return requestClient.get<PageResult<SysBlacklist>>('/system/blacklist/list', {
    params,
  });
}

/** 全量黑名单（支持与 list 相同过滤） */
export function fetchAllBlacklistApi(
  params?: Omit<BlacklistListQuery, 'page' | 'pageSize'>,
) {
  return requestClient.get<SysBlacklist[]>('/system/blacklist/all', {
    params: params ?? {},
  });
}

/** 黑名单详情 */
export function getBlacklistApi(id: number) {
  return requestClient.get<SysBlacklist>(`/system/blacklist/${id}`);
}

/** 新建黑名单 */
export function createBlacklistApi(body: CreateBlacklistRequest) {
  return requestClient.post<SysBlacklist>('/system/blacklist', body);
}

/** 更新黑名单 */
export function updateBlacklistApi({ id, data }: UpdateBlacklistRequest) {
  return requestClient.put<SysBlacklist>(`/system/blacklist/${id}`, data);
}

/** 软删黑名单 */
export function deleteBlacklistApi(id: number) {
  return requestClient.delete<SysBlacklist>(`/system/blacklist/${id}`);
}

/** 批量 enable | disable | delete */
export function batchBlacklistApi(body: BlacklistBatchRequest) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/blacklist/batch', body);
}

export type * from './types';
