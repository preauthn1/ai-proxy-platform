import type {
  CreateDictDataRequest,
  CreateDictTypeRequest,
  DictData,
  DictDataQuery,
  DictType,
  DictTypeQuery,
  PageResult,
  UpdateDictDataRequest,
  UpdateDictTypeRequest,
} from './types';

import { requestClient } from '#/api/request';

/* ============================================================
 * 字典类型 (dict-type)
 * ============================================================ */

/** 分页列出字典类型 */
export function fetchDictTypeListApi(params: DictTypeQuery = {}) {
  return requestClient.get<PageResult<DictType>>('/system/dict-type/list', {
    params,
  });
}

/**
 * 列出全部字典类型（用于联动：把当前搜索条件下「全部命中行」的 typeCode
 * 推送给右表做多选过滤，因此接口需要支持与 list 同样的过滤项）。
 */
export function fetchAllDictTypesApi(params?: {
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}) {
  return requestClient.get<DictType[]>('/system/dict-type/all', {
    params: params ?? {},
  });
}

/** 字典类型详情 */
export function getDictTypeApi(id: number) {
  return requestClient.get<DictType>(`/system/dict-type/${id}`);
}

/** 新建字典类型 */
export function createDictTypeApi(body: CreateDictTypeRequest) {
  return requestClient.post<DictType>('/system/dict-type', body);
}

/** 更新字典类型 */
export function updateDictTypeApi(
  id: number,
  body: Partial<UpdateDictTypeRequest>,
) {
  return requestClient.put<DictType>(`/system/dict-type/${id}`, body);
}

/** 删除字典类型 */
export function deleteDictTypeApi(id: number) {
  return requestClient.delete<unknown>(`/system/dict-type/${id}`);
}

/** 批量操作字典类型 */
export function batchDictTypeApi(body: {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/dict-type/batch', body);
}

/* ============================================================
 * 字典项 (dict-data)
 * ============================================================ */

/** 分页列出字典项 */
export function fetchDictDataListApi(params: DictDataQuery = {}) {
  return requestClient.get<PageResult<DictData>>('/system/dict-data/list', {
    params,
  });
}

/** 按类型编码取所有启用字典项（下拉用） */
export function fetchDictDataByTypeCodeApi(typeCode: string) {
  return requestClient.get<DictData[]>(
    `/system/dict-data/by-type/${encodeURIComponent(typeCode)}`,
  );
}

/** 新建字典项 */
export function createDictDataApi(body: CreateDictDataRequest) {
  return requestClient.post<DictData>('/system/dict-data', body);
}

/** 更新字典项 */
export function updateDictDataApi(
  id: number,
  body: Partial<UpdateDictDataRequest>,
) {
  return requestClient.put<DictData>(`/system/dict-data/${id}`, body);
}

/** 删除字典项 */
export function deleteDictDataApi(id: number) {
  return requestClient.delete<unknown>(`/system/dict-data/${id}`);
}

/** 批量操作字典项 */
export function batchDictDataApi(body: {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/dict-data/batch', body);
}

export type * from './types';
