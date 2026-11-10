import { eventHandler } from "h3";
import { listWorkflowTypeOptions } from "~/utils/mock-data";
import { useResponseSuccess } from "~/utils/response";

/**
 * 工作流类型下拉选项。
 * GET /api/system/task-config/workflow-types
 */
export default eventHandler(() => {
  return useResponseSuccess(listWorkflowTypeOptions());
});
