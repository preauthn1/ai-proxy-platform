import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  createSysMenu,
  ensureMenuApiSeeds,
  getMockSysMenuList,
  isAllowedMenuType,
  type MenuType,
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
    parentId?: number | null;
    name?: string;
    type?: string;
    path?: string | null;
    component?: string | null;
    icon?: string;
    redirect?: string;
    permissionCode?: string | null;
    metadata?: string | null;
    sort?: number;
    isHidden?: 0 | 1 | boolean;
    isEnabled?: 0 | 1 | boolean;
    remark?: string;
  }>(raw, [
    "parentId",
    "name",
    "type",
    "path",
    "component",
    "icon",
    "redirect",
    "permissionCode",
    "metadata",
    "sort",
    "isHidden",
    "isEnabled",
    "remark",
  ]);

  // name 必填
  const name = String(body.name ?? "").trim();
  if (!name) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name is required");
  }

  // type 必填且合法
  if (!isAllowedMenuType(body.type)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "type must be DIR | MENU | BUTTON");
  }
  const type = body.type as MenuType;

  // BUTTON 必填 permissionCode
  let permissionCode: string | null = null;
  if (body.permissionCode !== undefined && body.permissionCode !== null) {
    permissionCode = String(body.permissionCode).trim();
  }
  if (type === "BUTTON" && !permissionCode) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "BUTTON 类型必须填写 permissionCode");
  }

  // parentId 校验：存在且未软删，且不能是自己；BUTTON 也可挂在 MENU/DIR 下
  let parentId: number | null = null;
  if (body.parentId !== undefined && body.parentId !== null) {
    parentId = Number(body.parentId);
    if (!Number.isFinite(parentId)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "parentId must be a number");
    }
    const parent = getMockSysMenuList().find((m) => m.id === parentId && m.deleted_at === 0);
    if (!parent) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `parent ${parentId} not found`);
    }
    if (parent.type === "BUTTON") {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "BUTTON 类型不能作为父菜单");
    }
  }

  // path：MENU 类型必填；DIR/BUTTON 置 null
  let path: string | null = null;
  if (type === "MENU") {
    const p = String(body.path ?? "").trim();
    if (!p) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "MENU 类型必须填写 path");
    }
    path = p;
  }

  // component：仅 MENU 有效
  const component = type === "MENU" ? String(body.component ?? "").trim() || null : null;

  // icon：BUTTON 无图标
  const icon = type === "BUTTON" ? "" : String(body.icon ?? "").trim();

  const redirect = String(body.redirect ?? "").trim();
  const remark = String(body.remark ?? "").trim();
  const sort = Number(body.sort ?? 0) || 0;

  let isHidden: 0 | 1 = 0;
  if (body.isHidden !== undefined) {
    isHidden = Number(body.isHidden) ? 1 : 0;
  }
  let isEnabled: 0 | 1 = 1;
  if (body.isEnabled !== undefined) {
    isEnabled = Number(body.isEnabled) ? 1 : 0;
  }

  // metadata：字符串 JSON；接受对象则序列化
  let metadata: string | null = null;
  if (body.metadata !== undefined && body.metadata !== null) {
    const md = body.metadata;
    if (typeof md === "string") {
      metadata = md;
    } else {
      try {
        metadata = JSON.stringify(md);
      } catch {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "metadata must be valid JSON");
      }
    }
  }

  const row = createSysMenu({
    parentId,
    name,
    type,
    path,
    component,
    icon,
    redirect,
    permissionCode: permissionCode || null,
    metadata,
    sort,
    isHidden,
    isEnabled,
    remark,
  });
  return useResponseSuccess(toCamelRow(row));
});
