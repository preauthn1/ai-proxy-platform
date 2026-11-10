import type { EventHandlerRequest, H3Event } from "h3";

import type { UserInfo } from "./mock-data";

import { getHeader } from "h3";
import { randomUUID } from "node:crypto";

import { ensureUserSeeds, getMockSysUserList, getUserRoleCodes } from "./mock-data";
import { generateRsaKeyPair } from "./security/crypto";

/** 默认 30 天（秒），对齐 java-admin sa-token.timeout */
const DEFAULT_TIMEOUT_SECONDS = 2592000;

/** java 内省 HTTP 超时（毫秒） */
const DEFAULT_JAVA_INTROSPECT_TIMEOUT_MS = 3000;

interface SessionRecord {
  userId: number;
  username: string;
  expiresAt: number;
  /** 是否由 java Sa-Token 内省登记（hybrid 交叉联调） */
  foreign?: boolean;
  /** 会话专属 RSA 公钥 SPKI base64（登录返回；纯 mock 本地生成） */
  publicKeyBase64?: string;
  /** 会话专属 RSA 私钥 PEM（解密用） */
  privateKeyPem?: string;
}

export interface CreateSessionResult {
  accessToken: string;
  /** 会话专属公钥（SPKI base64） */
  publicKey: string;
}

/** token → 会话 */
const sessions = new Map<string, SessionRecord>();

/** 同一 token 并发内省去重 */
const pendingAdopts = new Map<string, Promise<boolean>>();

function parseBoolEnv(name: string, defaultValue: boolean): boolean {
  const raw = process.env[name];
  if (raw === undefined || raw === "") return defaultValue;
  const v = raw.trim().toLowerCase();
  if (["1", "true", "yes", "on"].includes(v)) return true;
  if (["0", "false", "no", "off"].includes(v)) return false;
  return defaultValue;
}

function parseTimeoutSeconds(): number {
  const raw = process.env.AUTH_TOKEN_TIMEOUT_SECONDS;
  if (raw === undefined || raw === "") return DEFAULT_TIMEOUT_SECONDS;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_TIMEOUT_SECONDS;
  return Math.floor(n);
}

function timeoutMs(): number {
  return parseTimeoutSeconds() * 1000;
}

/**
 * 鉴权运行模式：
 * - `mock`（默认）：纯 mock，校验本地会话 token
 * - `mixture`：与 java 交叉联调，**不校验** token（避免再调 java `/user/info` 触发 Encrypt）
 *
 * 环境变量：`AUTH_MODE=mock|mixture`（兼容 `pure`/`hybrid` 别名）
 */
export type AuthMode = "mock" | "mixture";

export function getAuthMode(): AuthMode {
  const raw = process.env.AUTH_MODE?.trim().toLowerCase();
  if (!raw) return "mock";
  if (raw === "mixture" || raw === "hybrid" || raw === "mix") return "mixture";
  if (raw === "mock" || raw === "pure" || raw === "local") return "mock";
  return "mock";
}

export function isMixtureMode(): boolean {
  return getAuthMode() === "mixture";
}

/**
 * （可选）向 java 内省 token 的完整 URL。
 * 默认关闭（空）；仅显式配置且 `AUTH_MODE=mock` 时由 bridge 使用。
 * mixture 模式不内省、不校验 token。
 */
export function getJavaIntrospectUrl(): string {
  const raw = process.env.AUTH_JAVA_INTROSPECT_URL;
  if (raw === undefined || raw === "") return "";
  return raw.trim();
}

/**
 * mixture 下放行时使用的 mock 用户，以及（可选）内省映射回落用户。
 * 默认 root。
 */
export function getJavaUserFallback(): string {
  const raw = process.env.AUTH_JAVA_USER_FALLBACK;
  if (raw === undefined || raw.trim() === "") return "root";
  return raw.trim();
}

function javaIntrospectTimeoutMs(): number {
  const raw = process.env.AUTH_JAVA_INTROSPECT_TIMEOUT_MS;
  if (raw === undefined || raw === "") return DEFAULT_JAVA_INTROSPECT_TIMEOUT_MS;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return DEFAULT_JAVA_INTROSPECT_TIMEOUT_MS;
  return Math.floor(n);
}

/** 是否允许多端登录（默认 true） */
export function isConcurrent(): boolean {
  return parseBoolEnv("AUTH_IS_CONCURRENT", true);
}

/** 同账号是否共享同一 token（默认 false） */
export function isShare(): boolean {
  return parseBoolEnv("AUTH_IS_SHARE", false);
}

