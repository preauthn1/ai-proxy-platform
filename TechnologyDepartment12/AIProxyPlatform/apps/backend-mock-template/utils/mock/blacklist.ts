/**
 * 访问黑名单（sys_blacklist）mock 数据 + 命中判定 + CRUD 领域逻辑。
 *
 * 对齐 Java BlacklistService / Filter：
 * - target: IP | USER | DEVICE；scope: LOGIN | API | ALL
 * - 运行时仅查 IP + session USER；DEVICE 不参与命中
 * - 同窗活跃重复拒绝；时间窗重叠允许（OR）
 * - Access Blocked：code=2005，固定文案，不回传 reason
 */

import { isoNow } from "./shared";

// ============================================================
// 类型与常量
// ============================================================

export const TARGET_TYPES = ["IP", "USER", "DEVICE"] as const;
export type BlacklistTargetType = (typeof TARGET_TYPES)[number];

export const SCOPES = ["LOGIN", "API", "ALL"] as const;
export type BlacklistScope = (typeof SCOPES)[number];

/** 与 java-admin ResultCode.ACCESS_BLOCKED 对齐 */
export const ACCESS_BLOCKED_CODE = 2005;
export const ACCESS_BLOCKED_MSG = "Access Blocked";

export interface SysBlacklist {
  id: number;
  target_type: BlacklistTargetType;
  target_value: string;
  scope: BlacklistScope;
  reason: string;
  /** ISO 时间；含边界 */
  starts_at: string;
  /** ISO 时间；null=永不过期；不含边界 */
  expires_at: string | null;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

export interface BlacklistHit {
  targetType: string;
  targetValue: string;
  scope: string;
  reason: string;
}

export type BlacklistResult<T> = { ok: true; data: T } | { ok: false; status: number; msg: string };

export interface BlacklistCreateInput {
  targetType?: string;
  targetValue?: string;
  scope?: string;
  reason?: string;
  startsAt?: string | null;
  expiresAt?: string | null;
  remark?: string;
  isEnabled?: number | boolean | null;
}

export interface BlacklistUpdateInput {
  targetType?: string | null;
  targetValue?: string | null;
  scope?: string | null;
  reason?: string | null;
  startsAt?: string | null;
  expiresAt?: string | null;
  clearExpiresAt?: boolean | null;
  remark?: string | null;
  isEnabled?: number | boolean | null;
}

export interface BlacklistListFilter {
  targetType?: string | null;
  targetValue?: string | null;
  scope?: string | null;
  status?: number | string | null;
}

// ============================================================
// 内存存储
// ============================================================

const mockBlacklistList: SysBlacklist[] = [];

export function getMockBlacklistList(): SysBlacklist[] {
  return mockBlacklistList;
}

/** 测试用：清空共享列表 */
export function resetBlacklistForTest(): void {
  mockBlacklistList.length = 0;
}

export function nextBlacklistId(): number {
  return Date.now() * 1000 + Math.floor(Math.random() * 1000);
}

/** 惰性种子（演示一条永久 IP 禁用样例，默认关闭避免误拦本地联调） */
function buildBlacklistSeeds(): SysBlacklist[] {
  const now = "2025-01-01T00:00:00.000Z";
  return [
    {
      id: 1,
      target_type: "IP",
      target_value: "203.0.113.10",
      scope: "ALL",
      reason: "demo seed (disabled)",
      starts_at: now,
      expires_at: null,
      remark: "mock seed — is_enabled=0，不参与拦截",
      is_enabled: 0,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
  ];
}

export function ensureBlacklistSeeds(): void {
  if (mockBlacklistList.length === 0) {
    mockBlacklistList.push(...buildBlacklistSeeds());
  }
}

// ============================================================
// 规范化 / 校验
// ============================================================

function trimToNull(value: unknown): string | null {
  if (value == null) return null;
  if (typeof value !== "string" && typeof value !== "number" && typeof value !== "boolean") {
    return null;
  }
  const s = String(value).trim();
  return s === "" ? null : s;
}

export function normalizeTargetType(raw: unknown): string | null {
  const t = trimToNull(raw);
  return t == null ? null : t.toUpperCase();
}

export function normalizeScope(raw: unknown): string {
  const t = trimToNull(raw);
  return t == null ? "ALL" : t.toUpperCase();
}

/**
 * 规范化 target_value：USER/DEVICE 原样 trim；IP 去端口并小写化。
 * 对齐 Java BlacklistManageModels.normalizeTargetValue。
 */
export function normalizeTargetValue(targetType: string, raw: unknown): string | null {
  const t = trimToNull(raw);
  if (t == null) return null;
  let value = t;
  if (targetType === "IP") {
    if (value.startsWith("[") && value.includes("]:")) {
      value = value.slice(1, value.indexOf("]:"));
    } else if ((value.match(/:/g) ?? []).length === 1 && value.includes(".")) {
      value = value.slice(0, value.indexOf(":"));
    }
    return value.toLowerCase();
  }
  return value;
}

function requireTargetType(raw: unknown): BlacklistResult<BlacklistTargetType> {
  const type = normalizeTargetType(raw);
  if (type == null || !(TARGET_TYPES as readonly string[]).includes(type)) {
    return { ok: false, status: 400, msg: "targetType must be IP|USER|DEVICE" };
  }
  return { ok: true, data: type as BlacklistTargetType };
}

function requireScope(raw: unknown): BlacklistResult<BlacklistScope> {
  const scope = normalizeScope(raw);
  if (!(SCOPES as readonly string[]).includes(scope)) {
    return { ok: false, status: 400, msg: "scope must be LOGIN|API|ALL" };
  }
  return { ok: true, data: scope as BlacklistScope };
}

function requireTargetValue(targetType: string, raw: unknown): BlacklistResult<string> {
  const value = normalizeTargetValue(targetType, raw);
  if (value == null) {
    return { ok: false, status: 400, msg: "targetValue is required" };
  }
  if (value.length > 128) {
    return { ok: false, status: 400, msg: "targetValue must be ≤ 128 chars" };
  }
  return { ok: true, data: value };
}

function clip(value: string | null | undefined, max: number): string {
  const s = value ?? "";
  return s.length <= max ? s : s.slice(0, max);
}

function normalize01(value: number | boolean | null | undefined, defaultValue: 0 | 1): 0 | 1 {
  if (value === undefined || value === null) return defaultValue;
  const n = Number(value);
  return n === 0 ? 0 : 1;
}

function parseIsoOrNull(raw: unknown): string | null {
  if (raw === undefined || raw === null || raw === "") return null;
  if (typeof raw !== "string" && typeof raw !== "number") return null;
  const s = String(raw).trim();
  if (!s) return null;
  const t = Date.parse(s);
  if (!Number.isFinite(t)) return null;
  return new Date(t).toISOString();
}

function sameExpires(a: string | null, b: string | null): boolean {
  if (a == null && b == null) return true;
  if (a == null || b == null) return false;
  return Date.parse(a) === Date.parse(b);
}

function sameStarts(a: string, b: string): boolean {
  return Date.parse(a) === Date.parse(b);
}

function validateWindow(startsAt: string, expiresAt: string | null): BlacklistResult<true> {
  if (!startsAt) {
    return { ok: false, status: 400, msg: "startsAt is required" };
  }
  if (expiresAt != null && !(Date.parse(expiresAt) > Date.parse(startsAt))) {
    return { ok: false, status: 400, msg: "expiresAt must be after startsAt" };
  }
  return { ok: true, data: true };
}

function existsExactWindow(
  targetType: string,
  targetValue: string,
  scope: string,
  startsAt: string,
  expiresAt: string | null,
  excludeId: number | null,
): boolean {
  return mockBlacklistList.some(
    (row) =>
      row.deleted_at === 0 &&
      (excludeId == null || row.id !== excludeId) &&
      row.target_type === targetType &&
      row.target_value === targetValue &&
      row.scope === scope &&
      sameStarts(row.starts_at, startsAt) &&
      sameExpires(row.expires_at, expiresAt),
  );
}

// ============================================================
// 运行时命中（S1 / Filter / 登录链路）
// ============================================================

/**
 * 查找当前生效命中；DEVICE 本波恒为 null。
 * requestScope: LOGIN | API（也可容错 ALL）。
 */
export function findBlockingHit(
  targetType: string,
  targetValue: string,
  requestScope: string,
  now: Date = new Date(),
): BlacklistHit | null {
  ensureBlacklistSeeds();

  const type = normalizeTargetType(targetType);
  const value = type == null ? null : normalizeTargetValue(type, targetValue);
  const scope = normalizeScope(requestScope);

  if (type == null || value == null || !(TARGET_TYPES as readonly string[]).includes(type)) {
    return null;
  }
  // DEVICE 本波不参与运行时拦截
  if (type === "DEVICE") {
    return null;
  }
  if (!["LOGIN", "API", "ALL"].includes(scope)) {
    return null;
  }

  const nowMs = now.getTime();
  const hit = mockBlacklistList.find((row) => {
    if (row.deleted_at !== 0 || row.is_enabled !== 1) return false;
    if (row.target_type !== type || row.target_value !== value) return false;
    // scope IN (requestScope, 'ALL')
    if (row.scope !== scope && row.scope !== "ALL") return false;
    if (Date.parse(row.starts_at) > nowMs) return false;
    if (row.expires_at != null && !(Date.parse(row.expires_at) > nowMs)) return false;
    return true;
  });

  if (!hit) return null;
  return {
    targetType: hit.target_type,
    targetValue: hit.target_value,
    scope: hit.scope,
    reason: hit.reason ?? "",
  };
}

/** Access Blocked 响应体（不带 reason） */
export function accessBlockedBody(): { code: number; msg: string; data: null } {
  return {
    code: ACCESS_BLOCKED_CODE,
    msg: ACCESS_BLOCKED_MSG,
    data: null,
  };
}

/**
 * 中间件/登录共用的拦截判定。
 * - LOGIN 路径：仅 IP
 * - 其余 /api/**：IP + 可选 userId（session USER）
 */
export function evaluateRequestBlacklist(input: {
  path: string;
  clientIp: string | null | undefined;
  userId?: number | string | null;
  now?: Date;
}): BlacklistHit | null {
  const rawPath = input.path ?? "";
  const path = rawPath.endsWith("/") && rawPath.length > 1 ? rawPath.slice(0, -1) : rawPath;
  if (!path.startsWith("/api/")) return null;

  const loginScene = path === "/api/auth/login";
  const requestScope = loginScene ? "LOGIN" : "API";
  const now = input.now ?? new Date();

  const ip = (input.clientIp ?? "").trim();
  if (ip) {
    const ipHit = findBlockingHit("IP", ip, requestScope, now);
    if (ipHit) return ipHit;
  }

  if (!loginScene && input.userId != null && String(input.userId).trim() !== "") {
    const userHit = findBlockingHit("USER", String(input.userId), "API", now);
    if (userHit) return userHit;
  }

  return null;
}

// ============================================================
// 列表 / CRUD
// ============================================================

function activeRows(): SysBlacklist[] {
  return mockBlacklistList.filter((x) => x.deleted_at === 0);
}

function applyFilter(rows: SysBlacklist[], filter: BlacklistListFilter): SysBlacklist[] {
  let out = rows;
  const targetType = normalizeTargetType(filter.targetType);
  if (targetType) {
    out = out.filter((x) => x.target_type === targetType);
  }
  const targetValue = trimToNull(filter.targetValue);
  if (targetValue) {
    const q = targetValue.toLowerCase();
    out = out.filter((x) => x.target_value.toLowerCase().includes(q));
  }
  const scope = normalizeTargetType(filter.scope); // 同样大写
  if (scope) {
    out = out.filter((x) => x.scope === scope);
  }
  if (filter.status !== undefined && filter.status !== null && filter.status !== "") {
    const st = Number(filter.status);
    if (st === 0 || st === 1) {
      out = out.filter((x) => x.is_enabled === st);
    }
  }
  return out.sort((a, b) => a.id - b.id);
}

export function listBlacklist(filter: BlacklistListFilter = {}): SysBlacklist[] {
  ensureBlacklistSeeds();
  return applyFilter(activeRows(), filter);
}

export function getBlacklistById(id: number): BlacklistResult<SysBlacklist> {
  ensureBlacklistSeeds();
  if (!Number.isFinite(id)) {
    return { ok: false, status: 400, msg: "id must be a number" };
  }
  const found = mockBlacklistList.find((x) => x.id === id && x.deleted_at === 0);
  if (!found) {
    return { ok: false, status: 404, msg: `blacklist ${id} not found` };
  }
  return { ok: true, data: found };
}

export function createBlacklist(input: BlacklistCreateInput): BlacklistResult<SysBlacklist> {
  ensureBlacklistSeeds();

  const typeRes = requireTargetType(input.targetType);
  if (!typeRes.ok) return typeRes;
  const valueRes = requireTargetValue(typeRes.data, input.targetValue);
  if (!valueRes.ok) return valueRes;
  const scopeRes = requireScope(input.scope);
  if (!scopeRes.ok) return scopeRes;

  const startsAt = parseIsoOrNull(input.startsAt) ?? new Date().toISOString();
  const expiresAt = parseIsoOrNull(input.expiresAt);
  const win = validateWindow(startsAt, expiresAt);
  if (!win.ok) return win;

  if (existsExactWindow(typeRes.data, valueRes.data, scopeRes.data, startsAt, expiresAt, null)) {
    return {
      ok: false,
      status: 400,
      msg: "active blacklist with the same target/scope/time-window already exists",
    };
  }

  const now = isoNow();
  const row: SysBlacklist = {
    id: nextBlacklistId(),
    target_type: typeRes.data,
    target_value: valueRes.data,
    scope: scopeRes.data,
    reason: clip(String(input.reason ?? "").trim(), 512),
    starts_at: startsAt,
    expires_at: expiresAt,
    remark: clip(String(input.remark ?? "").trim(), 512),
    is_enabled: normalize01(input.isEnabled, 1),
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  mockBlacklistList.unshift(row);
  return { ok: true, data: row };
}

export function updateBlacklist(
  id: number,
  input: BlacklistUpdateInput,
): BlacklistResult<SysBlacklist> {
  ensureBlacklistSeeds();
  const idx = mockBlacklistList.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    return { ok: false, status: 404, msg: `blacklist ${id} not found` };
  }

  const cur = { ...mockBlacklistList[idx] };

  if (input.targetType !== undefined && input.targetType !== null) {
    const typeRes = requireTargetType(input.targetType);
    if (!typeRes.ok) return typeRes;
    cur.target_type = typeRes.data;
  }
  if (input.targetValue !== undefined && input.targetValue !== null) {
    const valueRes = requireTargetValue(cur.target_type, input.targetValue);
    if (!valueRes.ok) return valueRes;
    cur.target_value = valueRes.data;
  }
  if (input.scope !== undefined && input.scope !== null) {
    const scopeRes = requireScope(input.scope);
    if (!scopeRes.ok) return scopeRes;
    cur.scope = scopeRes.data;
  }
  if (input.reason !== undefined && input.reason !== null) {
    cur.reason = clip(String(input.reason).trim(), 512);
  }
  if (input.startsAt !== undefined && input.startsAt !== null) {
    const parsed = parseIsoOrNull(input.startsAt);
    if (!parsed) {
      return { ok: false, status: 400, msg: "startsAt is invalid" };
    }
    cur.starts_at = parsed;
  }
  if (input.clearExpiresAt === true) {
    cur.expires_at = null;
  } else if (input.expiresAt !== undefined && input.expiresAt !== null) {
    const parsed = parseIsoOrNull(input.expiresAt);
    if (!parsed) {
      return { ok: false, status: 400, msg: "expiresAt is invalid" };
    }
    cur.expires_at = parsed;
  }
  if (input.remark !== undefined && input.remark !== null) {
    cur.remark = clip(String(input.remark).trim(), 512);
  }
  if (input.isEnabled !== undefined && input.isEnabled !== null) {
    cur.is_enabled = normalize01(input.isEnabled, cur.is_enabled);
  }

  const win = validateWindow(cur.starts_at, cur.expires_at);
  if (!win.ok) return win;

  if (
    existsExactWindow(
      cur.target_type,
      cur.target_value,
      cur.scope,
      cur.starts_at,
      cur.expires_at,
      id,
    )
  ) {
    return {
      ok: false,
      status: 400,
      msg: "active blacklist with the same target/scope/time-window already exists",
    };
  }

  cur.updated_at = isoNow();
  cur.updated_by = 0;
  mockBlacklistList[idx] = cur;
  return { ok: true, data: cur };
}

export function softDeleteBlacklist(id: number): BlacklistResult<SysBlacklist> {
  ensureBlacklistSeeds();
  const idx = mockBlacklistList.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    return { ok: false, status: 404, msg: `blacklist ${id} not found` };
  }
  const deletedAt = Date.now();
  const snapshot = { ...mockBlacklistList[idx], deleted_at: deletedAt };
  mockBlacklistList[idx] = snapshot;
  return { ok: true, data: snapshot };
}

export function batchBlacklist(
  action: string | undefined,
  rawIds: Array<number | string> | undefined,
): BlacklistResult<{ action: string; affected: number; ids: number[] }> {
  ensureBlacklistSeeds();
  const act = (action ?? "").trim();
  if (!["enable", "disable", "delete"].includes(act)) {
    return { ok: false, status: 400, msg: "action must be enable|disable|delete" };
  }
  const ids = [
    ...new Set(
      (Array.isArray(rawIds) ? rawIds : [])
        .map((v) => Number(v))
        .filter((v) => Number.isFinite(v) && v > 0),
    ),
  ];
  if (ids.length === 0) {
    return { ok: false, status: 400, msg: "ids must be a non-empty number[]" };
  }

  const targets = mockBlacklistList.filter((x) => ids.includes(x.id) && x.deleted_at === 0);
  if (targets.length === 0) {
    return { ok: false, status: 404, msg: "no active blacklist found for given ids" };
  }

  if (act === "delete") {
    const nowMs = Date.now();
    const deletedIds: number[] = [];
    for (const t of targets) {
      const i = mockBlacklistList.indexOf(t);
      mockBlacklistList[i] = { ...t, deleted_at: nowMs };
      deletedIds.push(t.id);
    }
    return { ok: true, data: { action: act, affected: deletedIds.length, ids: deletedIds } };
  }

  const next: 0 | 1 = act === "enable" ? 1 : 0;
  const now = isoNow();
  const affectedIds: number[] = [];
  for (const t of targets) {
    const i = mockBlacklistList.indexOf(t);
    mockBlacklistList[i] = { ...t, is_enabled: next, updated_at: now, updated_by: 0 };
    affectedIds.push(t.id);
  }
  return { ok: true, data: { action: act, affected: affectedIds.length, ids: affectedIds } };
}
