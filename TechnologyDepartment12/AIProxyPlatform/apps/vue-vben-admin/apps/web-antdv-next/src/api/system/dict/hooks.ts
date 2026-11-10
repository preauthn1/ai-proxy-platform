import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { ComputedRef, MaybeRefOrGetter } from 'vue';

/**
 * 字典管理 vue-query hook 层
 *
 * - 自动注入 platform = VITE_APP_PLATFORM || 'general'，与后端 `?platform=` 过滤对齐；
 * - 支持调用方显式传入 platform 覆盖默认；
 * - queryKey 用最终 merged 查询，避免缓存命中失败。
 *
 * 与现有 `apps/vue-vben-admin/apps/web-antdv-next/src/api/system/dict/index.ts`
 * （裸 request 函数）并存：调用方可按需切换；index.vue 默认走原 request 路径，
 * 保留现状。
 */
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

import { computed, isRef } from 'vue';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  createDictDataApi,
  createDictTypeApi,
  deleteDictDataApi,
  deleteDictTypeApi,
  fetchDictDataListApi,
  fetchDictTypeListApi,
  updateDictDataApi,
  updateDictTypeApi,
} from '.';

/**
 * 当前前端平台标识（VITE_APP_PLATFORM）。
 * 与 backend mock 的 ?platform= 过滤对齐：dict_data.list 默认只返回
 * platform = 'general' 或 platform = currentPlatform 的项。
 * 缺省 'general'（向后兼容未配置的环境）。
 */
export const CURRENT_PLATFORM: string =
  (import.meta.env.VITE_APP_PLATFORM as string | undefined) || 'general';

// =========================================================
// 内部工具：从 MaybeRefOrGetter 解包
// =========================================================

/**
 * 解包 ref / getter / 普通值。
 *
 * 修复历史：旧版仅判断 function / RefLike(value.value)，对普通对象
 * `{ typeCode: [...], includeGeneral: true }` 会强转 `RefLike<T>` 后读
 * `.value` 取到 `undefined`，导致 useListDictData 调出的 query 丢掉
 * 调用方传入的字段（只发 `?platform=vue-admin`）。
 *
 * 当前四分支优先级：
 *   1. null / undefined → fallback
 *   2. function → 当 getter 调用
 *   3. isRef(value) → 取 value.value（vue 官方守卫，避免 unref 重载让 T 退化）
 *   4. 其他（普通对象 / 数组 / 字符串 / 数字等）→ 原样返回
 */
function unwrap<T>(value: MaybeRefOrGetter<T> | undefined, fallback: T): T {
  if (value === undefined || value === null) return fallback;
  if (typeof value === 'function') {
    return (value as () => T)();
  }
  if (isRef(value)) {
    return value.value as T;
  }
  return value;
}

// =========================================================
// 字典类型（dict-type）
// =========================================================

export function useListDictType(
  query: MaybeRefOrGetter<DictTypeQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<DictType>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  // 解包一次拿到稳定值；queryKey 依赖稳定值避免 watchEffect 反复触发。
  const stableQuery = unwrap(query, {} as DictTypeQuery);
  return useQuery({
    queryKey: ['dict', 'listDictType', stableQuery] as const,
    queryFn: () => fetchDictTypeListApi(stableQuery),
    ...options,
  });
}

export function useCreateDictType(
  options?: UseMutationOptions<DictType, Error, CreateDictTypeRequest>,
) {
  return useMutation({
    mutationFn: (body) => createDictTypeApi(body),
    ...options,
  });
}

export function useUpdateDictType(
  options?: UseMutationOptions<DictType, Error, UpdateDictTypeRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateDictTypeApi(req.id, req),
    ...options,
  });
}

export function useDeleteDictType(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteDictTypeApi(id),
    ...options,
  });
}

// =========================================================
// 字典数据（dict-data）
// =========================================================

/**
 * 自动注入 platform 默认值。
 *
 * 优先级（高到低）：
 *   1. query.platform（调用方显式传入，覆盖一切）
 *   2. CURRENT_PLATFORM = VITE_APP_PLATFORM || 'general'
 *
 * 注意：queryKey 用最终 merged 查询，避免缓存命中失败。
 */
export function useListDictData(
  query: MaybeRefOrGetter<DictDataQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<DictData>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableQuery = unwrap(query, {} as DictDataQuery);
  const merged: DictDataQuery = {
    platform: CURRENT_PLATFORM,
    ...stableQuery,
  };
  return useQuery({
    queryKey: ['dict', 'listDictData', merged] as const,
    queryFn: () => fetchDictDataListApi(merged),
    ...options,
  });
}

export function useCreateDictData(
  options?: UseMutationOptions<DictData, Error, CreateDictDataRequest>,
) {
  return useMutation({
    mutationFn: (body) => createDictDataApi(body),
    ...options,
  });
}

export function useUpdateDictData(
  options?: UseMutationOptions<DictData, Error, UpdateDictDataRequest>,
) {
  return useMutation({
    mutationFn: (req) => updateDictDataApi(req.id, req),
    ...options,
  });
}

