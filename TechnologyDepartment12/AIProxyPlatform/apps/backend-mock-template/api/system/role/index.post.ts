import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  createSysRole,
  ensureUserSeeds,
  isRoleCodeTaken,
  isValidParentRole,
} from "~/utils/mock-data";
import { pickUserRoleCamelKeys, toUserRoleCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 创建角色：code/name/parentId/sort/isEnabled/remark。code 创建后不可改。 */
export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureUserSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickUserRoleCamelKeys<{
    code?: string;
    name?: string;
    parentId?: null | number;
    sort?: number;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
  }>(raw, ["code", "name", "parentId", "sort", "isEnabled", "remark"]);

  const code = String(body.code ?? "").trim();
  if (!code) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code is required");
  }
  if (isRoleCodeTaken(code)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `code ${code} already exists`);
  }

  const name = String(body.name ?? "").trim();
  if (!name) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name is required");
  }

  let parentId: null | number = null;
  if (body.parentId !== undefined && body.parentId !== null) {
    parentId = Number(body.parentId);
    if (!Number.isFinite(parentId)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "parentId must be a number");
    }
    if (!isValidParentRole(parentId)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `parent ${parentId} not found`);
    }
  }

  const row = createSysRole({
    code,
    name,
    parentId,
    sort: Number(body.sort ?? 0) || 0,
    isEnabled: body.isEnabled === undefined ? 1 : Number(body.isEnabled) ? 1 : 0,
    remark: String(body.remark ?? "").trim(),
  });

  return useResponseSuccess(toUserRoleCamelRow(row));
});
