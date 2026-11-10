/**
 * 全仓清理：递归删除构建产物与依赖目录。
 * 用法: node scripts/clean.mjs
 */
import { promises as fs } from "node:fs";
import { join, normalize } from "node:path";

const rootDir = process.cwd();
const CONCURRENCY_LIMIT = 10;
const SKIP_DIRS = new Set([".DS_Store", ".git", ".idea", ".vscode", ".scratch"]);
const TARGETS = ["node_modules", "dist", ".turbo", ".vite", "coverage", "target", "dist.zip"];

async function processItem(currentDir, item, targets) {
  if (SKIP_DIRS.has(item)) return false;

  try {
    const itemPath = normalize(join(currentDir, item));
    if (targets.includes(item)) {
      await fs.rm(itemPath, { force: true, recursive: true });
      console.log(`✔ deleted: ${itemPath}`);
      return false;
    }
    return true;
  } catch (error) {
    if (error.code !== "ENOENT") {
      console.error(`✗ ${item} in ${currentDir}: ${error.message}`);
    }
    return false;
  }
}

async function cleanRecursively(currentDir, targets, depth = 0) {
  if (depth > 12) return;

  let dirents;
  try {
    dirents = await fs.readdir(currentDir, { withFileTypes: true });
  } catch {
    return;
  }

  for (let i = 0; i < dirents.length; i += CONCURRENCY_LIMIT) {
    const batch = dirents.slice(i, i + CONCURRENCY_LIMIT);
    await Promise.all(
      batch.map(async (dirent) => {
        const shouldRecurse = await processItem(currentDir, dirent.name, targets);
        if (shouldRecurse && dirent.isDirectory()) {
          await cleanRecursively(normalize(join(currentDir, dirent.name)), targets, depth + 1);
        }
      }),
    );
  }
}

console.log(`→ clean targets: ${TARGETS.join(", ")}`);
console.log(`→ root: ${rootDir}`);
const start = Date.now();
await cleanRecursively(rootDir, TARGETS);
console.log(`✔ done in ${((Date.now() - start) / 1000).toFixed(2)}s`);
