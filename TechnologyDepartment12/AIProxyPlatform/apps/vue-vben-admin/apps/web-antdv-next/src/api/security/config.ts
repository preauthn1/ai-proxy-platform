/**
 * 前端请求安全开关：`VITE_SECURITY_*` 与后端 `app.security.*` / mock `SECURITY_*` 同步。
 * 每项独立；缺省或空值视为开启（dev/prod 示例均为全开）。
 */

export interface SecurityFlags {
  timestampEnabled: boolean;
  encryptEnabled: boolean;
  nonceEnabled: boolean;
  signEnabled: boolean;
  languageEnabled: boolean;
}

/** 解析布尔 env：仅 false/0/off/no（大小写不敏感）为关，其余含缺省为开。 */
export function envFlagEnabled(
  raw: boolean | null | string | undefined,
  defaultValue = true,
): boolean {
  if (raw === undefined || raw === null || raw === '') {
    return defaultValue;
  }
  if (typeof raw === 'boolean') {
    return raw;
  }
  const normalized = String(raw).trim().toLowerCase();
  return !['0', 'false', 'no', 'off'].includes(normalized);
}

export function loadSecurityFlags(
  env: Record<string, boolean | null | string | undefined> = {},
): SecurityFlags {
  return {
    timestampEnabled: envFlagEnabled(env.VITE_SECURITY_TIMESTAMP_ENABLED),
    encryptEnabled: envFlagEnabled(env.VITE_SECURITY_ENCRYPT_ENABLED),
    nonceEnabled: envFlagEnabled(env.VITE_SECURITY_NONCE_ENABLED),
    signEnabled: envFlagEnabled(env.VITE_SECURITY_SIGN_ENABLED),
    languageEnabled: envFlagEnabled(env.VITE_SECURITY_LANGUAGE_ENABLED),
  };
}

/** 运行时读取 import.meta.env（可在测试中用 loadSecurityFlags 注入）。 */
export function getSecurityFlags(): SecurityFlags {
  return loadSecurityFlags(
    import.meta.env as Record<string, boolean | string | undefined>,
  );
}
