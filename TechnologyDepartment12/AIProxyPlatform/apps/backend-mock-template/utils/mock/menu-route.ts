/**
 * 动态菜单路由 mock 数据（按角色分配 Dashboard / System / Log / Task）。
 *
 * 与 RBAC 的 sys_menu 种子（mock-menu-api.ts）是两套数据：
 * - 本文件是登录后 `/menu/all` 投影用的前端路由树（历史 vben 格式）。
 * - sys_menu 是系统管理页用的结构化菜单表。
 *
 * MOCK_MENU_LIST 仍被 menu/name-exists、menu/path-exists 引用。
 */

// ─── 动态菜单（按角色分配 Dashboard + System）─────────────────

const dashboardMenus = [
  {
    meta: {
      order: -1,
      title: "page.dashboard.title",
    },
    name: "Dashboard",
    path: "/dashboard",
    redirect: "/analytics",
    children: [
      {
        name: "Analytics",
        path: "/analytics",
        component: "/dashboard/analytics/index",
        meta: {
          affixTab: true,
          title: "page.dashboard.analytics",
        },
      },
      {
        name: "Workspace",
        path: "/workspace",
        component: "/dashboard/workspace/index",
        meta: {
          title: "page.dashboard.workspace",
        },
      },
    ],
  },
];

const systemMenus = (level: "full" | "partial") => {
  const fullChildren = [
    {
      name: "SystemUser",
      path: "/system/user",
      component: "/system/user/index",
      meta: {
        icon: "lucide:user-cog",
        order: 1,
        title: "system.user.title",
      },
    },
    {
      name: "SystemRole",
      path: "/system/role",
      component: "/system/role/index",
      meta: {
        icon: "lucide:shield-user",
        order: 2,
        title: "system.role.title",
      },
    },
    {
      name: "SystemDict",
      path: "/system/dict",
      component: "/system/dict/index",
      meta: {
        icon: "lucide:book-marked",
        order: 3,
        title: "system.dict.title",
      },
    },
    {
      name: "SystemI18n",
      path: "/system/i18n",
      component: "/system/i18n/index",
      meta: {
        icon: "lucide:languages",
        order: 4,
        title: "system.i18n.title",
      },
    },
    {
      name: "SystemMenu",
      path: "/system/menu",
      component: "/system/menu/index",
      meta: {
        icon: "lucide:menu",
        order: 5,
        title: "system.menu.title",
      },
    },
    {
      name: "SystemApi",
      path: "/system/api",
      component: "/system/api/index",
      meta: {
        icon: "lucide:terminal",
        order: 6,
        title: "system.api.title",
      },
    },
  ];
  const partialChildren = [
    {
      name: "SystemUser",
      path: "/system/user",
      component: "/system/user/index",
      meta: {
        icon: "lucide:user-cog",
        order: 1,
        title: "system.user.title",
      },
    },
    {
      name: "SystemRole",
      path: "/system/role",
      component: "/system/role/index",
      meta: {
        icon: "lucide:shield-user",
        order: 2,
        title: "system.role.title",
      },
    },
    {
      name: "SystemDict",
      path: "/system/dict",
      component: "/system/dict/index",
      meta: {
        icon: "lucide:book-marked",
        order: 3,
        title: "system.dict.title",
      },
    },
    {
      name: "SystemI18n",
      path: "/system/i18n",
      component: "/system/i18n/index",
      meta: {
        icon: "lucide:languages",
        order: 4,
        title: "system.i18n.title",
      },
    },
  ];
  return [
    {
      meta: {
        icon: "lucide:settings",
        order: 2005,
        title: "system.title",
      },
      name: "System",
      path: "/system",
      redirect: "/system/user",
      children: level === "full" ? fullChildren : partialChildren,
    },
  ];
};

/** 日志审计菜单（单菜单进页，页内 Tab 切换登录/API 日志） */
const logMenus = () => [
  {
    meta: {
      icon: "lucide:logs",
      order: 2004,
      title: "log.title",
    },
    name: "Log",
    path: "/log",
    component: "/log/index",
  },
];

/** 任务调度菜单（一级入口；页内 Tab 切换配置/执行记录） */
const taskMenus = () => [
  {
    meta: {
      icon: "lucide:timer",
      order: 2003,
      title: "task.title",
    },
    name: "Task",
    path: "/task",
    component: "/task/index",
  },
];

/** 历史按用户名投影的菜单（现已由 RBAC sys_role_menu 取代）；仅 root 全量对照。 */
export const MOCK_MENUS = [
  {
    menus: [...dashboardMenus, ...taskMenus(), ...logMenus(), ...systemMenus("full")],
    username: "root",
  },
];

