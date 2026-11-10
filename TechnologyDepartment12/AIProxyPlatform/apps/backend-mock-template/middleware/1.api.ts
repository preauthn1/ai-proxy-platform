import { defineEventHandler } from "h3";
import { forbiddenResponse, sleep } from "~/utils/response";

// 演示环境写操作白名单：user 长期允许；字典管理 dict-mock-api 任务追加。
// 路径同时支持带尾斜杠（/api/system/dict-type/<id>）与不带尾斜杠（/api/system/dict-type POST）。
const WRITE_WHITELIST = [
  "/api/system/user",
  "/api/system/role",
  "/api/system/dict-type",
  "/api/system/dict-data",
  "/api/system/i18n-locale",
  "/api/system/i18n-translation",
  "/api/system/menu",
  "/api/system/api",
  "/api/system/task-config",
  "/api/system/blacklist",
];

function isWhitelisted(path: string) {
  return WRITE_WHITELIST.some((prefix) => path === prefix || path.startsWith(`${prefix}/`));
}

export default defineEventHandler(async (event) => {
  event.node.res.setHeader("Access-Control-Allow-Origin", event.headers.get("Origin") ?? "*");
  if (event.method === "OPTIONS") {
    event.node.res.statusCode = 204;
    event.node.res.statusMessage = "No Content.";
    return "OK";
  } else if (
    ["DELETE", "PATCH", "POST", "PUT"].includes(event.method) &&
    event.path.startsWith("/api/system/") &&
    !isWhitelisted(event.path)
  ) {
    await sleep(Math.floor(Math.random() * 2000));
    return forbiddenResponse(event, "演示环境，禁止修改");
  }
});
