import type { SysMenu } from "./mock-data";
import {
  ensureMenuApiSeeds,
  ensureUserSeeds,
  getMockSysMenuList,
  getMockSysUserList,
  getRoleMenuIds,
  getUserRoleIds,
} from "./mock-data";

/** Runtime route tree node shared by React/Vue dynamic menus. */
export interface RuntimeMenuRoute {
  name: string;
  path: string;
  component?: string;
  redirect?: string;
  meta?: Record<string, unknown>;
  children?: RuntimeMenuRoute[];
}

type MenuMetaBag = {
  routeName?: string;
  title?: string;
  icon?: string;
  order?: number;
  authority?: string[];
  affix?: boolean;
  affixTab?: boolean;
  activeMenu?: string;
  activePath?: string;
  keepAlive?: boolean;
  hideInBreadcrumb?: boolean;
  badge?: string;
  badgeType?: string;
  badgeVariants?: string;
  [key: string]: unknown;
};

function parseMetadata(raw: string | null): MenuMetaBag {
  if (!raw) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as MenuMetaBag)
      : {};
  } catch {
    return {};
  }
}

/** /system/user -> SystemUser; dashboard -> Dashboard */
export function routeNameFromPath(path: string | null | undefined, fallback: string): string {
  const cleaned = (path ?? "")
    .replace(/^\//, "")
    .replace(/\/index$/i, "")
    .split("/")
    .filter(Boolean);
  if (cleaned.length === 0) {
    return fallback.replace(/\s+/g, "") || "Menu";
  }
  return cleaned
    .map((seg) => seg.replace(/[^a-zA-Z0-9]+/g, " "))
    .flatMap((seg) => seg.split(" ").filter(Boolean))
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");
}

function normalizeComponent(component: string | null): string | undefined {
  if (!component) return undefined;
  let value = component.trim().replace(/\\/g, "/");
  value = value.replace(/^(\.\/|\.\.\/)+/, "");
  if (!value.startsWith("/")) value = `/${value}`;
  // Drop framework directory prefixes; both ends normalize further.
  value = value.replace(/^\/(src\/)?(views|pages)(\/app)?\//i, "/");
  return value.replace(/\/+$/, "") || undefined;
}

/**
 * Project one SysMenu row into a runtime route node (DIR/MENU only).
 * BUTTON rows are never projected into the route tree.
 */
export function sysMenuToRouteRecord(menu: SysMenu): RuntimeMenuRoute | null {
  if (menu.type === "BUTTON") return null;
  if (menu.deleted_at !== 0 || menu.is_enabled !== 1) return null;

  const metaBag = parseMetadata(menu.metadata);
  const path =
    menu.path?.trim() ||
    (menu.type === "DIR" ? `/${routeNameFromPath(null, menu.name).toLowerCase()}` : "");
  if (!path) return null;

  const name =
    (typeof metaBag.routeName === "string" && metaBag.routeName) ||
    routeNameFromPath(menu.path, menu.name);

  const title = (typeof metaBag.title === "string" && metaBag.title) || menu.name;

  const icon = (typeof metaBag.icon === "string" && metaBag.icon) || menu.icon || undefined;

  // 路由树已由 role_menu RBAC 投影裁剪；不要把 MENU.permission_code 自动写入
  // meta.authority。否则 React 侧栏会用 /auth/codes（仅 BUTTON 码）二次过滤，
  // 把已授权的系统子菜单全部滤掉。仅当 metadata 显式声明 authority 时才透传。
  const authority =
    Array.isArray(metaBag.authority) && metaBag.authority.length > 0
      ? metaBag.authority
      : undefined;

  const meta: Record<string, unknown> = {
    title,
    order: typeof metaBag.order === "number" ? metaBag.order : menu.sort,
  };

  if (icon) meta.icon = icon;
  if (menu.is_hidden === 1) meta.hideInMenu = true;
  if (authority) meta.authority = authority;

  // metadata key aliases → RouteMeta
  const affixTab = metaBag.affixTab ?? metaBag.affix;
  if (typeof affixTab === "boolean") meta.affixTab = affixTab;

  const activePath = metaBag.activePath ?? metaBag.activeMenu;
  if (typeof activePath === "string" && activePath) meta.activePath = activePath;

  if (typeof metaBag.keepAlive === "boolean") meta.keepAlive = metaBag.keepAlive;
  if (typeof metaBag.hideInBreadcrumb === "boolean") {
    meta.hideInBreadcrumb = metaBag.hideInBreadcrumb;
  }
  if (typeof metaBag.badge === "string") meta.badge = metaBag.badge;
  if (typeof metaBag.badgeType === "string") meta.badgeType = metaBag.badgeType;
  if (typeof metaBag.badgeVariants === "string") {
    meta.badgeVariants = metaBag.badgeVariants;
  }

  // Keep unknown metadata keys (except ones we already mapped / internal).
  for (const [key, value] of Object.entries(metaBag)) {
    if (
      [
        "routeName",
        "title",
        "icon",
        "order",
        "authority",
        "affix",
        "affixTab",
        "activeMenu",
        "activePath",
        "keepAlive",
        "hideInBreadcrumb",
        "badge",
        "badgeType",
        "badgeVariants",
      ].includes(key)
    ) {
      continue;
    }
    if (meta[key] === undefined) meta[key] = value;
  }

  const node: RuntimeMenuRoute = {
    name,
    path,
    meta,
  };

  const component = normalizeComponent(menu.component);
  if (component) node.component = component;

  if (menu.redirect) node.redirect = menu.redirect;

  return node;
}

/** Include ancestor menus so granted leaves still form a tree. */
export function expandMenuIdsWithAncestors(
  grantedIds: Iterable<number>,
  menus: SysMenu[],
): Set<number> {
  const byId = new Map(menus.map((m) => [m.id, m]));
  const out = new Set<number>();
  for (const id of grantedIds) {
    let current = byId.get(id);
    while (current) {
      out.add(current.id);
      if (current.parent_id == null) break;
      current = byId.get(current.parent_id);
    }
  }
  return out;
}

/**
 * 自身 + 祖先链均未删除且已启用。
 * 父 DIR 禁用时，子节点不得升为根出现在动态菜单中。
 */
export function isRuntimeMenuEligible(menu: SysMenu, byId: Map<number, SysMenu>): boolean {
  if (menu.deleted_at !== 0 || menu.is_enabled !== 1) return false;
  let parentId = menu.parent_id;
  while (parentId != null) {
    const parent = byId.get(parentId);
    if (!parent || parent.deleted_at !== 0 || parent.is_enabled !== 1) {
      return false;
    }
    parentId = parent.parent_id;
  }
  return true;
}

/**
 * Build nested runtime routes from a flat SysMenu list and a set of allowed ids.
 * BUTTON nodes contribute no route; empty DIR branches are pruned.
 */
export function buildRuntimeMenuTree(
  menus: SysMenu[],
  allowedIds: Set<number>,
): RuntimeMenuRoute[] {
  const menuById = new Map(menus.map((m) => [m.id, m]));
  const usable = menus
    .filter(
      (m) => allowedIds.has(m.id) && m.type !== "BUTTON" && isRuntimeMenuEligible(m, menuById),
    )
    .sort((a, b) => a.sort - b.sort || a.id - b.id);

  type Node = RuntimeMenuRoute & { _id: number; _parentId: number | null };
  const nodes: Node[] = [];
  for (const menu of usable) {
    const route = sysMenuToRouteRecord(menu);
    if (!route) continue;
    nodes.push({ ...route, _id: menu.id, _parentId: menu.parent_id });
  }

  const byId = new Map<number, Node>();
  for (const node of nodes) byId.set(node._id, node);

  const roots: Node[] = [];
  for (const node of nodes) {
    if (node._parentId != null && byId.has(node._parentId)) {
      const parent = byId.get(node._parentId)!;
      parent.children = parent.children ?? [];
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  }

  const strip = (list: Node[]): RuntimeMenuRoute[] =>
    list
      .map((n) => {
        const children = n.children ? strip(n.children as Node[]) : undefined;
        // Drop empty directories (no leaf pages).
        if (n.component == null && (!children || children.length === 0)) {
          return null;
        }
        const { _id: _a, _parentId: _b, children: _c, ...rest } = n;
        const out: RuntimeMenuRoute = { ...rest };
        if (children && children.length > 0) out.children = children;
        // Default redirect for DIR with children when missing.
        if (!out.redirect && children && children.length > 0) {
          out.redirect = children[0]?.path;
        }
        return out;
      })
      .filter((n): n is RuntimeMenuRoute => n !== null);

  return strip(roots);
}

/**
 * 解析 mock RBAC 用的用户 id。
 * hybrid 时 java 的 loginId 可能与 mock 种子不一致，故：
 * 1) 优先按 username 命中 mock 用户；
 * 2) 仅当传入 userId 在 mock 中真实存在时才采用（避免 java id 误绑）。
 */
function resolveUserId(username: string, userId?: number): number | null {
  ensureUserSeeds();
  const byName = getMockSysUserList().find((u) => u.username === username && u.deleted_at === 0);
  if (byName) return byName.id;
  if (typeof userId === "number" && Number.isFinite(userId)) {
    const byId = getMockSysUserList().find((u) => u.id === userId && u.deleted_at === 0);
    if (byId) return byId.id;
  }
  return null;
}

/** Collect menu ids granted via user → roles → role_menu. */
export function getGrantedMenuIdsForUser(username: string, userId?: number): Set<number> {
  ensureMenuApiSeeds();
  ensureUserSeeds();
  const uid = resolveUserId(username, userId);
  if (uid == null) return new Set();

  const roleIds = getUserRoleIds(uid);
  const granted = new Set<number>();
  for (const roleId of roleIds) {
    for (const menuId of getRoleMenuIds(roleId)) {
      granted.add(menuId);
    }
  }
  return granted;
}

/** Runtime menus for GET /menu/all. */
export function getUserRuntimeMenus(username: string, userId?: number): RuntimeMenuRoute[] {
  ensureMenuApiSeeds();
  ensureUserSeeds();
  const menus = getMockSysMenuList();
  const granted = getGrantedMenuIdsForUser(username, userId);
  const allowed = expandMenuIdsWithAncestors(granted, menus);
  return buildRuntimeMenuTree(menus, allowed);
}

/** Access codes for GET /auth/codes — BUTTON permission_code under granted menus. */
export function getUserAccessCodes(username: string, userId?: number): string[] {
  ensureMenuApiSeeds();
  ensureUserSeeds();
  const menus = getMockSysMenuList();
  const menuById = new Map(menus.map((m) => [m.id, m]));
  const granted = getGrantedMenuIdsForUser(username, userId);
  // Codes from explicitly granted BUTTONs, or BUTTONs whose parent MENU is granted.
  // 祖先禁用时不发码，与动态菜单投影规则一致。
  const codes = new Set<string>();
  for (const menu of menus) {
    if (menu.type !== "BUTTON") continue;
    if (!isRuntimeMenuEligible(menu, menuById)) continue;
    if (!menu.permission_code) continue;
    const allowed = granted.has(menu.id) || (menu.parent_id != null && granted.has(menu.parent_id));
    if (allowed) codes.add(menu.permission_code);
  }
  return [...codes].sort();
}
