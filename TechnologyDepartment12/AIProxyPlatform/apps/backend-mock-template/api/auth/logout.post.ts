import { defineEventHandler } from "h3";
import { revokeAccessToken } from "~/utils/session-utils";
import { useResponseSuccess } from "~/utils/response";

/**
 * 登出：从 Authorization Bearer 作废当前会话（幂等）。
 */
export default defineEventHandler(async (event) => {
  revokeAccessToken(event);
  return useResponseSuccess("");
});
