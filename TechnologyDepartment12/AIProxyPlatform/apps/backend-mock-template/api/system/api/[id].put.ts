import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  getMockSysApiList,
  isAllowedMethod,
  updateSysApi,
} from "~/utils/mock-data";
import { pickCamelKeys, toCamelRow } from "~/utils/menu-api-camel";
import { useResponseError, useResponseSuccess, unAuthorizedResponse } from "~/utils/response";
import { verifyAccessToken } from "~/utils/session-utils";

export default defineEventHandler(async (event) => {
  const userinfo = verifyAccessToken(event);
  if (!userinfo) {
    return unAuthorizedResponse(event);
  }
  ensureMenuApiSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const exists = getMockSysApiList().find((a) => a.id === id && a.deleted_at === 0);
  if (!exists) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `api ${id} not found`);
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickCamelKeys<{
    name?: string;
    method?: string;
    path?: string;
    permissionCode?: string;
    apiGroup?: string;
    remark?: string;
    isEnabled?: 0 | 1 | boolean;
  }>(raw, ["name", "method", "path", "permissionCode", "apiGroup", "remark", "isEnabled"]);

  const list = getMockSysApiList();
  const patch: Record<string, unknown> = {};

  if (body.method !== undefined) {
    const method = String(body.method).trim().toUpperCase();
    if (!isAllowedMethod(method)) {
      setResponseStatus(event, 400);
      return useResponseError(
        "BadRequest",
        "method must be GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD",
      );
    }
    patch.method = method;
  }
  if (body.path !== undefined) {
    const path = String(body.path).trim();
    if (!path) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "path cannot be empty");
    }
    patch.path = path;
  }
  if (body.permissionCode !== undefined) {
    const permissionCode = String(body.permissionCode).trim();
    if (!permissionCode) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "permissionCode cannot be empty");
    }
    patch.permissionCode = permissionCode;
  }
  if (body.name !== undefined) {
    patch.name = String(body.name).trim();
  }
  if (body.apiGroup !== undefined) {
    patch.apiGroup = String(body.apiGroup).trim();
  }
  if (body.remark !== undefined) {
    patch.remark = String(body.remark);
  }
  if (body.isEnabled !== undefined) {
    patch.isEnabled = Number(body.isEnabled) ? 1 : 0;
  }

  // (method, path) 唯一校验
  const nextMethod = (patch.method as string | undefined) ?? exists.method;
  const nextPath = (patch.path as string | undefined) ?? exists.path;
  if (
    list.some(
      (a) => a.id !== id && a.deleted_at === 0 && a.method === nextMethod && a.path === nextPath,
    )
  ) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `${nextMethod} ${nextPath} 已存在`);
  }
  // permission_code 唯一校验
  const nextCode = (patch.permissionCode as string | undefined) ?? exists.permission_code;
  if (list.some((a) => a.id !== id && a.deleted_at === 0 && a.permission_code === nextCode)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `permissionCode ${nextCode} 已存在`);
  }

  const row = updateSysApi(id, patch);
  return useResponseSuccess(toCamelRow(row!));
});
