/**
 * Encrypt 开启且请求带会话 AES key 时，加密 JSON 响应并设置 X-Response-Is-Encrypt。
 */

import { defineNitroPlugin } from "nitropack/runtime";
import { setResponseHeader } from "h3";

import { SECURITY_HEADERS } from "~/utils/security/headers";
import { encryptResponseBody } from "~/utils/security/process-request";
import { securityErrorBody, SecurityResultCode } from "~/utils/security/result-codes";

export default defineNitroPlugin((nitroApp) => {
  nitroApp.hooks.hook("beforeResponse", (event, response) => {
    const aesKey = event.context.security?.responseAesKeyBase64;
    if (!aesKey) return;

    const body = response.body;
    if (body == null || body === "") return;

    // 已是 Buffer/Stream 的复杂响应跳过
    if (typeof body !== "string" && typeof body !== "object") return;
    if (
      typeof body === "object" &&
      (Buffer.isBuffer(body) || typeof (body as { pipe?: unknown }).pipe === "function")
    ) {
      return;
    }

    try {
      const plain = typeof body === "string" ? body : JSON.stringify(body);
      if (!plain) return;

      const encrypted = encryptResponseBody(plain, aesKey);
      response.body = encrypted;
      setResponseHeader(event, SECURITY_HEADERS.RESPONSE_IS_ENCRYPT, "true");
      setResponseHeader(event, "Content-Type", "application/json; charset=utf-8");
    } catch (e) {
      console.error("[security-response] 响应加密失败", e);
      response.body = securityErrorBody(SecurityResultCode.INTERNAL_ERROR);
    }
  });
});
