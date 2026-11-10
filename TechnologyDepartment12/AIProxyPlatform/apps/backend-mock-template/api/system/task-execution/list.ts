import { defineEventHandler, getQuery } from "h3";
import {
  ensureTemporalTaskSeeds,
  getMockTemporalTaskExecutionList,
  resolveTaskConfigName,
  type TemporalTaskExecution,
} from "~/utils/mock-data";
import { toTaskCamelRow } from "~/utils/task-camel";
import { usePageResponseSuccess } from "~/utils/response";

/**
 * 任务执行记录分页列表（无删除接口）。
 * 筛选：configId / status / startedAtFrom / startedAtTo / workflowType
 * 配置软删后仍可 list；configName 缺失时为 null（前端展示 —）。
 */
export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const query = getQuery(event);
  const {
    page = 1,
    pageSize = 20,
    configId,
    status,
    startedAtFrom,
    startedAtTo,
    workflowType,
  } = query;

  let filtered: TemporalTaskExecution[] = [...getMockTemporalTaskExecutionList()];

  if (configId !== undefined && configId !== null && String(configId) !== "") {
    const cid = Number(configId);
    if (Number.isFinite(cid)) {
      filtered = filtered.filter((r) => r.config_id === cid);
    }
  }
  if (status) {
    const s = String(status).toUpperCase();
    filtered = filtered.filter((r) => r.status === s);
  }
  if (workflowType !== undefined && workflowType !== null && String(workflowType) !== "") {
    const wt = String(workflowType);
    filtered = filtered.filter((r) => r.workflow_type === wt);
  }
  if (startedAtFrom) {
    const from = Date.parse(String(startedAtFrom));
    if (!Number.isNaN(from)) {
      // PENDING 无 started_at：按时间筛选时排除
      filtered = filtered.filter((r) => r.started_at != null && Date.parse(r.started_at) >= from);
    }
  }
  if (startedAtTo) {
    const to = Date.parse(String(startedAtTo));
    if (!Number.isNaN(to)) {
      filtered = filtered.filter((r) => r.started_at != null && Date.parse(r.started_at) <= to);
    }
  }

  // 最新优先：按 created_at（PENDING 无 started_at）
  filtered.sort((a, b) => Date.parse(b.created_at) - Date.parse(a.created_at) || b.id - a.id);

  const rows = filtered.map((r) => ({
    ...toTaskCamelRow(r),
    configName: resolveTaskConfigName(r.config_id),
  }));
  return usePageResponseSuccess(page as string, pageSize as string, rows);
});