function now(): number {
  return Date.now();
}

function buildUserInfo(sysUser: {
  id: number;
  username: string;
  nickname: string;
}): Omit<UserInfo, "password"> {
  ensureUserSeeds();
  const roles = getUserRoleCodes(sysUser.id);
  return {
    id: sysUser.id,
    username: sysUser.username,
    realName: sysUser.nickname,
    roles,
    homePath: "/analytics",
  };
}

function findSysUserByUsername(username: string) {
  ensureUserSeeds();
  return getMockSysUserList().find((item) => item.username === username && item.deleted_at === 0);
}

function findSysUserById(userId: number) {
  ensureUserSeeds();
  return getMockSysUserList().find((item) => item.id === userId && item.deleted_at === 0);
}

/**
 * 将 java 侧用户名映射到 mock RBAC 用户。
 * 优先同名；否则 AUTH_JAVA_USER_FALLBACK（默认 root）。
 */
function resolveMockUserForJavaUsername(javaUsername: string) {
  const exact = findSysUserByUsername(javaUsername);
  if (exact) return exact;
  return findSysUserByUsername(getJavaUserFallback());
}

function revokeByUserId(userId: number, exceptToken?: string) {
  for (const [token, record] of sessions) {
    if (record.userId === userId && token !== exceptToken) {
      sessions.delete(token);
    }
  }
}

function findActiveTokenByUserId(userId: number): string | null {
  const t = now();
  for (const [token, record] of sessions) {
    if (record.userId === userId && record.expiresAt > t) {
      return token;
    }
    if (record.userId === userId && record.expiresAt <= t) {
      sessions.delete(token);
    }
  }
  return null;
}

/**
 * 登录创建会话，返回 accessToken + 会话专属 publicKey。
 * - is-share=true：复用该用户未过期 token（及已有会话钥）
 * - is-concurrent=false：踢掉该用户其它会话
 */
export function createSession(user: Pick<UserInfo, "id" | "username">): CreateSessionResult {
  const userId = Number(user.id);
  const username = user.username;

  if (isShare()) {
    const existing = findActiveTokenByUserId(userId);
    if (existing) {
      const record = sessions.get(existing);
      if (record) {
        record.expiresAt = now() + timeoutMs();
        // 共享 token 时若缺会话钥则补生成
        if (!record.privateKeyPem || !record.publicKeyBase64) {
          const pair = generateRsaKeyPair();
          record.privateKeyPem = pair.privateKeyPem;
          record.publicKeyBase64 = pair.publicKeyBase64;
        }
        sessions.set(existing, record);
        return {
          accessToken: existing,
          publicKey: record.publicKeyBase64!,
        };
      }
    }
  }

  if (!isConcurrent()) {
    revokeByUserId(userId);
  }

  const pair = generateRsaKeyPair();
  const token = randomUUID();
  sessions.set(token, {
    userId,
    username,
    expiresAt: now() + timeoutMs(),
    publicKeyBase64: pair.publicKeyBase64,
    privateKeyPem: pair.privateKeyPem,
  });
  return {
    accessToken: token,
    publicKey: pair.publicKeyBase64,
  };
}

/** 读取本地会话私钥 PEM（无则 null） */
export function getSessionPrivateKeyPem(token: string | null | undefined): string | null {
  if (!token) return null;
  const record = sessions.get(token);
  if (!record || record.expiresAt <= now()) return null;
  return record.privateKeyPem?.trim() || null;
}

/**
 * 写入/更新会话专属密钥（java 拉取或 hybrid adopt 后缓存）。
 * 若本地尚无会话记录，创建 foreign 占位（仅密钥，RBAC 仍走 mixture fallback）。
 */
export function adoptSessionEncryptKeys(
  token: string,
  keys: { publicKeyBase64: string; privateKeyPem: string },
  user?: { id: number; username: string },
): void {
  const existing = sessions.get(token);
  if (existing) {
    existing.publicKeyBase64 = keys.publicKeyBase64;
    existing.privateKeyPem = keys.privateKeyPem;
    existing.expiresAt = now() + timeoutMs();
    sessions.set(token, existing);
    return;
  }
  const fallbackUser = user ?? {
    id: findSysUserByUsername(getJavaUserFallback())?.id ?? 0,
    username: getJavaUserFallback(),
  };
  sessions.set(token, {
    userId: Number(fallbackUser.id),
    username: fallbackUser.username,
    expiresAt: now() + timeoutMs(),
    foreign: true,
    publicKeyBase64: keys.publicKeyBase64,
    privateKeyPem: keys.privateKeyPem,
  });
}

