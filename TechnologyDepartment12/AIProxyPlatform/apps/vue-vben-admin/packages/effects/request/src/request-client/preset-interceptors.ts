import type { MakeErrorMessageFn, ResponseInterceptorConfig } from './types';

import { $t } from '@vben/locales';
import { isFunction } from '@vben/utils';

import axios from 'axios';

export const defaultResponseInterceptor = ({
  codeField = 'code',
  dataField = 'data',
  successCode = 0,
}: {
  /** 响应数据中代表访问结果的字段名 */
  codeField: string;
  /** 响应数据中装载实际数据的字段名，或者提供一个函数从响应数据中解析需要返回的数据 */
  dataField: ((response: any) => any) | string;
  /** 当codeField所指定的字段值与successCode相同时，代表接口访问成功。如果提供一个函数，则返回true代表接口访问成功 */
  successCode: ((code: any) => boolean) | number | string;
}): ResponseInterceptorConfig => {
  return {
    fulfilled: (response) => {
      const { config, data: responseData, status } = response;

      if (config.responseReturn === 'raw') {
        return response;
      }

      if (status >= 200 && status < 400) {
        if (config.responseReturn === 'body') {
          return responseData;
        } else if (
          isFunction(successCode)
            ? successCode(responseData[codeField])
            : responseData[codeField] === successCode
        ) {
          return isFunction(dataField)
            ? dataField(responseData)
            : responseData[dataField];
        }
      }
      throw Object.assign({}, response, { response });
    },
  };
};

function getRequestUrl(error: {
  config?: { url?: string };
  response?: { config?: { url?: string } };
}): string {
  const url = error?.config?.url ?? error?.response?.config?.url ?? '';
  return typeof url === 'string' ? url : '';
}

/** 登录等未持 token 的鉴权接口：业务 401 不是会话过期，不应 forceLogout */
function isUnauthenticatedAuthRequest(error: {
  config?: { url?: string };
  response?: { config?: { url?: string } };
}): boolean {
  return getRequestUrl(error).includes('/auth/login');
}

/** 公开接口失败不应触发登出 / 全局错误 toast（本地 i18n 已兜底） */
function isPublicOptionalRequest(error: {
  config?: { url?: string };
  response?: { config?: { url?: string } };
}): boolean {
  const url = getRequestUrl(error);
  return url.includes('/public/');
}

/**
 * 认证失败拦截器（sa-token 单 token 模式）
 * 401 时直接重新认证 / 登出，不做客户端 refresh 与队列重试
 */
export const authenticateResponseInterceptor = ({
  doReAuthenticate,
}: {
  doReAuthenticate: () => Promise<void>;
}): ResponseInterceptorConfig => {
  return {
    rejected: async (error) => {
      const { response } = error;
      // 非 401 直接抛出
      if (response?.status !== 401) {
        throw error;
      }
      // 登录失败 / 公开可选接口 401：不触发登出
      if (
        isUnauthenticatedAuthRequest(error) ||
        isPublicOptionalRequest(error)
      ) {
        throw error;
      }
      // 单 token：无 refresh 流程，直接重新登录
      await doReAuthenticate();
      throw error;
    },
  };
};

export const errorMessageResponseInterceptor = (
  makeErrorMessage?: MakeErrorMessageFn,
): ResponseInterceptorConfig => {
  return {
    rejected: (error: any) => {
      if (axios.isCancel(error)) {
        return Promise.reject(error);
      }

      // 公开可选接口（如 /public/i18n）静默失败，避免切语言时误 toast / 打断 UI
      if (isPublicOptionalRequest(error)) {
        return Promise.reject(error);
      }

      const err: string = error?.toString?.() ?? '';
      let errMsg = '';
      if (err?.includes('Network Error')) {
        errMsg = $t('ui.fallback.http.networkError');
      } else if (error?.message?.includes?.('timeout')) {
        errMsg = $t('ui.fallback.http.requestTimeout');
      }
      if (errMsg) {
        makeErrorMessage?.(errMsg, error);
        return Promise.reject(error);
      }

      let errorMessage: string;
      const status = error?.response?.status;

      switch (status) {
        case 400: {
          errorMessage = $t('ui.fallback.http.badRequest');
          break;
        }
        case 401: {
          errorMessage = $t('ui.fallback.http.unauthorized');
          break;
        }
        case 403: {
          errorMessage = $t('ui.fallback.http.forbidden');
          break;
        }
        case 404: {
          errorMessage = $t('ui.fallback.http.notFound');
          break;
        }
        case 408: {
          errorMessage = $t('ui.fallback.http.requestTimeout');
          break;
        }
        default: {
          errorMessage = $t('ui.fallback.http.internalServerError');
        }
      }
      makeErrorMessage?.(errorMessage, error);
      return Promise.reject(error);
    },
  };
};
