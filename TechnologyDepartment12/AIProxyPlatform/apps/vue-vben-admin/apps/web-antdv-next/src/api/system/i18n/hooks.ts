import type { UseMutationOptions, UseQueryOptions } from '@tanstack/vue-query';

import type { MaybeRefOrGetter } from 'vue';

/**
 * I18n（i18n-locale / i18n-translation）vue-query hook 层。
 *
 * 对齐本端 dict / role / menu / user 等模块的分层：index.ts 暴露裸 request 函数，
 * hooks.ts 提供 vue-query 封装，统一 queryKey 与缓存失效入口。
 *
 * 与 `./index.ts`（裸 request 函数）并存：
 *   - 列表分页查询（index.vue 的 vxe-grid proxyConfig）走裸函数，
 *     遵循本端既定列表查询约定，不强行改；
 *   - 全量下拉 / 详情 / mutation / 批量 / 导入导出 / byKey 走 hooks，
 *     与 dict/role 端 form.vue 调用模式一致。
 */
import type {
  CreateI18nLocaleRequest,
  CreateI18nTranslationRequest,
  I18nExportBatchRequest,
  I18nExportBatchResponse,
  I18nImportBatchRequest,
  I18nImportBatchResponse,
  I18nImportPreviewRequest,
  I18nImportPreviewResponse,
  I18nLocale,
  I18nLocaleQuery,
  I18nTranslation,
  I18nTranslationBatchUpsertByKeyRequest,
  I18nTranslationBatchUpsertByKeyResponse,
  I18nTranslationByKeyResponse,
  I18nTranslationKey,
  I18nTranslationKeyQuery,
  I18nTranslationQuery,
  PageResult,
  UpdateI18nLocaleRequest,
  UpdateI18nTranslationRequest,
} from './types';

import { isRef } from 'vue';

import { useMutation, useQuery } from '@tanstack/vue-query';

import {
  batchI18nLocaleApi,
  batchI18nTranslationApi,
  batchUpsertI18nTranslationByKeyApi,
  createI18nLocaleApi,
  createI18nTranslationApi,
  deleteI18nLocaleApi,
  deleteI18nTranslationApi,
  exportI18nBatchApi,
  fetchAllI18nLocalesApi,
  fetchI18nLocaleListApi,
  fetchI18nTranslationByKeyApi,
  fetchI18nTranslationByLocaleCodeApi,
  fetchI18nTranslationKeyListApi,
  fetchI18nTranslationListApi,
  getI18nLocaleApi,
  importI18nBatchApi,
  previewI18nImportApi,
  updateI18nLocaleApi,
  updateI18nTranslationApi,
} from '.';

// =========================================================
// 内部工具：从 MaybeRefOrGetter 解包
// =========================================================