export const MOCK_MENU_LIST = [
  {
    id: 1,
    name: "Workspace",
    status: 1,
    type: "menu",
    icon: "mdi:dashboard",
    path: "/workspace",
    component: "/dashboard/workspace/index",
    meta: {
      icon: "carbon:workspace",
      title: "page.dashboard.workspace",
      affixTab: true,
      order: 0,
    },
  },
  {
    id: 2,
    meta: {
      icon: "carbon:settings",
      order: 9997,
      title: "system.title",
      badge: "new",
      badgeType: "normal",
      badgeVariants: "primary",
    },
    status: 1,
    type: "catalog",
    name: "System",
    path: "/system",
    children: [
      {
        id: 201,
        pid: 2,
        path: "/system/menu",
        name: "SystemMenu",
        authCode: "System:Menu:List",
        status: 1,
        type: "menu",
        meta: {
          icon: "carbon:menu",
          title: "system.menu.title",
        },
        component: "/system/menu/list",
        children: [
          {
            id: 20_101,
            pid: 201,
            name: "SystemMenuCreate",
            status: 1,
            type: "button",
            authCode: "System:Menu:Create",
            meta: { title: "common.create" },
          },
          {
            id: 20_102,
            pid: 201,
            name: "SystemMenuEdit",
            status: 1,
            type: "button",
            authCode: "System:Menu:Edit",
            meta: { title: "common.edit" },
          },
          {
            id: 20_103,
            pid: 201,
            name: "SystemMenuDelete",
            status: 1,
            type: "button",
            authCode: "System:Menu:Delete",
            meta: { title: "common.delete" },
          },
        ],
      },
      {
        id: 202,
        pid: 2,
        path: "/system/dept",
        name: "SystemDept",
        status: 1,
        type: "menu",
        authCode: "System:Dept:List",
        meta: {
          icon: "carbon:container-services",
          title: "system.dept.title",
        },
        component: "/system/dept/list",
        children: [
          {
            id: 20_401,
            pid: 202,
            name: "SystemDeptCreate",
            status: 1,
            type: "button",
            authCode: "System:Dept:Create",
            meta: { title: "common.create" },
          },
          {
            id: 20_402,
            pid: 202,
            name: "SystemDeptEdit",
            status: 1,
            type: "button",
            authCode: "System:Dept:Edit",
            meta: { title: "common.edit" },
          },
          {
            id: 20_403,
            pid: 202,
            name: "SystemDeptDelete",
            status: 1,
            type: "button",
            authCode: "System:Dept:Delete",
            meta: { title: "common.delete" },
          },
        ],
      },
    ],
  },
  {
    id: 9,
    meta: {
      badgeType: "dot",
      order: 9998,
      title: "demos.vben.title",
      icon: "carbon:data-center",
    },
    name: "Project",
    path: "/vben-admin",
    type: "catalog",
    status: 1,
    children: [
      {
        id: 901,
        pid: 9,
        name: "VbenDocument",
        path: "/vben-admin/document",
        component: "IFrameView",
        type: "embedded",
        status: 1,
        meta: {
          icon: "carbon:book",
          iframeSrc: "https://doc.vben.pro",
          title: "demos.vben.document",
        },
      },
      {
        id: 902,
        pid: 9,
        name: "VbenGithub",
        path: "/vben-admin/github",
        component: "IFrameView",
        type: "link",
        status: 1,
        meta: {
          icon: "carbon:logo-github",
          link: "https://github.com/vbenjs/vue-vben-admin",
          title: "Github",
        },
      },
      {
        id: 903,
        pid: 9,
        name: "VbenAntdv",
        path: "/vben-admin/antdv",
        component: "IFrameView",
        type: "link",
        status: 0,
        meta: {
          icon: "carbon:hexagon-vertical-solid",
          badgeType: "dot",
          link: "https://ant.vben.pro",
          title: "demos.vben.antdv",
        },
      },
    ],
  },
  {
    id: 10,
    component: "_core/about/index",
    type: "menu",
    status: 1,
    meta: {
      icon: "lucide:copyright",
      order: 9999,
      title: "demos.vben.about",
    },
    name: "About",
    path: "/about",
  },
];

export function getMenuIds(
  menus: { id: number; children?: { id: number; children?: unknown[] }[] }[],
): number[] {
  const ids: number[] = [];
  menus.forEach((item) => {
    ids.push(item.id);
    if (item.children && item.children.length > 0) {
      ids.push(
        ...getMenuIds(
          item.children as { id: number; children?: { id: number; children?: unknown[] }[] }[],
        ),
      );
    }
  });
  return ids;
}
