/**
 * 登录日志 — sys_login_log / sys_login_log_archive mock 数据 + 种子。
 *
 * 字段对齐 schema.sql v5；只增不改；login 成功/失败均写热表。
 * parseUserAgent / hoursAgo / daysAgo 由 mock-shared 提供。
 */

import { hoursAgo, daysAgo, parseUserAgent } from "./shared";

// ============================================================
// 登录日志 — sys_login_log / sys_login_log_archive
// 字段对齐 schema.sql v5；只增不改；login 成功/失败均写热表。
// ============================================================

export type LoginMethod = "PASSWORD" | "SSO" | "OAUTH" | "SMS";

export interface SysLoginLog {
  id: number;
  username: string;
  success: 0 | 1;
  reason: string;
  status_code: null | number;
  sys_user_id: null | number;
  login_method: LoginMethod;
  login_time: string;
  login_ip: string;
  login_mac: string;
  client_id: string;
  client_name: string;
  user_agent: string;
  browser_name: string;
  browser_version: string;
  os_name: string;
  os_version: string;
  location: string;
  created_at: string;
}

export interface SysLoginLogArchive extends SysLoginLog {
  archived_at: string;
}

export interface AppendLoginLogInput {
  username: string;
  success: 0 | 1;
  reason?: string;
  statusCode?: null | number;
  sysUserId?: null | number;
  loginMethod?: LoginMethod;
  loginIp?: string;
  loginMac?: string;
  clientId?: string;
  clientName?: string;
  userAgent?: string;
  browserName?: string;
  browserVersion?: string;
  osName?: string;
  osVersion?: string;
  location?: string;
  loginTime?: string;
}

const mockSysLoginLogList: SysLoginLog[] = [];
const mockSysLoginLogArchiveList: SysLoginLogArchive[] = [];

export function getMockSysLoginLogList() {
  return mockSysLoginLogList;
}

export function getMockSysLoginLogArchiveList() {
  return mockSysLoginLogArchiveList;
}

let sysLoginLogIdSeq = 0;
function nextSysLoginLogId(): number {
  sysLoginLogIdSeq += 1;
  return sysLoginLogIdSeq;
}

let sysLoginLogArchiveIdSeq = 0;
function nextSysLoginLogArchiveId(): number {
  sysLoginLogArchiveIdSeq += 1;
  return sysLoginLogArchiveIdSeq;
}

/** 追加一条热表登录日志（只增不改）；写入前确保种子已就绪 */
export function appendLoginLog(input: AppendLoginLogInput): SysLoginLog {
  ensureLoginLogSeeds();
  const now = input.loginTime ?? new Date().toISOString();
  const ua = input.userAgent ?? "";
  const parsed = parseUserAgent(ua);
  const row: SysLoginLog = {
    id: nextSysLoginLogId(),
    username: input.username,
    success: input.success,
    reason: input.reason ?? "",
    status_code: input.statusCode ?? (input.success === 1 ? 200 : 403),
    sys_user_id: input.sysUserId ?? null,
    login_method: input.loginMethod ?? "PASSWORD",
    login_time: now,
    login_ip: input.loginIp ?? "",
    login_mac: input.loginMac ?? "",
    client_id: input.clientId ?? "web-admin",
    client_name: input.clientName ?? "Web Admin",
    user_agent: ua,
    browser_name: input.browserName ?? parsed.browser_name,
    browser_version: input.browserVersion ?? parsed.browser_version,
    os_name: input.osName ?? parsed.os_name,
    os_version: input.osVersion ?? parsed.os_version,
    location: input.location ?? "Mock-City",
    created_at: now,
  };
  mockSysLoginLogList.push(row);
  return row;
}

