/**
 * 将 .env* 写入 process.env。
 *
 * 背景：Nitro/c12 在 dev 下 `dotenv: true` 时**默认只读 `.env`**，
 * 不会读 `.env.development`。本项目配置写在 `.env.development`，
 * 导致 SECURITY_JAVA_KEY_PAIR_URL 等进不了 process.env。
 */

import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

const DEFAULT_FILES = [".env", ".env.local", ".env.development", ".env.development.local"] as const;

/**
 * @param cwd 项目根（mock 包目录）
 * @param files 后写覆盖先写（仅覆盖由本函数写入的 key；不覆盖启动前已有的 process.env）
 */
export function loadEnvFiles(
  cwd: string = process.cwd(),
  files: readonly string[] = DEFAULT_FILES,
): string[] {
  const loaded: string[] = [];
  /** 本轮从文件写入的 key，允许后续文件覆盖 */
  const fromFiles = new Set<string>();

  for (const name of files) {
    const path = resolve(cwd, name);
    if (!existsSync(path)) continue;
    const text = readFileSync(path, "utf8");
    let count = 0;
    for (const line of text.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const eq = trimmed.indexOf("=");
      if (eq <= 0) continue;
      const key = trimmed.slice(0, eq).trim();
      if (!key) continue;
      let value = trimmed.slice(eq + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      // 启动前已有的环境变量不覆盖；文件间后加载可覆盖
      if (process.env[key] !== undefined && !fromFiles.has(key)) {
        continue;
      }
      process.env[key] = value;
      fromFiles.add(key);
      count += 1;
    }
    if (count > 0) loaded.push(name);
  }
  return loaded;
}
