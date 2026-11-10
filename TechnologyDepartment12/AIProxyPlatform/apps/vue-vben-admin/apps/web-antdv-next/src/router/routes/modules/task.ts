import type { RouteRecordRaw } from 'vue-router';

import { $t } from '#/locales';

/**
 * 任务调度：侧栏单菜单，点击进入 /task，页内 Tab 切换配置 / 执行记录。
 * 与 backend 菜单 component `/task/index` 对齐。
 */
const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:timer',
      order: 2003,
      title: $t('task.title'),
      // 页内 ?tab= 切换不应在顶栏再开一个「任务调度」标签
      fullPathKey: false,
    },
    name: 'Task',
    path: '/task',
    component: () => import('#/views/task/index.vue'),
  },
];

export default routes;
