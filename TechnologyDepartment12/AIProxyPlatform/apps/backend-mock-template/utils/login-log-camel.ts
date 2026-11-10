/**
 * sys_login_log / sys_login_log_archive 字段 snake ↔ camel。
 * mock 内部 snake 存储，handler 出口转 camel。
 */

const TO_CAMEL: Record<string, string> = {
  status_code: "statusCode",
  sys_user_id: "sysUserId",
  login_method: "loginMethod",
  login_time: "loginTime",
  login_ip: "loginIp",
  login_mac: "loginMac",
  client_id: "clientId",
  client_name: "clientName",
  user_agent: "userAgent",
  browser_name: "browserName",
  browser_version: "browserVersion",
  os_name: "osName",
  os_version: "osVersion",
  created_at: "createdAt",
  archived_at: "archivedAt",
};

/** 内部 snake 行 → 对外 camelCase */
export function toLoginLogCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}
