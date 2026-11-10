/**
 * 辅 seam：Mock 安全协议对等
 * — 公钥形状、加密请求成功路径、关 Encrypt 明文路径、Timestamp/Nonce 等开关语义。
 */

import { afterEach, beforeEach, describe, expect, it } from "vite-plus/test";

import { loadSecurityConfig, type SecurityConfig } from "../utils/security/config";
import {
  aesDecryptCombined,
  aesEncrypt,
  buildAad,
  generateAesKey,
  generateRsaKeyPair,
  rsaEncrypt,
} from "../utils/security/crypto";
import { SECURITY_HEADERS, SIGN_DATA_AAD_KEY } from "../utils/security/headers";
import {
  ensureJavaKeyPairSynced,
  resetJavaKeyPairSyncForTest,
} from "../utils/security/java-key-sync";
import { getEncryptKeyPair, setEncryptKeyPairForTest } from "../utils/security/keys";
import { MemoryNonceStore } from "../utils/security/nonce-store";
import { isSecurityWhitelisted } from "../utils/security/path-matcher";
import {
  encryptResponseBody,
  processSecurityRequest,
  type ProcessSecurityDeps,
} from "../utils/security/process-request";
import { SecurityResultCode } from "../utils/security/result-codes";

function fullConfig(overrides: Partial<SecurityConfig> = {}): SecurityConfig {
  return {
    timestampEnabled: true,
    timestampExpireMs: 5 * 60 * 1000,
    encryptEnabled: true,
    nonceEnabled: true,
    nonceExpireMs: 0,
    signEnabled: true,
    languageEnabled: true,
    ...overrides,
  };
}

