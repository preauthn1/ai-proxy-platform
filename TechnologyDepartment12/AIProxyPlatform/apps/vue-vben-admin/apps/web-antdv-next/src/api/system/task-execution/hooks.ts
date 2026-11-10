import type { UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type { PageResult, TaskExecution, TaskExecutionQuery } from './types';

import { useQuery } from '@tanstack/vue-query';

import { fetchTaskExecutionListApi, getTaskExecutionApi } from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

export function useListTaskExecution(
  query: MaybeRefOrGetter<TaskExecutionQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<TaskExecution>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as TaskExecutionQuery);
  return useQuery({
    queryKey: ['task-execution', 'list', stable] as const,
    queryFn: () => fetchTaskExecutionListApi(stable),
    ...options,
  });
}

export function useGetTaskExecution(
  id: MaybeRefOrGetter<null | number | undefined>,
  options?: Omit<UseQueryOptions<TaskExecution, Error>, 'queryFn' | 'queryKey'>,
) {
  const stableId = unwrap(id, undefined as null | number | undefined);
  return useQuery({
    queryKey: ['task-execution', 'detail', stableId] as const,
    queryFn: () => getTaskExecutionApi(stableId as number),
    enabled: typeof stableId === 'number' && Number.isFinite(stableId),
    ...options,
  });
}
