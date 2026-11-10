/**
 * 字典管理（dict-type / dict-data）字段 snake ↔ camel 转换工具。
 *
 * 设计动机：仓库历史契约用 snake（与 DB 习惯对齐），前端 TS 类型希望用 camelCase。
 * 在 handler 边界做一次转换，避免改写 mock-data 内部存储结构。
 *
 * mock-data 内部 DictData / DictType 仍保持 snake；handler 入口用 pickDictCamelKeys 抽取
 * 字段（同时接受 camel 与 snake 入参），出口用 toDictCamelRow 转回 camel。
 */

const TO_CAMEL: Record<string, string> = {
  type_id: "typeId",
  is_default: "isDefault",
  tag_type: "tagType",
  is_enabled: "isEnabled",
  deleted_at: "deletedAt",
  created_at: "createdAt",
  updated_at: "updatedAt",
  created_by: "createdBy",
  updated_by: "updatedBy",
};

const TO_SNAKE: Record<string, string> = Object.fromEntries(
  Object.entries(TO_CAMEL).map(([k, v]) => [v, k]),
);

/**
 * 从 raw body 中按 camelCase 字段名抽取允许的字段。
 * 同时接受 camel 与 snake 入参：camel 优先，缺失时回退 snake。
 */
export function pickDictCamelKeys<T extends object>(
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
export function toDictCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}
