/**
 * API 调用日志（api_log / api_log_archive）
 * 字段对齐 schema.sql v5 与 mock camel 输出
 */

export type ApiLogSource = 'archive' | 'hot';

export interface ApiLogListItem {
  id: number;
  method: string;
  module: string;
  path: string;
  statusCode: null | number;
  success: 0 | 1;
  reason: string;
  costTime: number;
  requestId: string;
  sysUserId: null | number;
  username: string;
  requestUri: string;
  requestQuery: string;
  requestBody: string;
  requestHeader: string;
  referer: string;
  response: string;
  beforeChange: string;
  afterChange: string;
  formatChange: string;
  clientId: string;
  clientName: string;
  clientIp: string;
  userAgent: string;
  browserName: string;
  browserVersion: string;
  osName: string;
  osVersion: string;
  location: string;
  createdAt: string;
  /** 仅 source=archive 时有值 */
  archivedAt?: string;
}

export interface ApiLogListQuery {
  page?: number;
  pageSize?: number;
  source?: ApiLogSource;
  method?: string;
  module?: string;
  path?: string;
  success?: 0 | 1;
  statusCode?: number;
  username?: string;
  clientIp?: string;
  requestId?: string;
  createdAtFrom?: string;
  createdAtTo?: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
