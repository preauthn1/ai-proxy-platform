/**
 * mock 安全开关：环境变量可独立控制，默认全开（与 Java app.security 对齐）。
 *
 * | 变量 | 默认 | 含义 |
 * |---|---|---|
 * | SECURITY_TIMESTAMP_ENABLED | true | 时间窗校验 |
 * | SECURITY_TIMESTAMP_EXPIRE_MS | 300000 | 时间窗毫秒 |
 * | SECURITY_ENCRYPT_ENABLED | true | 强制加解密 |
 * | SECURITY_NONCE_ENABLED | true | X-Request-ID 防重放 |
 * | SECURITY_NONCE_EXPIRE_MS | 0 | Nonce TTL；<=0 用 2×时间窗 |
 * | SECURITY_SIGN_ENABLED | true | Encrypt 关时独立签名 |
 * | SECURITY_LANGUAGE_ENABLED | true | 解析 X-Language |
 */

export interface SecurityConfig {
  timestampEnabled: boolean;
  timestampExpireMs: number;
  encryptEnabled: boolean;
  nonceEnabled: boolean;
  /** 显式 Nonce TTL；0 表示派生。 */
  nonceExpireMs: number;
  signEnabled: boolean;
  languageEnabled: boolean;
}

export function loadSecurityConfig(env: NodeJS.ProcessEnv = process.env): SecurityConfig {
  return {
    timestampEnabled: envBoolFrom(env, "SECURITY_TIMESTAMP_ENABLED", true),
    timestampExpireMs: envNumberFrom(env, "SECURITY_TIMESTAMP_EXPIRE_MS", 5 * 60 * 1000),
    encryptEnabled: envBoolFrom(env, "SECURITY_ENCRYPT_ENABLED", true),
    nonceEnabled: envBoolFrom(env, "SECURITY_NONCE_ENABLED", true),
    nonceExpireMs: envNumberFrom(env, "SECURITY_NONCE_EXPIRE_MS", 0),
    signEnabled: envBoolFrom(env, "SECURITY_SIGN_ENABLED", true),
    languageEnabled: envBoolFrom(env, "SECURITY_LANGUAGE_ENABLED", true),
  };
}

function envBoolFrom(env: NodeJS.ProcessEnv, key: string, defaultValue: boolean): boolean {
  const raw = env[key];
  if (raw === undefined || raw === "") return defaultValue;
  return !["0", "false", "FALSE", "off", "OFF", "no", "NO"].includes(raw);
}

function envNumberFrom(env: NodeJS.ProcessEnv, key: string, defaultValue: number): number {
  const raw = env[key];
  if (raw === undefined || raw === "") return defaultValue;
  const n = Number(raw);
  return Number.isFinite(n) ? n : defaultValue;
}

/** Nonce TTL：显式配置优先，否则 2 倍时间窗。 */
export function resolveNonceExpireMs(config: SecurityConfig): number {
  if (config.nonceExpireMs > 0) return config.nonceExpireMs;
  return config.timestampExpireMs * 2;
}

/** 进程级默认配置（读 env）；测试可传独立 config。 */
export function getSecurityConfig(): SecurityConfig {
  return loadSecurityConfig();
}
