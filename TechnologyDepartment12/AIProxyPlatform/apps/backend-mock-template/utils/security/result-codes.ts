/**
 * 请求安全相关 Result 业务码，与 Java {@code ResultCode} 1xxx 对齐。
 */
export const SecurityResultCode = {
  SUCCESS: { code: 0, msg: "ok" },
  INTERNAL_ERROR: { code: 1003, msg: "内部错误" },
  REQUEST_EXPIRED: { code: 1004, msg: "请求已过期" },
  REQUEST_ERROR: { code: 1005, msg: "请求错误" },
  REQUEST_KEY_FAILED: { code: 1006, msg: "密钥错误" },
  REQUEST_NONCE_CONFLICT: { code: 1007, msg: "请求重复" },
  REQUEST_SIGN_FAILED: { code: 1008, msg: "签名错误" },
} as const;

export type SecurityErrorCode = (typeof SecurityResultCode)[keyof typeof SecurityResultCode];

export function securityErrorBody(result: SecurityErrorCode) {
  return {
    code: result.code,
    msg: result.msg,
    data: null as null,
  };
}
