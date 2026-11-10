import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { toBlacklistCamelRow } from "~/utils/blacklist-camel";
import { softDeleteBlacklist } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  const id = Number(getRouterParam(event, "id"));
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("id must be a number");
  }

  const result = softDeleteBlacklist(id);
  if (!result.ok) {
    setResponseStatus(event, result.status);
    return useResponseError(result.msg);
  }
  return useResponseSuccess(toBlacklistCamelRow(result.data));
});
