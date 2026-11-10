import { eventHandler } from "h3";
import { listTaskQueueOptions } from "~/utils/mock-data";
import { useResponseSuccess } from "~/utils/response";

/**
 * 任务队列下拉选项。
 * GET /api/system/task-config/task-queues
 */
export default eventHandler(() => {
  return useResponseSuccess(listTaskQueueOptions());
});
