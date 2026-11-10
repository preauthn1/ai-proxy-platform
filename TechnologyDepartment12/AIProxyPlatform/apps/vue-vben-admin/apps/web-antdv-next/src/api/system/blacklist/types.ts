/**
 * 访问黑名单 类型定义
 * 字段对齐 backend-mock / Java BlacklistVO（对外 camelCase）
 */

export type BlacklistTargetType = 'DEVICE' | 'IP' | 'USER';
export type BlacklistScope = 'ALL' | 'API' | 'LOGIN';

export interface SysBlacklist {
  id: number;
  targetType: BlacklistTargetType;
  targetValue: string;
  scope: BlacklistScope;
  reason: string;
  /** 时间字符串；含边界 */
  startsAt: string;
  /** 时间字符串；null=永不过期 */
  expiresAt: null | string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface BlacklistListQuery {
  page?: number;
  pageSize?: number;
  targetType?: BlacklistTargetType;
  targetValue?: string;
  scope?: BlacklistScope;
  status?: 0 | 1;
}

export interface CreateBlacklistRequest {
  targetType: BlacklistTargetType;
  targetValue: string;
  scope?: BlacklistScope;
  reason?: string;
  startsAt?: null | string;
  expiresAt?: null | string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateBlacklistRequest {
  id: number;
  data: {
    /** 为 true 时将 expiresAt 清空为永久封禁 */
    clearExpiresAt?: boolean | null;
    expiresAt?: null | string;
    isEnabled?: 0 | 1 | null;
    reason?: null | string;
    remark?: null | string;
    scope?: BlacklistScope | null;
    startsAt?: null | string;
    targetType?: BlacklistTargetType | null;
    targetValue?: null | string;
  };
}

export interface BlacklistBatchRequest {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
