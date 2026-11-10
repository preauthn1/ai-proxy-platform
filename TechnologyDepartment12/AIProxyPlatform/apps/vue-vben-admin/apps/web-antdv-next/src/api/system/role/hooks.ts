import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

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

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  createRoleApi,
  deleteRoleApi,
  fetchAllRolesApi,
  fetchRoleApisApi,
  fetchRoleListApi,
  fetchRoleMenusApi,
  setRoleApisApi,
  setRoleMenusApi,
  updateRoleApi,
} from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

// =========================================================
// 列表 / 全量
// =========================================================
export function useListRoles(
  query: MaybeRefOrGetter<RoleListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<RoleListItem>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as RoleListQuery);
  return useQuery({
    queryKey: ['role', 'listRoles', stable] as const,
    queryFn: () => fetchRoleListApi(stable),
    ...options,
  });
}

export function useAllRoles(
  params?: MaybeRefOrGetter<undefined | { status?: 0 | 1 }>,
  options?: Omit<UseQueryOptions<SysRole[], Error>, 'queryFn' | 'queryKey'>,
) {
  const stable = unwrap(params, {});
  return useQuery({
    queryKey: ['role', 'allRoles', stable] as const,
    queryFn: () => fetchAllRolesApi(stable),
    ...options,
  });
}

// =========================================================
// 新建 / 更新 / 删除
// =========================================================
export function useCreateRole(
  options?: UseMutationOptions<SysRole, Error, CreateRoleRequest>,
) {
  return useMutation({
    mutationFn: (body) => createRoleApi(body),
    ...options,
  });
}

export function useUpdateRole(
  options?: UseMutationOptions<SysRole, Error, UpdateRoleRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateRoleApi(req),
    ...options,
  });
}

export function useDeleteRole(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteRoleApi(id),
    ...options,
  });
}

// =========================================================
// 角色-菜单绑定
// =========================================================
export function useRoleMenus(
  id: null | number,
  options?: Omit<
    UseQueryOptions<RoleMenuBindItem[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['role', 'roleMenus', id] as const,
    queryFn: () => fetchRoleMenusApi(id as number),
    enabled: id !== null && id !== undefined,
    ...options,
  });
}

export function useSetRoleMenus(
  options?: UseMutationOptions<
    { menuIds: number[]; roleId: number },
    Error,
    { id: number; menuIds: number[] }
  >,
) {
  return useMutation({
    mutationFn: ({ id, menuIds }) => setRoleMenusApi(id, menuIds),
    ...options,
  });
}

// =========================================================
// 角色-接口绑定
// =========================================================
export function useRoleApis(
  id: null | number,
  options?: Omit<
    UseQueryOptions<RoleApiBindItem[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['role', 'roleApis', id] as const,
    queryFn: () => fetchRoleApisApi(id as number),
    enabled: id !== null && id !== undefined,
    ...options,
  });
}

export function useSetRoleApis(
  options?: UseMutationOptions<
    { apiIds: number[]; roleId: number },
    Error,
    { apiIds: number[]; id: number }
  >,
) {
  return useMutation({
    mutationFn: ({ id, apiIds }) => setRoleApisApi(id, apiIds),
    ...options,
  });
}
