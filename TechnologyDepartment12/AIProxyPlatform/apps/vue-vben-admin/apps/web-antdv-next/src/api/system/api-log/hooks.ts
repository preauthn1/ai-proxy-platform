import type { UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type { ApiLogListItem, ApiLogListQuery, PageResult } from './types';

import { useQuery } from '@tanstack/vue-query';

import { fetchApiLogListApi } from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

export function useListApiLogs(
  query: MaybeRefOrGetter<ApiLogListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<ApiLogListItem>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['listApiLogs', query],
    queryFn: () => fetchApiLogListApi(unwrap(query, {})),
    ...options,
  });
}
