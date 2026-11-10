import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type {
  ApiBatchRequest,
  ApiListQuery,
  ApiSyncResult,
  CreateApiRequest,
  PageResult,
  SysApi,
  UpdateApiRequest,
} from './types';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  batchApiApi,
  createApiApi,
  deleteApiApi,
  fetchAllApisApi,
  fetchApiGroupsApi,
  fetchApiListApi,
  syncApisApi,
  updateApiApi,
} from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

// =========================================================
// 列表
// =========================================================
export function useListApis(
  query: MaybeRefOrGetter<ApiListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<SysApi>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as ApiListQuery);
  return useQuery({
    queryKey: ['api', 'listApis', stable] as const,
    queryFn: () => fetchApiListApi(stable),
    ...options,
  });
}

export function useAllApis(
  options?: Omit<UseQueryOptions<SysApi[], Error>, 'queryFn' | 'queryKey'>,
) {
  return useQuery({
    queryKey: ['api', 'allApis'] as const,
    queryFn: () => fetchAllApisApi(),
    ...options,
  });
}

export function useApiGroups(
  options?: Omit<UseQueryOptions<string[], Error>, 'queryFn' | 'queryKey'>,
) {
  return useQuery({
    queryKey: ['api', 'groups'] as const,
    queryFn: () => fetchApiGroupsApi(),
    ...options,
  });
}

// =========================================================
// 新建 / 更新 / 删除 / 批量 / 同步
// =========================================================
export function useCreateApi(
  options?: UseMutationOptions<SysApi, Error, CreateApiRequest>,
) {
  return useMutation({
    mutationFn: (body) => createApiApi(body),
    ...options,
  });
}

export function useUpdateApi(
  options?: UseMutationOptions<SysApi, Error, UpdateApiRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateApiApi(req),
    ...options,
  });
}

export function useDeleteApi(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteApiApi(id),
    ...options,
  });
}

export function useBatchApi(
  options?: UseMutationOptions<
    { action: string; affected: number; ids: number[] },
    Error,
    ApiBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => batchApiApi(body),
    ...options,
  });
}

/** 同步接口 */
export function useSyncApisApi(
  options?: UseMutationOptions<ApiSyncResult, Error, void>,
) {
  return useMutation({
    mutationFn: () => syncApisApi(),
    ...options,
  });
}
