/**
 * I18n（i18n_locale / i18n_translation）字段 snake ↔ camel 转换工具。
 *
 * 与 utils/dict-camel.ts 同构：内部存 snake（DB 习惯），handler 边界做一次转换。
 */

const TO_CAMEL: Record<string, string> = {
  is_default: "isDefault",
  is_enabled: "isEnabled",
  deleted_at: "deletedAt",
  created_at: "createdAt",
  updated_at: "updatedAt",
  created_by: "createdBy",
  updated_by: "updatedBy",
  locale_id: "localeId",
  translation_key: "translationKey",
};

const TO_SNAKE: Record<string, string> = Object.fromEntries(
  Object.entries(TO_CAMEL).map(([k, v]) => [v, k]),
);

/**
 * 从 raw body 中按 camelCase 字段名抽取允许的字段。
 * 同时接受 camel 与 snake 入参：camel 优先，缺失时回退 snake。
 */
export function pickI18nCamelKeys<T extends object>(
  raw: Record<string, unknown>,
  allowed: readonly string[],
): Partial<T> {
  const out: Record<string, unknown> = {};
  for (const camel of allowed) {
    if (camel in raw) {
      out[camel] = raw[camel];
      continue;
    }
    const snake = TO_SNAKE[camel];
    if (snake && snake in raw) {
      out[camel] = raw[snake];
    }
  }
  return out as Partial<T>;
}

/** 把内部 snake 行转成对外 camelCase 行；其他键保持原样。 */
export function toI18nCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}
