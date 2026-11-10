/**
 * API 调用日志 — api_log / api_log_archive mock 数据 + 种子。
 *
 * 字段对齐 schema.sql v5；只增不改；列表/详情只读。
 * parseUserAgent / hoursAgo / daysAgo / makeRequestId 由 mock-shared 提供。
 */

import { hoursAgo, daysAgo, makeRequestId, parseUserAgent } from "./shared";

// ============================================================
// API 调用日志 — api_log / api_log_archive
// 字段对齐 schema.sql v5；只增不改；列表/详情只读。
// ============================================================

export interface ApiLog {
  id: number;
  method: string;
  module: string;
  path: string;
  status_code: null | number;
  success: 0 | 1;
  reason: string;
  cost_time: number;
  request_id: string;
  sys_user_id: null | number;
  username: string;
  request_uri: string;
  request_query: string;
  request_body: string;
  request_header: string;
  referer: string;
  response: string;
  before_change: string;
  after_change: string;
  format_change: string;
  client_id: string;
  client_name: string;
  client_ip: string;
  user_agent: string;
  browser_name: string;
  browser_version: string;
  os_name: string;
  os_version: string;
  location: string;
  created_at: string;
}

export interface ApiLogArchive extends ApiLog {
  archived_at: string;
}

export interface AppendApiLogInput {
  method: string;
  path: string;
  module?: string;
  statusCode?: null | number;
  success?: 0 | 1;
  reason?: string;
  costTime?: number;
  requestId?: string;
  sysUserId?: null | number;
  username?: string;
  requestUri?: string;
  requestQuery?: string;
  requestBody?: string;
  requestHeader?: string;
  referer?: string;
  response?: string;
  beforeChange?: string;
  afterChange?: string;
  formatChange?: string;
  clientId?: string;
  clientName?: string;
  clientIp?: string;
  userAgent?: string;
  browserName?: string;
  browserVersion?: string;
  osName?: string;
  osVersion?: string;
  location?: string;
  createdAt?: string;
}

const mockApiLogList: ApiLog[] = [];
const mockApiLogArchiveList: ApiLogArchive[] = [];

export function getMockApiLogList() {
  return mockApiLogList;
}

export function getMockApiLogArchiveList() {
  return mockApiLogArchiveList;
}

let apiLogIdSeq = 0;
function nextApiLogId(): number {
  apiLogIdSeq += 1;
  return apiLogIdSeq;
}

let apiLogArchiveIdSeq = 0;
function nextApiLogArchiveId(): number {
  apiLogArchiveIdSeq += 1;
  return apiLogArchiveIdSeq;
}

/** 追加一条热表 API 日志（只增不改）；写入前确保种子已就绪 */
export function appendApiLog(input: AppendApiLogInput): ApiLog {
  ensureApiLogSeeds();
  const now = input.createdAt ?? new Date().toISOString();
  const ua = input.userAgent ?? "";
  const parsed = parseUserAgent(ua);
  const status = input.statusCode ?? 200;
  const success: 0 | 1 =
    input.success ?? (status !== null && status >= 200 && status < 300 ? 1 : 0);
  const id = nextApiLogId();
  const row: ApiLog = {
    id,
    method: (input.method || "GET").toUpperCase(),
    module: input.module ?? "",
    path: input.path,
    status_code: status,
    success,
    reason: input.reason ?? "",
    cost_time: input.costTime ?? 0,
    request_id: input.requestId ?? makeRequestId("req", id),
    sys_user_id: input.sysUserId ?? null,
    username: input.username ?? "",
    request_uri: input.requestUri ?? input.path,
    request_query: input.requestQuery ?? "",
    request_body: input.requestBody ?? "",
    request_header: input.requestHeader ?? "",
    referer: input.referer ?? "",
    response: input.response ?? "",
    before_change: input.beforeChange ?? "",
    after_change: input.afterChange ?? "",
    format_change: input.formatChange ?? "",
    client_id: input.clientId ?? "web-admin",
    client_name: input.clientName ?? "Web Admin",
    client_ip: input.clientIp ?? "",
    user_agent: ua,
    browser_name: input.browserName ?? parsed.browser_name,
    browser_version: input.browserVersion ?? parsed.browser_version,
    os_name: input.osName ?? parsed.os_name,
    os_version: input.osVersion ?? parsed.os_version,
    location: input.location ?? "Mock-City",
    created_at: now,
  };
  mockApiLogList.push(row);
  return row;
}

