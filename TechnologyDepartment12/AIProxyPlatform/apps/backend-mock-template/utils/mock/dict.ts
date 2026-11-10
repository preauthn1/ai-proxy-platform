/**
 * 字典管理（dict_type / dict_data）mock 数据 + 校验 + 种子。
 *
 * 字段对齐 Open Design 原型 mql4ww2b-schema.sql。
 * 内部 snake 存储，handler 边界转 camel（见 utils/dict-camel.ts）。
 *
 * 共享可变列表（mockDictTypeList / mockDictDataList）是模块级单例，
 * ES module 语义保证全应用唯一实例；首次 list 调用时惰性填充种子。
 */

// ============================================================
// 字典管理（dict_type / dict_data）
// 字段对齐 Open Design 原型 mql4ww2b-schema.sql
// ============================================================

export interface DictType {
  id: number;
  code: string;
  name: string;
  remark: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
}

export interface DictData {
  id: number;
  type_id: number;
  value: string;
  label: string;
  sort: number;
  is_default: 0 | 1;
  /**
   * 归属平台：general = 跨前端通用；react-admin / vue-admin 表示只对对应前端可见。
   * 写入 / 修改时由 mock 校验，非法值 400。
   */
  platform: string;
  /**
   * 预设样式标识：default = 无样式；其余值由前端按标识映射 ant Tag 颜色 / vben Tag color。
   * 写入 / 修改时由 mock 校验，非法值 400。
   */
  tag_type: string;
  is_enabled: 0 | 1;
  deleted_at: number;
  remark: string;
  created_at: string;
  updated_at: string;
  created_by: number;
  updated_by: number;
  /**
   * 关联的字典类型编码，仅在 list 接口里 join 后返回；其他接口不返回该字段。
   */
  typeCode?: string;
}

/**
 * 字典项允许的 platform 取值（与 schema v8 注释 + 前端 VITE_APP_PLATFORM 对齐）。
 * 写入/修改时校验，非法值 400。
 */
export const ALLOWED_DICT_DATA_PLATFORMS = ["general", "react-admin", "vue-admin"] as const;
export type DictDataPlatform = (typeof ALLOWED_DICT_DATA_PLATFORMS)[number];

export function isAllowedDictDataPlatform(v: unknown): v is DictDataPlatform {
  return typeof v === "string" && (ALLOWED_DICT_DATA_PLATFORMS as readonly string[]).includes(v);
}

/**
 * 字典项允许的 tag_type 取值（与 schema v9 注释 + 前端 TAG_TYPE_OPTIONS 对齐）。
 *
 * 与前端的字面量联合类型对齐：前端 `DictData.tagType` 声明为
 * `LiteralUnion<PresetColorType | PresetStatusColorType>`（antd `<Tag color>` 的
 * 官方签名），可取 antd 13 项 preset 色 + 13 项 inverse 色（`*-inverse`）+ 5 项
 * 状态色（default / primary / success / warning / error / processing）。本枚举是
 * 它们的 17 项子集（不含 inverse），写入/修改时校验，非法值 400。
 */
export const ALLOWED_TAG_TYPES = [
  "default",
  "primary",
  "success",
  "warning",
  "error",
  "processing",
  "magenta",
  "red",
  "volcano",
  "orange",
  "gold",
  "lime",
  "green",
  "cyan",
  "blue",
  "geekblue",
  "purple",
] as const;
export type TagType = (typeof ALLOWED_TAG_TYPES)[number];

export function isAllowedTagType(v: unknown): v is TagType {
  return typeof v === "string" && (ALLOWED_TAG_TYPES as readonly string[]).includes(v);
}

/**
 * 共享的可变字典类型列表，给 system/dict-type 的 CRUD handler 使用。
 */
const mockDictTypeList: DictType[] = [];

export function getMockDictTypeList() {
  return mockDictTypeList;
}

/**
 * 共享的可变字典数据列表，给 system/dict-data 的 CRUD handler 使用。
 */
const mockDictDataList: DictData[] = [];

export function getMockDictDataList() {
  return mockDictDataList;
}

/**
 * 生成 mock 自增 ID（与 user 列表隔离，足够 demo 使用）。
 */
export function nextDictId(): number {
  return Date.now() * 1000 + Math.floor(Math.random() * 1000);
}

/**
 * 惰性种子：首次 list 调用时填充。每个 typeId 显式固定，方便 dict-data 关联。
 */
