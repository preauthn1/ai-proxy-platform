/**
 * GET /api/encrypt/public/key
 * 与 Java EncryptController 对齐：Result{ data: { publicKey } }，publicKey 为 SPKI base64。
 */

import { defineEventHandler } from "h3";

import { getEncryptKeyPair } from "~/utils/security/keys";
import { useResponseSuccess } from "~/utils/response";

export default defineEventHandler(() => {
  const { publicKeyBase64 } = getEncryptKeyPair();
  return useResponseSuccess({ publicKey: publicKeyBase64 });
});
