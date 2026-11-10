/**
 * temporal_task_config / temporal_task_execution 字段 snake ↔ camel。
 * mock 内部 snake 存储，handler 出口转 camel。
 */

const TO_CAMEL: Record<string, string> = {
  workflow_type: "workflowType",
  task_queue: "taskQueue",
  cron_expr: "cronExpr",
  retry_policy: "retryPolicy",
  timeout_seconds: "timeoutSeconds",
  is_enabled: "isEnabled",
  deleted_at: "deletedAt",
  created_at: "createdAt",
  updated_at: "updatedAt",
  created_by: "createdBy",
  updated_by: "updatedBy",
  config_id: "configId",
  workflow_id: "workflowId",
  run_id: "runId",
  pending_at: "pendingAt",
  started_at: "startedAt",
  closed_at: "closedAt",
  input_summary: "inputSummary",
  result_summary: "resultSummary",
  failure_reason: "failureReason",
  retry_count: "retryCount",
};

const TO_SNAKE: Record<string, string> = Object.fromEntries(
  Object.entries(TO_CAMEL).map(([k, v]) => [v, k]),
);

/**
 * 从 raw body 中按 camelCase 字段名抽取允许的字段。
 * 同时接受 camel 与 snake 入参：camel 优先，缺失时回退 snake。
 */
export function pickTaskCamelKeys<T extends object>(
  raw: Record<string, unknown>,
  allowed: readonly string[],
): Partial<T> {
  const out: Record<string, unknown> = {};
  for (const camel of allowed) {
    if (camel in raw) {
      out[camel] = raw[camel];
      continue;
    }
    const snake = TO_SNAKE[camel] ?? camel;
    if (snake in raw) {
      out[camel] = raw[snake];
    }
  }
  return out as Partial<T>;
}

/** 内部 snake 行 → 对外 camelCase；额外字段原样保留。 */
export function toTaskCamelRow<T extends object>(row: T): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    out[TO_CAMEL[k] ?? k] = v;
  }
  return out;
}

/** camel 字段名 → snake 字段名（写入 patch 用） */
export function taskCamelToSnakeKey(camel: string): string {
  return TO_SNAKE[camel] ?? camel;
}
