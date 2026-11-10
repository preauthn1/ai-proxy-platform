/**
 * 菜单/接口管理（sys_menu / sys_api）字段 snake ↔ camel 转换工具。
 *
 * 设计与 utils/dict-camel.ts 一致：mock-data 内部 snake 存储，
 * handler 入口用 pickCamelKeys 抽取字段（同时接受 camel 与 snake），
 * 出口用 toCamelRow 转回 camel。
 */

const TO_CAMEL: Record<string, string> = {
  parent_id: "parentId",
  permission_code: "permissionCode",
  tree_path: "treePath",
  is_hidden: "isHidden",
  is_enabled: "isEnabled",
  deleted_at: "deletedAt",
  created_at: "createdAt",
  updated_at: "updatedAt",
  created_by: "createdBy",
  updated_by: "updatedBy",
  api_group: "apiGroup",
  menu_id: "menuId",
  api_id: "apiId",
};

const TO_SNAKE: Record<string, string> = Object.fromEntries(
  Object.entries(TO_CAMEL).map(([k, v]) => [v, k]),
);

/**
 * 从 raw body 中按 camelCase 字段名抽取允许的字段。
 * 同时接受 camel 与 snake 入参：camel 优先，缺失时回退 snake。
 */
export function pickCamelKeys<T extends object>(
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
export function toCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}
