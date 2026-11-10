/**
 * 该文件可自行根据业务逻辑进行调整
 */
import type { RequestClientOptions } from '@vben/request';

import type { RequestSecurityDeps } from '#/api/security';

import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import {
  authenticateResponseInterceptor,
  defaultResponseInterceptor,
  errorMessageResponseInterceptor,
  RequestClient,
} from '@vben/request';
import { useAccessStore } from '@vben/stores';

import { message } from 'antdv-next';

import {
  applyRequestSecurity,
  clearCachedPublicKey,
  decryptResponseData,
  ensurePublicKey,
  getPublicCryptoKey,
  getSecurityFlags,
  isResponseEncrypted,
} from '#/api/security';
import { useAuthStore } from '#/store';
import {
  aesDecrypt,
  aesEncrypt,
  generateAesKey,
  rsaEncrypt,
} from '#/utils/crypto';
import { clearAccessMenusCache } from '#/utils/menu-cache';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);
/** 与 React `baseURL || '/api'` 对齐，避免 apiURL 空时请求落到错误路径 */
const resolvedApiURL = apiURL || '/api';

type AxiosConfigLike = {
  /** 本请求 AES 会话密钥，挂在 config 上避免并发竞态 */
  _aesKey?: CryptoKey | null;
  baseURL?: string;
  data?: unknown;
  headers?: Record<string, unknown>;
  meta?: { skipEncrypt?: boolean };
  method?: string;
  params?: Record<string, unknown> | string | URLSearchParams;
  responseType?: string;
  transformRequest?: unknown;
  url?: string;
};

function createSecurityDeps(): RequestSecurityDeps {
  return {
    aesEncrypt,
    aesDecrypt,
    generateAesKey,
    rsaEncrypt,
    // 未登录由 authLogin/登录页 prepareGlobalPublicKey 清会话钥并预拉全局钥；
    // 已登录走 storage 中的会话钥。apiURL 空时与 React 一样回退 /api。
    ensurePublicKey: () => ensurePublicKey(resolvedApiURL),
    getPublicCryptoKey,
  };
}

/**
 * 注入请求安全协议（Timestamp / Encrypt / Nonce·Request-ID / Sign / Language）。
 * requestClient 与 baseRequestClient 共用，保证 logout 等裸客户端在 Encrypt 开时也可通。
 */
function attachSecurityInterceptors(client: RequestClient) {
  const deps = createSecurityDeps();

  client.addRequestInterceptor({
    fulfilled: async (config) => {
      const cfg = config as AxiosConfigLike;
      const flags = getSecurityFlags();
      const contentType =
        typeof cfg.headers?.['Content-Type'] === 'string'
          ? (cfg.headers['Content-Type'] as string)
          : typeof cfg.headers?.['content-type'] === 'string'
            ? (cfg.headers['content-type'] as string)
            : null;

      const secured = await applyRequestSecurity(
        {
          baseURL: cfg.baseURL ?? resolvedApiURL,
          data: cfg.data,
          headers: cfg.headers as Record<string, unknown> | undefined,
          method: cfg.method,
          params: cfg.params,
          meta: cfg.meta,
          url: cfg.url,
          contentType,
          language: preferences.app.locale,
        },
        flags,
        deps,
      );

      cfg.headers = secured.headers as typeof cfg.headers;
      cfg.data = secured.data;
      cfg._aesKey = secured.aesKey ?? null;

      if (secured.responseType) {
        cfg.responseType = secured.responseType;
      }
      if (secured.rawBody) {
        // 加密 body 已是 base64 字符串，禁止 axios 再 JSON 序列化成带引号字符串
        cfg.transformRequest = [(data: unknown) => data];
        if (cfg.headers) {
          cfg.headers['Content-Type'] = 'application/json';
        }
      }

      return config;
    },
  });

  client.addResponseInterceptor({
    fulfilled: async (response) => {
      const cfg = response.config as AxiosConfigLike;
      const decrypted = await decryptResponseData(
        {
          data: response.data,
          isEncrypted: isResponseEncrypted(
            response.headers as Record<string, unknown>,
          ),
          aesKey: cfg._aesKey,
        },
        deps,
      );
      response.data = decrypted;
      return response;
    },
    rejected: async (error: unknown) => {
      const err = error as {
        response?: {
          config?: AxiosConfigLike;
          data?: unknown;
          headers?: Record<string, unknown>;
        };
      };
      if (err?.response) {
        const cfg = err.response.config;
        err.response.data = await decryptResponseData(
          {
            data: err.response.data,
            isEncrypted: isResponseEncrypted(err.response.headers),
            aesKey: cfg?._aesKey,
          },
          deps,
        );
      }
      throw error;
    },
  });
}

