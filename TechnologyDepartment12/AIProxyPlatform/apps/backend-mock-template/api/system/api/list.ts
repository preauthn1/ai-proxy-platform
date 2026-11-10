import { defineEventHandler, getQuery } from "h3";
import { ensureMenuApiSeeds, getMockSysApiList, type SysApi } from "~/utils/mock-data";
import { toCamelRow } from "~/utils/menu-api-camel";
import { pagination, unAuthorizedResponse, useResponseSuccess } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 接口管理：分页列表（sys_api）
 *
 * 分页单位是 **分组（api_group）**，不是单条接口：
 * - page / pageSize 作用于分组列表
 * - total 为分组总数
 * - items 为当前页各组下的全部接口（扁平，前端再组树）
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const query = getQuery(event);
  const { page = 1, pageSize = 20, name, path, method, group, status } = query;
  const shared = getMockSysApiList();

  let filtered: SysApi[] = shared.filter((a) => a.deleted_at === 0);
  if (name) {
    const q = String(name).toLowerCase();
    filtered = filtered.filter((a) => a.name.toLowerCase().includes(q));
  }
  if (path) {
    const q = String(path).toLowerCase();
    filtered = filtered.filter((a) => a.path.toLowerCase().includes(q));
  }
  if (method && method !== "全部") {
    filtered = filtered.filter((a) => a.method === String(method).toUpperCase());
  }
  if (group && group !== "全部") {
    filtered = filtered.filter((a) => a.api_group === String(group));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((a) => a.is_enabled === Number(status));
  }

  // 按分组归桶；空分组名归为「未分组」（与前端 buildApiGroupTree 一致）
  const groupMap = new Map<string, SysApi[]>();
  for (const a of filtered) {
    const g = a.api_group?.trim() || "未分组";
    const arr = groupMap.get(g) ?? [];
    arr.push(a);
    groupMap.set(g, arr);
  }

  const groupNames = [...groupMap.keys()].toSorted((a, b) => a.localeCompare(b, "zh-CN"));
  const pageNo = Number.parseInt(String(page), 10) || 1;
  const size = Number.parseInt(String(pageSize), 10) || 20;
  const pagedGroupNames = pagination(pageNo, size, groupNames);

  const pageApis: SysApi[] = [];
  for (const g of pagedGroupNames) {
    const apis = groupMap.get(g) ?? [];
    apis.sort((a, b) => a.id - b.id);
    pageApis.push(...apis);
  }

  return useResponseSuccess({
    items: pageApis.map(toCamelRow),
    /** 分组总数（分页 total，pageSize 作用于分组） */
    total: groupNames.length,
    /** 筛选后的接口条数（与分页无关，供「共 N 条数据」展示） */
    itemTotal: filtered.length,
  });
});
