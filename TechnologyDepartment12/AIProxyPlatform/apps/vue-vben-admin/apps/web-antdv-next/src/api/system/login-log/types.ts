/**
 * 登录日志（sys_login_log / sys_login_log_archive）
 * 字段对齐 schema.sql v5 与 mock camel 输出
 */

export type LoginMethod = 'OAUTH' | 'PASSWORD' | 'SMS' | 'SSO';

export type LoginLogSource = 'archive' | 'hot';

export interface LoginLogListItem {
  id: number;
  username: string;
  success: 0 | 1;
  reason: string;
  statusCode: null | number;
  sysUserId: null | number;
  loginMethod: LoginMethod | string;
  loginTime: string;
  loginIp: string;
  loginMac: string;
  clientId: string;
  clientName: string;
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

export interface LoginLogListQuery {
  page?: number;
  pageSize?: number;
  source?: LoginLogSource;
  username?: string;
  success?: 0 | 1;
  loginMethod?: string;
  loginIp?: string;
  loginTimeFrom?: string;
  loginTimeTo?: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
