import { defineEventHandler, getRouterParam, readBody, setResponseStatus } from "h3";
import { pickBlacklistCamelKeys, toBlacklistCamelRow } from "~/utils/blacklist-camel";
import { updateBlacklist } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED = [
  "targetType",
  "targetValue",
  "scope",
  "reason",
  "startsAt",
  "expiresAt",
  "clearExpiresAt",
  "remark",
  "isEnabled",
] as const;

export default defineEventHandler(async (event) => {
  const id = Number(getRouterParam(event, "id"));
  if (!Number.isFinite(id)) {
    setResponseStatus(event, 400);
    return useResponseError("id must be a number");
  }

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickBlacklistCamelKeys<{
    targetType?: string | null;
    targetValue?: string | null;
    scope?: string | null;
    reason?: string | null;
    startsAt?: string | null;
    expiresAt?: string | null;
    clearExpiresAt?: boolean | null;
    remark?: string | null;
    isEnabled?: number | boolean | null;
  }>(raw, ALLOWED);

  const result = updateBlacklist(id, body);
  if (!result.ok) {
    setResponseStatus(event, result.status);
    return useResponseError(result.msg);
  }
  return useResponseSuccess(toBlacklistCamelRow(result.data));
});
