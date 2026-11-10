/**
 * 任务配置（temporal_task_config）
 * 字段对齐 backend-mock-template camelCase 输出
 */

/** 任务配置下拉选项（workflowType / taskQueue） */
export interface TaskSelectOption {
  label: string;
  value: string;
}

export interface TaskConfig {
  id: number;
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  /** null = 仅手动触发 */
  cronExpr: null | string;
  retryPolicy: null | Record<string, unknown>;
  timeoutSeconds: null | number;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface TaskConfigQuery {
  page?: number;
  pageSize?: number;
  code?: string | string[];
  name?: string;
  /** isEnabled 0|1 */
  status?: 0 | 1;
  /** 工作流类型精确匹配 */
  workflowType?: string;
  /** 任务队列精确匹配 */
  taskQueue?: string;
}

export interface CreateTaskConfigRequest {
  code: string;
  name: string;
  workflowType: string;
  taskQueue: string;
  cronExpr?: null | string;
  retryPolicy?: null | Record<string, unknown> | string;
  timeoutSeconds?: null | number;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateTaskConfigRequest {
  id: number;
  data: Partial<CreateTaskConfigRequest>;
}

export type TaskConfigBatchAction = 'delete' | 'disable' | 'enable' | 'trigger';

export interface TaskConfigBatchRequest {
  action: TaskConfigBatchAction;
  ids: number[];
}

export interface TaskConfigBatchResult {
  action: string;
  affected: number;
  ids: number[];
  executionIds?: number[];
  skippedDisabled?: number[];
}

export interface TaskTriggerResult {
  config: TaskConfig;
  execution: {
    [key: string]: unknown;
    configId: null | number;
    id: number;
    runId: string;
    status: string;
    workflowId: string;
  };
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
