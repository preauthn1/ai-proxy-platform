/**
 * 辅 seam：前端 request 安全客户端
 * — 分项开关、Encrypt 开/关、Language 头、白名单不加密。
 */

import type { RequestSecurityDeps } from './request-security';

import { beforeEach, describe, expect, it, vi } from 'vitest';

import { envFlagEnabled, loadSecurityFlags } from './config';
import { SECURITY_HEADERS } from './headers';
import { isSecurityWhitelisted, resolveRequestPath } from './path-matcher';
import {
  applyRequestSecurity,
  decryptResponseData,
  isResponseEncrypted,
} from './request-security';

function fullFlags(
  overrides: Partial<ReturnType<typeof loadSecurityFlags>> = {},
) {
  return {
    ...loadSecurityFlags({}),
    ...overrides,
  };
}

function mockDeps(
  overrides: Partial<RequestSecurityDeps> = {},
): RequestSecurityDeps {
  const aesKey = {} as CryptoKey;
  const publicCryptoKey = {} as CryptoKey;
  return {
    aesEncrypt: vi.fn().mockResolvedValue({
      Ciphertext: 'cipher-b64',
      TagIv: 'tag-iv-b64',
    }),
    aesDecrypt: vi.fn().mockResolvedValue('{"code":0,"msg":"ok","data":1}'),
    generateAesKey: vi.fn().mockResolvedValue({
      key: aesKey,
      keyBase64: 'aes-key-b64',
    }),
    rsaEncrypt: vi.fn().mockResolvedValue('rsa-encrypted-key'),
    ensurePublicKey: vi.fn().mockResolvedValue('public-key-b64'),
    getPublicCryptoKey: vi.fn().mockResolvedValue(publicCryptoKey),
    now: () => 1_700_000_000_000,
    nonce: () => 'nonce-fixed-1',
    ...overrides,
  };
}

describe('security env flags', () => {
  it('defaults all switches on when env missing', () => {
    const flags = loadSecurityFlags({});
    expect(flags).toEqual({
      timestampEnabled: true,
      encryptEnabled: true,
      nonceEnabled: true,
      signEnabled: true,
      languageEnabled: true,
    });
  });

  it('can disable encrypt independently', () => {
    expect(envFlagEnabled('false')).toBe(false);
    expect(envFlagEnabled('0')).toBe(false);
    expect(envFlagEnabled('off')).toBe(false);
    const flags = loadSecurityFlags({
      VITE_SECURITY_ENCRYPT_ENABLED: 'false',
    });
    expect(flags.encryptEnabled).toBe(false);
    expect(flags.timestampEnabled).toBe(true);
    expect(flags.languageEnabled).toBe(true);
  });
});

describe('path whitelist', () => {
  it('resolves axios baseURL + url to full path', () => {
    expect(resolveRequestPath('/auth/login', '/api')).toBe('/api/auth/login');
    expect(resolveRequestPath('/encrypt/public/key', '/api')).toBe(
      '/api/encrypt/public/key',
    );
  });

  it('whitelists public key and altcha, not login', () => {
    expect(isSecurityWhitelisted('/api/encrypt/public/key')).toBe(true);
    expect(isSecurityWhitelisted('/api/altcha/challenge')).toBe(true);
    expect(isSecurityWhitelisted('/api/public/i18n/zh-CN')).toBe(true);
    expect(isSecurityWhitelisted('/public/i18n/en-US')).toBe(true);
    expect(isSecurityWhitelisted('/api/auth/login')).toBe(false);
  });
});