export function useDeleteDictData(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteDictDataApi(id),
    ...options,
  });
}

// =========================================================
// 字典查表 hook（dict-lookups）
// =========================================================
//
// 设计原则：fallback 由 hook 层承担，应用层不应再写 `?? '启用'` / `?? 'success'`
// 这类散落的兜底表达式。新增 fallback 类型时改本文件即可，不扩散到业务页。
//
// 与 useListDictData 的区别：
// - useListDictData  → 薄 query wrapper,只返回原始 items,无任何 fallback
// - useDictLookups   → 在 useListDictData 之上叠加 platform-preferred 命中、
//                       fallback label / tagType、vxe-grid 友好的 valueEnum

/** DictLookups 返回类型。Vue 端用 ComputedRef 包住以跟随 reactive 数据。 */
export interface DictLookups {
  /** 把 isEnabled 0/1 翻译成 dict label;命中失败返回 '启用' / '禁用' */
  lookupSwitchLabel: (n: 0 | 1 | number) => string;
  /** 返回 dict 的 tagType;命中失败按 n 走 'success' / 'default'。不预过滤白名单。 */
  lookupSwitchTagType: (n: 0 | 1 | number) => string | undefined;
  /** 把 isDefault 0/1 翻译成 dict label;命中失败返回 '默认' / '-' */
  lookupDefaultLabel: (n: 0 | 1 | number) => string;
  /** 返回 dict 的 tagType;命中失败按 n 走 'processing' / 'default'。 */
  lookupDefaultTagType: (n: 0 | 1 | number) => string | undefined;
  /** 把 platform code 翻译成 dict label;命中失败返回原 platform（p 未传返回 '-'） */
  lookupPlatformLabel: (platform: string | undefined) => string;
  /** 返回 dict 的 tagType;命中失败返回 undefined */
  lookupPlatformTagType: (platform: string | undefined) => string | undefined;
  /** vxe-grid status 列 valueEnum（text = label） */
  switchValueEnum: ComputedRef<Record<0 | 1, { text: string }>>;
  /** 归属平台列 valueEnum；dict 命中用 dict label，否则用 platformLabels */
  platformValueEnum: ComputedRef<Record<string, { text: string }>>;
  /** 字典是否已至少拉过 1 次 */
  loaded: ComputedRef<boolean>;
}

export interface UseDictLookupsOptions {
  /** 要拉的字典类型；默认 ['sys_switch_status', 'sys_default_status', 'sys_platform'] */
  typeCodes?: string[];
  /** 是否包含 general 平台；默认 true */
  includeGeneral?: boolean;
  /** platform valueEnum 的兜底 label map */
  platformLabels?: Record<string, string>;
}

const DEFAULT_TYPE_CODES = [
  'sys_switch_status',
  'sys_default_status',
  'sys_platform',
];
const SWITCH_LABEL_FALLBACK: Record<0 | 1, string> = { 1: '启用', 0: '禁用' };
const SWITCH_TAG_TYPE_FALLBACK: Record<0 | 1, string> = {
  1: 'success',
  0: 'default',
};
const DEFAULT_LABEL_FALLBACK: Record<0 | 1, string> = { 1: '默认', 0: '-' };
const DEFAULT_TAG_TYPE_FALLBACK: Record<0 | 1, string> = {
  1: 'processing',
  0: 'default',
};
const IS_ENABLED_KEY: Record<0 | 1, 'disabled' | 'enabled'> = {
  1: 'enabled',
  0: 'disabled',
};
const IS_DEFAULT_KEY: Record<0 | 1, 'default' | 'not-default'> = {
  1: 'default',
  0: 'not-default',
};

function isEnabledKey(n: number): 'disabled' | 'enabled' {
  return IS_ENABLED_KEY[n === 1 ? 1 : 0];
}

function isDefaultKey(n: number): 'default' | 'not-default' {
  return IS_DEFAULT_KEY[n === 1 ? 1 : 0];
}

/**
 * 多平台候选中选最优一条：当前平台优先 → tagType 非空优先 → 第一项。
 */
function pickPreferred(
  candidates: DictData[],
  currentPlatform: string,
): DictData | undefined {
  return (
    candidates.find((d) => d.platform === currentPlatform) ??
    candidates.find((d) => d.tagType) ??
    candidates[0]
  );
}

/**
 * 字典查表 hook。所有 fallback 文案 / 色值集中在本 hook 内；
 * 应用层只消费 helper，不再持有 fallback 常量。
 *
 * options 接受 MaybeRefOrGetter,与其他 hook 保持一致；
 * 内部 unwrap 一次拿稳定值,避免 watchEffect 反复触发。
 */
