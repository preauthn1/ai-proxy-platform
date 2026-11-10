/**
 * 从 mock 的 API_SYNC_MANIFEST + 固定菜单种子生成 RBAC SQL，
 * 并替换 schema_data.sql 中 Section 3–10（保留 Section 1–2 字典）。
 *
 * 运行: node backend/db/_gen_rbac_seed.mjs
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "../..");
const menuApiPath = path.join(root, "apps/backend-mock-template/utils/mock/menu-api.ts");
const src = fs.readFileSync(menuApiPath, "utf8");

const manifestMatch = src.match(/export const API_SYNC_MANIFEST = \[([\s\S]*?)\] as const;/);
if (!manifestMatch) throw new Error("API_SYNC_MANIFEST not found");
const body = manifestMatch[1];
const re =
  /\{\s*name:\s*"([^"]+)",\s*method:\s*"([^"]+)",\s*path:\s*"([^"]+)",\s*permissionCode:\s*"([^"]+)",\s*apiGroup:\s*"([^"]+)",\s*\}/g;
const apis = [];
let m;
while ((m = re.exec(body))) {
  apis.push({
    name: m[1],
    method: m[2],
    path: m[3],
    permissionCode: m[4],
    apiGroup: m[5],
  });
}
if (apis.length === 0) throw new Error("no apis parsed");

// schema UNIQUE(permission_code, deleted_at) 比 mock 更严：重复 code 做 path 派生消歧
const usedCodes = new Set();
for (const a of apis) {
  let code = a.permissionCode;
  if (!usedCodes.has(code)) {
    usedCodes.add(code);
    a.seedPermissionCode = code;
    continue;
  }
  const slug = a.path.replace(/^\/api\//, "").replace(/[/:]/g, "_");
  let candidate = `${code}__${slug}`;
  let i = 2;
  while (usedCodes.has(candidate)) {
    candidate = `${code}__${slug}_${i++}`;
  }
  a.seedPermissionCode = candidate;
  usedCodes.add(candidate);
}

const menus = [
  {
    id: 100,
    parent_id: null,
    name: "page.dashboard.title",
    type: "DIR",
    path: "/dashboard",
    component: null,
    icon: "lucide:layout-dashboard",
    redirect: "/analytics",
    permission_code: null,
    sort: -1,
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
    redirect: "",
    permission_code: null,
    sort: 1,
    metadata: JSON.stringify({
      routeName: "Analytics",
      affixTab: true,
      order: 1,
    }),
  },
  {
    id: 102,
    parent_id: 100,
    name: "page.dashboard.workspace",
    type: "MENU",
    path: "/workspace",
    component: "/dashboard/workspace/index",
    icon: "carbon:workspace",
    redirect: "",
    permission_code: null,
    sort: 2,
    metadata: JSON.stringify({ routeName: "Workspace", order: 2 }),
  },
  {
    id: 200,
    parent_id: null,
    name: "system.title",
    type: "DIR",
    path: "/system",
    component: null,
    icon: "lucide:settings",
    redirect: "/system/user",
    permission_code: null,
    sort: 2005,
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
    redirect: "",
    permission_code: "system:user:list",
    sort: 1,
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
    redirect: "",
    permission_code: "system:user:create",
    sort: 1,
    metadata: null,
  },
  {
    id: 2012,
    parent_id: 201,
    name: "编辑用户",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "system:user:update",
    sort: 2,
    metadata: null,
  },
  {
    id: 2013,
    parent_id: 201,
    name: "删除用户",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "system:user:delete",
    sort: 3,
    metadata: null,
  },
  {
    id: 202,
    parent_id: 200,
    name: "system.role.title",
    type: "MENU",
    path: "/system/role",
    component: "/system/role/index",
    icon: "lucide:shield-user",
    redirect: "",
    permission_code: "system:role:list",
    sort: 2,
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
    redirect: "",
    permission_code: "system:role:menu",
    sort: 1,
    metadata: null,
  },
  {
    id: 203,
    parent_id: 200,
    name: "system.dict.title",
    type: "MENU",
    path: "/system/dict",
    component: "/system/dict/index",
    icon: "lucide:book-marked",
    redirect: "",
    permission_code: "system:dict:list",
    sort: 3,
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
    redirect: "",
    permission_code: "system:i18n:list",
    sort: 4,
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
    redirect: "",
    permission_code: "system:menu:list",
    sort: 5,
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
    redirect: "",
    permission_code: "system:menu:create",
    sort: 1,
    metadata: null,
  },
  {
    id: 2052,
    parent_id: 205,
    name: "编辑菜单",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "system:menu:update",
    sort: 2,
    metadata: null,
  },
  {
    id: 2053,
    parent_id: 205,
    name: "删除菜单",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "system:menu:delete",
    sort: 3,
    metadata: null,
  },
  {
    id: 206,
    parent_id: 200,
    name: "system.api.title",
    type: "MENU",
    path: "/system/api",
    component: "/system/api/index",
    icon: "lucide:terminal",
    redirect: "",
    permission_code: "system:api:list",
    sort: 6,
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
    redirect: "",
    permission_code: "system:api:sync",
    sort: 1,
    metadata: null,
  },
  {
    id: 300,
    parent_id: null,
    name: "log.title",
    type: "MENU",
    path: "/log",
    component: "/log/index",
    icon: "lucide:logs",
    redirect: "",
    permission_code: null,
    sort: 2004,
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
    redirect: "",
    permission_code: "log:login-log:list",
    sort: 1,
    metadata: null,
  },
  {
    id: 302,
    parent_id: 300,
    name: "log.apiLog.title",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "log:api-log:list",
    sort: 2,
    metadata: null,
  },
  {
    id: 400,
    parent_id: null,
    name: "task.title",
    type: "MENU",
    path: "/task",
    component: "/task/index",
    icon: "lucide:timer",
    redirect: "",
    permission_code: null,
    sort: 2003,
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
    redirect: "",
    permission_code: "task:config:list",
    sort: 1,
    metadata: null,
  },
  {
    id: 402,
    parent_id: 400,
    name: "task.execution.title",
    type: "BUTTON",
    path: null,
    component: null,
    icon: "",
    redirect: "",
    permission_code: "task:execution:list",
    sort: 2,
    metadata: null,
  },
];

const byId = new Map(menus.map((x) => [x.id, x]));
function treePath(id) {
  const node = byId.get(id);
  if (node.parent_id == null) return `/${id}/`;
  return `${treePath(node.parent_id)}${id}/`;
}
for (const menu of menus) menu.tree_path = treePath(menu.id);

function sqlStr(v) {
  if (v === null || v === undefined) return "NULL";
  return `'${String(v).replace(/\\/g, "\\\\").replace(/'/g, "''")}'`;
}

const lines = [];
lines.push("");
lines.push("-- ============================================================");
lines.push("-- Section 3: sys_api（对齐 mock API_SYNC_MANIFEST）");
lines.push("-- 注: schema UNIQUE(permission_code, deleted_at) 比 mock 更严；");
lines.push("--     重复 permissionCode 的后续项用 path 派生后缀消歧（__slug）。");
lines.push("-- ============================================================");
lines.push("");
lines.push(
  "INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)",
);
lines.push("VALUES");
const apiVals = apis.map((a, i) => {
  const id = i + 1;
  a.id = id;
  return `    (${id}, ${sqlStr(a.name)}, ${sqlStr(a.method.toUpperCase())}, ${sqlStr(a.path)}, ${sqlStr(a.seedPermissionCode)}, ${sqlStr(a.apiGroup)}, '', 1, 0, 0, 0)`;
});
lines.push(`${apiVals.join(",\n")};`);
lines.push("");
lines.push(`ALTER TABLE sys_api AUTO_INCREMENT = ${apis.length + 1};`);
lines.push("");

lines.push("-- ============================================================");
lines.push("-- Section 4: sys_menu（对齐 mock buildSysMenuSeeds，固定 id 便于 tree_path / 授权）");
lines.push("-- ============================================================");
lines.push("");
lines.push(
  "INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)",
);
lines.push("VALUES");
const menuVals = menus.map((menu) => {
  return `    (${[
    menu.id,
    menu.parent_id === null ? "NULL" : menu.parent_id,
    sqlStr(menu.name),
    sqlStr(menu.type),
    sqlStr(menu.path),
    sqlStr(menu.component),
    sqlStr(menu.icon),
    sqlStr(menu.redirect),
    sqlStr(menu.permission_code),
    sqlStr(menu.tree_path),
    sqlStr(menu.metadata),
    menu.sort,
    0,
    1,
    0,
    "''",
    0,
    0,
  ].join(", ")})`;
});
lines.push(`${menuVals.join(",\n")};`);
lines.push("");
lines.push("ALTER TABLE sys_menu AUTO_INCREMENT = 1000;");
lines.push("");

lines.push("-- ============================================================");
lines.push("-- Section 5: sys_role（仅 root，对齐 java-admin / mock）");
lines.push("-- ============================================================");
lines.push("");
lines.push(
  "INSERT INTO sys_role (id, code, name, parent_id, sort, remark, is_enabled, deleted_at, created_by, updated_by)",
);
lines.push("VALUES");
lines.push("    (1, 'root', '超级管理员', NULL, 1, '系统内置 Root 角色，不可删除', 1, 0, 0, 0);");
lines.push("");
lines.push("ALTER TABLE sys_role AUTO_INCREMENT = 100;");
lines.push("");

// bcrypt of 123456 (bcryptjs cost 10)
const ROOT_HASH = "$2a$10$mzKVO0J.OxnOhHBO8AgBset0LzVRTLv285BJzaTfxpps1Jx7hrXom";
lines.push("-- ============================================================");
lines.push("-- Section 6: sys_user（仅 root；密码明文 123456，BCrypt）");
lines.push("-- ============================================================");
lines.push("");
lines.push(
  "INSERT INTO sys_user (id, username, password_hash, nickname, email, phone, avatar, language_code, last_login_at, last_login_ip, remark, is_enabled, deleted_at, created_by, updated_by)",
);
lines.push("VALUES");
lines.push(
  `    (1, 'root', ${sqlStr(ROOT_HASH)}, 'Root', 'root@trellis.local', '', '', 'zh-CN', NULL, '', '系统内置超级管理员', 1, 0, 0, 0);`,
);
lines.push("");
lines.push("ALTER TABLE sys_user AUTO_INCREMENT = 100;");
lines.push("");

lines.push("-- ============================================================");
lines.push("-- Section 7: sys_user_role（root 用户 → root 角色）");
lines.push("-- ============================================================");
lines.push("");
lines.push("INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);");
lines.push("");

const dashboard = [100, 101, 102];
const logBranch = [300, 301, 302];
const taskBranch = [400, 401, 402];
const systemFull = [
  200, 201, 2011, 2012, 2013, 202, 2021, 203, 204, 205, 2051, 2052, 2053, 206, 2061,
];
const rootMenus = [...new Set([...dashboard, ...logBranch, ...taskBranch, ...systemFull])];

lines.push("-- ============================================================");
lines.push("-- Section 8: sys_role_menu（root 全量菜单，含日志/任务按钮）");
lines.push("-- ============================================================");
lines.push("");
lines.push("INSERT INTO sys_role_menu (role_id, menu_id) VALUES");
const rm = [];
for (const mid of rootMenus) rm.push(`    (1, ${mid})`);
lines.push(`${rm.join(",\n")};`);
lines.push("");

lines.push("-- ============================================================");
lines.push("-- Section 9: sys_role_api（root 全量接口）");
lines.push("-- ============================================================");
lines.push("");
lines.push("INSERT INTO sys_role_api (role_id, api_id) VALUES");
const ra = [];
for (const a of apis) ra.push(`    (1, ${a.id})`);
lines.push(`${ra.join(",\n")};`);
lines.push("");

const binds = [
  [201, "GET", "/api/system/user/list"],
  [202, "GET", "/api/system/role/list"],
  [203, "GET", "/api/system/dict-type/list"],
  [204, "GET", "/api/system/i18n-locale/list"],
  [205, "GET", "/api/system/menu/list"],
  [206, "GET", "/api/system/api/list"],
  [206, "POST", "/api/system/api/sync"],
  [301, "GET", "/api/system/login-log/list"],
  [302, "GET", "/api/system/api-log/list"],
  [401, "GET", "/api/system/task-config/list"],
  [402, "GET", "/api/system/task-execution/list"],
];
const byKey = new Map(apis.map((a) => [`${a.method.toUpperCase()} ${a.path}`, a.id]));
lines.push("-- ============================================================");
lines.push("-- Section 10: sys_menu_api（菜单-接口快捷绑定）");
lines.push("-- ============================================================");
lines.push("");
lines.push("INSERT INTO sys_menu_api (menu_id, api_id, created_by) VALUES");
const ma = binds.map(([menuId, method, p]) => {
  const id = byKey.get(`${method} ${p}`);
  if (!id) throw new Error(`api not found ${method} ${p}`);
  return `    (${menuId}, ${id}, 0)`;
});
lines.push(`${ma.join(",\n")};`);
lines.push("");

const rbacSql = lines.join("\n");
const schemaDataPath = path.join(__dirname, "schema_data.sql");
const current = fs.readFileSync(schemaDataPath, "utf8");
const marker = "-- ============================================================\n-- Section 3:";
const markerIdx = current.indexOf(marker);
if (markerIdx < 0) {
  throw new Error("schema_data.sql missing Section 3 marker; cannot splice RBAC");
}
const prefix = current.slice(0, markerIdx).replace(/\s+$/, "");
const next =
  prefix +
  "\n" +
  rbacSql +
  "\nSET FOREIGN_KEY_CHECKS = 1;\n\n\n" +
  "-- ============================================================\n" +
  "-- End of schema_data.sql\n" +
  "-- ============================================================\n";
fs.writeFileSync(schemaDataPath, next, "utf8");
console.log(
  JSON.stringify(
    {
      apis: apis.length,
      menus: menus.length,
      role_menu: rm.length,
      role_api: ra.length,
      menu_api: ma.length,
      disambiguated: apis.filter((a) => a.seedPermissionCode !== a.permissionCode).length,
      out: schemaDataPath,
    },
    null,
    2,
  ),
);
