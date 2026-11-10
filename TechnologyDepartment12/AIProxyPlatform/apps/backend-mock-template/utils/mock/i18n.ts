/**
 * 国际化（i18n_locale / i18n_translation）mock 数据 + 种子。
 *
 * 字段对齐 Open Design 原型 mql4ww2b-schema.sql 的 i18n_locale / i18n_translation。
 * 内部 snake 存储，handler 边界转 camel（见 utils/i18n-camel.ts）。
 */

// ============================================================
// I18n（i18n_locale / i18n_translation）
// 字段对齐 Open Design 原型 mql4ww2b-schema.sql 的 i18n_locale / i18n_translation
// ============================================================

export interface I18nLocale {
  id: number;
  code: string;
  name: string;
  is_default: 0 | 1;
  sort: number;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

export interface I18nTranslation {
  id: number;
  locale_id: number;
  translation_key: string;
  value: string;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
  /** 仅在 list 接口 join 后返回 */
  localeCode?: string;
}

/**
 * 共享的可变 i18n_locale 列表，给 system/i18n-locale 的 CRUD handler 使用。
 */
const mockI18nLocaleList: I18nLocale[] = [];

export function getMockI18nLocaleList() {
  return mockI18nLocaleList;
}

/**
 * 共享的可变 i18n_translation 列表，给 system/i18n-translation 的 CRUD handler 使用。
 */
const mockI18nTranslationList: I18nTranslation[] = [];

export function getMockI18nTranslationList() {
  return mockI18nTranslationList;
}

/**
 * 生成 mock 自增 ID（与 dict / user 列表隔离，足够 demo 使用）。
 */
export function nextI18nId(): number {
  return Date.now() * 1000 + Math.floor(Math.random() * 1000);
}

/**
 * 惰性种子：首次 list 调用时填充。
 * 三个语言（zh-CN / en-US / ja-JP），每语言 8 条常见 UI 文案翻译。
 */
function buildI18nLocaleSeeds(): I18nLocale[] {
  const now = "2025-01-01T00:00:00.000Z";
  const base: Omit<I18nLocale, "id">[] = [
    {
      code: "zh-CN",
      name: "简体中文",
      is_default: 1,
      sort: 0,
      remark: "系统默认语言",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      code: "en-US",
      name: "English",
      is_default: 0,
      sort: 1,
      remark: "English (United States)",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      code: "ja-JP",
      name: "日本語",
      is_default: 0,
      sort: 2,
      remark: "Japanese (Japan)",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
  ];
  return base.map((b, idx) => ({ id: idx + 1, ...b }));
}

function buildI18nTranslationSeeds(): I18nTranslation[] {
  const now = "2025-01-01T00:00:00.000Z";
  // 8 条常见 UI 文案
  const seeds: Array<{ key: string; zh: string; en: string; ja: string }> = [
    { key: "common.confirm", zh: "确认", en: "Confirm", ja: "確認" },
    { key: "common.cancel", zh: "取消", en: "Cancel", ja: "キャンセル" },
    { key: "common.create", zh: "新建", en: "Create", ja: "新規" },
    { key: "common.edit", zh: "编辑", en: "Edit", ja: "編集" },
    { key: "common.delete", zh: "删除", en: "Delete", ja: "削除" },
    { key: "common.search", zh: "搜索", en: "Search", ja: "検索" },
    { key: "common.save", zh: "保存", en: "Save", ja: "保存" },
    { key: "common.refresh", zh: "刷新", en: "Refresh", ja: "更新" },
  ];
  const entries: I18nTranslation[] = [];
  // locale_id 1=zh-CN, 2=en-US, 3=ja-JP
  const groups: Array<{
    localeId: number;
    localeCode: string;
    pick: (s: (typeof seeds)[number]) => string;
  }> = [
    { localeId: 1, localeCode: "zh-CN", pick: (s) => s.zh },
    { localeId: 2, localeCode: "en-US", pick: (s) => s.en },
    { localeId: 3, localeCode: "ja-JP", pick: (s) => s.ja },
  ];
  let id = 1;
  for (const g of groups) {
    for (const s of seeds) {
      entries.push({
        id: id++,
        locale_id: g.localeId,
        translation_key: s.key,
        value: g.pick(s),
        remark: "",
        is_enabled: 1,
        deleted_at: 0,
        created_at: now,
        updated_at: now,
        created_by: 0,
        updated_by: 0,
        localeCode: g.localeCode,
      });
    }
  }
  return entries;
}

/**
 * 首次访问 list 时把种子写入共享 list；之后 create/update/delete 改它，list 不会重置。
 */
export function ensureI18nSeeds(): void {
  if (mockI18nLocaleList.length === 0) {
    mockI18nLocaleList.push(...buildI18nLocaleSeeds());
  }
  if (mockI18nTranslationList.length === 0) {
    mockI18nTranslationList.push(...buildI18nTranslationSeeds());
  }
}
