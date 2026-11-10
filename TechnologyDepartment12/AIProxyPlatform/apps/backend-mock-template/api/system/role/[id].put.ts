import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { ensureUserSeeds, isValidParentRole, updateSysRole } from "~/utils/mock-data";
import { pickUserRoleCamelKeys, toUserRoleCamelRow } from "~/utils/user-role-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

/** 更新角色：code 不可改；parentId 变更做成环检测。 */
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
    name?: string;
    parentId?: null | number;
    sort?: number;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
  }>(raw, ["name", "parentId", "sort", "isEnabled", "remark"]);

  // parentId 校验
  if (body.parentId !== undefined && body.parentId !== null) {
    const pid = Number(body.parentId);
    if (!Number.isFinite(pid)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "parentId must be a number");
    }
    if (pid !== id && !isValidParentRole(pid)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `parent ${pid} not found`);
    }
  }

  const patch: Record<string, unknown> = {};
  if (body.name !== undefined) {
    const name = String(body.name).trim();
    if (!name) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name cannot be empty");
    }
    patch.name = name;
  }
  if (body.parentId !== undefined) {
    patch.parentId = body.parentId === null ? null : Number(body.parentId);
  }
  if (body.sort !== undefined) patch.sort = Number(body.sort) || 0;
  if (body.isEnabled !== undefined) patch.isEnabled = Number(body.isEnabled) ? 1 : 0;
  if (body.remark !== undefined) patch.remark = String(body.remark);

  const result = updateSysRole(id, patch);
  if (!result.ok) {
    setResponseStatus(event, result.reason.includes("not found") ? 404 : 400);
    return useResponseError("BadRequest", result.reason);
  }
  return useResponseSuccess(toUserRoleCamelRow(result.row));
});
