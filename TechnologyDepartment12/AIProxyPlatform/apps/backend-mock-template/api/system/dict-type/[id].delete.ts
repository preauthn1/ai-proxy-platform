import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureDictSeeds, getMockDictDataList, getMockDictTypeList } from "~/utils/mock-data";
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

  const types = getMockDictTypeList();
  const idx = types.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `dict-type ${id} not found`);
  }

  // 若仍有字典项，禁止删除（前端可读 400 + error 字段直接提示）
  const hasEntries = getMockDictDataList().some((d) => d.type_id === id && d.deleted_at === 0);
  if (hasEntries) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "请先清空字典项");
  }

  // 软删：标记 deletedAt，保留行便于将来恢复
  types[idx] = { ...types[idx], deleted_at: Date.now() };
  return useResponseSuccess(toDictCamelRow(types[idx]));
});
