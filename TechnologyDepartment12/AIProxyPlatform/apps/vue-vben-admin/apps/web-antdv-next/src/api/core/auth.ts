import { useAccessStore } from '@vben/stores';

import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
    /** ALTCHA PoW payload（Base64） */
    altcha?: string;
  }

  /** 登录接口返回值（sa-token 单 token + 用户摘要 + 会话专属公钥） */
  export interface LoginResult {
    accessToken: string;
    id?: number | string;
    username?: string;
    realName?: string;
    roles?: string[];
    homePath?: string;
    /** 会话专属 RSA 公钥（SPKI base64）；登录后客户端加密应使用此钥 */
    publicKey?: string;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/auth/login', data);
}

/**
 * 退出登录
 *
 * 必须走 baseRequestClient（无 401 重认证拦截器）。
 * 若走 requestClient，logout 自身 401 会再次触发 doReAuthenticate → 死循环。
 */
export async function logoutApi() {
  const accessStore = useAccessStore();
  const token = accessStore.accessToken;
  return baseRequestClient.post('/auth/logout', null, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
}

/**
 * 获取用户权限码
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
