/**
 * 菜单/接口管理（sys_menu / sys_api / sys_menu_api）mock 数据 + CRUD + 种子。
 *
 * 字段对齐 Open Design 原型 schema.sql 的 sys_menu / sys_api / sys_menu_api。
 * 内部 snake 存储，handler 边界转 camel（见 utils/menu-api-camel.ts）。
 *
 * 依赖说明：mock-user-role 的角色-接口种子需要先有 sys_api 种子，
 * 因此导出 buildSysApiSeeds 供其调用（避免循环依赖：user-role 单向依赖本模块）。
 */

// ============================================================
// 菜单/接口管理（sys_menu / sys_api / sys_menu_api）
// 字段对齐 Open Design 原型 schema.sql 的 sys_menu / sys_api / sys_menu_api
// 内部 snake 存储，handler 边界转 camel（见 utils/menu-api-camel.ts）
// ============================================================

export type MenuType = "DIR" | "MENU" | "BUTTON";

export interface SysMenu {
  id: number;
  /** 父菜单 ID，NULL = 根 */
  parent_id: number | null;
  name: string;
  type: MenuType;
  /** 路由路径；仅 MENU 类型有效，DIR/BUTTON 为 NULL */
  path: string | null;
  /** 前端组件路径；仅 MENU 类型有效 */
  component: string | null;
  icon: string;
  /** 路由重定向；仅 MENU 类型，缺省 "" */
  redirect: string;
  /** 权限码；BUTTON 必填，MENU/DIR 可空 */
  permission_code: string | null;
  /** 物化路径，如 /1/11/，便于查祖先/子树；根为 /<id>/ */
  tree_path: string;
  /** 前端扩展 JSON 字符串（badge/hideInBreadcrumb/keepAlive/affix/activeMenu） */
  metadata: string | null;
  sort: number;
  is_hidden: 0 | 1;
  is_enabled: 0 | 1;
  deleted_at: number;
  remark: string;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

export interface SysApi {
  id: number;
  name: string;
  /** HTTP method: GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD */
  method: string;
  /** 接口路径，支持 :id 占位，不含 host */
  path: string;
  /** 权限码（与按钮权限码同构） */
  permission_code: string;
  /** 分组 */
  api_group: string;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

/** 菜单-API 绑定关联（sys_menu_api），复合主键 (menu_id, api_id) */
export interface SysMenuApi {
  menu_id: number;
  api_id: number;
  created_at: string;
  created_by: number;
}

const ALLOWED_METHODS = ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"] as const;
export const HTTP_METHODS = [...ALLOWED_METHODS];
export type HttpMethod = (typeof ALLOWED_METHODS)[number];

export function isAllowedMethod(v: unknown): v is HttpMethod {
  return typeof v === "string" && (ALLOWED_METHODS as readonly string[]).includes(v.toUpperCase());
}

const MENU_TYPE_VALUES = ["DIR", "MENU", "BUTTON"] as const;
export function isAllowedMenuType(v: unknown): v is MenuType {
  return typeof v === "string" && (MENU_TYPE_VALUES as readonly string[]).includes(v);
}

/** 共享可变菜单列表 */
const mockSysMenuList: SysMenu[] = [];
export function getMockSysMenuList() {
  return mockSysMenuList;
}

/** 共享可变接口列表 */
const mockSysApiList: SysApi[] = [];
export function getMockSysApiList() {
  return mockSysApiList;
}

/** 共享可变菜单-接口绑定列表 */
const mockSysMenuApiList: SysMenuApi[] = [];
export function getMockSysMenuApiList() {
  return mockSysMenuApiList;
}

/** 生成菜单自增 ID（用计数器，便于 tree_path 稳定） */
let menuIdSeq = 0;
function nextMenuId(): number {
  menuIdSeq += 1;
  return menuIdSeq;
}

/** 生成接口自增 ID */
let apiIdSeq = 0;
function nextApiId(): number {
  apiIdSeq += 1;
  return apiIdSeq;
}

/**
 * 按 parent_id 计算物化路径 tree_path。
 * 根节点（parentId=null）→ `/${id}/`；子节点 → `${parent.tree_path}${id}/`。
 */
function buildTreePath(id: number, parentId: number | null): string {
  if (parentId === null || parentId === undefined) return `/${id}/`;
  const parent = mockSysMenuList.find((m) => m.id === parentId && m.deleted_at === 0);
  if (!parent) return `/${id}/`;
  return `${parent.tree_path}${id}/`;
}

/**
 * 某节点删除后，递归重建其所有后代的 tree_path。
 * 父节点 tree_path 变更时调用；demo 数据量小，遍历重建即可。
 */
function rebuildDescendantTreePaths(parentId: number): void {
  const children = mockSysMenuList.filter((m) => m.parent_id === parentId && m.deleted_at === 0);
  for (const child of children) {
    const parent = mockSysMenuList.find((m) => m.id === parentId);
    const newPath = parent ? `${parent.tree_path}${child.id}/` : `/${child.id}/`;
    const idx = mockSysMenuList.indexOf(child);
    mockSysMenuList[idx] = { ...child, tree_path: newPath };
    rebuildDescendantTreePaths(child.id);
  }
}

/** 给定 id 是否存在未软删的后代节点 */
export function hasMenuChildren(id: number): boolean {
  return mockSysMenuList.some((m) => m.parent_id === id && m.deleted_at === 0);
}

/** 软删某菜单；调用方需先 hasMenuChildren 校验 */
export function softDeleteMenu(id: number): SysMenu | undefined {
  const idx = mockSysMenuList.findIndex((m) => m.id === id && m.deleted_at === 0);
  if (idx < 0) return undefined;
  mockSysMenuList[idx] = { ...mockSysMenuList[idx], deleted_at: Date.now() };
  return mockSysMenuList[idx];
}

/** 创建菜单（含 tree_path 计算）。返回新建行。 */
export function createSysMenu(input: {
  parentId: number | null;
  name: string;
  type: MenuType;
  path: string | null;
  component: string | null;
  icon: string;
  redirect: string;
  permissionCode: string | null;
  metadata: string | null;
  sort: number;
  isHidden: 0 | 1;
  isEnabled: 0 | 1;
  remark: string;
}): SysMenu {
  const id = nextMenuId();
  const now = new Date().toISOString();
  const row: SysMenu = {
    id,
    parent_id: input.parentId,
    name: input.name,
    type: input.type,
    path: input.path,
    component: input.component,
    icon: input.icon,
    redirect: input.redirect,
    permission_code: input.permissionCode,
    tree_path: "", // 先占位，下面按 parentId 计算
    metadata: input.metadata,
    sort: input.sort,
    is_hidden: input.isHidden,
    is_enabled: input.isEnabled,
    deleted_at: 0,
    remark: input.remark,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  row.tree_path = buildTreePath(id, input.parentId);
  mockSysMenuList.push(row);
  return row;
}

/** 更新菜单；parentId 变更时重算自身及子树 tree_path。返回更新后行或 undefined。 */
export function updateSysMenu(
  id: number,
  patch: Partial<{
    parentId: number | null;
    name: string;
    type: MenuType;
    path: string | null;
    component: string | null;
    icon: string;
    redirect: string;
    permissionCode: string | null;
    metadata: string | null;
    sort: number;
    isHidden: 0 | 1;
    isEnabled: 0 | 1;
    remark: string;
  }>,
): SysMenu | undefined {
  const idx = mockSysMenuList.findIndex((m) => m.id === id && m.deleted_at === 0);
  if (idx < 0) return undefined;
  const now = new Date().toISOString();
  const before = mockSysMenuList[idx];
  const parentIdChanged = patch.parentId !== undefined && patch.parentId !== before.parent_id;
  const next: SysMenu = {
    ...before,
    parent_id: patch.parentId !== undefined ? patch.parentId : before.parent_id,
    name: patch.name !== undefined ? patch.name : before.name,
    type: patch.type !== undefined ? patch.type : before.type,
    path: patch.path !== undefined ? patch.path : before.path,
    component: patch.component !== undefined ? patch.component : before.component,
    icon: patch.icon !== undefined ? patch.icon : before.icon,
    redirect: patch.redirect !== undefined ? patch.redirect : before.redirect,
    permission_code:
      patch.permissionCode !== undefined ? patch.permissionCode : before.permission_code,
    metadata: patch.metadata !== undefined ? patch.metadata : before.metadata,
    sort: patch.sort !== undefined ? patch.sort : before.sort,
    is_hidden: patch.isHidden !== undefined ? patch.isHidden : before.is_hidden,
    is_enabled: patch.isEnabled !== undefined ? patch.isEnabled : before.is_enabled,
    remark: patch.remark !== undefined ? patch.remark : before.remark,
    updated_at: now,
    updated_by: 0,
  };
  if (parentIdChanged) {
    next.tree_path = buildTreePath(id, next.parent_id);
  }
  mockSysMenuList[idx] = next;
  if (parentIdChanged) {
    rebuildDescendantTreePaths(id);
  }
  return next;
}

/** 创建接口。返回新建行。 */
export function createSysApi(input: {
  name: string;
  method: string;
  path: string;
  permissionCode: string;
  apiGroup: string;
  remark: string;
  isEnabled: 0 | 1;
}): SysApi {
  const id = nextApiId();
  const now = new Date().toISOString();
  const row: SysApi = {
    id,
    name: input.name,
    method: input.method.toUpperCase(),
    path: input.path,
    permission_code: input.permissionCode,
    api_group: input.apiGroup,
    remark: input.remark,
    is_enabled: input.isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  mockSysApiList.push(row);
  return row;
}

/** 更新接口。返回更新后行或 undefined。 */
export function updateSysApi(
  id: number,
  patch: Partial<{
    name: string;
    method: string;
    path: string;
    permissionCode: string;
    apiGroup: string;
    remark: string;
    isEnabled: 0 | 1;
  }>,
): SysApi | undefined {
  const idx = mockSysApiList.findIndex((a) => a.id === id && a.deleted_at === 0);
  if (idx < 0) return undefined;
  const now = new Date().toISOString();
  const before = mockSysApiList[idx];
  const next: SysApi = {
    ...before,
    name: patch.name !== undefined ? patch.name : before.name,
    method: patch.method !== undefined ? patch.method.toUpperCase() : before.method,
    path: patch.path !== undefined ? patch.path : before.path,
    permission_code:
      patch.permissionCode !== undefined ? patch.permissionCode : before.permission_code,
    api_group: patch.apiGroup !== undefined ? patch.apiGroup : before.api_group,
    remark: patch.remark !== undefined ? patch.remark : before.remark,
    is_enabled: patch.isEnabled !== undefined ? patch.isEnabled : before.is_enabled,
    updated_at: now,
    updated_by: 0,
  };
  mockSysApiList[idx] = next;
  return next;
}

/** 软删接口 */
export function softDeleteApi(id: number): SysApi | undefined {
  const idx = mockSysApiList.findIndex((a) => a.id === id && a.deleted_at === 0);
  if (idx < 0) return undefined;
  mockSysApiList[idx] = { ...mockSysApiList[idx], deleted_at: Date.now() };
  return mockSysApiList[idx];
}

/** 读取某菜单已绑定的接口 ID 列表 */
export function getMenuApiIds(menuId: number): number[] {
  return mockSysMenuApiList.filter((r) => r.menu_id === menuId).map((r) => r.api_id);
}

/**
 * 按菜单 ID 列表聚合 sys_menu_api → 去重的未软删 api_id。
 * 供角色授权「从已选菜单带出接口」使用（结构化快捷绑定，非直接授权）。
 */
export function getApiIdsByMenuIds(menuIds: number[]): number[] {
  if (!menuIds.length) return [];
  const menuIdSet = new Set(menuIds);
  const validApiIds = new Set(mockSysApiList.filter((a) => a.deleted_at === 0).map((a) => a.id));
  const out: number[] = [];
  const seen = new Set<number>();
  for (const row of mockSysMenuApiList) {
    if (!menuIdSet.has(row.menu_id)) continue;
    if (!validApiIds.has(row.api_id) || seen.has(row.api_id)) continue;
    seen.add(row.api_id);
    out.push(row.api_id);
  }
  return out.sort((a, b) => a - b);
}

/** 全量替换某菜单的接口绑定（覆盖写）。返回最终绑定的 api_id 列表。 */
export function setMenuApis(menuId: number, apiIds: number[]): number[] {
  // 删除旧的
  for (let i = mockSysMenuApiList.length - 1; i >= 0; i--) {
    if (mockSysMenuApiList[i].menu_id === menuId) {
      mockSysMenuApiList.splice(i, 1);
    }
  }
  const now = new Date().toISOString();
  // 只绑定存在且未软删的接口
  const validIds = new Set(mockSysApiList.filter((a) => a.deleted_at === 0).map((a) => a.id));
  const bound: number[] = [];
  for (const aid of apiIds) {
    if (validIds.has(aid) && !bound.includes(aid)) {
      mockSysMenuApiList.push({ menu_id: menuId, api_id: aid, created_at: now, created_by: 0 });
      bound.push(aid);
    }
  }
  return bound;
}

/** 清除某菜单的全部绑定（菜单软删前调用） */
export function clearMenuApis(menuId: number): void {
  for (let i = mockSysMenuApiList.length - 1; i >= 0; i--) {
    if (mockSysMenuApiList[i].menu_id === menuId) {
      mockSysMenuApiList.splice(i, 1);
    }
  }
}

/** 清除某接口的全部绑定（接口软删前调用） */
export function clearApiMenus(apiId: number): void {
  for (let i = mockSysMenuApiList.length - 1; i >= 0; i--) {
    if (mockSysMenuApiList[i].api_id === apiId) {
      mockSysMenuApiList.splice(i, 1);
    }
  }
}

/** 同步 upsert：按 (method, path) 命中则跳过，否则新增。返回 added/skipped/total。 */
export function syncApisFromManifest(
  manifest: Array<{
    name: string;
    method: string;
    path: string;
    permissionCode: string;
    apiGroup: string;
  }>,
): { added: number; skipped: number; total: number } {
  let added = 0;
  let skipped = 0;
  for (const item of manifest) {
    const exists = mockSysApiList.some(
      (a) => a.deleted_at === 0 && a.method === item.method.toUpperCase() && a.path === item.path,
    );
    if (exists) {
      skipped += 1;
      continue;
    }
    createSysApi({
      name: item.name,
      method: item.method,
      path: item.path,
      permissionCode: item.permissionCode,
      apiGroup: item.apiGroup,
      remark: "同步自后端路由清单",
      isEnabled: 1,
    });
    added += 1;
  }
  return { added, skipped, total: mockSysApiList.filter((a) => a.deleted_at === 0).length };
}

/** 种子：菜单树（对齐两端实际页面 path/component，供 RBAC → /menu/all 投影） */
function buildSysMenuSeeds(): SysMenu[] {
  const now = "2025-01-10T08:00:00.000Z";
  const base = {
    redirect: "",
    metadata: null as string | null,
    is_hidden: 0 as 0 | 1,
    is_enabled: 1 as 0 | 1,
    deleted_at: 0,
    remark: "",
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };

  const defs: Array<Omit<SysMenu, "tree_path">> = [
    // Dashboard
    {
      id: 100,
      parent_id: null,
      name: "page.dashboard.title",
      type: "DIR",
      path: "/dashboard",
      component: null,
      icon: "lucide:layout-dashboard",
      permission_code: null,
      sort: -1,
      ...base,
      redirect: "/analytics",
      metadata: JSON.stringify({ routeName: "Dashboard", order: -1 }),
    },
    {
      id: 101,
      parent_id: 100,
      name: "page.dashboard.analytics",
      type: "MENU",
      path: "/analytics",
      component: "/dashboard/analytics/index",
      icon: "lucide:area-chart",
      permission_code: null,
      sort: 1,
      ...base,
      metadata: JSON.stringify({ routeName: "Analytics", affixTab: true, order: 1 }),
    },
    {
      id: 102,
      parent_id: 100,
      name: "page.dashboard.workspace",
      type: "MENU",
      path: "/workspace",
      component: "/dashboard/workspace/index",
      icon: "carbon:workspace",
      permission_code: null,
      sort: 2,
      ...base,
      metadata: JSON.stringify({ routeName: "Workspace", order: 2 }),
    },
    // System
    {
      id: 200,
      parent_id: null,
      name: "system.title",
      type: "DIR",
      path: "/system",
      component: null,
      icon: "lucide:settings",
      permission_code: null,
      sort: 2005,
      ...base,
      redirect: "/system/user",
      metadata: JSON.stringify({ routeName: "System", order: 2005 }),
    },
    {
      id: 201,
      parent_id: 200,
      name: "system.user.title",
      type: "MENU",
      path: "/system/user",
      component: "/system/user/index",
      icon: "lucide:user-cog",
      permission_code: "system:user:list",
      sort: 1,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemUser", order: 1 }),
    },
    {
      id: 2011,
      parent_id: 201,
      name: "新增用户",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:user:create",
      sort: 1,
      ...base,
    },
    {
      id: 2012,
      parent_id: 201,
      name: "编辑用户",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:user:update",
      sort: 2,
      ...base,
    },
    {
      id: 2013,
      parent_id: 201,
      name: "删除用户",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:user:delete",
      sort: 3,
      ...base,
    },
    {
      id: 202,
      parent_id: 200,
      name: "system.role.title",
      type: "MENU",
      path: "/system/role",
      component: "/system/role/index",
      icon: "lucide:shield-user",
      permission_code: "system:role:list",
      sort: 2,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemRole", order: 2 }),
    },
    {
      id: 2021,
      parent_id: 202,
      name: "分配菜单",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:role:menu",
      sort: 1,
      ...base,
    },
    {
      id: 203,
      parent_id: 200,
      name: "system.dict.title",
      type: "MENU",
      path: "/system/dict",
      component: "/system/dict/index",
      icon: "lucide:book-marked",
      permission_code: "system:dict:list",
      sort: 3,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemDict", order: 3 }),
    },
    {
      id: 204,
      parent_id: 200,
      name: "system.i18n.title",
      type: "MENU",
      path: "/system/i18n",
      component: "/system/i18n/index",
      icon: "lucide:languages",
      permission_code: "system:i18n:list",
      sort: 4,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemI18n", order: 4 }),
    },
    {
      id: 205,
      parent_id: 200,
      name: "system.menu.title",
      type: "MENU",
      path: "/system/menu",
      component: "/system/menu/index",
      icon: "lucide:menu",
      permission_code: "system:menu:list",
      sort: 5,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemMenu", order: 5 }),
    },
    {
      id: 2051,
      parent_id: 205,
      name: "新增菜单",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:menu:create",
      sort: 1,
      ...base,
    },
    {
      id: 2052,
      parent_id: 205,
      name: "编辑菜单",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:menu:update",
      sort: 2,
      ...base,
    },
    {
      id: 2053,
      parent_id: 205,
      name: "删除菜单",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:menu:delete",
      sort: 3,
      ...base,
    },
    {
      id: 206,
      parent_id: 200,
      name: "system.api.title",
      type: "MENU",
      path: "/system/api",
      component: "/system/api/index",
      icon: "lucide:terminal",
      permission_code: "system:api:list",
      sort: 6,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemApi", order: 6 }),
    },
    {
      id: 2061,
      parent_id: 206,
      name: "同步接口",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "system:api:sync",
      sort: 1,
      ...base,
    },
    // 访问黑名单 — 对齐 V2 seed id=207
    {
      id: 207,
      parent_id: 200,
      name: "system.blacklist.title",
      type: "MENU",
      path: "/system/blacklist",
      component: "/system/blacklist/index",
      icon: "lucide:shield-ban",
      permission_code: "system:blacklist:list",
      sort: 7,
      ...base,
      metadata: JSON.stringify({ routeName: "SystemBlacklist", order: 7 }),
    },
    // 日志审计 — 单 MENU 进页；BUTTON 发 list 权限码供页内 Tab 显隐
    {
      id: 300,
      parent_id: null,
      name: "log.title",
      type: "MENU",
      path: "/log",
      component: "/log/index",
      icon: "lucide:logs",
      permission_code: null,
      sort: 2004,
      ...base,
      // fullPathKey:false — 页内 ?tab= 切换不产生重复顶栏标签（Vue tabbar）
      metadata: JSON.stringify({
        routeName: "Log",
        order: 2004,
        fullPathKey: false,
      }),
    },
    {
      id: 301,
      parent_id: 300,
      name: "log.loginLog.title",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "log:login-log:list",
      sort: 1,
      ...base,
    },
    {
      id: 302,
      parent_id: 300,
      name: "log.apiLog.title",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "log:api-log:list",
      sort: 2,
      ...base,
    },
    // 任务调度 — 一级菜单；页内 Tab 切换配置/执行记录
    {
      id: 400,
      parent_id: null,
      name: "task.title",
      type: "MENU",
      path: "/task",
      component: "/task/index",
      icon: "lucide:timer",
      permission_code: null,
      sort: 2003,
      ...base,
      metadata: JSON.stringify({
        routeName: "Task",
        order: 2003,
        fullPathKey: false,
      }),
    },
    {
      id: 401,
      parent_id: 400,
      name: "task.config.title",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "task:config:list",
      sort: 1,
      ...base,
    },
    {
      id: 402,
      parent_id: 400,
      name: "task.execution.title",
      type: "BUTTON",
      path: null,
      component: null,
      icon: "",
      permission_code: "task:execution:list",
      sort: 2,
      ...base,
    },
  ];

  for (const d of defs) {
    mockSysMenuList.push({ ...d, tree_path: buildTreePath(d.id, d.parent_id) });
  }
  menuIdSeq = Math.max(...defs.map((d) => d.id), 0);
  return mockSysMenuList.slice();
}

/**
 * 后端路由清单（sys_api 种子 + sync 共用唯一源）。
 * 只收录现有 mock 中真实存在的系统管理接口；不含 /api/admin 伪造路径、
 * 不含 dept / table / demo / timezone 等非本产品系统模块。
 */
export const API_SYNC_MANIFEST = [
  // —— 会话壳 ——
  {
    name: "权限码列表",
    method: "GET",
    path: "/api/auth/codes",
    permissionCode: "auth:codes",
    apiGroup: "会话",
  },
  {
    name: "当前用户信息",
    method: "GET",
    path: "/api/user/info",
    permissionCode: "user:info",
    apiGroup: "会话",
  },
  {
    name: "用户菜单路由",
    method: "GET",
    path: "/api/menu/all",
    permissionCode: "menu:all",
    apiGroup: "会话",
  },
  {
    name: "文件上传",
    method: "POST",
    path: "/api/upload",
    permissionCode: "system:upload",
    apiGroup: "会话",
  },
  // —— 用户管理 ——
  {
    name: "用户分页列表",
    method: "GET",
    path: "/api/system/user/list",
    permissionCode: "system:user:list",
    apiGroup: "用户管理",
  },
  {
    name: "创建用户",
    method: "POST",
    path: "/api/system/user",
    permissionCode: "system:user:create",
    apiGroup: "用户管理",
  },
  {
    name: "更新用户",
    method: "PUT",
    path: "/api/system/user/:id",
    permissionCode: "system:user:update",
    apiGroup: "用户管理",
  },
  {
    name: "删除用户",
    method: "DELETE",
    path: "/api/system/user/:id",
    permissionCode: "system:user:delete",
    apiGroup: "用户管理",
  },
  {
    name: "启停用户",
    method: "PUT",
    path: "/api/system/user/:id/status",
    permissionCode: "system:user:status",
    apiGroup: "用户管理",
  },
  {
    name: "重置用户密码",
    method: "POST",
    path: "/api/system/user/:id/password",
    permissionCode: "system:user:password",
    apiGroup: "用户管理",
  },
  // —— 角色管理 ——
  {
    name: "角色分页列表",
    method: "GET",
    path: "/api/system/role/list",
    permissionCode: "system:role:list",
    apiGroup: "角色管理",
  },
  {
    name: "角色全量列表",
    method: "GET",
    path: "/api/system/role/all",
    permissionCode: "system:role:list",
    apiGroup: "角色管理",
  },
  {
    name: "创建角色",
    method: "POST",
    path: "/api/system/role",
    permissionCode: "system:role:create",
    apiGroup: "角色管理",
  },
  {
    name: "更新角色",
    method: "PUT",
    path: "/api/system/role/:id",
    permissionCode: "system:role:update",
    apiGroup: "角色管理",
  },
  {
    name: "删除角色",
    method: "DELETE",
    path: "/api/system/role/:id",
    permissionCode: "system:role:delete",
    apiGroup: "角色管理",
  },
  {
    name: "角色已绑菜单",
    method: "GET",
    path: "/api/system/role/:id/menus",
    permissionCode: "system:role:menu",
    apiGroup: "角色管理",
  },
  {
    name: "分配角色菜单",
    method: "POST",
    path: "/api/system/role/:id/menus",
    permissionCode: "system:role:menu",
    apiGroup: "角色管理",
  },
  {
    name: "角色已绑接口",
    method: "GET",
    path: "/api/system/role/:id/apis",
    permissionCode: "system:role:api",
    apiGroup: "角色管理",
  },
  {
    name: "分配角色接口",
    method: "POST",
    path: "/api/system/role/:id/apis",
    permissionCode: "system:role:api",
    apiGroup: "角色管理",
  },
  // —— 菜单管理 ——
  {
    name: "菜单分页列表",
    method: "GET",
    path: "/api/system/menu/list",
    permissionCode: "system:menu:list",
    apiGroup: "菜单管理",
  },
  {
    name: "菜单全量列表",
    method: "GET",
    path: "/api/system/menu/all",
    permissionCode: "system:menu:list",
    apiGroup: "菜单管理",
  },
  {
    name: "创建菜单",
    method: "POST",
    path: "/api/system/menu",
    permissionCode: "system:menu:create",
    apiGroup: "菜单管理",
  },
  {
    name: "更新菜单",
    method: "PUT",
    path: "/api/system/menu/:id",
    permissionCode: "system:menu:update",
    apiGroup: "菜单管理",
  },
  {
    name: "删除菜单",
    method: "DELETE",
    path: "/api/system/menu/:id",
    permissionCode: "system:menu:delete",
    apiGroup: "菜单管理",
  },
  {
    name: "批量操作菜单",
    method: "POST",
    path: "/api/system/menu/batch",
    permissionCode: "system:menu:batch",
    apiGroup: "菜单管理",
  },
  {
    name: "菜单名是否存在",
    method: "GET",
    path: "/api/system/menu/name-exists",
    permissionCode: "system:menu:list",
    apiGroup: "菜单管理",
  },
  {
    name: "菜单路径是否存在",
    method: "GET",
    path: "/api/system/menu/path-exists",
    permissionCode: "system:menu:list",
    apiGroup: "菜单管理",
  },
  {
    name: "菜单已绑接口",
    method: "GET",
    path: "/api/system/menu/:id/apis",
    permissionCode: "system:menu:api",
    apiGroup: "菜单管理",
  },
  {
    name: "设置菜单接口",
    method: "POST",
    path: "/api/system/menu/:id/apis",
    permissionCode: "system:menu:api",
    apiGroup: "菜单管理",
  },
  // —— 接口管理 ——
  {
    name: "接口分页列表",
    method: "GET",
    path: "/api/system/api/list",
    permissionCode: "system:api:list",
    apiGroup: "接口管理",
  },
  {
    name: "接口全量列表",
    method: "GET",
    path: "/api/system/api/all",
    permissionCode: "system:api:list",
    apiGroup: "接口管理",
  },
  {
    name: "接口分组列表",
    method: "GET",
    path: "/api/system/api/groups",
    permissionCode: "system:api:list",
    apiGroup: "接口管理",
  },
  {
    name: "创建接口",
    method: "POST",
    path: "/api/system/api",
    permissionCode: "system:api:create",
    apiGroup: "接口管理",
  },
  {
    name: "更新接口",
    method: "PUT",
    path: "/api/system/api/:id",
    permissionCode: "system:api:update",
    apiGroup: "接口管理",
  },
  {
    name: "删除接口",
    method: "DELETE",
    path: "/api/system/api/:id",
    permissionCode: "system:api:delete",
    apiGroup: "接口管理",
  },
  {
    name: "批量操作接口",
    method: "POST",
    path: "/api/system/api/batch",
    permissionCode: "system:api:batch",
    apiGroup: "接口管理",
  },
  {
    name: "同步接口",
    method: "POST",
    path: "/api/system/api/sync",
    permissionCode: "system:api:sync",
    apiGroup: "接口管理",
  },
  // —— 字典管理 ——
  {
    name: "字典类型分页",
    method: "GET",
    path: "/api/system/dict-type/list",
    permissionCode: "system:dict:list",
    apiGroup: "字典管理",
  },
  {
    name: "字典类型全量",
    method: "GET",
    path: "/api/system/dict-type/all",
    permissionCode: "system:dict:list",
    apiGroup: "字典管理",
  },
  {
    name: "字典类型详情",
    method: "GET",
    path: "/api/system/dict-type/:id",
    permissionCode: "system:dict:list",
    apiGroup: "字典管理",
  },
  {
    name: "创建字典类型",
    method: "POST",
    path: "/api/system/dict-type",
    permissionCode: "system:dict:create",
    apiGroup: "字典管理",
  },
  {
    name: "更新字典类型",
    method: "PUT",
    path: "/api/system/dict-type/:id",
    permissionCode: "system:dict:update",
    apiGroup: "字典管理",
  },
  {
    name: "删除字典类型",
    method: "DELETE",
    path: "/api/system/dict-type/:id",
    permissionCode: "system:dict:delete",
    apiGroup: "字典管理",
  },
  {
    name: "批量操作字典类型",
    method: "POST",
    path: "/api/system/dict-type/batch",
    permissionCode: "system:dict:batch",
    apiGroup: "字典管理",
  },
  {
    name: "字典数据分页",
    method: "GET",
    path: "/api/system/dict-data/list",
    permissionCode: "system:dict:data:list",
    apiGroup: "字典管理",
  },
  {
    name: "按类型查字典数据",
    method: "GET",
    path: "/api/system/dict-data/by-type/:code",
    permissionCode: "system:dict:data:list",
    apiGroup: "字典管理",
  },
  {
    name: "创建字典数据",
    method: "POST",
    path: "/api/system/dict-data",
    permissionCode: "system:dict:data:create",
    apiGroup: "字典管理",
  },
  {
    name: "更新字典数据",
    method: "PUT",
    path: "/api/system/dict-data/:id",
    permissionCode: "system:dict:data:update",
    apiGroup: "字典管理",
  },
  {
    name: "删除字典数据",
    method: "DELETE",
    path: "/api/system/dict-data/:id",
    permissionCode: "system:dict:data:delete",
    apiGroup: "字典管理",
  },
  {
    name: "批量操作字典数据",
    method: "POST",
    path: "/api/system/dict-data/batch",
    permissionCode: "system:dict:data:batch",
    apiGroup: "字典管理",
  },
  // —— 国际化 ——
  {
    name: "语言分页列表",
    method: "GET",
    path: "/api/system/i18n-locale/list",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "语言全量列表",
    method: "GET",
    path: "/api/system/i18n-locale/all",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "语言详情",
    method: "GET",
    path: "/api/system/i18n-locale/:id",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "创建语言",
    method: "POST",
    path: "/api/system/i18n-locale",
    permissionCode: "system:i18n:create",
    apiGroup: "国际化",
  },
  {
    name: "更新语言",
    method: "PUT",
    path: "/api/system/i18n-locale/:id",
    permissionCode: "system:i18n:update",
    apiGroup: "国际化",
  },
  {
    name: "删除语言",
    method: "DELETE",
    path: "/api/system/i18n-locale/:id",
    permissionCode: "system:i18n:delete",
    apiGroup: "国际化",
  },
  {
    name: "批量操作语言",
    method: "POST",
    path: "/api/system/i18n-locale/batch",
    permissionCode: "system:i18n:batch",
    apiGroup: "国际化",
  },
  {
    name: "导出语言",
    method: "GET",
    path: "/api/system/i18n-locale/export",
    permissionCode: "system:i18n:export",
    apiGroup: "国际化",
  },
  {
    name: "批量导出语言",
    method: "POST",
    path: "/api/system/i18n-locale/export-batch",
    permissionCode: "system:i18n:export",
    apiGroup: "国际化",
  },
  {
    name: "翻译分页列表",
    method: "GET",
    path: "/api/system/i18n-translation/list",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "按语言查翻译",
    method: "GET",
    path: "/api/system/i18n-translation/by-locale/:code",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "按 key 查翻译",
    method: "GET",
    path: "/api/system/i18n-translation/by-key/:key",
    permissionCode: "system:i18n:list",
    apiGroup: "国际化",
  },
  {
    name: "创建翻译",
    method: "POST",
    path: "/api/system/i18n-translation",
    permissionCode: "system:i18n:create",
    apiGroup: "国际化",
  },
  {
    name: "更新翻译",
    method: "PUT",
    path: "/api/system/i18n-translation/:id",
    permissionCode: "system:i18n:update",
    apiGroup: "国际化",
  },
  {
    name: "删除翻译",
    method: "DELETE",
    path: "/api/system/i18n-translation/:id",
    permissionCode: "system:i18n:delete",
    apiGroup: "国际化",
  },
  {
    name: "批量操作翻译",
    method: "POST",
    path: "/api/system/i18n-translation/batch",
    permissionCode: "system:i18n:batch",
    apiGroup: "国际化",
  },
  {
    name: "按 key 批量 upsert 翻译",
    method: "POST",
    path: "/api/system/i18n-translation/batch-upsert-by-key",
    permissionCode: "system:i18n:update",
    apiGroup: "国际化",
  },
  {
    name: "导入翻译预览",
    method: "POST",
    path: "/api/system/i18n-translation/import-preview",
    permissionCode: "system:i18n:import",
    apiGroup: "国际化",
  },
  {
    name: "批量导入翻译",
    method: "POST",
    path: "/api/system/i18n-translation/import-batch",
    permissionCode: "system:i18n:import",
    apiGroup: "国际化",
  },
  // —— 日志审计 ——
  {
    name: "登录日志分页列表",
    method: "GET",
    path: "/api/system/login-log/list",
    permissionCode: "log:login-log:list",
    apiGroup: "日志审计",
  },
  {
    name: "API 日志分页列表",
    method: "GET",
    path: "/api/system/api-log/list",
    permissionCode: "log:api-log:list",
    apiGroup: "日志审计",
  },
  // —— 任务调度 ——
  {
    name: "任务配置分页",
    method: "GET",
    path: "/api/system/task-config/list",
    permissionCode: "task:config:list",
    apiGroup: "任务调度",
  },
  {
    name: "工作流类型选项",
    method: "GET",
    path: "/api/system/task-config/workflow-types",
    permissionCode: "task:config:list__system_task-config_workflow-types",
    apiGroup: "任务调度",
  },
  {
    name: "任务队列选项",
    method: "GET",
    path: "/api/system/task-config/task-queues",
    permissionCode: "task:config:list__system_task-config_task-queues",
    apiGroup: "任务调度",
  },
  {
    name: "任务配置详情",
    method: "GET",
    path: "/api/system/task-config/:id",
    permissionCode: "task:config:list",
    apiGroup: "任务调度",
  },
  {
    name: "创建任务配置",
    method: "POST",
    path: "/api/system/task-config",
    permissionCode: "task:config:create",
    apiGroup: "任务调度",
  },
  {
    name: "更新任务配置",
    method: "PUT",
    path: "/api/system/task-config/:id",
    permissionCode: "task:config:update",
    apiGroup: "任务调度",
  },
  {
    name: "删除任务配置",
    method: "DELETE",
    path: "/api/system/task-config/:id",
    permissionCode: "task:config:delete",
    apiGroup: "任务调度",
  },
  {
    name: "批量操作任务配置",
    method: "POST",
    path: "/api/system/task-config/batch",
    permissionCode: "task:config:batch",
    apiGroup: "任务调度",
  },
  {
    name: "手动触发任务配置",
    method: "POST",
    path: "/api/system/task-config/:id/trigger",
    permissionCode: "task:config:trigger",
    apiGroup: "任务调度",
  },
  {
    name: "任务执行分页",
    method: "GET",
    path: "/api/system/task-execution/list",
    permissionCode: "task:execution:list",
    apiGroup: "任务调度",
  },
  {
    name: "任务执行详情",
    method: "GET",
    path: "/api/system/task-execution/:id",
    permissionCode: "task:execution:list",
    apiGroup: "任务调度",
  },
  // —— 访问黑名单 ——
  {
    name: "黑名单分页",
    method: "GET",
    path: "/api/system/blacklist/list",
    permissionCode: "system:blacklist:list",
    apiGroup: "访问黑名单",
  },
  {
    name: "黑名单全量",
    method: "GET",
    path: "/api/system/blacklist/all",
    permissionCode: "system:blacklist:list__system_blacklist_all",
    apiGroup: "访问黑名单",
  },
  {
    name: "黑名单详情",
    method: "GET",
    path: "/api/system/blacklist/:id",
    permissionCode: "system:blacklist:list__system_blacklist__id",
    apiGroup: "访问黑名单",
  },
  {
    name: "创建黑名单",
    method: "POST",
    path: "/api/system/blacklist",
    permissionCode: "system:blacklist:create",
    apiGroup: "访问黑名单",
  },
  {
    name: "更新黑名单",
    method: "PUT",
    path: "/api/system/blacklist/:id",
    permissionCode: "system:blacklist:update",
    apiGroup: "访问黑名单",
  },
  {
    name: "删除黑名单",
    method: "DELETE",
    path: "/api/system/blacklist/:id",
    permissionCode: "system:blacklist:delete",
    apiGroup: "访问黑名单",
  },
  {
    name: "批量操作黑名单",
    method: "POST",
    path: "/api/system/blacklist/batch",
    permissionCode: "system:blacklist:batch",
    apiGroup: "访问黑名单",
  },
] as const;

/** 按 method+path 查找种子接口 id（deleted_at=0） */
function findSysApiId(method: string, path: string): number | undefined {
  const m = method.toUpperCase();
  return mockSysApiList.find((a) => a.deleted_at === 0 && a.method === m && a.path === path)?.id;
}

/** 种子：接口列表（与 API_SYNC_MANIFEST / 真实 mock 路由 1:1） */
export function buildSysApiSeeds(): SysApi[] {
  const now = "2025-01-10T08:00:00.000Z";
  let id = 0;
  for (const item of API_SYNC_MANIFEST) {
    id += 1;
    mockSysApiList.push({
      id,
      name: item.name,
      method: item.method.toUpperCase(),
      path: item.path,
      permission_code: item.permissionCode,
      api_group: item.apiGroup,
      remark: "",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    });
  }
  apiIdSeq = id;
  return mockSysApiList.slice();
}

/**
 * 首次访问时把菜单/接口/绑定种子写入共享 list；之后 create/update/delete 改它。
 */
export function ensureMenuApiSeeds(): void {
  if (mockSysMenuList.length === 0) {
    buildSysMenuSeeds();
  }
  if (mockSysApiList.length === 0) {
    buildSysApiSeeds();
  }
  // sys_menu_api：按 method+path 绑定，避免硬编码 api_id
  if (mockSysMenuApiList.length === 0) {
    const now = "2025-01-10T08:00:00.000Z";
    const bind = (menuId: number, method: string, path: string) => {
      const apiId = findSysApiId(method, path);
      if (apiId !== undefined) {
        mockSysMenuApiList.push({
          menu_id: menuId,
          api_id: apiId,
          created_at: now,
          created_by: 0,
        });
      }
    };
    // 用户管理(201)
    bind(201, "GET", "/api/system/user/list");
    // 角色管理(202)
    bind(202, "GET", "/api/system/role/list");
    // 字典管理(203)
    bind(203, "GET", "/api/system/dict-type/list");
    // 国际化(204)
    bind(204, "GET", "/api/system/i18n-locale/list");
    // 菜单管理(205)
    bind(205, "GET", "/api/system/menu/list");
    // 接口管理(206)：列表 + 同步
    bind(206, "GET", "/api/system/api/list");
    bind(206, "POST", "/api/system/api/sync");
    // 访问黑名单(207)
    bind(207, "GET", "/api/system/blacklist/list");
    // 登录日志 list 按钮(301)
    bind(301, "GET", "/api/system/login-log/list");
    // API 日志 list 按钮(302)
    bind(302, "GET", "/api/system/api-log/list");
    // 任务调度：配置 list + 选项 + 执行 list（按钮 401/402）
    bind(401, "GET", "/api/system/task-config/list");
    bind(401, "GET", "/api/system/task-config/workflow-types");
    bind(401, "GET", "/api/system/task-config/task-queues");
    bind(402, "GET", "/api/system/task-execution/list");
  }
}
