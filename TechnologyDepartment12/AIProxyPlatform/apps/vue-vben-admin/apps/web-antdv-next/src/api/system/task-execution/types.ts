/**
 * 任务执行记录（temporal_task_execution）
 * 字段对齐 backend-mock-template camelCase 输出；无删除。
 */

export type TaskExecutionStatus =
  | 'CANCELLED'
  | 'COMPLETED'
  | 'CONTINUED_AS_NEW'
  | 'FAILED'
  | 'PENDING'
  | 'RETRYING'
  | 'RUNNING'
  | 'TERMINATED'
  | 'TIMED_OUT';

export interface TaskExecution {
  id: number;
  /** 软外键；配置软删后可悬空 */
  configId: null | number;
  /** list/detail 附加字段；缺失时前端展示 — */
  configName?: null | string;
  workflowId: string;
  runId: string;
  workflowType: string;
  taskQueue: string;
  status: string | TaskExecutionStatus;
  /** 进入等待中的时间 */
  pendingAt: null | string;
  /** 真正运行开始时间；尚未真正运行时为 null */
  startedAt: null | string;
  closedAt: null | string;
  inputSummary: null | Record<string, unknown>;
  resultSummary: null | Record<string, unknown>;
  failureReason: null | string;
  /** 已发生重试次数；首次执行为 0 */
  retryCount: number;
  createdAt: string;
}

export interface TaskExecutionQuery {
  page?: number;
  pageSize?: number;
  configId?: number;
  status?: string | TaskExecutionStatus;
  startedAtFrom?: string;
  startedAtTo?: string;
  /** 工作流类型精确匹配 */
  workflowType?: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
