/**
 * 访问黑名单字段 snake ↔ camel 转换（handler 边界）。
 */

const TO_CAMEL: Record<string, string> = {
  target_type: "targetType",
  target_value: "targetValue",
  starts_at: "startsAt",
  expires_at: "expiresAt",
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
 * 从 raw body 按 camel 字段抽取；同时接受 camel 与 snake 入参。
 */
export function pickBlacklistCamelKeys<T extends object>(
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

/** 内部 snake 行 → 对外 camelCase VO */
export function toBlacklistCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}
