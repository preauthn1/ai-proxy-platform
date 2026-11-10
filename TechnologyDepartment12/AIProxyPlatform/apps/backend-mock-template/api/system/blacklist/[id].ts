import { defineEventHandler, getRouterParam, setResponseStatus } from "h3";
import { toBlacklistCamelRow } from "~/utils/blacklist-camel";
import { getBlacklistById } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  const id = Number(getRouterParam(event, "id"));
  const result = getBlacklistById(id);
  if (!result.ok) {
    setResponseStatus(event, result.status);
    return useResponseError(result.msg);
  }
  return useResponseSuccess(toBlacklistCamelRow(result.data));
});
