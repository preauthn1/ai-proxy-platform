import type { PageResult, TaskExecution, TaskExecutionQuery } from './types';

import { requestClient } from '#/api/request';

/** 分页列出执行记录（无删除接口） */
export function fetchTaskExecutionListApi(params: TaskExecutionQuery = {}) {
  return requestClient.get<PageResult<TaskExecution>>(
    '/system/task-execution/list',
    { params },
  );
}

/** 执行记录详情（含 failureReason） */
export function getTaskExecutionApi(id: number) {
  return requestClient.get<TaskExecution>(`/system/task-execution/${id}`);
}

export type * from './types';
