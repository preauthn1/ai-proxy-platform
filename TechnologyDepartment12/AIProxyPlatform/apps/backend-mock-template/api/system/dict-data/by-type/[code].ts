import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictDataList, getMockDictTypeList } from "~/utils/mock-data";
import { toDictCamelRow } from "~/utils/dict-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureDictSeeds();

  const code = getRouterParam(event, "code");
  if (!code) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code is required");
  }

  const types = getMockDictTypeList().filter((t) => t.deleted_at === 0 && t.code === code);
  if (types.length === 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-type code=${code} not found`);
  }

  const typeIds = new Set(types.map((t) => t.id));

  const items = getMockDictDataList()
    .filter((d) => typeIds.has(d.type_id) && d.deleted_at === 0 && d.is_enabled === 1)
    .sort((a, b) => a.sort - b.sort || a.id - b.id)
    .map(toDictCamelRow);
  return useResponseSuccess(items);
});
