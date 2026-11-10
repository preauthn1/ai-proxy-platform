import { defineEventHandler, getQuery } from "h3";
import {
  ensureTemporalTaskSeeds,
  getMockTemporalTaskConfigList,
  type TemporalTaskConfig,
} from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { usePageResponseSuccess } from "~/utils/response";

/**
 * 任务配置分页列表。
 * 筛选：code / name / status(isEnabled 0|1) / workflowType / taskQueue
 * 软删行不出现在 list。
 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const query = getQuery(event);
  const code = (query.code ?? query["code[]"]) as string | string[] | undefined;
  const { page = 1, pageSize = 20, name, status, workflowType, taskQueue } = query;
  const shared = getMockTemporalTaskConfigList();

  let filtered: TemporalTaskConfig[] = shared.filter((x) => x.deleted_at === 0);
  if (Array.isArray(code)) {
    const codes = new Set((code as unknown[]).map((v) => String(v)));
    if (codes.size > 0) {
      filtered = filtered.filter((x) => codes.has(x.code));
    }
  } else if (code) {
    const q = String(code as string).toLowerCase();
    filtered = filtered.filter((x) => x.code.toLowerCase().includes(q));
  }
  if (name) {
    const q = String(name as string);
    filtered = filtered.filter((x) => x.name.includes(q));
  }
  if (["0", "1"].includes(status as string)) {
    filtered = filtered.filter((x) => x.is_enabled === Number(status));
  }
  if (workflowType !== undefined && workflowType !== null && String(workflowType) !== "") {
    const wt = String(workflowType);
    filtered = filtered.filter((x) => x.workflow_type === wt);
  }
  if (taskQueue !== undefined && taskQueue !== null && String(taskQueue) !== "") {
    const tq = String(taskQueue);
    filtered = filtered.filter((x) => x.task_queue === tq);
  }
  filtered.sort((a, b) => a.id - b.id);

  return usePageResponseSuccess(page as string, pageSize as string, filtered.map(toTaskCamelRow));
});
