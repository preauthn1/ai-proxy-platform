import type { UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

import type { LoginLogListItem, LoginLogListQuery, PageResult } from './types';

import { useQuery } from '@tanstack/vue-query';

import { fetchLoginLogListApi } from '.';

function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') return (value as () => T)();
  const v = value as { value?: T };
  return (v && 'value' in v ? v.value : (value as T)) ?? fallback;
}

export function useListLoginLogs(
  query: MaybeRefOrGetter<LoginLogListQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<LoginLogListItem>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  return useQuery({
    queryKey: ['listLoginLogs', query],
    queryFn: () => fetchLoginLogListApi(unwrap(query, {})),
    ...options,
  });
}
