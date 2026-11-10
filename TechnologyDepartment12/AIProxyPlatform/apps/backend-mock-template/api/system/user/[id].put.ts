import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureUserSeeds,
  getMockSysRoleList,
  getUserRoleIds,
  setUserRoles,
  updateSysUser,
} from "~/utils/mock-data";
import { pickUserRoleCamelKeys, toUserCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 更新用户（基本信息 + 角色）。password 不在此改（走 /password 端点）。
 * username 不可改（与 schema 唯一约束一致）。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickUserRoleCamelKeys<{
    nickname?: string;
    email?: string;
    phone?: string;
    avatar?: string;
    languageCode?: null | string;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
    roleIds?: number[];
  }>(raw, [
    "nickname",
    "email",
    "phone",
    "avatar",
    "languageCode",
    "isEnabled",
    "remark",
    "roleIds",
  ]);

  // roleIds 校验（若提供）
  if (Array.isArray(body.roleIds)) {
    const roleIds = (body.roleIds as number[])
      .map((n) => Number(n))
      .filter((n) => Number.isFinite(n));
    const validRoleIds = new Set(
      getMockSysRoleList()
        .filter((r) => r.deleted_at === 0)
        .map((r) => r.id),
    );
    for (const rid of roleIds) {
      if (!validRoleIds.has(rid)) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", `role ${rid} not found`);
      }
    }
  }

  const patch: Record<string, unknown> = {};
  if (body.nickname !== undefined) patch.nickname = String(body.nickname).trim();
  if (body.email !== undefined) patch.email = String(body.email).trim();
  if (body.phone !== undefined) patch.phone = String(body.phone).trim();
  if (body.avatar !== undefined) patch.avatar = String(body.avatar).trim();
  if (body.languageCode !== undefined)
    patch.languageCode = body.languageCode ? String(body.languageCode).trim() : null;
  if (body.isEnabled !== undefined) patch.isEnabled = Number(body.isEnabled) ? 1 : 0;
  if (body.remark !== undefined) patch.remark = String(body.remark).trim();

  const row = updateSysUser(id, patch);
  if (!row) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `user ${id} not found`);
  }

  // 更新角色关联（若提供）
  let roleIds = getUserRoleIds(id);
  if (Array.isArray(body.roleIds)) {
    roleIds = (body.roleIds as number[]).map((n) => Number(n)).filter((n) => Number.isFinite(n));
    setUserRoles(id, roleIds);
  }

  const roleById = new Map(getMockSysRoleList().map((r) => [r.id, r]));
  const roleNames = roleIds.map((rid) => roleById.get(rid)?.name ?? "").filter(Boolean);
  return useResponseSuccess({ ...toUserCamelRow(row), roleIds, roleNames });
});
