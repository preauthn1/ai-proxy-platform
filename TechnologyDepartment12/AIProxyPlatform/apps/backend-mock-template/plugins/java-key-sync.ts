/**
 * 启动时尝试同步 java RSA 密钥（须 SECURITY_JAVA_KEY_PAIR_URL 已注入 process.env）。
 * 不依赖首个业务请求，便于在控制台立刻看到成功/失败。
 */

import { defineNitroPlugin } from "nitropack/runtime";

import { ensureJavaKeyPairSynced, getJavaKeyPairUrl } from "~/utils/security/java-key-sync";

export default defineNitroPlugin(() => {
  const url = getJavaKeyPairUrl();
  if (!url) {
    console.info(
      "[security] SECURITY_JAVA_KEY_PAIR_URL 未配置，跳过从 java 拉密钥（将使用本地密钥）",
    );
    return;
  }
  console.info("[security] 启动同步 java key pair:", url);
  void ensureJavaKeyPairSynced().then((ok) => {
    if (!ok) {
      console.warn(
        "[security] java key pair 同步未成功（见上方 warn）；Encrypt 开时 hybrid 可能 1006",
      );
    }
  });
});
