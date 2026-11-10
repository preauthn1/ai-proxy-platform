import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  createSysUser,
  ensureUserSeeds,
  getMockSysRoleList,
  isUsernameTaken,
  type SysUser,
} from "~/utils/mock-data";
import { pickUserRoleCamelKeys, toUserCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/**
 * 创建用户（sys_user + sys_user_role）。
 * 字段对齐 schema：username/nickname/email/phone/avatar/languageCode/isEnabled/password/roleIds/remark。
 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickUserRoleCamelKeys<{
    username?: string;
    password?: string;
    nickname?: string;
    email?: string;
    phone?: string;
    avatar?: string;
    languageCode?: null | string;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
    roleIds?: number[];
  }>(raw, [
    "username",
    "password",
    "nickname",
    "email",
    "phone",
    "avatar",
    "languageCode",
    "isEnabled",
    "remark",
    "roleIds",
  ]);

  // username 必填且唯一
  const username = String(body.username ?? "").trim();
  if (!username) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "username is required");
  }
  if (isUsernameTaken(username)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `username ${username} already exists`);
  }

  // password 创建时必填
  const password = String(body.password ?? "").trim();
  if (!password) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "password is required");
  }

  // nickname 必填
  const nickname = String(body.nickname ?? "").trim();
  if (!nickname) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "nickname is required");
  }

  // roleIds 校验：存在且未软删
  const roleIds = Array.isArray(body.roleIds)
    ? (body.roleIds as number[]).map((n) => Number(n)).filter((n) => Number.isFinite(n))
    : [];
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

  const row = createSysUser({
    username,
    password,
    nickname,
    email: String(body.email ?? "").trim(),
    phone: String(body.phone ?? "").trim(),
    avatar: String(body.avatar ?? "").trim(),
    languageCode: body.languageCode ? String(body.languageCode).trim() : null,
    isEnabled: body.isEnabled === undefined ? 1 : Number(body.isEnabled) ? 1 : 0,
    remark: String(body.remark ?? "").trim(),
    roleIds,
  });

  return useResponseSuccess(toUserCamel(row, roleIds));
});

/** 组装用户 camel 行：附 roleIds + roleNames。 */
function toUserCamel(row: SysUser, roleIds: number[]) {
  const roleById = new Map(getMockSysRoleList().map((r) => [r.id, r]));
  const roleNames = roleIds.map((rid) => roleById.get(rid)?.name ?? "").filter(Boolean);
  return { ...toUserCamelRow(row), roleIds, roleNames };
}