function buildLoginLogSeeds(): SysLoginLog[] {
  const chromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
  const macUa =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15";
  const seeds: Omit<SysLoginLog, "id">[] = [
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 1,
      login_method: "PASSWORD",
      login_time: hoursAgo(1),
      login_ip: "10.0.0.12",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(1),
    },
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 2,
      login_method: "PASSWORD",
      login_time: hoursAgo(3),
      login_ip: "10.0.0.21",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: macUa,
      browser_name: "Safari",
      browser_version: "17.2",
      os_name: "macOS",
      os_version: "14.3",
      location: "Mock-City",
      created_at: hoursAgo(3),
    },
    {
      username: "root",
      success: 0,
      reason: "Username or password is incorrect.",
      status_code: 403,
      sys_user_id: null,
      login_method: "PASSWORD",
      login_time: hoursAgo(5),
      login_ip: "203.0.113.8",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(5),
    },
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 3,
      login_method: "PASSWORD",
      login_time: hoursAgo(8),
      login_ip: "10.0.0.33",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(8),
    },
    {
      username: "unknown",
      success: 0,
      reason: "Username or password is incorrect.",
      status_code: 403,
      sys_user_id: null,
      login_method: "PASSWORD",
      login_time: hoursAgo(12),
      login_ip: "198.51.100.4",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(12),
    },
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 1,
      login_method: "SSO",
      login_time: hoursAgo(24),
      login_ip: "10.0.0.12",
      login_mac: "",
      client_id: "sso-portal",
      client_name: "SSO Portal",
      user_agent: chromeUa,
      browser_name: "Chrome",
      browser_version: "122.0.0.0",
      os_name: "Windows",
      os_version: "10/11",
      location: "Mock-City",
      created_at: hoursAgo(24),
    },
    {
      username: "root",
      success: 0,
      reason: "Username and password are required",
      status_code: 400,
      sys_user_id: null,
      login_method: "PASSWORD",
      login_time: hoursAgo(30),
      login_ip: "10.0.0.99",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
      user_agent: "",
      browser_name: "",
      browser_version: "",
      os_name: "",
      os_version: "",
      location: "Mock-City",
      created_at: hoursAgo(30),
    },
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 2,
      login_method: "OAUTH",
      login_time: hoursAgo(48),
      login_ip: "10.0.0.21",
      login_mac: "",
      client_id: "oauth-app",
      client_name: "OAuth App",
      user_agent: macUa,
      browser_name: "Safari",
      browser_version: "17.2",
      os_name: "macOS",
      os_version: "14.3",
      location: "Mock-City",
      created_at: hoursAgo(48),
    },
  ];
  return seeds.map((s) => {
    const id = nextSysLoginLogId();
    return { id, ...s };
  });
}

function buildLoginLogArchiveSeeds(): SysLoginLogArchive[] {
  const chromeUa =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  const seeds: Omit<SysLoginLogArchive, "id">[] = [
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 1,
      login_method: "PASSWORD",
      login_time: daysAgo(45),
      login_ip: "10.0.0.12",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
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
      username: "root",
      success: 0,
      reason: "Username or password is incorrect.",
      status_code: 403,
      sys_user_id: null,
      login_method: "PASSWORD",
      login_time: daysAgo(50),
      login_ip: "203.0.113.20",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
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
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 3,
      login_method: "SMS",
      login_time: daysAgo(60),
      login_ip: "10.0.0.33",
      login_mac: "",
      client_id: "mobile-app",
      client_name: "Mobile App",
      user_agent: "MockMobile/1.0",
      browser_name: "Unknown",
      browser_version: "",
      os_name: "Android",
      os_version: "14",
      location: "Mock-City",
      created_at: daysAgo(60),
      archived_at: daysAgo(20),
    },
    {
      username: "root",
      success: 1,
      reason: "",
      status_code: 200,
      sys_user_id: 1,
      login_method: "PASSWORD",
      login_time: daysAgo(70),
      login_ip: "10.0.0.12",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
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
      username: "ghost",
      success: 0,
      reason: "Username or password is incorrect.",
      status_code: 403,
      sys_user_id: null,
      login_method: "PASSWORD",
      login_time: daysAgo(80),
      login_ip: "198.51.100.99",
      login_mac: "",
      client_id: "web-admin",
      client_name: "Web Admin",
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
    const id = nextSysLoginLogArchiveId();
    return { id, ...s };
  });
}

/** 是否已完成种子装载（与 list 长度解耦，避免登录先写导致种子被跳过） */
let loginLogSeedsReady = false;

/** 确保登录日志种子已写入（幂等）。 */
export function ensureLoginLogSeeds(): void {
  if (loginLogSeedsReady) return;
  loginLogSeedsReady = true;
  if (mockSysLoginLogList.length === 0) {
    mockSysLoginLogList.push(...buildLoginLogSeeds());
  }
  if (mockSysLoginLogArchiveList.length === 0) {
    mockSysLoginLogArchiveList.push(...buildLoginLogArchiveSeeds());
  }
}
