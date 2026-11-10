import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

import errorHandler from "./error";
import { loadEnvFiles } from "./utils/load-env-files";

// Nitro 默认 dotenv 只加载 `.env`；本项目配置在 `.env.development`，须在此注入 process.env
// 这样 dev worker 继承时才能读到 SECURITY_JAVA_KEY_PAIR_URL 等
const mockRoot = dirname(fileURLToPath(import.meta.url));
const loadedEnvFiles = loadEnvFiles(mockRoot);
if (loadedEnvFiles.length > 0) {
  console.info("[nitro] loaded env files:", loadedEnvFiles.join(", "));
  console.info(
    "[nitro] SECURITY_JAVA_KEY_PAIR_URL=",
    process.env.SECURITY_JAVA_KEY_PAIR_URL || "(empty)",
  );
}

process.env.COMPATIBILITY_DATE = new Date().toISOString();

export default defineNitroConfig({
  devErrorHandler: errorHandler,
  errorHandler: "~/error",
  // 显式固定 dev 端口，便于前端 Vite 代理（端口在 package.json 的 start 脚本中通过 --port 设置）
  devProxy: {},
  routeRules: {
    "/api/**": {
      cors: true,
      headers: {
        "Access-Control-Allow-Credentials": "true",
        "Access-Control-Allow-Headers":
          "Accept, Authorization, Content-Length, Content-Type, If-Match, If-Modified-Since, If-None-Match, If-Unmodified-Since, X-CSRF-TOKEN, X-Requested-With, X-Request-Timestamp, X-Timestamp, X-Request-ID, X-Request-Encrypted-Key, X-Request-Signature, X-Sign, X-Language",
        "Access-Control-Allow-Methods": "GET,HEAD,PUT,PATCH,POST,DELETE",
        // Allow-Origin 由 middleware/1.api.ts 动态回显 Origin；
        // 不能同时给 "*" + Allow-Credentials，浏览器会拒绝带 cookie 的请求
        "Access-Control-Expose-Headers": "X-Response-Is-Encrypt, *",
      },
    },
  },
});
