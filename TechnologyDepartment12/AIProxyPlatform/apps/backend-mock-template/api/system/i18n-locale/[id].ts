import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureI18nSeeds, getMockI18nLocaleList } from "~/utils/mock-data";
import { toI18nCamelRow } from "~/utils/i18n-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureI18nSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const found = getMockI18nLocaleList().find((x) => x.id === id && x.deleted_at === 0);
  if (!found) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `i18n-locale ${id} not found`);
  }
  return useResponseSuccess(toI18nCamelRow(found));
});
