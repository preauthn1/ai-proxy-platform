import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  createSysApi,
  ensureMenuApiSeeds,
  getMockSysApiList,
  isAllowedMethod,
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

  const name = String(body.name ?? "").trim();
  const method = String(body.method ?? "")
    .trim()
    .toUpperCase();
  const path = String(body.path ?? "").trim();
  const permissionCode = String(body.permissionCode ?? "").trim();
  const apiGroup = String(body.apiGroup ?? "").trim();

  if (!name) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name is required");
  }
  if (!isAllowedMethod(method)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "method must be GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD");
  }
  if (!path) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "path is required");
  }
  if (!permissionCode) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "permissionCode is required");
  }

  const list = getMockSysApiList();
  // (method, path) 唯一
  if (list.some((a) => a.deleted_at === 0 && a.method === method && a.path === path)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `${method} ${path} 已存在`);
  }
  // permission_code 唯一
  if (list.some((a) => a.deleted_at === 0 && a.permission_code === permissionCode)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `permissionCode ${permissionCode} 已存在`);
  }

  let isEnabled: 0 | 1 = 1;
  if (body.isEnabled !== undefined) {
    isEnabled = Number(body.isEnabled) ? 1 : 0;
  }

  const row = createSysApi({
    name,
    method,
    path,
    permissionCode,
    apiGroup,
    remark: String(body.remark ?? "").trim(),
    isEnabled,
  });
  return useResponseSuccess(toCamelRow(row));
});
