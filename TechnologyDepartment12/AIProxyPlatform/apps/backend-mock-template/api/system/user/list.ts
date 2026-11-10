import { defineEventHandler, getQuery } from "h3";
import {
  ensureUserSeeds,
  getMockSysRoleList,
  getMockSysUserList,
  getMockSysUserRoleList,
  type SysUser,
} from "~/utils/mock-data";
import { toUserCamelRow } from "~/utils/user-role-camel";
import { usePageResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 用户管理：分页列表（sys_user）。
 * 返回字段对齐 schema：nickname / roleIds / isEnabled 等；roleNames 冗余便于列表展示。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const query = getQuery(event);
  const { page = 1, pageSize = 20, username, nickname, status, roleId } = query;
  const shared = getMockSysUserList();

  let filtered: SysUser[] = shared.filter((u) => u.deleted_at === 0);
  if (username) {
    const q = String(username).toLowerCase();
    filtered = filtered.filter((u) => u.username.toLowerCase().includes(q));
  }
  if (nickname) {
    const q = String(nickname);
    filtered = filtered.filter((u) => u.nickname.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((u) => u.is_enabled === Number(status));
  }
  if (roleId) {
    const rid = Number(roleId);
    const userIds = new Set(
      getMockSysUserRoleList()
        .filter((r) => r.role_id === rid)
        .map((r) => r.user_id),
    );
    filtered = filtered.filter((u) => userIds.has(u.id));
  }

  // 按 id 升序
  filtered.sort((a, b) => a.id - b.id);

  // 组装 camel 行：附 roleIds + roleNames
  const roleById = new Map(getMockSysRoleList().map((r) => [r.id, r]));
  const rows = filtered.map((u) => {
    const roleIds = getMockSysUserRoleList()
      .filter((r) => r.user_id === u.id)
      .map((r) => r.role_id);
    const roleNames = roleIds.map((rid) => roleById.get(rid)?.name ?? "").filter(Boolean);
    return { ...toUserCamelRow(u), roleIds, roleNames };
  });

  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
