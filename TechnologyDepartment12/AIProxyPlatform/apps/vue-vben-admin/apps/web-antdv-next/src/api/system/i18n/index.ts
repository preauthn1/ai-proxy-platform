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

import { requestClient } from '#/api/request';

/* ============================================================
 * 语言 (i18n-locale)
 * ============================================================ */

export function fetchI18nLocaleListApi(params: I18nLocaleQuery = {}) {
  return requestClient.get<PageResult<I18nLocale>>('/system/i18n-locale/list', {
    params,
  });
}

export function fetchAllI18nLocalesApi(params?: {
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}) {
  return requestClient.get<I18nLocale[]>('/system/i18n-locale/all', {
    params: params ?? {},
  });
}

export function getI18nLocaleApi(id: number) {
  return requestClient.get<I18nLocale>(`/system/i18n-locale/${id}`);
}

export function createI18nLocaleApi(body: CreateI18nLocaleRequest) {
  return requestClient.post<I18nLocale>('/system/i18n-locale', body);
}

export function updateI18nLocaleApi(
  id: number,
  body: Partial<UpdateI18nLocaleRequest>,
) {
  return requestClient.put<I18nLocale>(`/system/i18n-locale/${id}`, body);
}

export function deleteI18nLocaleApi(id: number) {
  return requestClient.delete<unknown>(`/system/i18n-locale/${id}`);
}

export function batchI18nLocaleApi(body: {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/i18n-locale/batch', body);
}

/* ============================================================
 * 翻译 (i18n-translation)
 * ============================================================ */

export function fetchI18nTranslationListApi(params: I18nTranslationQuery = {}) {
  return requestClient.get<PageResult<I18nTranslation>>(
    '/system/i18n-translation/list',
    { params },
  );
}

/**
 * 按 translationKey 聚合的主行分页（默认视图用）。
 * 后端忽略 localeId/localeCode 参数。
 */
export function fetchI18nTranslationKeyListApi(
  params: I18nTranslationKeyQuery = {},
) {
  return requestClient.get<PageResult<I18nTranslationKey>>(
    '/system/i18n-translation/list',
    { params: { ...params, byKey: 'true' } },
  );
}

export function fetchI18nTranslationByLocaleCodeApi(code: string) {
  return requestClient.get<I18nTranslation[]>(
    `/system/i18n-translation/by-locale/${encodeURIComponent(code)}`,
  );
}

export function createI18nTranslationApi(body: CreateI18nTranslationRequest) {
  return requestClient.post<I18nTranslation>('/system/i18n-translation', body);
}

export function updateI18nTranslationApi(
  id: number,
  body: Partial<UpdateI18nTranslationRequest>,
) {
  return requestClient.put<I18nTranslation>(
    `/system/i18n-translation/${id}`,
    body,
  );
}

export function deleteI18nTranslationApi(id: number) {
  return requestClient.delete<unknown>(`/system/i18n-translation/${id}`);
}

export function batchI18nTranslationApi(body: {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}) {
  return requestClient.post<{
    action: string;
    affected: number;
    ids: number[];
  }>('/system/i18n-translation/batch', body);
}

/** 按 translation_key 聚合查询（多语言编辑抽屉打开时） */
export function fetchI18nTranslationByKeyApi(key: string) {
  return requestClient.get<I18nTranslationByKeyResponse>(
    `/system/i18n-translation/by-key/${encodeURIComponent(key)}`,
  );
}

/** 单 key 多语言事务化 upsert（多语言编辑抽屉保存时） */
export function batchUpsertI18nTranslationByKeyApi(
  body: I18nTranslationBatchUpsertByKeyRequest,
) {
  return requestClient.post<I18nTranslationBatchUpsertByKeyResponse>(
    '/system/i18n-translation/batch-upsert-by-key',
    body,
  );
}

/** 批量导出：每语言一个文件，前端用 JSZip 打包下载 */
export function exportI18nBatchApi(body: I18nExportBatchRequest) {
  return requestClient.post<I18nExportBatchResponse>(
    '/system/i18n-locale/export-batch',
    body,
  );
}

/** 批量导入（多文件、每文件独立事务） */
export function importI18nBatchApi(body: I18nImportBatchRequest) {
  return requestClient.post<I18nImportBatchResponse>(
    '/system/i18n-translation/import-batch',
    body,
  );
}

/** 导入预览：返回 currentRows 全字段，前端自己过滤分页 */
export function previewI18nImportApi(body: I18nImportPreviewRequest) {
  return requestClient.post<I18nImportPreviewResponse>(
    '/system/i18n-translation/import-preview',
    body,
  );
}

export type * from './types';