export function useDictLookups(
  options: MaybeRefOrGetter<UseDictLookupsOptions> = {},
): DictLookups {
  const stableOptions = unwrap(options, {} as UseDictLookupsOptions);
  const typeCodes = computed<string[]>(
    () => stableOptions.typeCodes ?? DEFAULT_TYPE_CODES,
  );
  const includeGeneral = computed<boolean>(
    () => stableOptions.includeGeneral ?? true,
  );
  const platformLabels = computed<Record<string, string> | undefined>(
    () => stableOptions.platformLabels,
  );

  // 拉原始字典数据（薄包装 useListDictData，无 fallback）
  const query = useListDictData(() => ({
    typeCode: typeCodes.value,
    includeGeneral: includeGeneral.value,
  }));
  const items = computed<DictData[]>(() => query.data.value?.items ?? []);

  // 1) 按 typeCode + value 拆桶，2) 每桶用 pickPreferred 选最优一条
  // 必须 computed:items.value 后续更新时,helper 通过读 .value 拿到最新数据。
  const switchHits = computed<Map<string, DictData>>(() => {
    const byValue = new Map<string, DictData[]>();
    for (const d of items.value) {
      if (d.typeCode !== 'sys_switch_status') continue;
      const arr = byValue.get(d.value) ?? [];
      arr.push(d);
      byValue.set(d.value, arr);
    }
    const out = new Map<string, DictData>();
    for (const [v, candidates] of byValue.entries()) {
      const hit = pickPreferred(candidates, CURRENT_PLATFORM);
      if (hit) out.set(v, hit);
    }
    return out;
  });

  const platformHits = computed<Map<string, DictData>>(() => {
    const byValue = new Map<string, DictData[]>();
    for (const d of items.value) {
      if (d.typeCode !== 'sys_platform') continue;
      const arr = byValue.get(d.value) ?? [];
      arr.push(d);
      byValue.set(d.value, arr);
    }
    const out = new Map<string, DictData>();
    for (const [v, candidates] of byValue.entries()) {
      const hit = pickPreferred(candidates, CURRENT_PLATFORM);
      if (hit) out.set(v, hit);
    }
    return out;
  });

  const defaultHits = computed<Map<string, DictData>>(() => {
    const byValue = new Map<string, DictData[]>();
    for (const d of items.value) {
      if (d.typeCode !== 'sys_default_status') continue;
      const arr = byValue.get(d.value) ?? [];
      arr.push(d);
      byValue.set(d.value, arr);
    }
    const out = new Map<string, DictData>();
    for (const [v, candidates] of byValue.entries()) {
      const hit = pickPreferred(candidates, CURRENT_PLATFORM);
      if (hit) out.set(v, hit);
    }
    return out;
  });

  // 2) helper：每个 fallback 在 hook 内闭环;每次调用读 computed .value 保证反应性。
  const lookupSwitchLabel = (n: 0 | 1 | number): string => {
    const hit = switchHits.value.get(isEnabledKey(n));
    return hit?.label ?? SWITCH_LABEL_FALLBACK[n === 1 ? 1 : 0];
  };
  const lookupSwitchTagType = (n: 0 | 1 | number): string | undefined => {
    const hit = switchHits.value.get(isEnabledKey(n));
    return hit?.tagType ?? SWITCH_TAG_TYPE_FALLBACK[n === 1 ? 1 : 0];
  };
  const lookupDefaultLabel = (n: 0 | 1 | number): string => {
    const hit = defaultHits.value.get(isDefaultKey(n));
    return hit?.label ?? DEFAULT_LABEL_FALLBACK[n === 1 ? 1 : 0];
  };
  const lookupDefaultTagType = (n: 0 | 1 | number): string | undefined => {
    const hit = defaultHits.value.get(isDefaultKey(n));
    return hit?.tagType ?? DEFAULT_TAG_TYPE_FALLBACK[n === 1 ? 1 : 0];
  };
  const lookupPlatformLabel = (p: string | undefined): string => {
    if (!p) return '-';
    const hit = platformHits.value.get(p);
    return hit?.label ?? p;
  };
  const lookupPlatformTagType = (p: string | undefined): string | undefined => {
    if (!p) return undefined;
    return platformHits.value.get(p)?.tagType;
  };

  // 3) valueEnum：直接喂 vxe-grid column.valueEnum
  const switchValueEnum = computed<Record<0 | 1, { text: string }>>(() => ({
    1: { text: lookupSwitchLabel(1) },
    0: { text: lookupSwitchLabel(0) },
  }));

  const platformValueEnum = computed<Record<string, { text: string }>>(() => {
    const out: Record<string, { text: string }> = {};
    const labels = platformLabels.value ?? {};
    if (items.value.length === 0) {
      // dict 还没拉回来 → 用 platformLabels 兜底
      for (const [v, label] of Object.entries(labels)) {
        out[v] = { text: label };
      }
      return out;
    }
    // dict 已加载：优先 dict.label，缺值用 platformLabels，再缺用 raw value
    for (const [v, hit] of platformHits.value.entries()) {
      out[v] = { text: hit.label };
    }
    for (const [v, label] of Object.entries(labels)) {
      if (!out[v]) out[v] = { text: label };
    }
    return out;
  });

  const loaded = computed<boolean>(() => items.value.length > 0);

  return {
    lookupSwitchLabel,
    lookupSwitchTagType,
    lookupDefaultLabel,
    lookupDefaultTagType,
    lookupPlatformLabel,
    lookupPlatformTagType,
    switchValueEnum,
    platformValueEnum,
    loaded,
  };
}
