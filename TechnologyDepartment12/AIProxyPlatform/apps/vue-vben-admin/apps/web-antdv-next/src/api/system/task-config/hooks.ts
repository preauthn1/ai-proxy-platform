import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type {
  CreateTaskConfigRequest,
  PageResult,
  TaskConfig,
  TaskConfigBatchRequest,
  TaskConfigBatchResult,
  TaskConfigQuery,
  TaskSelectOption,
  TaskTriggerResult,
  UpdateTaskConfigRequest,
} from './types';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  batchTaskConfigApi,
  createTaskConfigApi,
  deleteTaskConfigApi,
  fetchTaskConfigListApi,
  fetchTaskQueuesApi,
  fetchTaskWorkflowTypesApi,
  triggerTaskConfigApi,
  updateTaskConfigApi,
} from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

export function useListTaskConfig(
  query: MaybeRefOrGetter<TaskConfigQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<TaskConfig>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stable = unwrap(query, {} as TaskConfigQuery);
  return useQuery({
    queryKey: ['task-config', 'list', stable] as const,
    queryFn: () => fetchTaskConfigListApi(stable),
    ...options,
  });
}

export function useListTaskWorkflowTypes(
  options?: Omit<
    UseQueryOptions<TaskSelectOption[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['task-config', 'workflow-types'] as const,
    queryFn: () => fetchTaskWorkflowTypesApi(),
    staleTime: 60_000,
    ...options,
  });
}

export function useListTaskQueues(
  options?: Omit<
    UseQueryOptions<TaskSelectOption[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['task-config', 'task-queues'] as const,
    queryFn: () => fetchTaskQueuesApi(),
    staleTime: 60_000,
    ...options,
  });
}

export function useCreateTaskConfig(
  options?: UseMutationOptions<TaskConfig, Error, CreateTaskConfigRequest>,
) {
  return useMutation({
    mutationFn: (body) => createTaskConfigApi(body),
    ...options,
  });
}

export function useUpdateTaskConfig(
  options?: UseMutationOptions<TaskConfig, Error, UpdateTaskConfigRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateTaskConfigApi(req),
    ...options,
  });
}

export function useDeleteTaskConfig(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteTaskConfigApi(id),
    ...options,
  });
}

export function useBatchTaskConfig(
  options?: UseMutationOptions<
    TaskConfigBatchResult,
    Error,
    TaskConfigBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => batchTaskConfigApi(body),
    ...options,
  });
}

export function useTriggerTaskConfig(
  options?: UseMutationOptions<TaskTriggerResult, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => triggerTaskConfigApi(id),
    ...options,
  });
}