function buildApiLogSeeds(): ApiLog[] {
  const chromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
  const macUa =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15";
  const seeds: Omit<ApiLog, "id">[] = [
    {
      method: "GET",
      module: "user",
      path: "/api/system/user/list",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 42,
      request_id: "req-hot-0001-user-list",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/user/list?page=1&pageSize=20",
      request_query: "page=1&pageSize=20",
      request_body: "",
      request_header: '{"accept":"application/json","authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/user",
      response: '{"code":0,"data":{"items":[],"total":0}}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(1),
    },
    {
      method: "POST",
      module: "user",
      path: "/api/system/user",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 128,
      request_id: "req-hot-0002-user-create",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/user",
      request_query: "",
      request_body: '{"username":"demo","nickname":"Demo"}',
      request_header: '{"content-type":"application/json","authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/user",
      response: '{"code":0,"data":{"id":99}}',
      before_change: "",
      after_change: '{"id":99,"username":"demo","nickname":"Demo"}',
      format_change: "username: →demo; nickname: →Demo",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(2),
    },
    {
      method: "PUT",
      module: "role",
      path: "/api/system/role/2",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 85,
      request_id: "req-hot-0003-role-update",
      sys_user_id: 2,
      username: "root",
      request_uri: "/api/system/role/2",
      request_query: "",
      request_body: '{"name":"管理员","isEnabled":1}',
      request_header: '{"content-type":"application/json","authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/role",
      response: '{"code":0,"data":true}',
      before_change: '{"name":"Admin","isEnabled":1}',
      after_change: '{"name":"管理员","isEnabled":1}',
      format_change: "name: Admin→管理员",
      client_id: "web-admin-react",
      client_name: "React Admin",
      client_ip: "10.0.0.21",
      user_agent: macUa,
      browser_name: "Safari",
      browser_version: "17.2",
      os_name: "macOS",
      os_version: "14.3",
      location: "Mock-City",
      created_at: hoursAgo(4),
    },
    {
      method: "DELETE",
      module: "menu",
      path: "/api/system/menu/999",
      status_code: 404,
      success: 0,
      reason: "Menu not found",
      cost_time: 18,
      request_id: "req-hot-0004-menu-delete",
      sys_user_id: 2,
      username: "root",
      request_uri: "/api/system/menu/999",
      request_query: "",
      request_body: "",
      request_header: '{"authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/menu",
      response: '{"code":404,"message":"Menu not found"}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-react",
      client_name: "React Admin",
      client_ip: "10.0.0.21",
      user_agent: macUa,
      browser_name: "Safari",
      browser_version: "17.2",
      os_name: "macOS",
      os_version: "14.3",
      location: "Mock-City",
      created_at: hoursAgo(6),
    },
    {
      method: "GET",
      module: "auth",
      path: "/api/auth/userinfo",
      status_code: 401,
      success: 0,
      reason: "Token expired",
      cost_time: 5,
      request_id: "req-hot-0005-auth-userinfo",
      sys_user_id: null,
      username: "",
      request_uri: "/api/auth/userinfo",
      request_query: "",
      request_body: "",
      request_header: '{"authorization":"Bearer expired"}',
      referer: "",
      response: '{"code":401,"message":"Unauthorized"}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "203.0.113.8",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(8),
    },
    {
      method: "GET",
      module: "dict",
      path: "/api/system/dict-data/list",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 33,
      request_id: "req-hot-0006-dict-list",
      sys_user_id: 3,
      username: "root",
      request_uri: "/api/system/dict-data/list?typeCode=user_status",
      request_query: "typeCode=user_status",
      request_body: "",
      request_header: '{"accept":"application/json","authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/dict",
      response: '{"code":0,"data":{"items":[{"value":"1","label":"启用"}]}}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.33",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(12),
    },
    {
      method: "POST",
      module: "login-log",
      path: "/api/system/login-log/list",
      status_code: 405,
      success: 0,
      reason: "Method Not Allowed",
      cost_time: 2,
      request_id: "req-hot-0007-wrong-method",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/login-log/list",
      request_query: "",
      request_body: "{}",
      request_header: '{"content-type":"application/json"}',
      referer: "http://localhost:5173/log?tab=login",
      response: '{"code":405,"message":"Method Not Allowed"}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(24),
    },
    {
      method: "GET",
      module: "menu",
      path: "/api/system/menu/all",
      status_code: 500,
      success: 0,
      reason: "Internal Server Error",
      cost_time: 1200,
      request_id: "req-hot-0008-menu-all-err",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/menu/all",
      request_query: "",
      request_body: "",
      request_header: '{"authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/menu",
      response: '{"code":500,"message":"Internal Server Error"}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(36),
    },
  ];
  return seeds.map((s) => {
    const id = nextApiLogId();
    return { id, ...s };
  });
}