describe("mock security protocol seam", () => {
  let keyPair: ReturnType<typeof generateRsaKeyPair>;
  let nonceStore: MemoryNonceStore;
  let deps: ProcessSecurityDeps;

  beforeEach(() => {
    keyPair = generateRsaKeyPair();
    nonceStore = new MemoryNonceStore();
    deps = {
      config: fullConfig(),
      privateKeyPem: keyPair.privateKeyPem,
      nonceStore,
    };
  });

  it("public key shape matches Result data.publicKey (SPKI base64)", () => {
    // 与 GET /api/encrypt/public/key 返回形状对齐
    const result = {
      code: 0,
      msg: "ok",
      data: { publicKey: keyPair.publicKeyBase64 },
    };
    expect(result.code).toBe(0);
    expect(result.data.publicKey).toMatch(/^[A-Za-z0-9+/=]+$/);
    expect(result.data.publicKey.length).toBeGreaterThan(100);
    // 可被 RSA 加密使用
    const aes = generateAesKey();
    const enc = rsaEncrypt(aes, keyPair.publicKeyBase64);
    expect(enc.length).toBeGreaterThan(0);
  });

  it("whitelists java dev session-key path (align SecurityPathMatcher)", () => {
    expect(isSecurityWhitelisted("/api/encrypt/dev/session-key")).toBe(true);
    expect(isSecurityWhitelisted("/api/encrypt/dev/key-pair")).toBe(true);
    expect(isSecurityWhitelisted("/api/auth/login")).toBe(false);
  });

  it("session private key decrypts when deps use session keypair", () => {
    const sessionPair = generateRsaKeyPair();
    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, sessionPair.publicKeyBase64);
    const now = Date.now();
    const plainBody = JSON.stringify({ ping: true });

    const buildEnc = (requestId: string) => {
      const aad = buildAad({
        [SECURITY_HEADERS.REQUEST_ID]: requestId,
        [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
      });
      return aesEncrypt(plainBody, aesKey, aad);
    };

    // 全局钥无法解密会话公钥加密的 AES key
    const encGlobal = buildEnc("session-key-global-fail");
    const withGlobal = processSecurityRequest(
      {
        method: "POST",
        path: "/api/menu/all",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: encGlobal.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: "session-key-global-fail",
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: encGlobal.ciphertext,
        contentType: "application/json",
        nowMs: now,
      },
      deps,
    );
    expect(withGlobal.ok).toBe(false);

    // 换新 requestId，避免 Nonce 冲突
    const encSession = buildEnc("session-key-ok");
    const withSession = processSecurityRequest(
      {
        method: "POST",
        path: "/api/menu/all",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: encSession.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: "session-key-ok",
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: encSession.ciphertext,
        contentType: "application/json",
        nowMs: now,
      },
      { ...deps, privateKeyPem: sessionPair.privateKeyPem },
    );
    expect(withSession.ok).toBe(true);
    if (!withSession.ok) return;
    expect(withSession.body).toBe(plainBody);
  });

  it("encrypt on: encrypted request succeeds and response can be encrypted", () => {
    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, keyPair.publicKeyBase64);
    const now = Date.now();
    const requestId = "mock-encrypt-ok-1";
    const aad = buildAad({
      [SECURITY_HEADERS.REQUEST_ID]: requestId,
      [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
    });
    const plainBody = JSON.stringify({ username: "root", REDACTED });
    const enc = aesEncrypt(plainBody, aesKey, aad);

    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: enc.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: requestId,
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: enc.ciphertext,
        contentType: "application/json",
        nowMs: now,
      },
      deps,
    );

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.body).toBe(plainBody);
    expect(result.responseAesKeyBase64).toBe(aesKey);

    const plainResp = JSON.stringify({ code: 0, msg: "ok", data: { accessToken: "t" } });
    const encryptedResp = encryptResponseBody(plainResp, result.responseAesKeyBase64!);
    const decrypted = aesDecryptCombined(encryptedResp, aesKey, "").toString("utf8");
    expect(JSON.parse(decrypted)).toEqual({ code: 0, msg: "ok", data: { accessToken: "t" } });
  });

  it("encrypt off: plaintext body passes without crypto headers", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: false });
    const plainBody = JSON.stringify({ username: "root", REDACTED });

    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {},
        body: plainBody,
        contentType: "application/json",
      },
      deps,
    );

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.body).toBe(plainBody);
    expect(result.responseAesKeyBase64).toBeUndefined();
  });

  it("sign when encrypt off: valid signature passes and body stays plain", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: true });
    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, keyPair.publicKeyBase64);
    const now = Date.now();
    const plainBody = JSON.stringify({ username: "root", REDACTED });
    const aad = buildAad({
      [SECURITY_HEADERS.REQUEST_ID]: "sign-ok",
      [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
      [SIGN_DATA_AAD_KEY]: plainBody,
    });
    const sign = aesEncrypt("", aesKey, aad);

    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: sign.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: "sign-ok",
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: plainBody,
        contentType: "application/json",
        nowMs: now,
      },
      deps,
    );

    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.body).toBe(plainBody);
  });

  it("sign when encrypt off: missing signature rejected", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: true });
    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {},
        body: '{"username":"root"}',
        contentType: "application/json",
      },
      deps,
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.body.code).toBe(SecurityResultCode.REQUEST_ERROR.code);
  });

  it("sign when encrypt off: GET with query params in AAD passes", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: true });
    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, keyPair.publicKeyBase64);
    const now = Date.now();
    // 对齐前端 normalizeParams：数组只取首值
    const query = {
      typeCode: "sys_switch_status",
      page: "1",
      pageSize: "20",
      includeGeneral: "true",
      platform: "react-admin",
    };
    const aad = buildAad({
      [SECURITY_HEADERS.REQUEST_ID]: "sign-query",
      [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
      ...query,
    });
    const sign = aesEncrypt("", aesKey, aad);

    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/system/dict-data/list",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: sign.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: "sign-query",
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: "",
        query,
        nowMs: now,
      },
      deps,
    );

    expect(result.ok).toBe(true);
  });

  it("sign when encrypt off: AAD query mismatch yields 1008", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: true });
    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, keyPair.publicKeyBase64);
    const now = Date.now();
    // 错误复现：客户端把数组 String() 成 "a,b"，服务端只有首值 "a"
    const aadClient = buildAad({
      [SECURITY_HEADERS.REQUEST_ID]: "sign-mismatch",
      [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
      typeCode: "sys_switch_status,sys_platform",
    });
    const sign = aesEncrypt("", aesKey, aadClient);

    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/system/dict-data/list",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_SIGNATURE]: sign.tagIv,
          [SECURITY_HEADERS.REQUEST_ID]: "sign-mismatch",
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: "",
        query: { typeCode: "sys_switch_status" },
        nowMs: now,
      },
      deps,
    );

    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.body.code).toBe(SecurityResultCode.REQUEST_SIGN_FAILED.code);
  });

  it("encrypt on: missing encrypted key on login is rejected", () => {
    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {},
        body: '{"username":"root"}',
        contentType: "application/json",
      },
      deps,
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.body.code).toBe(SecurityResultCode.REQUEST_ERROR.code);
    expect(result.body.msg).toBe(SecurityResultCode.REQUEST_ERROR.msg);
  });

  it("encrypt on: public key path allows plaintext", () => {
    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/encrypt/public/key",
        headers: {},
        body: "",
      },
      deps,
    );
    expect(result.ok).toBe(true);
  });

  it("encrypt on: altcha path allows plaintext", () => {
    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/altcha/challenge",
        headers: {},
        body: "",
      },
      deps,
    );
    expect(result.ok).toBe(true);
  });

  it("timestamp expired is rejected when enabled", () => {
    const expired = Date.now() - 6 * 60 * 1000;
    const result = processSecurityRequest(
      {
        method: "POST",
        path: "/api/auth/login",
        headers: {
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(expired),
        },
        body: "",
        nowMs: Date.now(),
      },
      deps,
    );
    expect(result.ok).toBe(false);
    if (result.ok) return;
    expect(result.body.code).toBe(SecurityResultCode.REQUEST_EXPIRED.code);
  });

  it("nonce conflict on second same request id", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: false });
    const headers = { [SECURITY_HEADERS.REQUEST_ID]: "nonce-1" };
    const first = processSecurityRequest(
      { method: "POST", path: "/api/test", headers, body: "" },
      deps,
    );
    const second = processSecurityRequest(
      { method: "POST", path: "/api/test", headers, body: "" },
      deps,
    );
    expect(first.ok).toBe(true);
    expect(second.ok).toBe(false);
    if (second.ok) return;
    expect(second.body.code).toBe(SecurityResultCode.REQUEST_NONCE_CONFLICT.code);
  });

  it("language header is resolved when enabled", () => {
    deps.config = fullConfig({ encryptEnabled: false, signEnabled: false });
    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/user/info",
        headers: { [SECURITY_HEADERS.LANGUAGE]: "zh-CN" },
        body: "",
      },
      deps,
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.language).toBe("zh-CN");
  });

  it("env config defaults all enabled", () => {
    const cfg = loadSecurityConfig({});
    expect(cfg.timestampEnabled).toBe(true);
    expect(cfg.encryptEnabled).toBe(true);
    expect(cfg.nonceEnabled).toBe(true);
    expect(cfg.signEnabled).toBe(true);
    expect(cfg.languageEnabled).toBe(true);
  });

  it("env config can disable encrypt independently", () => {
    const cfg = loadSecurityConfig({ SECURITY_ENCRYPT_ENABLED: "false" });
    expect(cfg.encryptEnabled).toBe(false);
    expect(cfg.timestampEnabled).toBe(true);
  });

  it("whitelist matcher covers public key and excludes login", () => {
    expect(isSecurityWhitelisted("/api/encrypt/public/key")).toBe(true);
    expect(isSecurityWhitelisted("/api/encrypt/dev/key-pair")).toBe(true);
    expect(isSecurityWhitelisted("/api/altcha/challenge")).toBe(true);
    expect(isSecurityWhitelisted("/api/public/i18n/zh-CN")).toBe(true);
    expect(isSecurityWhitelisted("/api/auth/login")).toBe(false);
  });
});

