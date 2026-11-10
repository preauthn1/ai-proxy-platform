import { defineEventHandler, getQuery } from "h3";
import { ensureMenuApiSeeds, getMockSysMenuList, type SysMenu } from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { pagination, unAuthorizedResponse, useResponseSuccess } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 菜单管理：分页列表（sys_menu）
 *
 * 分页单位是 **最外层菜单**（parent_id 为空），不是单条菜单：
 * - page / pageSize 作用于根节点列表
 * - total 为根节点总数
 * - items 为当前页各根节点下的完整子树（扁平，前端再组树）
 * - itemTotal 为筛选命中范围内的菜单条数（供「共 N 条数据」展示）
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const query = getQuery(event);
  const { page = 1, pageSize = 20, name, type, permissionCode, status } = query;
  const shared = getMockSysMenuList();
  const all = shared.filter((m) => m.deleted_at === 0);
  const byId = new Map(all.map((m) => [m.id, m]));

  /** 节点是否命中筛选（无筛选条件时全部命中） */
  const hasFilter = Boolean(
    name || (type && type !== "全部") || permissionCode || ["0", "1"].includes(status as string),
  );

  function matches(m: SysMenu): boolean {
    if (!hasFilter) return true;
    if (name && !m.name.includes(String(name))) return false;
    if (type && type !== "全部" && m.type !== type) return false;
    if (permissionCode) {
      const q = String(permissionCode).toLowerCase();
      if (!m.permission_code || !m.permission_code.toLowerCase().includes(q)) return false;
    }
    if (["0", "1"].includes(status as string) && m.is_enabled !== Number(status)) {
      return false;
    }
    return true;
  }

  /** 沿 parent 追溯到最外层根 */
  function findRoot(m: SysMenu): SysMenu {
    let cur = m;
    const seen = new Set<number>();
    while (cur.parent_id != null) {
      if (seen.has(cur.id)) break;
      seen.add(cur.id);
      const parent = byId.get(cur.parent_id);
      if (!parent) break;
      cur = parent;
    }
    return cur;
  }

  // 子节点索引：parent_id → children
  const childrenMap = new Map<number | null, SysMenu[]>();
  for (const m of all) {
    const key = m.parent_id;
    const arr = childrenMap.get(key) ?? [];
    arr.push(m);
    childrenMap.set(key, arr);
  }
  for (const arr of childrenMap.values()) {
    arr.sort((a, b) => a.sort - b.sort || a.id - b.id);
  }

  /** 收集根下完整子树（含根，DFS 顺序稳定） */
  function collectSubtree(root: SysMenu): SysMenu[] {
    const out: SysMenu[] = [];
    const walk = (node: SysMenu) => {
      out.push(node);
      const kids = childrenMap.get(node.id) ?? [];
      for (const c of kids) walk(c);
    };
    walk(root);
    return out;
  }

  // 根集合：无筛选 = 全部根；有筛选 = 命中节点所属的最外层根（去重）
  let roots: SysMenu[];
  if (!hasFilter) {
    roots = [...(childrenMap.get(null) ?? [])];
  } else {
    const rootIds = new Set<number>();
    for (const m of all) {
      if (matches(m)) {
        rootIds.add(findRoot(m).id);
      }
    }
    roots = all
      .filter((m) => m.parent_id == null && rootIds.has(m.id))
      .sort((a, b) => a.sort - b.sort || a.id - b.id);
  }

  // itemTotal：筛选命中范围内、各根完整子树的菜单条数
  let itemTotal = 0;
  for (const r of roots) {
    itemTotal += collectSubtree(r).length;
  }

  const pageNo = Number.parseInt(String(page), 10) || 1;
  const size = Number.parseInt(String(pageSize), 10) || 20;
  const pagedRoots = pagination(pageNo, size, roots);

  const pageMenus: SysMenu[] = [];
  for (const r of pagedRoots) {
    pageMenus.push(...collectSubtree(r));
  }

  return useResponseSuccess({
    items: pageMenus.map(toCamelRow),
    /** 最外层根节点总数（分页 total） */
    total: roots.length,
    /** 菜单条数（完整子树合计，供展示） */
    itemTotal,
  });
});
