import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type {
  CreateMenuRequest,
  MenuBatchRequest,
  MenuBindApiItem,
  MenuListQuery,
  PageResult,
  SysMenu,
  UpdateMenuRequest,
} from './types';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  batchMenuApi,
  createMenuApi,
  deleteMenuApi,
  fetchAllMenusApi,
  fetchMenuApisApi,
  fetchMenuListApi,
  setMenuApisApi,
  updateMenuApi,
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
export function useListMenus(
  query: MaybeRefOrGetter<MenuListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<SysMenu>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as MenuListQuery);
  return useQuery({
    queryKey: ['menu', 'listMenus', stable] as const,
    queryFn: () => fetchMenuListApi(stable),
    ...options,
  });
}

export function useAllMenus(
  options?: Omit<UseQueryOptions<SysMenu[], Error>, 'queryFn' | 'queryKey'>,
) {
  return useQuery({
    queryKey: ['menu', 'allMenus'] as const,
    queryFn: () => fetchAllMenusApi(),
    ...options,
  });
}

// =========================================================
// 新建 / 更新 / 删除 / 批量
// =========================================================
export function useCreateMenu(
  options?: UseMutationOptions<SysMenu, Error, CreateMenuRequest>,
) {
  return useMutation({
    mutationFn: (body) => createMenuApi(body),
    ...options,
  });
}

export function useUpdateMenu(
  options?: UseMutationOptions<SysMenu, Error, UpdateMenuRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateMenuApi(req),
    ...options,
  });
}

export function useDeleteMenu(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteMenuApi(id),
    ...options,
  });
}

export function useBatchMenu(
  options?: UseMutationOptions<
    { action: string; affected: number; ids: number[] },
    Error,
    MenuBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => batchMenuApi(body),
    ...options,
  });
}

// =========================================================
// 菜单-接口绑定
// =========================================================
export function useMenuApis(
  id: null | number,
  options?: Omit<
    UseQueryOptions<MenuBindApiItem[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['menu', 'menuApis', id] as const,
    queryFn: () => fetchMenuApisApi(id as number),
    enabled: id !== null && id !== undefined,
    ...options,
  });
}

export function useSetMenuApis(
  options?: UseMutationOptions<
    { apiIds: number[]; menuId: number },
    Error,
    { apiIds: number[]; id: number }
  >,
) {
  return useMutation({
    mutationFn: ({ id, apiIds }) => setMenuApisApi(id, apiIds),
    ...options,
  });
}
