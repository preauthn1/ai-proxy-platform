import { defineEventHandler, getQuery } from "h3";
import {
  ensureApiLogSeeds,
  getMockApiLogArchiveList,
  getMockApiLogList,
  type ApiLog,
  type ApiLogArchive,
} from "~/utils/mock-data";
import { toApiLogCamelRow } from "~/utils/api-log-camel";
import { usePageResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * API 调用日志分页列表（api_log / api_log_archive）。
 * query.source: hot | archive（默认 hot）
 * 筛选：method / module / path / success / statusCode / username / clientIp / requestId / createdAtFrom / createdAtTo
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureApiLogSeeds();

  const query = getQuery(event);
  const {
    page = 1,
    pageSize = 20,
    source = "hot",
    method,
    module,
    path,
    success,
    statusCode,
    username,
    clientIp,
    requestId,
    createdAtFrom,
    createdAtTo,
  } = query;

  const isArchive = String(source) === "archive";
  let filtered: Array<ApiLog | ApiLogArchive> = isArchive
    ? [...getMockApiLogArchiveList()]
    : [...getMockApiLogList()];

  if (method) {
    const m = String(method).toUpperCase();
    filtered = filtered.filter((r) => r.method.toUpperCase() === m);
  }
  if (module) {
    const q = String(module).toLowerCase();
    filtered = filtered.filter((r) => r.module.toLowerCase().includes(q));
  }
  if (path) {
    const p = String(path).toLowerCase();
    filtered = filtered.filter((r) => r.path.toLowerCase().includes(p));
  }
  if (["0", "1"].includes(String(success))) {
    const s = Number(success);
    filtered = filtered.filter((r) => r.success === s);
  }
  if (statusCode !== undefined && statusCode !== null && String(statusCode) !== "") {
    const code = Number(statusCode);
    if (!Number.isNaN(code)) {
      filtered = filtered.filter((r) => r.status_code === code);
    }
  }
  if (username) {
    const q = String(username).toLowerCase();
    filtered = filtered.filter((r) => r.username.toLowerCase().includes(q));
  }
  if (clientIp) {
    const ip = String(clientIp);
    filtered = filtered.filter((r) => r.client_ip.includes(ip));
  }
  if (requestId) {
    const rid = String(requestId).toLowerCase();
    filtered = filtered.filter((r) => r.request_id.toLowerCase().includes(rid));
  }
  if (createdAtFrom) {
    const from = Date.parse(String(createdAtFrom));
    if (!Number.isNaN(from)) {
      filtered = filtered.filter((r) => Date.parse(r.created_at) >= from);
    }
  }
  if (createdAtTo) {
    const to = Date.parse(String(createdAtTo));
    if (!Number.isNaN(to)) {
      filtered = filtered.filter((r) => Date.parse(r.created_at) <= to);
    }
  }

  // 最新优先
  filtered.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id);

  const rows = filtered.map((r) => toApiLogCamelRow(r));
  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