function buildApiLogArchiveSeeds(): ApiLogArchive[] {
  const chromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  const seeds: Omit<ApiLogArchive, "id">[] = [
    {
      method: "GET",
      module: "user",
      path: "/api/system/user/list",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 55,
      request_id: "req-arc-0001-user-list",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/user/list?page=1",
      request_query: "page=1",
      request_body: "",
      request_header: '{"authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/user",
      response: '{"code":0,"data":{"items":[],"total":3}}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "120.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: daysAgo(45),
      archived_at: daysAgo(15),
    },
    {
      method: "POST",
      module: "auth",
      path: "/api/auth/login",
      status_code: 403,
      success: 0,
      reason: "Invalid credentials",
      cost_time: 30,
      request_id: "req-arc-0002-login-fail",
      sys_user_id: null,
      username: "ghost",
      request_uri: "/api/auth/login",
      request_query: "",
      request_body: '{"username":"ghost","password":"***"}',
      request_header: '{"content-type":"application/json"}',
      referer: "http://localhost:5173/auth/login",
      response: '{"code":403,"message":"Invalid credentials"}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      client_ip: "198.51.100.99",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "120.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: daysAgo(50),
      archived_at: daysAgo(15),
    },
    {
      method: "PUT",
      module: "dict",
      path: "/api/system/dict-type/1",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 70,
      request_id: "req-arc-0003-dict-update",
      sys_user_id: 2,
      username: "root",
      request_uri: "/api/system/dict-type/1",
      request_query: "",
      request_body: '{"name":"用户状态"}',
      request_header: '{"content-type":"application/json"}',
      referer: "http://localhost:5173/system/dict",
      response: '{"code":0,"data":true}',
      before_change: '{"name":"User Status"}',
      after_change: '{"name":"用户状态"}',
      format_change: "name: User Status→用户状态",
      client_id: "web-admin-react",
      client_name: "React Admin",
      client_ip: "10.0.0.21",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "120.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: daysAgo(60),
      archived_at: daysAgo(20),
    },
    {
      method: "DELETE",
      module: "role",
      path: "/api/system/role/9",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 95,
      request_id: "req-arc-0004-role-delete",
      sys_user_id: 1,
      username: "root",
      request_uri: "/api/system/role/9",
      request_query: "",
      request_body: "",
      request_header: '{"authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/role",
      response: '{"code":0,"data":true}',
      before_change: '{"id":9,"code":"temp"}',
      after_change: "",
      format_change: "deleted role temp",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.12",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "120.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: daysAgo(70),
      archived_at: daysAgo(25),
    },
    {
      method: "GET",
      module: "i18n",
      path: "/api/system/i18n-locale/list",
      status_code: 200,
      success: 1,
      reason: "",
      cost_time: 22,
      request_id: "req-arc-0005-i18n-list",
      sys_user_id: 3,
      username: "root",
      request_uri: "/api/system/i18n-locale/list",
      request_query: "",
      request_body: "",
      request_header: '{"authorization":"Bearer ***"}',
      referer: "http://localhost:5173/system/i18n",
      response: '{"code":0,"data":{"items":[{"code":"zh-CN"}]}}',
      before_change: "",
      after_change: "",
      format_change: "",
      client_id: "web-admin-vue3",
      client_name: "Vue Admin",
      client_ip: "10.0.0.33",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "120.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: daysAgo(80),
      archived_at: daysAgo(30),
    },
  ];
  return seeds.map((s) => {
    const id = nextApiLogArchiveId();
    return { id, ...s };
  });
}

let apiLogSeedsReady = false;

/** 确保 API 日志种子已写入（幂等）。 */
export function ensureApiLogSeeds(): void {
  if (apiLogSeedsReady) return;
  apiLogSeedsReady = true;
  if (mockApiLogList.length === 0) {
    mockApiLogList.push(...buildApiLogSeeds());
  }
  if (mockApiLogArchiveList.length === 0) {
    mockApiLogArchiveList.push(...buildApiLogArchiveSeeds());
  }
}
