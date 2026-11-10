import { defineEventHandler, getQuery } from "h3";
import {
  countUsersByRole,
  ensureUserSeeds,
  getMockSysRoleList,
  type SysRole,
} from "~/utils/mock-data";
import { toUserRoleCamelRow } from "~/utils/user-role-camel";
import { usePageResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 角色管理：分页列表（sys_role）。
 * 附 userCount（实时统计）+ parentName（父角色名）；字段对齐 schema。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const query = getQuery(event);
  const { page = 1, pageSize = 20, code, name, status } = query;
  const shared = getMockSysRoleList();

  let filtered: SysRole[] = shared.filter((r) => r.deleted_at === 0);
  if (code) {
    const q = String(code).toLowerCase();
    filtered = filtered.filter((r) => r.code.toLowerCase().includes(q));
  }
  if (name) {
    const q = String(name);
    filtered = filtered.filter((r) => r.name.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((r) => r.is_enabled === Number(status));
  }

  // 按 sort 升序、再 id 升序
  filtered.sort((a, b) => a.sort - b.sort || a.id - b.id);

  const roleById = new Map(getMockSysRoleList().map((r) => [r.id, r]));
  const rows = filtered.map((r) => {
    const parent = r.parent_id ? roleById.get(r.parent_id) : undefined;
    return {
      ...toUserRoleCamelRow(r),
      userCount: countUsersByRole(r.id),
      parentName: parent?.name ?? null,
    };
  });

  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
