import { defineEventHandler } from "h3";
import { createAltchaChallenge } from "~/utils/altcha";

/**
 * GET /api/altcha/challenge
 * 返回一个全新 ALTCHA PoW challenge，供前端 <altcha-widget> 拉取并求解。
 *
 * 注意：ALTCHA widget 直接把响应体当作 challenge 对象解析
 * （期望顶层 `{ parameters, signature }`），因此这里**不能**用
 * useResponseSuccess 包一层 `{ code, data }`，必须返回原始 challenge。
 */
export default defineEventHandler(async () => {
  return createAltchaChallenge();
});
