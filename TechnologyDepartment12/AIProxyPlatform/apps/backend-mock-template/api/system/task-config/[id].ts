import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { ensureTemporalTaskSeeds, getMockTemporalTaskConfigList } from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const idStr = getRouterParam(event, "id");
  const id = Number(idStr);
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "id must be a number");
  }

  const found = getMockTemporalTaskConfigList().find((x) => x.id === id && x.deleted_at === 0);
  if (!found) {
    setResponseStatus(event, 404);
    return useResponseError("NotFound", `task-config ${id} not found`);
  }
  return useResponseSuccess(toTaskCamelRow(found));
});
