import type { ApiLogListItem, ApiLogListQuery, PageResult } from './types';

import { requestClient } from '#/api/request';

/** 分页列出 API 调用日志（热表 / 归档） */
export function fetchApiLogListApi(params: ApiLogListQuery = {}) {
  return requestClient.get<PageResult<ApiLogListItem>>('/system/api-log/list', {
    params,
  });
}

export type * from './types';