describe("java key pair sync", () => {
  const originalFetch = globalThis.fetch;
  const envKeys = [
    "SECURITY_JAVA_KEY_PAIR_URL",
    "SECURITY_RSA_PUBLIC_KEY",
    "SECURITY_RSA_PRIVATE_KEY",
  ] as const;
  const savedEnv: Record<string, string | undefined> = {};

  beforeEach(() => {
    for (const k of envKeys) {
      savedEnv[k] = process.env[k];
      delete process.env[k];
    }
    resetJavaKeyPairSyncForTest();
    setEncryptKeyPairForTest(null);
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    for (const k of envKeys) {
      if (savedEnv[k] === undefined) delete process.env[k];
      else process.env[k] = savedEnv[k];
    }
    resetJavaKeyPairSyncForTest();
    setEncryptKeyPairForTest(null);
  });

  it("adopts java key pair so encrypt decrypt works", async () => {
    const pair = generateRsaKeyPair();
    process.env.SECURITY_JAVA_KEY_PAIR_URL = "http://java.test/api/encrypt/dev/key-pair";
    globalThis.fetch = (async () =>
      new Response(
        JSON.stringify({
          code: 0,
          msg: "ok",
          data: { publicKey: pair.publicKeyBase64, privateKey: pair.privateKeyPem },
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      )) as typeof fetch;

    const ok = await ensureJavaKeyPairSynced();
    expect(ok).toBe(true);

    const adopted = getEncryptKeyPair();
    expect(adopted.publicKeyBase64).toBe(pair.publicKeyBase64);
    expect(adopted.privateKeyPem).toBe(pair.privateKeyPem);

    const aesKey = generateAesKey();
    const encryptedKey = rsaEncrypt(aesKey, pair.publicKeyBase64);
    const now = Date.now();
    const requestId = "java-key-sync-1";
    const result = processSecurityRequest(
      {
        method: "GET",
        path: "/api/menu/all",
        headers: {
          [SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]: encryptedKey,
          [SECURITY_HEADERS.REQUEST_ID]: requestId,
          [SECURITY_HEADERS.REQUEST_TIMESTAMP]: String(now),
        },
        body: "",
        nowMs: now,
      },
      {
        config: fullConfig(),
        privateKeyPem: adopted.privateKeyPem,
        nonceStore: new MemoryNonceStore(),
      },
    );
    expect(result.ok).toBe(true);
    if (!result.ok) return;
    expect(result.responseAesKeyBase64).toBe(aesKey);
  });

  it("unset or empty SECURITY_JAVA_KEY_PAIR_URL skips fetch", async () => {
    // 未配置
    delete process.env.SECURITY_JAVA_KEY_PAIR_URL;
    let called = 0;
    globalThis.fetch = (async () => {
      called += 1;
      throw new Error("should not fetch");
    }) as typeof fetch;

    expect(await ensureJavaKeyPairSynced()).toBe(false);
    expect(called).toBe(0);

    resetJavaKeyPairSyncForTest();
    process.env.SECURITY_JAVA_KEY_PAIR_URL = "";
    expect(await ensureJavaKeyPairSynced()).toBe(false);
    expect(called).toBe(0);
  });

  it("fixed SECURITY_RSA_* skips java fetch", async () => {
    const pair = generateRsaKeyPair();
    process.env.SECURITY_RSA_PUBLIC_KEY = pair.publicKeyBase64;
    process.env.SECURITY_RSA_PRIVATE_KEY = pair.privateKeyPem;
    let called = 0;
    globalThis.fetch = (async () => {
      called += 1;
      throw new Error("should not fetch");
    }) as typeof fetch;

    const ok = await ensureJavaKeyPairSynced();
    expect(ok).toBe(false);
    expect(called).toBe(0);
  });
});
