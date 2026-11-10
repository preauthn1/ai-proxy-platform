/**
 * mock-data barrel —— 按域拆分后的统一再导出入口。
 *
 * 原始的 ~4500 行单文件已按领域拆成 utils/mock/ 下的模块（与 utils/*-camel.ts
 * 一一对应）：
 *   mock/shared        跨域纯函数 helper（isoNow / parseUserAgent / hoursAgo …）
 *   mock/auth          认证/会话静态数据（UserInfo / MOCK_USERS / TIME_ZONE_OPTIONS）
 *   mock/menu-route    动态菜单路由（MOCK_MENUS / MOCK_MENU_LIST / getMenuIds）
 *   mock/dict          字典管理（DictType / DictData / seeds）
 *   mock/i18n          国际化（I18nLocale / I18nTranslation / seeds）
 *   mock/menu-api      菜单/接口管理（SysMenu / SysApi / API_SYNC_MANIFEST）
 *   mock/user-role     RBAC（SysUser / SysRole / CRUD / seeds）
 *   mock/login-log      登录日志（SysLoginLog / appendLoginLog）
 *   mock/api-log        API 日志（ApiLog / appendApiLog）
 *   mock/task           Temporal 任务调度（TemporalTaskConfig / Execution）
 *   mock/blacklist      访问黑名单（SysBlacklist / 命中判定 / CRUD）
 *
 * handler 仍从 `~/utils/mock-data` 导入，路径不变；各域模块级单例
 * （mockXxxList）由 ES module 语义保证全应用唯一实例，行为与拆分前一致。
 *
 * 注意：原 getMockUserList / mockUserList（空 any[]，无任何引用）已作为死代码删除。
 */

export * from "./mock/shared";
export * from "./mock/auth";
export * from "./mock/menu-route";
export * from "./mock/dict";
export * from "./mock/i18n";
export * from "./mock/menu-api";
export * from "./mock/user-role";
export * from "./mock/login-log";
export * from "./mock/api-log";
export * from "./mock/task";
export * from "./mock/blacklist";
