import { defineEventHandler, getQuery } from "h3";
import { toBlacklistCamelRow } from "~/utils/blacklist-camel";
import { listBlacklist } from "~/utils/mock-data";
import { usePageResponseSuccess } from "~/utils/response";

export default defineEventHandler(async (event) => {
  const query = getQuery(event);
  const { page = 1, pageSize = 20, targetType, targetValue, scope, status } = query;

  const rows = listBlacklist({
    targetType: targetType as string | undefined,
    targetValue: targetValue as string | undefined,
    scope: scope as string | undefined,
    status: status as string | number | undefined,
  });

  return usePageResponseSuccess(page as string, pageSize as string, rows.map(toBlacklistCamelRow));
});
