import { defineEventHandler, readBody, setResponseStatus } from "h3";
import { pickBlacklistCamelKeys, toBlacklistCamelRow } from "~/utils/blacklist-camel";
import { createBlacklist } from "~/utils/mock-data";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const ALLOWED = [
  "targetType",
  "targetValue",
  "scope",
  "reason",
  "startsAt",
  "expiresAt",
  "remark",
  "isEnabled",
] as const;

export default defineEventHandler(async (event) => {
  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickBlacklistCamelKeys<{
    targetType?: string;
    targetValue?: string;
    scope?: string;
    reason?: string;
    startsAt?: string | null;
    expiresAt?: string | null;
    remark?: string;
    isEnabled?: number | boolean | null;
  }>(raw, ALLOWED);

  const result = createBlacklist(body);
  if (!result.ok) {
    setResponseStatus(event, result.status);
    return useResponseError(result.msg);
  }
  return useResponseSuccess(toBlacklistCamelRow(result.data));
});
