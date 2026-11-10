import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import {
  appendMockTaskExecution,
  ensureTemporalTaskSeeds,
  getMockTemporalTaskConfigList,
} from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 手动触发任务配置。
 * is_enabled === 0 → 400；成功则追加至少一条 execution（mock 合成）。
 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const config = getMockTemporalTaskConfigList().find((x) => x.id === id && x.deleted_at === 0);
  if (!config) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `task-config ${id} not found`);
  }
  if (config.is_enabled === 0) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "disabled task cannot be triggered");
  }

  // 对齐真实后端：触发后先 PENDING，job 实际运行后再 RUNNING
  const execution = appendMockTaskExecution(config, { status: "PENDING" });
  return useResponseSuccess({
    config: toTaskCamelRow(config),
    execution: toTaskCamelRow(execution),
  });
});
