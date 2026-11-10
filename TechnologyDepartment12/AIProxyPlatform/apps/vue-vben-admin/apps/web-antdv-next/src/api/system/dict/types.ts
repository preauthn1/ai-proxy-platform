/**
 * 字典管理 类型定义
 * 字段对齐 backend-mock-template 的 schema；软删 deletedAt: 0=未删
 */

/**
 * 与 antd `_util/type` 内 `LiteralUnion` 等价的最小实现。
 *
 * antdv-next 1.4 的 `_util/type` 没有导出 `LiteralUnion`，因此前端两侧各自内联
 * 一份等价定义，语义与 antd 官方 `T | (U & Record<never, never>)` 一致：
 *  - T 部分给 IDE auto-complete（命中预设字面量时收窄）
 *  - 任意 string 仍可传入，避免丢失向后兼容
 */
export type LiteralUnion<T, U extends string = string> =
  | (Record<never, never> & U)
  | T;

/**
 * 预设样式联合类型：与 antdv-next `<Tag color>` prop 的官方签名一致。
 * 从 antdv-next `dist/_util/colors` 子路径取类型（`./dist/*` 是 antdv-next
 * package.json 唯一允许的子路径 exports 模式）。
 */
export type DictTagType = LiteralUnion<
  | import('antdv-next/dist/_util/colors').PresetColorType
  | import('antdv-next/dist/_util/colors').PresetStatusColorType
>;

export interface DictType {
  id: number;
  code: string;
  name: string;
  remark: string;
  isEnabled: 0 | 1;
  deletedAt: number;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
}

export interface DictData {
  id: number;
  typeId: number;
  value: string;
  label: string;
  sort: number;
  isDefault: 0 | 1;
  /** 归属平台：general / react-admin / vue-admin；与 schema v8 对齐 */
  platform: string;
  /**
   * 预设样式标识：与 antdv-next `<Tag color>` 签名一致
   * （LiteralUnion<PresetColorType | PresetStatusColorType>）。
   * 可选值集合收敛到 13 项 preset 色 + 13 项 inverse + 5 项状态色
   * （default / primary / success / warning / error / processing
   *  / magenta / red / volcano / orange / gold / lime / green
   *  / cyan / blue / geekblue / purple / 各自 -inverse）。
   * 与 backend-mock 的 ALLOWED_TAG_TYPES（17 项无 inverse）完全相容。
   */
  tagType: DictTagType;
  isEnabled: 0 | 1;
  deletedAt: number;
  remark: string;
  createdAt: string;
  updatedAt: string;
  createdBy?: number;
  updatedBy?: number;
  /** 关联的字典类型编码（仅 list 接口返回） */
  typeCode?: string;
}

export interface DictTypeQuery {
  page?: number;
  pageSize?: number;
  /** 字典类型编码；前端多选下拉时传数组（精确匹配任一） */
  code?: string | string[];
  name?: string;
  status?: 0 | 1;
}

export interface DictDataQuery {
  page?: number;
  pageSize?: number;
  typeId?: number;
  /** 字典类型编码；多选下拉时传数组（精确匹配任一） */
  typeCode?: string | string[];
  label?: string;
  value?: string;
  status?: 0 | 1;
  /** 归属平台过滤（精确匹配；缺省由调用方注入 VITE_APP_PLATFORM） */
  platform?: string;
  /** 是否把通用（general）并入过滤结果（仅当 platform !== 'general' 时生效） */
  includeGeneral?: boolean;
}

export interface CreateDictTypeRequest {
  code: string;
  name: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface UpdateDictTypeRequest {
  id: number;
  code?: string;
  name?: string;
  remark?: string;
  isEnabled?: 0 | 1;
}

export interface CreateDictDataRequest {
  typeId: number;
  value: string;
  label: string;
  sort?: number;
  isDefault?: boolean;
  /** 归属平台；缺省 mock 层回退到 'general' */
  platform?: string;
  /** 预设样式标识；缺省 mock 层回退到 'default' */
  tagType?: DictTagType;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface UpdateDictDataRequest {
  id: number;
  value?: string;
  label?: string;
  sort?: number;
  isDefault?: 0 | 1;
  platform?: string;
  tagType?: DictTagType;
  isEnabled?: 0 | 1;
  remark?: string;
}

export interface PageResult<T> {
  items: T[];
  total: number;
}
