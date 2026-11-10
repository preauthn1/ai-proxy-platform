import { defineEventHandler, readBody, setResponseStatus } from "h3";
import {
  ensureTemporalTaskSeeds,
  getMockTemporalTaskConfigList,
  isoNow,
  nextTaskConfigId,
  requireAllowedTaskQueue,
  requireAllowedWorkflowType,
} from "~/utils/mock-data";
import { pickTaskCamelKeys, toTaskCamelRow } from "~/utils/task-camel";
import { useResponseError, useResponseSuccess } from "~/utils/response";

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

export default defineEventHandler(async (event) => {
  ensureTemporalTaskSeeds();

  const raw = ((await readBody(event)) ?? {}) as Record<string, unknown>;
  const body = pickTaskCamelKeys<{
    code?: string;
    name?: string;
    workflowType?: string;
    taskQueue?: string;
    cronExpr?: string | null;
    retryPolicy?: Record<string, unknown> | null;
    timeoutSeconds?: number | null;
    remark?: string;
    isEnabled?: 0 | 1 | boolean;
  }>(raw, [
    "code",
    "name",
    "workflowType",
    "taskQueue",
    "cronExpr",
    "retryPolicy",
    "timeoutSeconds",
    "remark",
    "isEnabled",
  ]);

  const code = String(body.code ?? "").trim();
  const name = String(body.name ?? "").trim();
  const workflowType = String(body.workflowType ?? "").trim();
  const taskQueue = String(body.taskQueue ?? "").trim();

  if (!code) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code is required");
  }
  if (!CODE_PATTERN.test(code)) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "code must match ^[a-z][a-z0-9_]{0,63}$");
  }
  if (!name) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name is required");
  }
  if (name.length > 128) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "name must be ≤ 128 chars");
  }
  if (!workflowType) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "workflowType is required");
  }
  const normalizedWorkflowType = requireAllowedWorkflowType(workflowType);
  if (!normalizedWorkflowType) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `unknown workflowType: ${workflowType}`);
  }
  if (!taskQueue) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", "taskQueue is required");
  }
  const normalizedTaskQueue = requireAllowedTaskQueue(taskQueue);
  if (!normalizedTaskQueue) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `unknown taskQueue: ${taskQueue}`);
  }

  let cronExpr: string | null = null;
  if (body.cronExpr !== undefined && body.cronExpr !== null && body.cronExpr !== "") {
    const c = String(body.cronExpr).trim();
    if (c.length > 64) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "cronExpr must be ≤ 64 chars");
    }
    cronExpr = c;
  }

  let timeoutSeconds: number | null = null;
  if (body.timeoutSeconds !== undefined && body.timeoutSeconds !== null) {
    const t = Number(body.timeoutSeconds);
    if (!Number.isFinite(t) || t < 0 || !Number.isInteger(t)) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "timeoutSeconds must be a non-negative integer");
    }
    timeoutSeconds = t;
  }

  let isEnabled: 0 | 1 = 1;
  if (body.isEnabled !== undefined) {
    const n = Number(body.isEnabled);
    if (n !== 0 && n !== 1) {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "isEnabled must be 0 or 1");
    }
    isEnabled = n as 0 | 1;
  }

  let retryPolicy: Record<string, unknown> | null = null;
  if (body.retryPolicy !== undefined && body.retryPolicy !== null) {
    if (typeof body.retryPolicy === "string") {
      try {
        const parsed = JSON.parse(body.retryPolicy) as unknown;
        if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
          retryPolicy = parsed as Record<string, unknown>;
        } else {
          setResponseStatus(event, 400);
          return useResponseError("BadRequest", "retryPolicy must be a JSON object");
        }
      } catch {
        setResponseStatus(event, 400);
        return useResponseError("BadRequest", "retryPolicy must be valid JSON");
      }
    } else if (typeof body.retryPolicy === "object" && !Array.isArray(body.retryPolicy)) {
      retryPolicy = body.retryPolicy as Record<string, unknown>;
    } else {
      setResponseStatus(event, 400);
      return useResponseError("BadRequest", "retryPolicy must be a JSON object");
    }
  }

  const list = getMockTemporalTaskConfigList();
  const conflict = list.find((x) => x.deleted_at === 0 && x.code === code);
  if (conflict) {
    setResponseStatus(event, 400);
    return useResponseError("BadRequest", `code ${code} already exists`);
  }

  const now = isoNow();
  const newRow = {
    id: nextTaskConfigId(),
    code,
    name,
    workflow_type: normalizedWorkflowType,
    task_queue: normalizedTaskQueue,
    cron_expr: cronExpr,
    retry_policy: retryPolicy,
    timeout_seconds: timeoutSeconds,
    remark: body.remark != null ? String(body.remark) : "",
    is_enabled: isEnabled,
    deleted_at: 0,
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  };
  list.unshift(newRow);
  return useResponseSuccess(toTaskCamelRow(newRow));
});
