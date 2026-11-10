import { defineEventHandler, getQuery } from "h3";
import {
  ensureLoginLogSeeds,
  getMockSysLoginLogArchiveList,
  getMockSysLoginLogList,
  type SysLoginLog,
  type SysLoginLogArchive,
} from "~/utils/mock-data";
import { toLoginLogCamelRow } from "~/utils/login-log-camel";
import { usePageResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 登录日志分页列表（sys_login_log / sys_login_log_archive）。
 * query.source: hot | archive（默认 hot）
 * 筛选：username / success / loginMethod / loginIp / loginTimeFrom / loginTimeTo
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureLoginLogSeeds();

  const query = getQuery(event);
  const {
    page = 1,
    pageSize = 20,
    source = "hot",
    username,
    success,
    loginMethod,
    loginIp,
    loginTimeFrom,
    loginTimeTo,
  } = query;

  const isArchive = String(source) === "archive";
  let filtered: Array<SysLoginLog | SysLoginLogArchive> = isArchive
    ? [...getMockSysLoginLogArchiveList()]
    : [...getMockSysLoginLogList()];

  if (username) {
    const q = String(username).toLowerCase();
    filtered = filtered.filter((r) => r.username.toLowerCase().includes(q));
  }
  if (["0", "1"].includes(String(success))) {
    const s = Number(success);
    filtered = filtered.filter((r) => r.success === s);
  }
  if (loginMethod) {
    const m = String(loginMethod).toUpperCase();
    filtered = filtered.filter((r) => r.login_method.toUpperCase() === m);
  }
  if (loginIp) {
    const ip = String(loginIp);
    filtered = filtered.filter((r) => r.login_ip.includes(ip));
  }
  if (loginTimeFrom) {
    const from = Date.parse(String(loginTimeFrom));
    if (!Number.isNaN(from)) {
      filtered = filtered.filter((r) => Date.parse(r.login_time) >= from);
    }
  }
  if (loginTimeTo) {
    const to = Date.parse(String(loginTimeTo));
    if (!Number.isNaN(to)) {
      filtered = filtered.filter((r) => Date.parse(r.login_time) <= to);
    }
  }

  // 最新优先
  filtered.sort((a, b) => Date.parse(b.login_time) - Date.parse(a.login_time) || b.id - a.id);

  const rows = filtered.map((r) => toLoginLogCamelRow(r));
  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
