import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictDataList } from "~/utils/mock-data";
import { toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const list = getMockDictDataList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-data ${id} not found`);
  }
  list[idx] = { ...list[idx], deleted_at: Date.now() };
  return useResponseSuccess(toDictCamelRow(list[idx]));
});
