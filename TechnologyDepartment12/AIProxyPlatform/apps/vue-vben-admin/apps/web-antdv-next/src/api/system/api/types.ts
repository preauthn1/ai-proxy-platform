/**
 * 接口管理 类型定义
 * 字段对齐 backend-mock-template 的 sys_api；软删 deletedAt: 0=未删
 */

export type HttpMethod =
  | 'DELETE'
  | 'GET'
  | 'HEAD'
  | 'OPTIONS'
  | 'PATCH'
  | 'POST'
  | 'PUT';

export interface SysApi {
  id: number;
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface ApiListQuery {
  page?: number;
  pageSize?: number;
  name?: string;
  path?: string;
  method?: HttpMethod;
  group?: string;
  status?: 0 | 1;
}

export interface CreateApiRequest {
  name: string;
  method: HttpMethod;
  path: string;
  permissionCode: string;
  apiGroup?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateApiRequest {
  id: number;
  data: Partial<CreateApiRequest>;
}

export interface ApiBatchRequest {
  action: 'delete' | 'disable' | 'enable';
  ids: number[];
}

export interface ApiSyncResult {
  added: number;
  skipped: number;
  total: number;
}

export interface PageResult<T> {
  items: T[];
  /** 分页总数：接口管理为「分组数」 */
  total: number;
  /**
   * 接口条数（筛选后）。
   * 仅接口列表等「按组分页」接口会返回；普通分页可不传。
   */
  itemTotal?: number;
}
