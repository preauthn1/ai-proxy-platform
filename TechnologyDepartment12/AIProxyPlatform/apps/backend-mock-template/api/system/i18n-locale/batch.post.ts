import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureI18nSeeds,
  getMockI18nLocaleList,
  getMockI18nTranslationList,
  isoNow,
} from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * i18n_locale 批量操作。
 *
 * action:
 *   - "enable"  : 批量启用
 *   - "disable" : 批量禁用
 *   - "delete"  : 批量软删；若仍有翻译或包含默认语言，整个事务回滚并返回 400。
 *
 * body: { action: "enable" | "disable" | "delete", ids: number[] }
 */
export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const body = (await readBody(event)) as {
    action?: "enable" | "disable" | "delete";
    ids?: number[] | string[];
  };

  const action = body?.action;
  const rawIds = Array.isArray(body?.ids) ? body!.ids : [];
  const ids = rawIds.map((v) => Number(v)).filter((v) => Number.isFinite(v) && v > 0);

  if (!action || !["enable", "disable", "delete"].includes(action)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "action must be enable|disable|delete");
  }
  if (ids.length === 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "ids must be a non-empty number[]");
  }

  const list = getMockI18nLocaleList();
  const targets = list.filter((x) => ids.includes(x.id) && x.deleted_at === 0);
  if (targets.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", "no active i18n-locale found for given ids");
  }

  if (action === "delete") {
    // 默认语言禁止批量删除
    const defaultTarget = targets.find((t) => t.is_default === 1);
    if (defaultTarget) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "默认语言禁止删除");
    }
    // 仍有翻译 → 拒绝
    const translations = getMockI18nTranslationList();
    const blocked = targets.find((t) =>
      translations.some((d) => d.locale_id === t.id && d.deleted_at === 0),
    );
    if (blocked) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", `语言 ${blocked.code} 仍有翻译，请先清空`);
    }
    const nowMs = Date.now();
    for (const t of targets) {
      const idx = list.indexOf(t);
      list[idx] = { ...t, deleted_at: nowMs };
    }
    return useResponseSuccess({
      action,
      affected: targets.length,
      ids: targets.map((t) => t.id),
    });
  }

  const next: 0 | 1 = action === "enable" ? 1 : 0;
  const now = isoNow();
  for (const t of targets) {
    const idx = list.indexOf(t);
    list[idx] = { ...t, is_enabled: next, updated_at: now, updated_by: 0 };
  }
  return useResponseSuccess({
    action,
    affected: targets.length,
    ids: targets.map((t) => t.id),
  });
});
