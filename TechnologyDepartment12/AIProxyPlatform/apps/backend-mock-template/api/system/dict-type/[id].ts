import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictTypeList } from "~/utils/mock-data";
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

  const found = getMockDictTypeList().find((x) => x.id === id && x.deleted_at === 0);
  if (!found) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-type ${id} not found`);
  }
  return useResponseSuccess(toDictCamelRow(found));
});
