import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { useAppConfig } from '@vben/hooks';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'antdv-next';
import { defineStore } from 'pinia';

import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from '#/api';
import {
  clearCachedPublicKey,
  prepareGlobalPublicKey,
  setCachedPublicKey,
} from '#/api/security';
import { $t } from '#/locales';
import { clearAccessMenusCache } from '#/utils/menu-cache';

const { apiURL } = useAppConfig(import.meta.env, import.meta.env.PROD);

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      // 登录须用全局公钥：清掉可能残留的会话钥，强制 GET /encrypt/public/key（对齐 React）
      await prepareGlobalPublicKey(apiURL || '/api');
      const { accessToken, publicKey } = await loginApi(params);

      // 如果成功获取到 accessToken
      if (accessToken) {
        accessStore.setAccessToken(accessToken);
        // 登录后改用会话专属公钥（对齐 java / mock LoginResponse.publicKey）
        if (publicKey) {
          setCachedPublicKey(publicKey);
        }

        // 获取用户信息并存储到 accessStore 中
        const [fetchUserInfoResult, accessCodes] = await Promise.all([
          fetchUserInfo(),
          getAccessCodesApi(),
        ]);

        userInfo = fetchUserInfoResult;

        userStore.setUserInfo(userInfo);
        accessStore.setAccessCodes(accessCodes);

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (userInfo?.realName) {
          notification.success({
            description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            duration: 3,
            title: $t('authentication.loginSuccess'),
          });
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  /**
   * 退出登录
   * @param redirect 是否带 redirect 回跳参数
   * @param options.skipApi 为 true 时跳过服务端 logout（用于 401 重认证：会话已失效，再调会二次 401）
   */
  async function logout(
    redirect: boolean = true,
    options: { skipApi?: boolean } = {},
  ) {
    if (!options.skipApi) {
      try {
        await logoutApi();
      } catch {
        // 服务端登出失败不影响本地清理
      }
    }
    resetAllStores();
    accessStore.setLoginExpired(false);
    // 必须清菜单缓存，避免新登录误用上一会话 menu
    clearAccessMenusCache();
    // 清会话公钥，下次登录前重新走全局 /encrypt/public/key
    clearCachedPublicKey();

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    userStore.setUserInfo(userInfo);
    return userInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});
