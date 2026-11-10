import type {
  CreateTaskConfigRequest,
  PageResult,
  TaskConfig,
  TaskConfigBatchRequest,
  TaskConfigBatchResult,
  TaskConfigQuery,
  TaskSelectOption,
  TaskTriggerResult,
  UpdateTaskConfigRequest,
} from './types';

import { requestClient } from '#/api/request';

/** 分页列出任务配置 */
export function fetchTaskConfigListApi(params: TaskConfigQuery = {}) {
  return requestClient.get<PageResult<TaskConfig>>('/system/task-config/list', {
    params,
  });
}

/** 工作流类型下拉选项 */
export function fetchTaskWorkflowTypesApi() {
  return requestClient.get<TaskSelectOption[]>(
    '/system/task-config/workflow-types',
  );
}

/** 任务队列下拉选项 */
export function fetchTaskQueuesApi() {
  return requestClient.get<TaskSelectOption[]>(
    '/system/task-config/task-queues',
  );
}

/** 任务配置详情 */
export function getTaskConfigApi(id: number) {
  return requestClient.get<TaskConfig>(`/system/task-config/${id}`);
}

/** 新建任务配置 */
export function createTaskConfigApi(body: CreateTaskConfigRequest) {
  return requestClient.post<TaskConfig>('/system/task-config', body);
}

/** 更新任务配置 */
export function updateTaskConfigApi(req: UpdateTaskConfigRequest) {
  return requestClient.put<TaskConfig>(
    `/system/task-config/${req.id}`,
    req.data,
  );
}

/** 软删任务配置 */
export function deleteTaskConfigApi(id: number) {
  return requestClient.delete<unknown>(`/system/task-config/${id}`);
}

/** 批量操作：enable / disable / delete / trigger */
export function batchTaskConfigApi(body: TaskConfigBatchRequest) {
  return requestClient.post<TaskConfigBatchResult>(
    '/system/task-config/batch',
    body,
  );
}

/** 手动触发（禁用配置 → 400） */
export function triggerTaskConfigApi(id: number) {
  return requestClient.post<TaskTriggerResult>(
    `/system/task-config/${id}/trigger`,
  );
}

export type * from './types';
