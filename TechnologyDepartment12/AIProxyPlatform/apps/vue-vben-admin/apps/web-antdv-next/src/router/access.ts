import type {
  ComponentRecordType,
  GenerateMenuAndRoutesOptions,
  RouteRecordStringComponent,
} from '@vben/types';

import { generateAccessible } from '@vben/access';
import { preferences } from '@vben/preferences';
import { useAccessStore } from '@vben/stores';

import { message } from 'antdv-next';

import { getAllMenusApi } from '#/api';
import { BasicLayout, IFrameView } from '#/layouts';
import { $t } from '#/locales';
import { loadAccessMenusCache, saveAccessMenusCache } from '#/utils/menu-cache';

const forbiddenComponent = () => import('#/views/_core/fallback/forbidden.vue');

async function generateAccess(options: GenerateMenuAndRoutesOptions) {
  const pageMap: ComponentRecordType = import.meta.glob('../views/**/*.vue');

  const layoutMap: ComponentRecordType = {
    BasicLayout,
    IFrameView,
  };

  return await generateAccessible(preferences.app.accessMode, {
    ...options,
    fetchMenuListAsync: async () => {
      message.loading({
        content: `${$t('common.loadingMenu')}...`,
        duration: 1.5,
      });

      const token = useAccessStore().accessToken;
      try {
        const menus = await getAllMenusApi();
        const list = (menus ?? []) as RouteRecordStringComponent[];
        if (token) {
          saveAccessMenusCache(token, list);
        }
        return list;
      } catch (error) {
        // 已登录且本 token 有缓存：降级用旧菜单；新登录无缓存：抛出由 guard 阻断
        if (token) {
          const cached =
            loadAccessMenusCache<RouteRecordStringComponent>(token);
          if (cached) {
            message.warning('菜单加载失败，已使用本地缓存');
            return cached;
          }
        }
        throw error;
      }
    },
    // 可以指定没有权限跳转403页面
    forbiddenComponent,
    // 如果 route.meta.menuVisibleWithForbidden = true
    layoutMap,
    pageMap,
  });
}

export { generateAccess };