function buildDictTypeSeeds(): DictType[] {
  const now = "2025-01-01T00:00:00.000Z";
  const baseTypes: DictType[] = [
    {
      id: 1,
      code: "sys_user_sex",
      name: "用户性别",
      remark: "用户性别字典",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 2,
      code: "sys_yes_no",
      name: "系统是否",
      remark: "通用 Y/N",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 3,
      code: "sys_menu_type",
      name: "菜单类型",
      remark: "DIR / MENU / BUTTON",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 4,
      code: "sys_notice_type",
      name: "通知类型",
      remark: "通知/公告/提醒",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 5,
      code: "sys_switch_status",
      name: "开关状态",
      remark: "跨模块通用启用/禁用状态",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 6,
      code: "sys_default_status",
      name: "默认状态",
      remark: "是否默认值（用于字典项 / 语言等场景的「默认/否」列）",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
    {
      id: 10,
      code: "sys_platform",
      name: "平台",
      remark: "前端归属平台（general / react-admin / vue-admin）",
      is_enabled: 1,
      deleted_at: 0,
      created_at: now,
      updated_at: now,
      created_by: 0,
      updated_by: 0,
    },
  ];
  return baseTypes;
}

function buildDictDataSeeds(): DictData[] {
  const now = "2025-01-01T00:00:00.000Z";
  const seed = (
    id: number,
    type_id: number,
    value: string,
    label: string,
    sort: number,
    is_default: 0 | 1 = 0,
    platform: DictData["platform"] = "general",
    tag_type: DictData["tag_type"] = "default",
  ): DictData => ({
    id,
    type_id,
    value,
    label,
    sort,
    is_default,
    platform,
    tag_type,
    is_enabled: 1,
    deleted_at: 0,
    remark: "",
    created_at: now,
    updated_at: now,
    created_by: 0,
    updated_by: 0,
  });
  // 字典类型单份 1..5；字典项 1001.. 起
  const entries: DictData[] = [];
  // sys_user_sex (type_id=1)
  entries.push(seed(1001, 1, "0", "男", 0, 1));
  entries.push(seed(1002, 1, "1", "女", 1));
  entries.push(seed(1003, 1, "2", "未知", 2));
  // sys_yes_no (type_id=2)
  entries.push(seed(1011, 2, "Y", "是", 0, 1));
  entries.push(seed(1012, 2, "N", "否", 1));
  // sys_menu_type (type_id=3)
  entries.push(seed(1021, 3, "DIR", "目录", 0));
  entries.push(seed(1022, 3, "MENU", "菜单", 1));
  entries.push(seed(1023, 3, "BUTTON", "按钮", 2));
  // sys_notice_type (type_id=4)
  entries.push(seed(1031, 4, "1", "通知", 0));
  entries.push(seed(1032, 4, "2", "公告", 1));
  entries.push(seed(1033, 4, "3", "提醒", 2));
  // sys_switch_status (type_id=5)
  // sort 单调递增 1..6（对齐 backend/db/schema_data.sql 与 design.md）
  entries.push(seed(1041, 5, "enabled", "启用", 1, 0, "general", ""));
  entries.push(seed(1042, 5, "disabled", "禁用", 2, 1, "general", ""));
  entries.push(seed(1051, 5, "enabled", "启用", 3, 0, "react-admin", "success"));
  entries.push(seed(1052, 5, "disabled", "禁用", 4, 1, "react-admin", "error"));
  entries.push(seed(1061, 5, "enabled", "启用", 5, 0, "vue-admin", "success"));
  entries.push(seed(1062, 5, "disabled", "禁用", 6, 1, "vue-admin", "error"));
  // sys_default_status (type_id=6)
  // value 约定：'default' = 默认（isDefault=1）/ 'not-default' = 否（isDefault=0）
  // 单平台 general；tag_type 与各前端 Tag 颜色对齐
  // （value=default → processing；value=not-default → default）。
  // name 列填 '-' 表示"否"，与现有 i18n 列"否"占位一致。
  entries.push(seed(1071, 6, "default", "默认", 0, 1, "general", "processing"));
  entries.push(seed(1072, 6, "not-default", "-", 1, 0, "general", "default"));
  entries.push(seed(1081, 6, "default", "默认", 2, 1, "react-admin", "processing"));
  entries.push(seed(1082, 6, "not-default", "-", 3, 0, "react-admin", "default"));
  entries.push(seed(1091, 6, "default", "默认", 4, 1, "vue-admin", "processing"));
  entries.push(seed(1092, 6, "not-default", "-", 5, 0, "vue-admin", "default"));
  // sys_platform (type_id=10)
  // 平台字段的字典驱动源：value = platform 字符串，platform 字段 = value（自洽）。
  // tag_type 全部置空，平台 CellTag 不着色（与 design.md「sys_platform 字典契约」一致）。
  entries.push(seed(2001, 10, "general", "通用", 1, 1, "general", ""));
  entries.push(seed(2002, 10, "react-admin", "React Admin", 2, 0, "react-admin", ""));
  entries.push(seed(2003, 10, "vue-admin", "Vue Admin", 3, 0, "vue-admin", ""));
  return entries;
}

/**
 * 首次访问 list 时把种子写入共享 list；之后 create/update/delete 改它，list 不会重置。
 */
export function ensureDictSeeds(): void {
  if (mockDictTypeList.length === 0) {
    mockDictTypeList.push(...buildDictTypeSeeds());
  }
  if (mockDictDataList.length === 0) {
    mockDictDataList.push(...buildDictDataSeeds());
  }
}
