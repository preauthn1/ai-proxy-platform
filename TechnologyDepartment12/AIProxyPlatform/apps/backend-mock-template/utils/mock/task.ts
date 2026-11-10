/**
 * Temporal 任务调度 — temporal_task_config / temporal_task_execution mock 数据 + 种子。
 *
 * 字段对齐 backend/db/schema.sql §8 / §22；mock-only，不接真实 Temporal。
 * isoNow 由 mock-shared 提供。
 */

import { isoNow } from "./shared";

// ============================================================
// Temporal 任务调度 — temporal_task_config / temporal_task_execution
// 字段对齐 backend/db/schema.sql §8 / §22；mock-only，不接真实 Temporal。
// ============================================================

export type TaskExecutionStatus =
  | "PENDING"
  | "RUNNING"
  | "RETRYING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED"
  | "TERMINATED"
  | "TIMED_OUT"
  | "CONTINUED_AS_NEW";

export interface TemporalTaskConfig {
  id: number;
  code: string;
  name: string;
  workflow_type: string;
  task_queue: string;
  /** NULL = 仅手动触发 */
  cron_expr: string | null;
  /** 重试策略 JSON 对象 */
  retry_policy: Record<string, unknown> | null;
  timeout_seconds: number | null;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

export interface TemporalTaskExecution {
  id: number;
  /** 软外键 → temporal_task_config.id；配置软删后可悬空 */
  config_id: number | null;
  workflow_id: string;
  run_id: string;
  workflow_type: string;
  task_queue: string;
  status: TaskExecutionStatus;
  /** 进入等待中的时间 */
  pending_at: string | null;
  /** 真正运行开始时间；尚未真正运行时为 null */
  started_at: string | null;
  closed_at: string | null;
  input_summary: Record<string, unknown> | null;
  result_summary: Record<string, unknown> | null;
  failure_reason: string | null;
  /** 已发生重试次数；首次执行为 0 */
  retry_count: number;
  created_at: string;
}

const mockTemporalTaskConfigList: TemporalTaskConfig[] = [];
const mockTemporalTaskExecutionList: TemporalTaskExecution[] = [];

/**
 * 允许的 workflowType（下拉 + create/update 门禁）。
 * 含 mock 演示种子值，并兼容 Java 侧 TemporalWorkflowType.ALL。
 */
export const ALLOWED_WORKFLOW_TYPES = [
  "LogCountTickWorkflow",
  "ReportDailyWorkflow",
  "OrderSettlementWorkflow",
  "DataArchiveWorkflow",
  "CacheWarmupWorkflow",
  "SessionCleanupWorkflow",
] as const;

/**
 * 允许的 taskQueue（下拉 + create/update 门禁）。
 * 含 mock 演示种子值，并兼容 Java 侧 TemporalTaskQueue.ALL。
 */
export const ALLOWED_TASK_QUEUES = ["demo", "reports", "finance", "maintenance"] as const;

export type TaskSelectOption = { label: string; value: string };

export function listWorkflowTypeOptions(): TaskSelectOption[] {
  return ALLOWED_WORKFLOW_TYPES.map((v) => ({ label: v, value: v }));
}

export function listTaskQueueOptions(): TaskSelectOption[] {
  return ALLOWED_TASK_QUEUES.map((v) => ({ label: v, value: v }));
}

/** 规范化并校验 workflowType；非法返回 null */
export function requireAllowedWorkflowType(raw: string): string | null {
  const trimmed = String(raw ?? "").trim();
  if (!trimmed) return null;
  const hit = ALLOWED_WORKFLOW_TYPES.find((v) => v.toLowerCase() === trimmed.toLowerCase());
  return hit ?? null;
}

/** 规范化并校验 taskQueue；非法返回 null */
export function requireAllowedTaskQueue(raw: string): string | null {
  const trimmed = String(raw ?? "").trim();
  if (!trimmed) return null;
  const hit = ALLOWED_TASK_QUEUES.find((v) => v.toLowerCase() === trimmed.toLowerCase());
  return hit ?? null;
}

export function getMockTemporalTaskConfigList() {
  return mockTemporalTaskConfigList;
}

export function getMockTemporalTaskExecutionList() {
  return mockTemporalTaskExecutionList;
}

let taskConfigIdSeq = 0;
let taskExecutionIdSeq = 0;

export function nextTaskConfigId(): number {
  taskConfigIdSeq += 1;
  return taskConfigIdSeq;
}

export function nextTaskExecutionId(): number {
  taskExecutionIdSeq += 1;
  return taskExecutionIdSeq;
}

function buildTemporalTaskConfigSeeds(): TemporalTaskConfig[] {
  const base = {
    deleted_at: 0,
    created_by: 0,
    updated_by: 0,
  };
  return [
    {
      id: 1,
      code: "report_daily",
      name: "日报生成",
      workflow_type: "ReportDailyWorkflow",
      task_queue: "reports",
      cron_expr: "0 0 2 * * ?",
      retry_policy: { maxAttempts: 3, initialInterval: "30s", backoff: 2.0 },
      timeout_seconds: 3600,
      remark: "每日凌晨生成运营日报",
      is_enabled: 1,
      created_at: "2025-02-01T11:00:00.000Z",
      updated_at: "2025-02-01T11:00:00.000Z",
      ...base,
    },
    {
      id: 2,
      code: "order_settlement",
      name: "订单结算",
      workflow_type: "OrderSettlementWorkflow",
      task_queue: "finance",
      cron_expr: "0 0 1 * * ?",
      retry_policy: { maxAttempts: 5, initialInterval: "60s", backoff: 2.0 },
      timeout_seconds: 7200,
      remark: "订单日终结算",
      is_enabled: 1,
      created_at: "2025-02-01T11:01:00.000Z",
      updated_at: "2025-02-01T11:01:00.000Z",
      ...base,
    },
    {
      id: 3,
      code: "data_archive",
      name: "数据归档",
      workflow_type: "DataArchiveWorkflow",
      task_queue: "maintenance",
      cron_expr: "0 0 3 * * ?",
      retry_policy: { maxAttempts: 2, initialInterval: "120s", backoff: 1.5 },
      timeout_seconds: 14400,
      remark: "历史数据归档",
      is_enabled: 1,
      created_at: "2025-02-15T12:00:00.000Z",
      updated_at: "2025-02-15T12:00:00.000Z",
      ...base,
    },
    {
      id: 4,
      code: "cache_warmup",
      name: "缓存预热",
      workflow_type: "CacheWarmupWorkflow",
      task_queue: "maintenance",
      cron_expr: null,
      retry_policy: { maxAttempts: 2, initialInterval: "10s", backoff: 1.0 },
      timeout_seconds: 600,
      remark: "仅手动触发",
      is_enabled: 1,
      created_at: "2025-03-01T10:00:00.000Z",
      updated_at: "2025-03-01T10:00:00.000Z",
      ...base,
    },
    {
      id: 5,
      code: "session_cleanup",
      name: "会话清理",
      workflow_type: "SessionCleanupWorkflow",
      task_queue: "maintenance",
      cron_expr: "0 */30 * * * ?",
      retry_policy: { maxAttempts: 1 },
      timeout_seconds: 300,
      remark: "已禁用示例",
      is_enabled: 0,
      created_at: "2025-04-01T10:00:00.000Z",
      updated_at: "2025-04-01T10:00:00.000Z",
      ...base,
    },
  ];
}

function buildTemporalTaskExecutionSeeds(): TemporalTaskExecution[] {
  return [
    {
      id: 1,
      config_id: 1,
      workflow_id: "wf-report-20260620-0200",
      run_id: "run-aaa111",
      workflow_type: "ReportDailyWorkflow",
      task_queue: "reports",
      status: "COMPLETED",
      pending_at: "2026-06-20T02:00:00.000Z",
      started_at: "2026-06-20T02:00:00.000Z",
      closed_at: "2026-06-20T02:08:42.000Z",
      input_summary: { date: "2026-06-20" },
      result_summary: { rows: 1280 },
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-20T02:00:00.000Z",
    },
    {
      id: 2,
      config_id: 2,
      workflow_id: "wf-settle-20260620-0100",
      run_id: "run-bbb222",
      workflow_type: "OrderSettlementWorkflow",
      task_queue: "finance",
      status: "COMPLETED",
      pending_at: "2026-06-20T01:00:00.000Z",
      started_at: "2026-06-20T01:00:00.000Z",
      closed_at: "2026-06-20T01:32:11.000Z",
      input_summary: null,
      result_summary: { settled: 42 },
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-20T01:00:00.000Z",
    },
    {
      id: 3,
      config_id: 3,
      workflow_id: "wf-archive-20260620-0300",
      run_id: "run-ccc333",
      workflow_type: "DataArchiveWorkflow",
      task_queue: "maintenance",
      status: "FAILED",
      pending_at: "2026-06-20T03:00:00.000Z",
      started_at: "2026-06-20T03:00:00.000Z",
      closed_at: "2026-06-20T03:05:21.000Z",
      input_summary: null,
      result_summary: null,
      failure_reason: "connection timeout to archive-db",
      retry_count: 0,
      created_at: "2026-06-20T03:00:00.000Z",
    },
    {
      id: 4,
      config_id: 1,
      workflow_id: "wf-report-20260619-0200",
      run_id: "run-aaa110",
      workflow_type: "ReportDailyWorkflow",
      task_queue: "reports",
      status: "COMPLETED",
      pending_at: "2026-06-19T02:00:00.000Z",
      started_at: "2026-06-19T02:00:00.000Z",
      closed_at: "2026-06-19T02:07:55.000Z",
      input_summary: { date: "2026-06-19" },
      result_summary: { rows: 1199 },
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-19T02:00:00.000Z",
    },
    {
      id: 5,
      config_id: 2,
      workflow_id: "wf-settle-20260619-0100",
      run_id: "run-bbb221",
      workflow_type: "OrderSettlementWorkflow",
      task_queue: "finance",
      status: "COMPLETED",
      pending_at: "2026-06-19T01:00:00.000Z",
      started_at: "2026-06-19T01:00:00.000Z",
      closed_at: "2026-06-19T01:28:43.000Z",
      input_summary: null,
      result_summary: { settled: 38 },
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-19T01:00:00.000Z",
    },
    {
      id: 6,
      config_id: 4,
      workflow_id: "wf-warmup-20260620-0814",
      run_id: "run-ddd444",
      workflow_type: "CacheWarmupWorkflow",
      task_queue: "maintenance",
      status: "RUNNING",
      pending_at: "2026-06-20T08:14:00.000Z",
      started_at: "2026-06-20T08:14:00.000Z",
      closed_at: null,
      input_summary: { keys: ["home", "catalog"] },
      result_summary: null,
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-20T08:14:00.000Z",
    },
    {
      id: 7,
      config_id: 5,
      workflow_id: "wf-cleanup-20260620-0930",
      run_id: "run-eee555",
      workflow_type: "SessionCleanupWorkflow",
      task_queue: "maintenance",
      status: "TIMED_OUT",
      pending_at: "2026-06-20T09:30:00.000Z",
      started_at: "2026-06-20T09:30:00.000Z",
      closed_at: "2026-06-20T09:35:00.000Z",
      input_summary: null,
      result_summary: null,
      failure_reason: "timeout 300s exceeded",
      retry_count: 0,
      created_at: "2026-06-20T09:30:00.000Z",
    },
    {
      id: 8,
      config_id: 1,
      workflow_id: "wf-report-20260618-0200",
      run_id: "run-aaa109",
      workflow_type: "ReportDailyWorkflow",
      task_queue: "reports",
      status: "COMPLETED",
      pending_at: "2026-06-18T02:00:00.000Z",
      started_at: "2026-06-18T02:00:00.000Z",
      closed_at: "2026-06-18T02:08:12.000Z",
      input_summary: { date: "2026-06-18" },
      result_summary: { rows: 1305 },
      failure_reason: null,
      retry_count: 0,
      created_at: "2026-06-18T02:00:00.000Z",
    },
  ];
}

/** 为启用中的配置合成一条 mock 执行记录（手动/批量触发）。 */
export function appendMockTaskExecution(
  config: TemporalTaskConfig,
  opts?: { status?: TaskExecutionStatus; failureReason?: string | null },
): TemporalTaskExecution {
  const now = isoNow();
  const stamp = now.replace(/[-:TZ.]/g, "").slice(0, 14);
  const id = nextTaskExecutionId();
  const status = opts?.status ?? "PENDING";
  const closed = status === "PENDING" || status === "RUNNING" || status === "RETRYING" ? null : now;
  const row: TemporalTaskExecution = {
    id,
    config_id: config.id,
    workflow_id: `wf-${config.code}-${stamp}-${id}`,
    run_id: `run-${stamp}-${id}`,
    workflow_type: config.workflow_type,
    task_queue: config.task_queue,
    status,
    pending_at: now,
    started_at: status === "PENDING" ? null : now,
    closed_at: closed,
    input_summary: { trigger: "manual", configCode: config.code },
    result_summary: status === "COMPLETED" ? { ok: true } : null,
    failure_reason: opts?.failureReason ?? null,
    retry_count: 0,
    created_at: now,
  };
  mockTemporalTaskExecutionList.unshift(row);
  return row;
}

/** 解析配置名：配置软删或缺失时返回 null（前端展示 —） */
export function resolveTaskConfigName(configId: number | null): string | null {
  if (configId == null) return null;
  const found = mockTemporalTaskConfigList.find((c) => c.id === configId);
  if (!found || found.deleted_at !== 0) return null;
  return found.name;
}

let temporalTaskSeedsReady = false;

/** 确保 Temporal 任务配置/执行种子已写入（幂等）。 */
export function ensureTemporalTaskSeeds(): void {
  if (temporalTaskSeedsReady) return;
  temporalTaskSeedsReady = true;
  if (mockTemporalTaskConfigList.length === 0) {
    const configs = buildTemporalTaskConfigSeeds();
    mockTemporalTaskConfigList.push(...configs);
    taskConfigIdSeq = Math.max(...configs.map((c) => c.id), 0);
  }
  if (mockTemporalTaskExecutionList.length === 0) {
    const execs = buildTemporalTaskExecutionSeeds();
    mockTemporalTaskExecutionList.push(...execs);
    taskExecutionIdSeq = Math.max(...execs.map((e) => e.id), 0);
  }
}
