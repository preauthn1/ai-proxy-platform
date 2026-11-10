import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/**
 * 日志审计：侧栏单菜单，点击直接进入 /log，页内 Tab 切换登录日志 / API 日志。
 */
const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:logs',
      order: 2004,
      title: $t('log.title'),
      // 页内 ?tab= 切换不应在顶栏再开一个「日志审计」标签
      fullPathKey: false,
    },
    name: 'Log',
    path: '/log',
    component: () => import('#/views/log/index.vue'),
  },
];

export default routes;
