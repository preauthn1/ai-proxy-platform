import type { LoginLogListItem, LoginLogListQuery, PageResult } from './types';

import { requestClient } from '#/api/request';

/** 分页列出登录日志（热表 / 归档） */
export function fetchLoginLogListApi(params: LoginLogListQuery = {}) {
  return requestClient.get<PageResult<LoginLogListItem>>(
    '/system/login-log/list',
    { params },
  );
}

export type * from './types';