function createRequestClient(baseURL: string, options?: RequestClientOptions) {
  const client = new RequestClient({
    ...options,
    baseURL,
    // 数组 query 用重复键 typeCode=a&typeCode=b，避免默认 typeCode[]= 导致
    // 签名 AAD 的 key 与 mock/Java 解析结果不一致（1008 签名错误）。
    // 与 security/request-security.normalizeParams（多值取首项）配套。
    paramsSerializer: options?.paramsSerializer ?? 'repeat',
  });

  /**
   * 重新认证逻辑（sa-token 单 token：401 后直接登出 / 弹窗重新登录）
   *
   * - 单飞锁：并发 401 只处理一次，避免风暴
   * - skipApi：会话已 401，再调 /auth/logout 会二次 401 并死循环
   */
  let reAuthPromise: null | Promise<void> = null;
  async function doReAuthenticate() {
    if (reAuthPromise) {
      return reAuthPromise;
    }
    reAuthPromise = (async () => {
      console.warn('Access token is invalid or expired.');
      const accessStore = useAccessStore();
      const authStore = useAuthStore();
      accessStore.setAccessToken(null);
      // token 失效时清菜单缓存，避免过期会话菜单残留到下次登录
      clearAccessMenusCache();
      // 会话钥与 token 同生命周期：仅清 token 时也必须清公钥，否则重登会复用死会话钥
      clearCachedPublicKey();
      if (
        preferences.app.loginExpiredMode === 'modal' &&
        accessStore.isAccessChecked
      ) {
        accessStore.setLoginExpired(true);
      } else {
        await authStore.logout(true, { skipApi: true });
      }
    })().finally(() => {
      reAuthPromise = null;
    });
    return reAuthPromise;
  }

  function formatToken(token: null | string) {
    return token ? `Bearer ${token}` : null;
  }

  // 请求头处理（鉴权 + Accept-Language）
  client.addRequestInterceptor({
    fulfilled: async (config) => {
      // public 端点不需要登录态
      if (!config.url?.startsWith('/public/')) {
        const accessStore = useAccessStore();
        config.headers.Authorization = formatToken(accessStore.accessToken);
      }
      config.headers['Accept-Language'] = preferences.app.locale;
      return config;
    },
  });

  // 请求安全：Timestamp / Encrypt / Nonce / Sign / X-Language
  attachSecurityInterceptors(client);

  // 处理返回的响应数据格式（须在解密拦截器之后）
  client.addResponseInterceptor(
    defaultResponseInterceptor({
      codeField: 'code',
      dataField: 'data',
      successCode: 0,
    }),
  );

  // token 过期：直接重新认证，不做客户端 refresh
  client.addResponseInterceptor(
    authenticateResponseInterceptor({
      doReAuthenticate,
    }),
  );

  // 通用的错误处理,如果没有进入上面的错误处理逻辑，就会进入这里
  client.addResponseInterceptor(
    errorMessageResponseInterceptor((msg: string, error) => {
      // 统一契约：优先 msg（java-admin Result），兼容旧 message/error
      const responseData = error?.response?.data ?? error ?? {};
      const errorMessage =
        responseData?.msg ?? responseData?.message ?? responseData?.error ?? '';
      // 如果没有错误信息，则会根据状态码进行提示
      message.error(errorMessage || msg);
    }),
  );

  return client;
}

export const requestClient = createRequestClient(resolvedApiURL, {
  responseReturn: 'data',
});

/** 无 401 重认证拦截器的客户端（logout 等）；仍挂载安全协议。 */
export const baseRequestClient = new RequestClient({
  baseURL: resolvedApiURL,
  // 与 requestClient 一致：数组 query 重复键，避免签名 1008
  paramsSerializer: 'repeat',
});
attachSecurityInterceptors(baseRequestClient);
