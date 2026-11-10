import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 2005,
      title: $t('system.title'),
    },
    name: 'System',
    path: '/system',
    children: [
      {
        name: 'SystemUser',
        path: 'user',
        component: () => import('#/views/system/user/index.vue'),
        meta: {
          icon: 'lucide:user-cog',
          order: 1,
          title: $t('system.user.title'),
        },
      },
      {
        name: 'SystemRole',
        path: 'role',
        component: () => import('#/views/system/role/index.vue'),
        meta: {
          icon: 'lucide:shield-user',
          order: 2,
          title: $t('system.role.title'),
        },
      },
      {
        name: 'SystemDict',
        path: 'dict',
        component: () => import('#/views/system/dict/index.vue'),
        meta: {
          icon: 'lucide:book-marked',
          order: 3,
          title: $t('system.dict.title'),
        },
      },
      {
        name: 'SystemI18n',
        path: 'i18n',
        component: () => import('#/views/system/i18n/index.vue'),
        meta: {
          icon: 'lucide:languages',
          order: 4,
          title: $t('system.i18n.title'),
        },
      },
      {
        name: 'SystemMenu',
        path: 'menu',
        component: () => import('#/views/system/menu/index.vue'),
        meta: {
          icon: 'lucide:menu',
          order: 5,
          title: $t('system.menu.title'),
        },
      },
      {
        name: 'SystemApi',
        path: 'api',
        component: () => import('#/views/system/api/index.vue'),
        meta: {
          icon: 'lucide:terminal',
          order: 6,
          title: $t('system.api.title'),
        },
      },
      {
        name: 'SystemBlacklist',
        path: 'blacklist',
        component: () => import('#/views/system/blacklist/index.vue'),
        meta: {
          icon: 'lucide:shield-ban',
          order: 7,
          title: $t('system.blacklist.title'),
        },
      },
    ],
  },
];

export default routes;
