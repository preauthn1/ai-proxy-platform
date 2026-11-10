import { defineEventHandler, getHeader, getRequestIP, setResponseStatus } from "h3";

import {
  accessBlockedBody,
  evaluateRequestBlacklist,
  type BlacklistHit,
} from "~/utils/mock/blacklist";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 访问黑名单运行时拦截（对齐 Java BlacklistFilter）。
 *
 * - LOGIN（/api/auth/login）：仅查 IP；USER 在 login handler 发 token 前查
 * - 其余 /api/**：查 IP；若有有效会话再查 USER
 * - DEVICE 本波不查
 * - 命中：HTTP 403 + code=2005 + 固定文案 Access Blocked；不回传 reason
 */
export default defineEventHandler(async (event) => {
  if (event.method === "OPTIONS") return;

  const path = event.path ?? "";
  if (!path.startsWith("/api/")) return;

  const clientIp =
    getRequestIP(event, { xForwardedFor: true }) ??
    getHeader(event, "x-forwarded-for")?.split(",")[0]?.trim() ??
    getHeader(event, "x-real-ip") ??
    event.node.req.socket?.remoteAddress ??
    "";

  // 仅 API 场景需要 userId；LOGIN 场景 Filter 不解析 body/会话
  const normalized = path.endsWith("/") && path.length > 1 ? path.slice(0, -1) : path;
  const isLogin = normalized === "/api/auth/login";

  let userId: number | null = null;
  if (!isLogin) {
    const user = verifyAccessToken(event);
    if (user?.id != null) {
      userId = Number(user.id);
    }
  }

  const hit = evaluateRequestBlacklist({
    path,
    clientIp,
    userId,
  });

  if (!hit) return;

  logAccessBlocked(hit, clientIp, isLogin ? "LOGIN" : "API");
  setResponseStatus(event, 403);
  return accessBlockedBody();
});

function logAccessBlocked(hit: BlacklistHit, clientIp: string, scene: string): void {
  // reason 仅服务端日志，不对客户端暴露
  console.warn(
    `[BLACKLIST] Access Blocked targetType=${hit.targetType} targetValue=${hit.targetValue} scene=${scene} hitScope=${hit.scope} clientIp=${clientIp} reason=${hit.reason}`,
  );
}
