/** 与 Java SecurityHeaders / mock SECURITY_HEADERS 对齐。 */

export const SECURITY_HEADERS = {
  REQUEST_TIMESTAMP: 'X-Request-Timestamp',
  REQUEST_ID: 'X-Request-ID',
  REQUEST_ENCRYPTED_KEY: 'X-Request-Encrypted-Key',
  REQUEST_SIGNATURE: 'X-Request-Signature',
  RESPONSE_IS_ENCRYPT: 'X-Response-Is-Encrypt',
  LANGUAGE: 'X-Language',
} as const;

/** 独立 Sign 时 body 写入 AAD 的 key（与 Java SecurityConstants 对齐）。 */
export const SIGN_DATA_AAD_KEY = 'signData';