describe('applyRequestSecurity', () => {
  let deps: RequestSecurityDeps;

  beforeEach(() => {
    deps = mockDeps();
  });

  it('language on: injects X-Language when encrypt off', async () => {
    const result = await applyRequestSecurity(
      {
        url: '/user/info',
        baseURL: '/api',
        method: 'GET',
        language: 'zh-CN',
      },
      fullFlags({
        encryptEnabled: false,
        signEnabled: false,
      }),
      deps,
    );
    expect(result.headers[SECURITY_HEADERS.LANGUAGE]).toBe('zh-CN');
    expect(
      result.headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY],
    ).toBeUndefined();
    expect(deps.aesEncrypt).not.toHaveBeenCalled();
  });

  it('encrypt on: encrypts POST body and sets crypto headers', async () => {
    const body = { username: 'root', REDACTED };
    const result = await applyRequestSecurity(
      {
        url: '/auth/login',
        baseURL: '/api',
        method: 'POST',
        data: body,
        language: 'en-US',
      },
      fullFlags(),
      deps,
    );

    expect(result.headers[SECURITY_HEADERS.REQUEST_TIMESTAMP]).toBe(
      '1700000000000',
    );
    expect(result.headers[SECURITY_HEADERS.REQUEST_ID]).toBe('nonce-fixed-1');
    expect(result.headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]).toBe(
      'rsa-encrypted-key',
    );
    expect(result.headers[SECURITY_HEADERS.REQUEST_SIGNATURE]).toBe(
      'tag-iv-b64',
    );
    expect(result.headers[SECURITY_HEADERS.LANGUAGE]).toBe('en-US');
    expect(result.data).toBe('cipher-b64');
    expect(result.rawBody).toBe(true);
    expect(result.responseType).toBe('text');
    expect(result.aesKey).toBeDefined();
    expect(deps.aesEncrypt).toHaveBeenCalledWith(
      expect.anything(),
      'X-Request-ID=nonce-fixed-1&X-Request-Timestamp=1700000000000',
      body,
    );
  });

  it('encrypt on: GET signs empty payload and keeps query in AAD', async () => {
    await applyRequestSecurity(
      {
        url: '/system/api/list',
        baseURL: '/api',
        method: 'GET',
        params: { page: 1 },
      },
      fullFlags(),
      deps,
    );

    expect(deps.aesEncrypt).toHaveBeenCalledWith(
      expect.anything(),
      'X-Request-ID=nonce-fixed-1&X-Request-Timestamp=1700000000000&page=1',
      undefined,
    );
  });

  it('sign 模式：数组 query 只取首值进 AAD（对齐 mock/Java，修 dict-data typeCode[]）', async () => {
    await applyRequestSecurity(
      {
        url: '/system/dict-data/list',
        baseURL: '/api',
        method: 'GET',
        params: {
          typeCode: ['sys_switch_status', 'sys_platform'],
          page: 1,
          pageSize: 20,
          includeGeneral: true,
          platform: 'vue-admin',
          emptyArr: [],
        },
      },
      fullFlags({ encryptEnabled: false, signEnabled: true }),
      deps,
    );

    // 多值只取首项；不得变成 "a,b"；空数组跳过
    const expectedAad = [
      'X-Request-ID=nonce-fixed-1',
      'X-Request-Timestamp=1700000000000',
      'includeGeneral=true',
      'page=1',
      'pageSize=20',
      'platform=vue-admin',
      'typeCode=sys_switch_status',
    ].join('&');
    expect(deps.aesEncrypt).toHaveBeenCalledWith(
      expect.anything(),
      expectedAad,
      undefined,
    );
  });

  it('encrypt 模式：数组 query 同样只取首值进 AAD', async () => {
    await applyRequestSecurity(
      {
        url: '/system/dict-data/list',
        baseURL: '/api',
        method: 'GET',
        params: {
          typeCode: ['sys_switch_status', 'sys_platform'],
          includeGeneral: true,
        },
      },
      fullFlags({ encryptEnabled: true }),
      deps,
    );

    expect(deps.aesEncrypt).toHaveBeenCalledWith(
      expect.anything(),
      'X-Request-ID=nonce-fixed-1&X-Request-Timestamp=1700000000000&includeGeneral=true&typeCode=sys_switch_status',
      undefined,
    );
  });

  it('encrypt on: public key path stays plaintext', async () => {
    const result = await applyRequestSecurity(
      {
        url: '/encrypt/public/key',
        baseURL: '/api',
        method: 'GET',
      },
      fullFlags(),
      deps,
    );

    expect(
      result.headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY],
    ).toBeUndefined();
    expect(deps.aesEncrypt).not.toHaveBeenCalled();
    expect(deps.ensurePublicKey).not.toHaveBeenCalled();
  });

  it('encrypt off + sign on: keeps body plain and sets signature', async () => {
    const body = { name: 'x' };
    const result = await applyRequestSecurity(
      {
        url: '/system/api',
        baseURL: '/api',
        method: 'POST',
        data: body,
      },
      fullFlags({ encryptEnabled: false, signEnabled: true }),
      deps,
    );

    expect(result.data).toEqual(body);
    expect(result.headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY]).toBe(
      'rsa-encrypted-key',
    );
    expect(result.headers[SECURITY_HEADERS.REQUEST_SIGNATURE]).toBe(
      'tag-iv-b64',
    );
    expect(result.rawBody).toBeUndefined();
    // signData 进入 AAD
    expect(deps.aesEncrypt).toHaveBeenCalledWith(
      expect.anything(),
      expect.stringContaining('signData='),
      undefined,
    );
  });

  it('encrypt off + sign off: plaintext without crypto headers', async () => {
    const body = { a: 1 };
    const result = await applyRequestSecurity(
      {
        url: '/auth/login',
        baseURL: '/api',
        method: 'POST',
        data: body,
      },
      fullFlags({ encryptEnabled: false, signEnabled: false }),
      deps,
    );

    expect(result.data).toEqual(body);
    expect(
      result.headers[SECURITY_HEADERS.REQUEST_ENCRYPTED_KEY],
    ).toBeUndefined();
    expect(result.headers[SECURITY_HEADERS.REQUEST_SIGNATURE]).toBeUndefined();
    expect(deps.ensurePublicKey).not.toHaveBeenCalled();
  });

  it('language off: does not inject X-Language', async () => {
    const result = await applyRequestSecurity(
      {
        url: '/user/info',
        baseURL: '/api',
        method: 'GET',
        language: 'zh-CN',
      },
      fullFlags({
        encryptEnabled: false,
        signEnabled: false,
        languageEnabled: false,
      }),
      deps,
    );
    expect(result.headers[SECURITY_HEADERS.LANGUAGE]).toBeUndefined();
  });
});

