/**
 * I18n（i18n_locale / i18n_translation）类型定义
 * 字段对齐 backend-mock-template 的 schema；软删 deletedAt: 0=未删
 */

export interface I18nLocale {
  id: number;
  code: string;
  name: string;
  isDefault: 0 | 1;
  sort: number;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface I18nTranslation {
  id: number;
  localeId: number;
  translationKey: string;
  value: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
  /** 关联语言编码（仅 list 接口 join 后返回） */
  localeCode?: string;
}

export interface I18nLocaleQuery {
  page?: number;
  pageSize?: number;
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}

export interface I18nTranslationQuery {
  page?: number;
  pageSize?: number;
  localeId?: number;
  localeCode?: string;
  value?: string;
  status?: 0 | 1;
}

/**
 * 按 translationKey 聚合的主行（list 接口加 ?byKey=true 返回）。
 * sampleRowId 取同 key 下某一行 id，传给抽屉打开 byKeyQuery。
 */
export interface I18nTranslationKey {
  translationKey: string;
  localeCount: number;
  sampleRowId: number;
  sampleLocaleId: number;
  sampleLocaleCode?: string;
  sampleUpdatedAt: string;
}

export interface I18nTranslationKeyQuery {
  page?: number;
  pageSize?: number;
  /** 模糊匹配 translationKey */
  value?: string;
}

export interface CreateI18nLocaleRequest {
  code: string;
  name: string;
  sort?: number;
  remark?: string;
  isDefault?: 0 | 1;
  isEnabled?: 0 | 1;
}

export interface UpdateI18nLocaleRequest {
  id: number;
  code?: string;
  name?: string;
  sort?: number;
  remark?: string;
  isDefault?: 0 | 1;
  isEnabled?: 0 | 1;
}

export interface CreateI18nTranslationRequest {
  localeId: number;
  translationKey: string;
  value: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateI18nTranslationRequest {
  id: number;
  translationKey?: string;
  value?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

/**
 * 按 translation_key 聚合返回的多语言版本（GET /system/i18n-translation/by-key/:key）。
 */
export interface I18nTranslationByKeyValue {
  id: number;
  localeId: number;
  localeCode?: string;
  value: string;
  remark: string;
  isEnabled: 0 | 1;
}

export interface I18nTranslationByKeyResponse {
  translationKey: string;
  values: I18nTranslationByKeyValue[];
}

/**
 * 单 key 多语言事务化 upsert（POST /system/i18n-translation/batch-upsert-by-key）。
 */
export interface I18nTranslationBatchUpsertByKeyItem {
  localeId: number;
  value: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface I18nTranslationBatchUpsertByKeyRequest {
  translationKey: string;
  newTranslationKey?: string;
  items: I18nTranslationBatchUpsertByKeyItem[];
  deletedIds?: number[];
}

export interface I18nTranslationBatchUpsertError {
  code: string;
  message: string;
  localeId?: number;
  id?: number;
}

export interface I18nTranslationBatchUpsertByKeyResponse {
  ok: boolean;
  affected?: {
    created: number;
    deleted: number;
    renamed: number;
    updated: number;
  };
  values?: I18nTranslationByKeyValue[];
  errors?: I18nTranslationBatchUpsertError[];
}

/** 导出 JSON 请求参数 */
export interface I18nExportParams {
  ids: number[];
  type: 'raw' | 'simple';
}

/** raw 导出格式 */
export interface I18nRawExport {
  '@type': 'raw';
  locale: I18nLocale;
  translations: Array<{
    id?: number;
    isEnabled?: 0 | 1;
    remark?: string;
    translationKey: string;
    value: string;
  }>;
}

/** simple 导出格式：顶层为嵌套字典 */
export interface I18nSimpleExport {
  '@type': 'simple';
  [key: string]: unknown;
}

export type I18nExportData = I18nRawExport | I18nSimpleExport;

/* ============================================================
 * 批量导入（多文件）
 * ============================================================ */

export type I18nImportFormat = 'raw' | 'simple';

export interface I18nImportBatchItem {
  name: string;
  prefix?: string;
  localeCode: string;
  format: I18nImportFormat;
  payload: unknown;
}

export interface I18nImportBatchRequest {
  items: I18nImportBatchItem[];
}

export interface I18nImportBatchPerFile {
  name: string;
  ok: boolean;
  error?: string;
  createdLocales: number;
  softDeleted: number;
  createdTranslations: number;
}

export interface I18nImportBatchResponse {
  ok: boolean;
  affected: {
    createdLocales: number;
    createdTranslations: number;
    perFile: I18nImportBatchPerFile[];
    softDeleted: number;
  };
}

export interface I18nImportPreviewItem {
  localeCode: string;
  keys: string[];
}

export interface I18nImportPreviewRequest {
  items: I18nImportPreviewItem[];
}

export interface I18nImportPreviewResponse {
  currentRows: I18nTranslation[];
}

export interface I18nExportBatchRequest {
  ids: number[];
  format: I18nImportFormat;
}

export interface I18nExportBatchFile {
  code: string;
  format: I18nImportFormat;
  content: I18nRawExport | I18nSimpleExport;
}

export interface I18nExportBatchResponse {
  files: I18nExportBatchFile[];
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