/**
 * 解包 ref / getter / 普通值。
 * 与 dict/hooks.ts 的 unwrap 同语义，确保调用方传 reactive query 时
 * queryKey 依赖稳定值，避免 watchEffect 反复触发。
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
// 语言（i18n-locale）
// =========================================================

export function useListI18nLocale(
  query: MaybeRefOrGetter<I18nLocaleQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<I18nLocale>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableQuery = unwrap(query, {} as I18nLocaleQuery);
  return useQuery({
    queryKey: ['i18n', 'listLocale', stableQuery] as const,
    queryFn: () => fetchI18nLocaleListApi(stableQuery),
    ...options,
  });
}

export function useListAllI18nLocale(
  params?: MaybeRefOrGetter<
    undefined | { code?: string | string[]; name?: string; status?: 0 | 1 }
  >,
  options?: Omit<UseQueryOptions<I18nLocale[], Error>, 'queryFn' | 'queryKey'>,
) {
  const stableParams = unwrap(params, undefined);
  return useQuery({
    queryKey: ['i18n', 'allLocale', stableParams] as const,
    queryFn: () => fetchAllI18nLocalesApi(stableParams),
    ...options,
  });
}

export function useGetI18nLocale(
  id: MaybeRefOrGetter<null | number | undefined>,
  options?: Omit<UseQueryOptions<I18nLocale, Error>, 'queryFn' | 'queryKey'>,
) {
  const stableId = unwrap(id, undefined);
  return useQuery({
    queryKey: ['i18n', 'locale', stableId] as const,
    queryFn: () => getI18nLocaleApi(stableId as number),
    enabled: stableId !== null && stableId !== undefined,
    ...options,
  });
}

export function useCreateI18nLocale(
  options?: UseMutationOptions<I18nLocale, Error, CreateI18nLocaleRequest>,
) {
  return useMutation({
    mutationFn: (body) => createI18nLocaleApi(body),
    ...options,
  });
}

export function useUpdateI18nLocale(
  options?: UseMutationOptions<
    I18nLocale,
    Error,
    { data: UpdateI18nLocaleRequest; id: number }
  >,
) {
  return useMutation({
    mutationFn: (req) => updateI18nLocaleApi(req.id, req.data),
    ...options,
  });
}

export function useDeleteI18nLocale(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteI18nLocaleApi(id),
    ...options,
  });
}

export function useBatchI18nLocale(
  options?: UseMutationOptions<
    { action: string; affected: number; ids: number[] },
    Error,
    { action: 'delete' | 'disable' | 'enable'; ids: number[] }
  >,
) {
  return useMutation({
    mutationFn: (body) => batchI18nLocaleApi(body),
    ...options,
  });
}

// =========================================================
// 翻译（i18n-translation）
// =========================================================

export function useListI18nTranslation(
  query: MaybeRefOrGetter<I18nTranslationQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<I18nTranslation>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableQuery = unwrap(query, {} as I18nTranslationQuery);
  return useQuery({
    queryKey: ['i18n', 'listTranslation', stableQuery] as const,
    queryFn: () => fetchI18nTranslationListApi(stableQuery),
    ...options,
  });
}

export function useListI18nTranslationKey(
  query: MaybeRefOrGetter<I18nTranslationKeyQuery> = {},
  options?: Omit<
    UseQueryOptions<PageResult<I18nTranslationKey>, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableQuery = unwrap(query, {} as I18nTranslationKeyQuery);
  return useQuery({
    queryKey: ['i18n', 'listTranslationKey', stableQuery] as const,
    queryFn: () => fetchI18nTranslationKeyListApi(stableQuery),
    ...options,
  });
}

export function useListI18nTranslationByLocaleCode(
  code: MaybeRefOrGetter<null | string | undefined>,
  options?: Omit<
    UseQueryOptions<I18nTranslation[], Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableCode = unwrap(code, undefined);
  return useQuery({
    queryKey: ['i18n', 'translationByLocale', stableCode] as const,
    queryFn: () => fetchI18nTranslationByLocaleCodeApi(stableCode as string),
    enabled: !!stableCode,
    ...options,
  });
}

export function useGetI18nTranslationByKey(
  key: MaybeRefOrGetter<null | string | undefined>,
  options?: Omit<
    UseQueryOptions<I18nTranslationByKeyResponse, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableKey = unwrap(key, undefined);
  return useQuery({
    queryKey: ['i18n', 'translationByKey', stableKey] as const,
    queryFn: () => fetchI18nTranslationByKeyApi(stableKey as string),
    enabled: !!stableKey,
    ...options,
  });
}

export function useCreateI18nTranslation(
  options?: UseMutationOptions<
    I18nTranslation,
    Error,
    CreateI18nTranslationRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => createI18nTranslationApi(body),
    ...options,
  });
}

export function useUpdateI18nTranslation(
  options?: UseMutationOptions<
    I18nTranslation,
    Error,
    { data: UpdateI18nTranslationRequest; id: number }
  >,
) {
  return useMutation({
    mutationFn: (req) => updateI18nTranslationApi(req.id, req.data),
    ...options,
  });
}

export function useDeleteI18nTranslation(
  options?: UseMutationOptions<unknown, Error, number>,
) {
  return useMutation({
    mutationFn: (id) => deleteI18nTranslationApi(id),
    ...options,
  });
}

export function useBatchI18nTranslation(
  options?: UseMutationOptions<
    { action: string; affected: number; ids: number[] },
    Error,
    { action: 'delete' | 'disable' | 'enable'; ids: number[] }
  >,
) {
  return useMutation({
    mutationFn: (body) => batchI18nTranslationApi(body),
    ...options,
  });
}

export function useBatchUpsertI18nTranslationByKey(
  options?: UseMutationOptions<
    I18nTranslationBatchUpsertByKeyResponse,
    Error,
    I18nTranslationBatchUpsertByKeyRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => batchUpsertI18nTranslationByKeyApi(body),
    ...options,
  });
}

// =========================================================
// 导出 / 导入 / 预览
// =========================================================

export function useExportI18nBatch(
  options?: UseMutationOptions<
    I18nExportBatchResponse,
    Error,
    I18nExportBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => exportI18nBatchApi(body),
    ...options,
  });
}

export function useImportI18nBatch(
  options?: UseMutationOptions<
    I18nImportBatchResponse,
    Error,
    I18nImportBatchRequest
  >,
) {
  return useMutation({
    mutationFn: (body) => importI18nBatchApi(body),
    ...options,
  });
}

export function usePreviewI18nImport(
  body: MaybeRefOrGetter<I18nImportPreviewRequest | undefined>,
  options?: Omit<
    UseQueryOptions<I18nImportPreviewResponse, Error>,
    'queryFn' | 'queryKey'
  >,
) {
  const stableBody = unwrap(body, undefined);
  return useQuery({
    queryKey: ['i18n', 'importPreview', stableBody] as const,
    queryFn: () => previewI18nImportApi(stableBody as I18nImportPreviewRequest),
    enabled: !!stableBody,
    ...options,
  });
}
