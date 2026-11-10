import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { batchBlacklist } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

/**
 * 黑名单批量操作。
 * body: { action: "enable" | "disable" | "delete", ids: number[] }
 */
export default defineEventHandler(async (event) => {
  const body = (await readBody(event)) as {
    action?: string;
    ids?: number[] | string[];
  };

  const result = batchBlacklist(body?.action, body?.ids);
  if (!result.ok) {
    setResponseStatus(event, result.status);
    return useResponseError(result.msg);
  }
  return useResponseSuccess(result.data);
});
