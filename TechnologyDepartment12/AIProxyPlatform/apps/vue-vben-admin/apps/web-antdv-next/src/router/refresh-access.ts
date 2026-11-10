import type { Router } from 'vue-router';

import { useAccessStore, useUserStore } from '@vben/stores';
import { resetStaticRoutes } from '@vben/utils';

import { getAccessCodesApi } from '#/api';
import { accessRoutes, routes } from '#/router/routes';

import { generateAccess } from './access';

/**
 * Rebuild runtime menus/routes from GET /menu/all after RBAC changes.
 * Safe to call from admin CRUD success handlers.
 */
export async function refreshAccess(router: Router) {
  const accessStore = useAccessStore();
  const userStore = useUserStore();

  // Drop previously injected dynamic routes back to static core.
  resetStaticRoutes(router, routes);
  accessStore.setIsAccessChecked(false);
  accessStore.setAccessMenus([]);
  accessStore.setAccessRoutes([]);

  // Refresh button codes (derived from granted BUTTONs server-side).
  try {
    const codes = await getAccessCodesApi();
    accessStore.setAccessCodes(codes);
  } catch (error) {
    console.warn('[refreshAccess] failed to reload access codes', error);
  }

  const userRoles = userStore.userInfo?.roles ?? [];
  const { accessibleMenus, accessibleRoutes } = await generateAccess({
    roles: userRoles,
    router,
    routes: accessRoutes,
  });

  accessStore.setAccessMenus(accessibleMenus);
  accessStore.setAccessRoutes(accessibleRoutes);
  accessStore.setIsAccessChecked(true);
}
