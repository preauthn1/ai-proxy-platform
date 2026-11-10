import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  ensureTemporalTaskSeeds,
  getMockTemporalTaskExecutionList,
  resolveTaskConfigName,
} from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/** 执行记录详情（含 failureReason）；无删除。 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const found = getMockTemporalTaskExecutionList().find((x) => x.id === id);
  if (!found) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `task-execution ${id} not found`);
  }
  return useResponseSuccess({
    ...toTaskCamelRow(found),
    configName: resolveTaskConfigName(found.config_id),
  });
});
