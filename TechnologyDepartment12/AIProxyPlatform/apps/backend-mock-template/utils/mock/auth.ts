/**
 * 认证/会话相关静态 mock 数据。
 *
 * - UserInfo：会话用户信息类型（session-utils / login 共用）。
 * - MOCK_USERS / MOCK_CODES：历史 demo 数据，现已由 RBAC sys_user 种子（仅 root）取代；
 *   保留单条 root 便于对照文档，无 handler 直接依赖。
 * - TIME_ZONE_OPTIONS：时区下拉选项，timezone 接口在用。
 */

export interface UserInfo {
  id: number;
  password: string;
  realName: string;
  roles: string[];
  username: string;
  homePath?: string;
}

export interface TimezoneOption {
  offset: number;
  timezone: string;
}

/**
 * 统一用户数据源（历史兼容）：与 sys_user 种子对齐，仅 root。
 */
export const MOCK_USERS: UserInfo[] = [
  {
    id: 1,
    REDACTED,
    realName: "Root",
    roles: ["root"],
    username: "root",
    homePath: "/analytics",
  },
];

export const MOCK_CODES = [
  {
    codes: ["AC_100100", "AC_100110", "AC_100120", "AC_100010"],
    username: "root",
  },
];

/**
 * 时区选项
 */
export const TIME_ZONE_OPTIONS: TimezoneOption[] = [
  {
    offset: -5,
    timezone: "America/New_York",
  },
  {
    offset: 0,
    timezone: "Europe/London",
  },
  {
    offset: 8,
    timezone: "Asia/Shanghai",
  },
  {
    offset: 9,
    timezone: "Asia/Tokyo",
  },
  {
    offset: 9,
    timezone: "Asia/Seoul",
  },
];
