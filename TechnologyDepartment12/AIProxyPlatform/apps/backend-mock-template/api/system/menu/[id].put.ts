import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import {
  ensureMenuApiSeeds,
  getMockSysMenuList,
  isAllowedMenuType,
  updateSysMenu,
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

  const exists = getMockSysMenuList().find((m) => m.id === id && m.deleted_at === 0);
  if (!exists) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `menu ${id} not found`);
  }

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

  const patch: Record<string, unknown> = {};

  if (body.parentId !== undefined) {
    let parentId: number | null = null;
    if (body.parentId !== null) {
      parentId = Number(body.parentId);
      if (!Number.isFinite(parentId)) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "parentId must be a number");
      }
      if (parentId === id) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "parentId 不能是自己");
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
      // 防止把节点移到自己的后代下（成环）
      if (parent.tree_path.startsWith(`${exists.tree_path}`)) {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "不能将菜单移到自身后代下");
      }
    }
    patch.parentId = parentId;
  }

  if (body.name !== undefined) {
    const name = String(body.name).trim();
    if (!name) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "name cannot be empty");
    }
    patch.name = name;
  }

  if (body.type !== undefined) {
    if (!isAllowedMenuType(body.type)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "type must be DIR | MENU | BUTTON");
    }
    patch.type = body.type;
  }

  // path / component / icon / redirect / permissionCode 跟随 type
  const effectiveType = (patch.type as string | undefined) ?? exists.type;
  if (body.path !== undefined) {
    patch.path = effectiveType === "MENU" ? String(body.path ?? "").trim() || null : null;
  }
  if (body.component !== undefined) {
    patch.component = effectiveType === "MENU" ? String(body.component ?? "").trim() || null : null;
  }
  if (body.icon !== undefined) {
    patch.icon = effectiveType === "BUTTON" ? "" : String(body.icon ?? "").trim();
  }
  if (body.redirect !== undefined) {
    patch.redirect = String(body.redirect ?? "").trim();
  }
  if (body.permissionCode !== undefined) {
    const pc = body.permissionCode === null ? null : String(body.permissionCode).trim();
    if (effectiveType === "BUTTON" && !pc) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "BUTTON 类型必须填写 permissionCode");
    }
    patch.permissionCode = pc || null;
  }
  if (body.sort !== undefined) {
    patch.sort = Number(body.sort) || 0;
  }
  if (body.isHidden !== undefined) {
    patch.isHidden = Number(body.isHidden) ? 1 : 0;
  }
  if (body.isEnabled !== undefined) {
    patch.isEnabled = Number(body.isEnabled) ? 1 : 0;
  }
  if (body.remark !== undefined) {
    patch.remark = String(body.remark);
  }
  if (body.metadata !== undefined && body.metadata !== null) {
    const md = body.metadata;
    if (typeof md === "string") {
      patch.metadata = md;
    } else {
      try {
        patch.metadata = JSON.stringify(md);
      } catch {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "metadata must be valid JSON");
      }
    }
  } else if (body.metadata === null) {
    patch.metadata = null;
  }

  const row = updateSysMenu(id, patch);
  if (!row) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `menu ${id} not found`);
  }
  return useResponseSuccess(toCamelRow(row));
});