describe('decryptResponseData', () => {
  it('parses decrypted JSON when response is encrypted', async () => {
    const deps = mockDeps();
    const data = await decryptResponseData(
      {
        data: 'encrypted-blob',
        isEncrypted: true,
        aesKey: {} as CryptoKey,
      },
      deps,
    );
    expect(data).toEqual({ code: 0, msg: 'ok', data: 1 });
    expect(deps.aesDecrypt).toHaveBeenCalledWith(
      'encrypted-blob',
      expect.anything(),
      '',
    );
  });

  it('passes through when not encrypted', async () => {
    const deps = mockDeps();
    const payload = { code: 0, data: 'plain' };
    const data = await decryptResponseData(
      { data: payload, isEncrypted: false, aesKey: null },
      deps,
    );
    expect(data).toBe(payload);
    expect(deps.aesDecrypt).not.toHaveBeenCalled();
  });

  it('parses plain JSON string when responseType text but not encrypted', async () => {
    const deps = mockDeps();
    const data = await decryptResponseData(
      {
        data: '{"code":0,"msg":"ok","data":{"id":1}}',
        isEncrypted: false,
        aesKey: null,
      },
      deps,
    );
    expect(data).toEqual({ code: 0, msg: 'ok', data: { id: 1 } });
  });

  it('throws when encrypt on but public key unavailable', async () => {
    const deps = mockDeps({
      ensurePublicKey: vi.fn().mockResolvedValue(''),
    });
    await expect(
      applyRequestSecurity(
        {
          url: '/auth/login',
          baseURL: '/api',
          method: 'POST',
          data: { username: 'a' },
        },
        fullFlags(),
        deps,
      ),
    ).rejects.toThrow(/公钥/);
  });

  it('detects X-Response-Is-Encrypt header case-insensitively', () => {
    expect(isResponseEncrypted({ 'x-response-is-encrypt': 'true' })).toBe(true);
    expect(isResponseEncrypted({ 'X-Response-Is-Encrypt': 'false' })).toBe(
      false,
    );
  });
});
