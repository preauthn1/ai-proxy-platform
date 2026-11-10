import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type {
  CreateUserRequest,
  PageResult,
  ResetPasswordRequest,
  ToggleUserStatusRequest,
  UpdateUserRequest,
  UserListItem,
  UserListQuery,
} from './types';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  createUserApi,
  deleteUserApi,
  fetchUserListApi,
  resetUserPasswordApi,
  toggleUserStatusApi,
  updateUserApi,
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
export function useListUsers(
  query: MaybeRefOrGetter<UserListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<UserListItem>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as UserListQuery);
  return useQuery({
    queryKey: ['user', 'listUsers', stable] as const,
    queryFn: () => fetchUserListApi(stable),
    ...options,
  });
}

// =========================================================
// 新建 / 更新 / 删除
// =========================================================
export function useCreateUser(
  options?: UseMutationOptions<UserListItem, Error, CreateUserRequest>,
) {
  return useMutation({
    mutationFn: (body) => createUserApi(body),
    ...options,
  });
}

export function useUpdateUser(
  options?: UseMutationOptions<UserListItem, Error, UpdateUserRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateUserApi(req),
    ...options,
  });
}

export function useDeleteUser(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteUserApi(id),
    ...options,
  });
}

// =========================================================
// 启停 / 重置密码
// =========================================================
export function useToggleUserStatus(
  options?: UseMutationOptions<UserListItem, Error, ToggleUserStatusRequest>,
) {
  return useMutation({
    mutationFn: (req) => toggleUserStatusApi(req),
    ...options,
  });
}

export function useResetUserPassword(
  options?: UseMutationOptions<{ id: number }, Error, ResetPasswordRequest>,
) {
  return useMutation({
    mutationFn: (req) => resetUserPasswordApi(req),
    ...options,
  });
}
