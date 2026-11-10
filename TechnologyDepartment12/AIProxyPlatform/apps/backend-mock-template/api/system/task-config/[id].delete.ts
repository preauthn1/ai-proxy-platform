import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureTemporalTaskSeeds, getMockTemporalTaskConfigList } from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 软删任务配置。允许存在 execution（config_id 可悬空）。
 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const list = getMockTemporalTaskConfigList();
  const idx = list.findIndex((x) => x.id === id && x.deleted_at === 0);
  if (idx < 0) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `task-config ${id} not found`);
  }

  list[idx] = { ...list[idx], deleted_at: Date.now() };
  return useResponseSuccess(toTaskCamelRow(list[idx]));
});