/** 将已校验的外部 token 登记为本地会话（绑定 mock 用户 id/username） */
function registerForeignSession(token: string, user: { id: number; username: string }): void {
  sessions.set(token, {
    userId: Number(user.id),
    username: user.username,
    expiresAt: now() + timeoutMs(),
    foreign: true,
  });
}

export function revokeSession(token: string | null | undefined): void {
  if (!token) return;
  sessions.delete(token);
}

export function extractBearerToken(event: H3Event<EventHandlerRequest>): string | null {
  const authHeader = getHeader(event, "Authorization");
  if (!authHeader?.startsWith("Bearer")) {
    return null;
  }
  const tokenParts = authHeader.split(" ");
  if (tokenParts.length !== 2) {
    return null;
  }
  const token = tokenParts[1];
  return token || null;
}

interface JavaUserInfoBody {
  code?: number;
  data?: {
    id?: number | string;
    username?: string;
    realName?: string;
    roles?: string[];
    homePath?: string;
  } | null;
}

/**
 * 调用 java `GET /api/user/info` 校验 Sa-Token，成功则映射 mock 用户并写入本地 session。
 * @returns 是否登记成功
 */
async function adoptFromJava(token: string, introspectUrl: string): Promise<boolean> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), javaIntrospectTimeoutMs());
  try {
    const res = await fetch(introspectUrl, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
      signal: controller.signal,
    });
    if (!res.ok) {
      return false;
    }
    const body = (await res.json()) as JavaUserInfoBody;
    if (body?.code !== 0 || !body.data?.username) {
      return false;
    }
    const mockUser = resolveMockUserForJavaUsername(String(body.data.username));
    if (!mockUser) {
      return false;
    }
    registerForeignSession(token, mockUser);
    return true;
  } catch {
    // java 未起 / 超时 / 网络错误：保持未登记，后续 verify 返回 401
    return false;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * 可选：本地无会话时用 java 内省登记 token。
 * - `AUTH_MODE=mixture`：不调用（不校验 token）
 * - 须显式配置 `AUTH_JAVA_INTROSPECT_URL` 才会请求 java
 */
export async function ensureTokenAdopted(token: string | null | undefined): Promise<void> {
  if (!token) return;
  if (isMixtureMode()) return;

  const existing = sessions.get(token);
  if (existing) {
    if (existing.expiresAt > now()) return;
    sessions.delete(token);
  }

  const introspectUrl = getJavaIntrospectUrl();
  if (!introspectUrl) return;

  let pending = pendingAdopts.get(token);
  if (!pending) {
    pending = adoptFromJava(token, introspectUrl).finally(() => {
      pendingAdopts.delete(token);
    });
    pendingAdopts.set(token, pending);
  }
  await pending;
}

/** mixture：不校验 token，固定映射到 AUTH_JAVA_USER_FALLBACK（默认 root）。 */
function resolveMixtureUser(): null | Omit<UserInfo, "password"> {
  const sysUser = findSysUserByUsername(getJavaUserFallback());
  if (!sysUser) return null;
  return buildUserInfo(sysUser);
}

/**
 * 校验访问身份并返回用户信息（不含 password）。
 * - `mock`：校验本地 Bearer 会话（滑动续期）
 * - `mixture`：**不校验** token，直接返回 fallback mock 用户（RBAC 用）
 */
export function verifyAccessToken(
  event: H3Event<EventHandlerRequest>,
): null | Omit<UserInfo, "password"> {
  if (isMixtureMode()) {
    return resolveMixtureUser();
  }

  const token = extractBearerToken(event);
  if (!token) {
    return null;
  }

  const record = sessions.get(token);
  if (!record) {
    return null;
  }

  if (record.expiresAt <= now()) {
    sessions.delete(token);
    return null;
  }

  // 滑动续期
  record.expiresAt = now() + timeoutMs();
  sessions.set(token, record);

  // 优先 username：foreign 会话已绑定 mock 用户名，避免 java id 误命中
  const sysUser = findSysUserByUsername(record.username) ?? findSysUserById(record.userId);
  if (!sysUser) {
    sessions.delete(token);
    return null;
  }

  return buildUserInfo(sysUser);
}

/** 登出：作废当前请求中的 token */
export function revokeAccessToken(event: H3Event<EventHandlerRequest>): void {
  revokeSession(extractBearerToken(event));
}

/** 兼容旧命名：登录签发 token（仅 token 字符串） */
export function generateAccessToken(user: UserInfo): string {
  return createSession(user).accessToken;
}
