import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type {
  BlacklistBatchRequest,
  BlacklistListQuery,
  CreateBlacklistRequest,
  PageResult,
  SysBlacklist,
  UpdateBlacklistRequest,
} from './types';

import { isRef } from 'vue';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  batchBlacklistApi,
  createBlacklistApi,
  deleteBlacklistApi,
  fetchAllBlacklistApi,
  fetchBlacklistListApi,
  getBlacklistApi,
  updateBlacklistApi,
} from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  if (isRef(value)) return value.value as T;
  return value;
}

// =========================================================
// 列表 / 全量 / 详情
// =========================================================

export function useListBlacklist(
  query: MaybeRefOrGetter<BlacklistListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<SysBlacklist>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as BlacklistListQuery);
  return useQuery({
    queryKey: ['blacklist', 'list', stable] as const,
    queryFn: () => fetchBlacklistListApi(stable),
    ...options,
  });
}

export function useAllBlacklist(
  query: MaybeRefOrGetter<Omit<BlacklistListQuery, 'page' | 'pageSize'>> = {},
  options?: Omit<
    UseQueryOptions<SysBlacklist[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {});
  return useQuery({
    queryKey: ['blacklist', 'all', stable] as const,
    queryFn: () => fetchAllBlacklistApi(stable),
    ...options,
  });
}

export function useBlacklistDetail(
  id: MaybeRefOrGetter<number | undefined>,
  options?: Omit<
    UseQueryOptions<SysBlacklist, Error>,
    'enabled' | 'queryFn' | 'queryKey'
  >,
) {
  const stableId = unwrap(id, undefined);
  return useQuery({
    queryKey: ['blacklist', 'detail', stableId] as const,
    queryFn: () => getBlacklistApi(stableId as number),
    enabled: typeof stableId === 'number' && Number.isFinite(stableId),
    ...options,
  });
}

// =========================================================
// 新建 / 更新 / 删除 / 批量
// =========================================================

export function useCreateBlacklist(
  options?: UseMutationOptions<SysBlacklist, Error, CreateBlacklistRequest>,
) {
  return useMutation({
    mutationFn: (body) => createBlacklistApi(body),
    ...options,
  });
}

export function useUpdateBlacklist(
  options?: UseMutationOptions<SysBlacklist, Error, UpdateBlacklistRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateBlacklistApi(req),
    ...options,
  });
}

export function useDeleteBlacklist(
  options?: UseMutationOptions<SysBlacklist, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteBlacklistApi(id),
    ...options,
  });
}

export function useBatchBlacklist(
  options?: UseMutationOptions<
    { action: string; affected: number; ids: number[] },
    Error,
    BlacklistBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => batchBlacklistApi(body),
    ...options,
  });
}
