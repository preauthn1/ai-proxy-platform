# 表字段速查 (v11)

> 本文件是 `backend/db/schema.sql` 的**逐表字段速查**。本文件**不**解释为什么这样设计——设计动机见 `db-conventions.md`。
>
> 共 23 张表，按模块分组。对齐 schema 当前态：v5 基线 + `dict_data` v8/v9/v10 + `sys_blacklist` v11。

---

## 1. RBAC 模块

### 1.1 `sys_user` — 用户

| 字段            | 类型            | 必填 | 默认           | 说明                                        |
| --------------- | --------------- | ---- | -------------- | ------------------------------------------- |
| `id`            | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                        |
| `username`      | VARCHAR(64)     | 是   | -              | 登录名                                      |
| `password_hash` | VARCHAR(128)    | 是   | -              | 密码哈希（bcrypt/argon2）                   |
| `nickname`      | VARCHAR(64)     | 是   | `''`           | 展示名                                      |
| `email`         | VARCHAR(128)    | 是   | `''`           | 邮箱                                        |
| `phone`         | VARCHAR(32)     | 是   | `''`           | 手机号                                      |
| `avatar`        | VARCHAR(255)    | 是   | `''`           | 头像 URL                                    |
| `language_code` | VARCHAR(16)     | 否   | NULL           | 用户默认语言（软外键 → `i18n_locale.code`） |
| `last_login_at` | TIMESTAMP       | 否   | NULL           | 最近登录时间                                |
| `last_login_ip` | VARCHAR(45)     | 是   | `''`           | 最近登录 IP                                 |
| `remark`        | VARCHAR(512)    | 是   | `''`           | 管理员备注                                  |
| `is_enabled`    | TINYINT(1)      | 是   | 1              | 启用/禁用                                   |
| `deleted_at`    | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）                          |
| `created_at`    | TIMESTAMP       | 是   | NOW()          |                                             |
| `updated_at`    | TIMESTAMP       | 是   | NOW()          |                                             |
| `created_by`    | BIGINT UNSIGNED | 是   | 0              | 0=系统操作；非0=软引用 `sys_user.id`        |
| `updated_by`    | BIGINT UNSIGNED | 是   | 0              | 0=系统操作；非0=软引用 `sys_user.id`        |

**索引**：`PRIMARY(id)` / `UNIQUE(username, deleted_at)` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无

> v5: 移除 `dept_id`（原为 DEPT 类数据权限锚点；现由 `sys_data_permission` 承担）

---

### 1.2 `sys_role` — 角色（v4+ 支持父子层级）

| 字段         | 类型            | 必填 | 默认           | 说明                     |
| ------------ | --------------- | ---- | -------------- | ------------------------ |
| `id`         | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                     |
| `code`       | VARCHAR(32)     | 是   | -              | 角色编码                 |
| `name`       | VARCHAR(64)     | 是   | -              | 角色名                   |
| `parent_id`  | BIGINT UNSIGNED | 否   | NULL           | 父角色 ID（自引用；v4+） |
| `sort`       | INT             | 是   | 0              | 排序                     |
| `remark`     | VARCHAR(512)    | 是   | `''`           | 管理员备注               |
| `is_enabled` | TINYINT(1)      | 是   | 1              |                          |
| `deleted_at` | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）       |
| `created_at` | TIMESTAMP       | 是   | NOW()          |                          |
| `updated_at` | TIMESTAMP       | 是   | NOW()          |                          |
| `created_by` | BIGINT UNSIGNED | 是   | 0              |                          |
| `updated_by` | BIGINT UNSIGNED | 是   | 0              |                          |

**索引**：`PRIMARY(id)` / `UNIQUE(code, deleted_at)` / `idx_parent_id` / `idx_is_enabled` / `idx_deleted_at`

**外键**：`fk_parent_id` → `sys_role(id)`（自引用，ALTER TABLE 后置；v4+）

---

### 1.3 `sys_user_role` — 用户-角色关联

| 字段         | 类型            | 必填 | 默认  | 说明    |
| ------------ | --------------- | ---- | ----- | ------- |
| `user_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `role_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `created_at` | TIMESTAMP       | 是   | NOW() |         |

**索引**：`PRIMARY(user_id, role_id)` / `idx_role_id`

**外键**：`fk_user_id` → `sys_user(id)` / `fk_role_id` → `sys_role(id)`

---

### 1.4 `sys_role_api` — 角色-API 授权

| 字段         | 类型            | 必填 | 默认  | 说明    |
| ------------ | --------------- | ---- | ----- | ------- |
| `role_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `api_id`     | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `created_at` | TIMESTAMP       | 是   | NOW() |         |

**索引**：`PRIMARY(role_id, api_id)` / `idx_api_id`

**外键**：`fk_role_id` → `sys_role(id)` / `fk_api_id` → `sys_api(id)`

---

### 1.5 `sys_role_menu` — 角色-菜单授权

| 字段         | 类型            | 必填 | 默认  | 说明    |
| ------------ | --------------- | ---- | ----- | ------- |
| `role_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `menu_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK |
| `created_at` | TIMESTAMP       | 是   | NOW() |         |

**索引**：`PRIMARY(role_id, menu_id)` / `idx_menu_id`

**外键**：`fk_role_id` → `sys_role(id)` / `fk_menu_id` → `sys_menu(id)`

---

## 2. API 管理模块

### 2.1 `sys_api` — API/接口

| 字段              | 类型            | 必填 | 默认           | 说明               |
| ----------------- | --------------- | ---- | -------------- | ------------------ |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键               |
| `name`            | VARCHAR(64)     | 是   | -              | 接口名             |
| `method`          | VARCHAR(8)      | 是   | -              | HTTP method        |
| `path`            | VARCHAR(255)    | 是   | -              | 接口路径           |
| `permission_code` | VARCHAR(128)    | 是   | -              | 权限码             |
| `api_group`       | VARCHAR(64)     | 是   | `''`           | 分组               |
| `remark`          | VARCHAR(512)    | 是   | `''`           | 管理员备注         |
| `is_enabled`      | TINYINT(1)      | 是   | 1              | 启用/禁用          |
| `deleted_at`      | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒） |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                    |
| `updated_at`      | TIMESTAMP       | 是   | NOW()          |                    |
| `created_by`      | BIGINT UNSIGNED | 是   | 0              |                    |
| `updated_by`      | BIGINT UNSIGNED | 是   | 0              |                    |

**索引**：`PRIMARY(id)` / `UNIQUE(method, path, deleted_at)` / `UNIQUE(permission_code, deleted_at)` / `idx_api_group` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无

---

## 3. 菜单管理模块

### 3.1 `sys_menu` — 菜单（树形 + 物化路径 + 按钮权限）

| 字段              | 类型            | 必填 | 默认           | 说明                              |
| ----------------- | --------------- | ---- | -------------- | --------------------------------- |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                              |
| `parent_id`       | BIGINT UNSIGNED | 否   | NULL           | 父菜单 ID（自引用）               |
| `name`            | VARCHAR(64)     | 是   | -              | 菜单名                            |
| `type`            | VARCHAR(16)     | 是   | -              | DIR / MENU / BUTTON               |
| `path`            | VARCHAR(255)    | 否   | NULL           | 路由路径（仅 MENU）               |
| `component`       | VARCHAR(255)    | 否   | NULL           | 前端组件路径（仅 MENU）           |
| `icon`            | VARCHAR(64)     | 是   | `''`           | 图标                              |
| `redirect`        | VARCHAR(255)    | 是   | `''`           | 路由重定向（vue-vben-admin 习惯） |
| `permission_code` | VARCHAR(128)    | 否   | NULL           | 权限码（BUTTON 必填）             |
| `tree_path`       | VARCHAR(1024)   | 否   | NULL           | 物化路径（如 `/1/3/7/`；v4+）     |
| `metadata`        | JSON            | 否   | NULL           | 前端扩展字段（v4+）               |
| `sort`            | INT             | 是   | 0              | 排序                              |
| `is_hidden`       | TINYINT(1)      | 是   | 0              | 隐藏                              |
| `is_enabled`      | TINYINT(1)      | 是   | 1              | 启用                              |
| `deleted_at`      | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）                |
| `remark`          | VARCHAR(512)    | 是   | `''`           | 管理员备注                        |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                                   |
| `updated_at`      | TIMESTAMP       | 是   | NOW()          |                                   |
| `created_by`      | BIGINT UNSIGNED | 是   | 0              |                                   |
| `updated_by`      | BIGINT UNSIGNED | 是   | 0              |                                   |

**索引**：`PRIMARY(id)` / `idx_parent_id` / `idx_tree_path` / `idx_permission_code` / `idx_type` / `idx_is_enabled` / `idx_deleted_at`

**外键**：`fk_parent_id` → `sys_menu(id)`（自引用，ALTER TABLE 后置）

---

### 3.2 `sys_menu_api` — 菜单-API 快捷绑定

| 字段         | 类型            | 必填 | 默认  | 说明                   |
| ------------ | --------------- | ---- | ----- | ---------------------- |
| `menu_id`    | BIGINT UNSIGNED | 是   | -     | 复合 PK                |
| `api_id`     | BIGINT UNSIGNED | 是   | -     | 复合 PK                |
| `created_at` | TIMESTAMP       | 是   | NOW() |                        |
| `created_by` | BIGINT UNSIGNED | 是   | 0     | 0=系统操作；非0=创建人 |

**索引**：`PRIMARY(menu_id, api_id)` / `idx_api_id`

**外键**：`fk_menu_id` → `sys_menu(id)` / `fk_api_id` → `sys_api(id)`

---

## 4. I18n 模块

### 4.1 `i18n_locale` — 语言/区域

| 字段         | 类型            | 必填 | 默认           | 说明                       |
| ------------ | --------------- | ---- | -------------- | -------------------------- |
| `id`         | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                       |
| `code`       | VARCHAR(16)     | 是   | -              | 语言代码（zh-CN / en-US）  |
| `name`       | VARCHAR(64)     | 是   | -              | 展示名                     |
| `is_default` | TINYINT(1)      | 是   | 0              | 是否默认（应用层保证唯一） |
| `sort`       | INT             | 是   | 0              | 排序                       |
| `remark`     | VARCHAR(512)    | 是   | `''`           |                            |
| `is_enabled` | TINYINT(1)      | 是   | 1              |                            |
| `deleted_at` | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）         |
| `created_at` | TIMESTAMP       | 是   | NOW()          |                            |
| `updated_at` | TIMESTAMP       | 是   | NOW()          |                            |
| `created_by` | BIGINT UNSIGNED | 是   | 0              |                            |
| `updated_by` | BIGINT UNSIGNED | 是   | 0              |                            |

**索引**：`PRIMARY(id)` / `UNIQUE(code, deleted_at)` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无

---

### 4.2 `i18n_translation` — 翻译（UI 字符串）

| 字段              | 类型            | 必填 | 默认           | 说明               |
| ----------------- | --------------- | ---- | -------------- | ------------------ |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键               |
| `locale_id`       | BIGINT UNSIGNED | 是   | -              | 所属语言           |
| `translation_key` | VARCHAR(255)    | 是   | -              | 翻译键             |
| `value`           | TEXT            | 是   | -              | 翻译值             |
| `remark`          | VARCHAR(512)    | 是   | `''`           |                    |
| `is_enabled`      | TINYINT(1)      | 是   | 1              |                    |
| `deleted_at`      | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒） |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                    |
| `updated_at`      | TIMESTAMP       | 是   | NOW()          |                    |
| `created_by`      | BIGINT UNSIGNED | 是   | 0              |                    |
| `updated_by`      | BIGINT UNSIGNED | 是   | 0              |                    |

**索引**：`PRIMARY(id)` / `UNIQUE(locale_id, translation_key, deleted_at)` / `idx_translation_key` / `idx_deleted_at`

**外键**：`fk_locale_id` → `i18n_locale(id)`

---

## 5. 字典模块

### 5.1 `dict_type` — 字典类型

| 字段         | 类型            | 必填 | 默认           | 说明               |
| ------------ | --------------- | ---- | -------------- | ------------------ |
| `id`         | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键               |
| `code`       | VARCHAR(64)     | 是   | -              | 字典类型编码       |
| `name`       | VARCHAR(64)     | 是   | -              | 字典类型名         |
| `remark`     | VARCHAR(512)    | 是   | `''`           |                    |
| `is_enabled` | TINYINT(1)      | 是   | 1              |                    |
| `deleted_at` | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒） |
| `created_at` | TIMESTAMP       | 是   | NOW()          |                    |
| `updated_at` | TIMESTAMP       | 是   | NOW()          |                    |
| `created_by` | BIGINT UNSIGNED | 是   | 0              |                    |
| `updated_by` | BIGINT UNSIGNED | 是   | 0              |                    |

**索引**：`PRIMARY(id)` / `UNIQUE(code, deleted_at)` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无

> v6 曾加 `platform`，v7 已移除：字典**类型**不做平台归属；平台过滤落在 `dict_data.platform`（v8+）。

---

### 5.2 `dict_data` — 字典数据

| 字段         | 类型            | 必填 | 默认           | 说明                                                                     |
| ------------ | --------------- | ---- | -------------- | ------------------------------------------------------------------------ |
| `id`         | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                                                     |
| `type_id`    | BIGINT UNSIGNED | 是   | -              | 所属类型                                                                 |
| `value`      | VARCHAR(64)     | 是   | -              | 字典值                                                                   |
| `label`      | VARCHAR(128)    | 是   | -              | 字典标签                                                                 |
| `sort`       | INT             | 是   | 0              | 排序                                                                     |
| `is_default` | TINYINT(1)      | 是   | 0              | 是否该类型默认值                                                         |
| `platform`   | VARCHAR(32)     | 是   | `'general'`    | 归属平台（v8+）：`general` / `react-admin` / `vue-admin`                 |
| `tag_type`   | VARCHAR(32)     | 是   | `'default'`    | 预设样式标识（v9+）；前端映射 ant Tag / vben Tag color；`default`=无样式 |
| `is_enabled` | TINYINT(1)      | 是   | 1              |                                                                          |
| `deleted_at` | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）                                                       |
| `remark`     | VARCHAR(512)    | 是   | `''`           |                                                                          |
| `created_at` | TIMESTAMP       | 是   | NOW()          |                                                                          |
| `updated_at` | TIMESTAMP       | 是   | NOW()          |                                                                          |
| `created_by` | BIGINT UNSIGNED | 是   | 0              |                                                                          |
| `updated_by` | BIGINT UNSIGNED | 是   | 0              |                                                                          |

**索引**：`PRIMARY(id)` / `UNIQUE(type_id, value, platform, deleted_at)` / `idx_type_id_sort` / `idx_platform` / `idx_is_enabled` / `idx_deleted_at`

**外键**：`fk_type_id` → `dict_type(id)`

> v8: 加 `platform`（字典项归属平台；与前端 `VITE_APP_PLATFORM` 配合做「只看自己 + general」过滤）。`dict_type` 保持无 `platform`（v7 决策）。
>
> v9: 加 `tag_type`（`default` / `primary` / `success` / `warning` / `error` / `processing` / `magenta` / `red` / `volcano` / `orange` / `gold` / `lime` / `green` / `cyan` / `blue` / `geekblue` / `purple`）。
>
> v10: 软删感知唯一键纳入 `platform` → `UNIQUE(type_id, value, platform, deleted_at)`，允许同类型同 value 在不同平台各有一条活跃行（与 `schema_data.sql` 中 `sys_switch_status` 一致）。
>
> 初始 seed 见 `backend/db/schema_data.sql`：字典 7 类 + RBAC（api/menu/role/root 用户与关联，对齐 mock）。

---

## 6. ABAC 数据权限模块

### 6.1 `sys_data_permission` — ABAC 行级授权

| 字段             | 类型            | 必填 | 默认           | 说明                                    |
| ---------------- | --------------- | ---- | -------------- | --------------------------------------- |
| `id`             | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                    |
| `subject_type`   | VARCHAR(16)     | 是   | -              | USER / ROLE / ANY_USER / ANY_ROLE       |
| `subject_id`     | BIGINT UNSIGNED | 是   | 0              | 主体 ID；`ANY_*` 时为 0                 |
| `resource_table` | VARCHAR(32)     | 是   | -              | 资源表名                                |
| `action`         | JSON            | 是   | -              | 操作列表                                |
| `action_key`     | VARCHAR(64)     | 是   | `'read'`       | 规范化操作键                            |
| `scope_type`     | VARCHAR(32)     | 是   | `'none'`       | all / none / include / exclude / custom |
| `scope_field`    | VARCHAR(64)     | 是   | `'id'`         | 作用域匹配字段                          |
| `scope_values`   | JSON            | 是   | -              | 作用域值列表                            |
| `conditions`     | JSON            | 是   | -              | 行过滤条件                              |
| `priority`       | INT             | 是   | 0              | 冲突优先级                              |
| `remark`         | VARCHAR(512)    | 是   | `''`           | 管理员备注                              |
| `is_enabled`     | TINYINT(1)      | 是   | 1              | 启用/禁用                               |
| `deleted_at`     | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）                      |
| `created_at`     | TIMESTAMP       | 是   | NOW()          |                                         |
| `updated_at`     | TIMESTAMP       | 是   | NOW()          |                                         |
| `created_by`     | BIGINT UNSIGNED | 是   | 0              |                                         |
| `updated_by`     | BIGINT UNSIGNED | 是   | 0              |                                         |

**索引**：`PRIMARY(id)` / `UNIQUE(subject_type, subject_id, resource_table, action_key, deleted_at)` / `idx_subject` / `idx_subject_resource` / `idx_resource` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无（多态主体，无法 FK）

---

## 6b. 访问黑名单模块（v11）

### 6b.1 `sys_blacklist` — 访问黑名单

| 字段           | 类型            | 必填 | 默认              | 说明                                                     |
| -------------- | --------------- | ---- | ----------------- | -------------------------------------------------------- |
| `id`           | BIGINT UNSIGNED | 是   | AUTO_INCREMENT    | 主键                                                     |
| `target_type`  | VARCHAR(16)     | 是   | -                 | `IP` / `USER` / `DEVICE`                                 |
| `target_value` | VARCHAR(128)    | 是   | -                 | IP 文本；USER=`sys_user.id` 字符串；DEVICE=deviceId 原样 |
| `scope`        | VARCHAR(16)     | 是   | `'ALL'`           | `LOGIN` / `API` / `ALL`                                  |
| `reason`       | VARCHAR(512)    | 是   | `''`              | 封禁原因（对用户/审计可见）                              |
| `starts_at`    | TIMESTAMP       | 是   | CURRENT_TIMESTAMP | 生效开始（含）                                           |
| `expires_at`   | TIMESTAMP       | 否   | NULL              | 生效结束（不含）；NULL=永不过期                          |
| `remark`       | VARCHAR(512)    | 是   | `''`              | 管理员内部备注                                           |
| `is_enabled`   | TINYINT(1)      | 是   | 1                 | 启用/禁用                                                |
| `deleted_at`   | BIGINT UNSIGNED | 是   | 0                 | 软删时间戳（毫秒）                                       |
| `created_at`   | TIMESTAMP       | 是   | NOW()             |                                                          |
| `updated_at`   | TIMESTAMP       | 是   | NOW()             |                                                          |
| `created_by`   | BIGINT UNSIGNED | 是   | 0                 |                                                          |
| `updated_by`   | BIGINT UNSIGNED | 是   | 0                 |                                                          |

**索引**：`PRIMARY(id)` / `UNIQUE(target_type, target_value, scope, starts_at, expires_at, deleted_at)` / `idx_target(target_type, target_value)` / `idx_expires_at` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无（`USER` 为软引用；IP/DEVICE 无实体）

> 生效：`deleted_at=0 AND is_enabled=1 AND starts_at<=NOW() AND (expires_at IS NULL OR expires_at>NOW())`；请求侧再 `scope IN (场景, 'ALL')`。多行 OR。详见 `db-conventions.md` §18。

---

## 7. 日志模块（记录型）

### 7.1 `api_log` — API 调用日志（v5+ 字段扩充对齐 PG `sys_api_log`）

| 字段              | 类型            | 必填 | 默认           | 说明                                                             |
| ----------------- | --------------- | ---- | -------------- | ---------------------------------------------------------------- |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                                             |
| `method`          | VARCHAR(16)     | 是   | -              | HTTP method                                                      |
| `module`          | VARCHAR(255)    | 是   | `''`           | 业务模块                                                         |
| `path`            | VARCHAR(255)    | 是   | -              | 请求路径（不含 query）                                           |
| `status_code`     | INT UNSIGNED    | 否   | NULL           | HTTP 状态码（早期失败可能未设置）                                |
| `success`         | TINYINT(1)      | 是   | 0              | 业务级成功（中间件按 `status_code` 判定，2xx=1）                 |
| `reason`          | VARCHAR(255)    | 是   | `''`           | 失败原因                                                         |
| `cost_time`       | BIGINT UNSIGNED | 是   | 0              | 耗时（毫秒）                                                     |
| `request_id`      | VARCHAR(128)    | 是   | -              | 请求唯一 ID（中间件生成；UNIQUE；v5 64→128）                     |
| `sys_user_id`     | BIGINT UNSIGNED | 否   | NULL           | 操作用户（未登录为 NULL；v5 改名 `user_id`→`sys_user_id`）       |
| `username`        | VARCHAR(64)     | 是   | `''`           | 冗余：用户删除前的痕迹                                           |
| `request_uri`     | TEXT            | 是   | `''`           | 完整 URI（含 query；便于回放）                                   |
| `request_query`   | TEXT            | 是   | `''`           | query string                                                     |
| `request_body`    | MEDIUMTEXT      | 是   | `''`           | 请求 body（应用层截断 64KB）                                     |
| `request_header`  | MEDIUMTEXT      | 是   | `''`           | 请求头（敏感字段脱敏后存储）                                     |
| `referer`         | VARCHAR(2048)   | 是   | `''`           | 来源页                                                           |
| `response`        | MEDIUMTEXT      | 是   | `''`           | 响应 body（应用层截断 64KB；v5 改名 `response_body`→`response`） |
| `before_change`   | MEDIUMTEXT      | 是   | `''`           | 操作前数据快照（写操作场景）                                     |
| `after_change`    | MEDIUMTEXT      | 是   | `''`           | 操作后数据快照                                                   |
| `format_change`   | TEXT            | 是   | `''`           | 格式化变更摘要（人读）                                           |
| `client_id`       | VARCHAR(128)    | 是   | `''`           | 客户端 ID                                                        |
| `client_name`     | VARCHAR(128)    | 是   | `''`           | 客户端名                                                         |
| `client_ip`       | VARCHAR(64)     | 是   | `''`           | 客户端 IP（IPv6 兼容；v5 45→64）                                 |
| `user_agent`      | TEXT            | 是   | `''`           | User Agent（v5 VARCHAR(512)→TEXT）                               |
| `browser_name`    | VARCHAR(128)    | 是   | `''`           | 浏览器名（由 UA 解析）                                           |
| `browser_version` | VARCHAR(128)    | 是   | `''`           | 浏览器版本                                                       |
| `os_name`         | VARCHAR(128)    | 是   | `''`           | 操作系统名                                                       |
| `os_version`      | VARCHAR(128)    | 是   | `''`           | 操作系统版本                                                     |
| `location`        | VARCHAR(255)    | 是   | `''`           | IP 解析地理位置                                                  |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                                                                  |

**索引**：`PRIMARY(id)` / `UNIQUE(request_id)` / `idx_sys_user_id_created_at` / `idx_module_created_at` / `idx_path_created_at` / `idx_status_code_created_at` / `idx_success_created_at` / `idx_client_ip_created_at`

**外键**：无

---

### 7.2 `api_log_archive` — API 日志归档

结构同 `api_log`（v5+），**额外**加 `archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

索引包含 `UNIQUE(request_id)`

---

### 7.3 `sys_login_log` — 登录日志（v5+ 字段扩充对齐 PG `sys_login_log`）

| 字段              | 类型            | 必填 | 默认           | 说明                                                             |
| ----------------- | --------------- | ---- | -------------- | ---------------------------------------------------------------- |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                                             |
| `username`        | VARCHAR(64)     | 是   | `''`           | 登录用户名                                                       |
| `success`         | TINYINT(1)      | 是   | 0              | 1=成功 0=失败                                                    |
| `reason`          | VARCHAR(255)    | 是   | `''`           | 失败原因（v5 改名 `failure_reason`→`reason` 与 PG 对齐）         |
| `status_code`     | INT UNSIGNED    | 否   | NULL           | HTTP 状态码（200=成功）                                          |
| `sys_user_id`     | BIGINT UNSIGNED | 否   | NULL           | 登录成功后关联（v5 改名 `user_id`→`sys_user_id`）                |
| `login_method`    | VARCHAR(32)     | 是   | `'PASSWORD'`   | PASSWORD / SSO / OAUTH / SMS                                     |
| `login_time`      | TIMESTAMP       | 是   | NOW()          | 登录尝试时间（应用层可与 `created_at` 区分；异步上报时可能略晚） |
| `login_ip`        | VARCHAR(64)     | 是   | `''`           | 登录 IP（v5 改名 `client_ip`→`login_ip`）                        |
| `login_mac`       | VARCHAR(128)    | 是   | `''`           | 登录 MAC（CS 场景下多为空）                                      |
| `client_id`       | VARCHAR(128)    | 是   | `''`           | 客户端 ID                                                        |
| `client_name`     | VARCHAR(128)    | 是   | `''`           | 客户端名                                                         |
| `user_agent`      | TEXT            | 是   | `''`           | User Agent（v5 VARCHAR(512)→TEXT）                               |
| `browser_name`    | VARCHAR(128)    | 是   | `''`           | 浏览器名（由 UA 解析）                                           |
| `browser_version` | VARCHAR(128)    | 是   | `''`           | 浏览器版本                                                       |
| `os_name`         | VARCHAR(128)    | 是   | `''`           | 操作系统名                                                       |
| `os_version`      | VARCHAR(128)    | 是   | `''`           | 操作系统版本                                                     |
| `location`        | VARCHAR(255)    | 是   | `''`           | IP 解析地理位置                                                  |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                                                                  |

**索引**：`PRIMARY(id)` / `idx_username_created_at` / `idx_success_created_at` / `idx_sys_user_id` / `idx_login_ip_created_at` / `idx_login_time`

**外键**：无

> v5: 移除 `device` / `os` / `browser` / `country` / `province` / `city`，由 `os_name/version` + `browser_name/version` + `location` + `client_*` 替代

---

### 7.4 `sys_login_log_archive` — 登录日志归档

结构同 `sys_login_log`（v5+），**额外**加 `archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

---

### 7.5 `operation_log` — 操作日志

| 字段           | 类型            | 必填 | 默认           | 说明                    |
| -------------- | --------------- | ---- | -------------- | ----------------------- |
| `id`           | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                    |
| `user_id`      | BIGINT UNSIGNED | 否   | NULL           | 操作人（系统级为 NULL） |
| `username`     | VARCHAR(64)     | 是   | `''`           | 冗余（系统级写 system） |
| `module`       | VARCHAR(64)     | 是   | -              | 业务模块                |
| `action`       | VARCHAR(64)     | 是   | -              | 动作                    |
| `target_id`    | BIGINT UNSIGNED | 否   | NULL           | 被操作对象              |
| `before_value` | JSON            | 否   | NULL           | 改前快照                |
| `after_value`  | JSON            | 否   | NULL           | 改后快照                |
| `request_id`   | VARCHAR(64)     | 否   | NULL           | 关联 `api_log`          |
| `source`       | VARCHAR(16)     | 是   | `'AUTO'`       | AUTO / EXPLICIT         |
| `remark`       | VARCHAR(512)    | 是   | `''`           |                         |
| `client_ip`    | VARCHAR(45)     | 是   | `''`           |                         |
| `user_agent`   | VARCHAR(512)    | 是   | `''`           |                         |
| `created_at`   | TIMESTAMP       | 是   | NOW()          |                         |

**索引**：`PRIMARY(id)` / `idx_user_id_created_at` / `idx_module_action_created_at` / `idx_source`

**外键**：无

---

### 7.6 `operation_log_archive` — 操作日志归档

结构同 `operation_log`，**额外**加 `archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`

---

## 8. Temporal 任务调度模块

### 8.1 `temporal_task_config` — 任务配置

| 字段              | 类型            | 必填 | 默认           | 说明                   |
| ----------------- | --------------- | ---- | -------------- | ---------------------- |
| `id`              | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                   |
| `code`            | VARCHAR(64)     | 是   | -              | 任务编码               |
| `name`            | VARCHAR(128)    | 是   | -              | 任务名                 |
| `workflow_type`   | VARCHAR(128)    | 是   | -              | Temporal workflow 类名 |
| `task_queue`      | VARCHAR(128)    | 是   | -              | Temporal task queue    |
| `cron_expr`       | VARCHAR(64)     | 否   | NULL           | NULL=仅手动触发        |
| `retry_policy`    | JSON            | 否   | NULL           | 重试策略               |
| `timeout_seconds` | INT UNSIGNED    | 否   | NULL           | 超时（秒）             |
| `remark`          | VARCHAR(512)    | 是   | `''`           |                        |
| `is_enabled`      | TINYINT(1)      | 是   | 1              |                        |
| `deleted_at`      | BIGINT UNSIGNED | 是   | 0              | 软删时间戳（毫秒）     |
| `created_at`      | TIMESTAMP       | 是   | NOW()          |                        |
| `updated_at`      | TIMESTAMP       | 是   | NOW()          |                        |
| `created_by`      | BIGINT UNSIGNED | 是   | 0              |                        |
| `updated_by`      | BIGINT UNSIGNED | 是   | 0              |                        |

**索引**：`PRIMARY(id)` / `UNIQUE(code, deleted_at)` / `idx_is_enabled` / `idx_deleted_at`

**外键**：无

---

### 8.2 `temporal_task_execution` — 任务执行（摘要镜像）

| 字段             | 类型            | 必填 | 默认           | 说明                                                    |
| ---------------- | --------------- | ---- | -------------- | ------------------------------------------------------- |
| `id`             | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键                                                    |
| `config_id`      | BIGINT UNSIGNED | 否   | NULL           | 软外键（不建 FK）                                       |
| `workflow_id`    | VARCHAR(128)    | 是   | -              | Temporal 原生                                           |
| `run_id`         | VARCHAR(128)    | 是   | -              | Temporal 原生                                           |
| `workflow_type`  | VARCHAR(128)    | 是   | -              |                                                         |
| `task_queue`     | VARCHAR(128)    | 是   | -              |                                                         |
| `status`         | VARCHAR(32)     | 是   | -              | PENDING / RUNNING / RETRYING / COMPLETED / FAILED / ... |
| `pending_at`     | TIMESTAMP       | 否   | NULL           | 进入等待中（PENDING）的时间                             |
| `started_at`     | TIMESTAMP       | 否   | NULL           | 真正运行开始时间（NULL=尚未真正运行）                   |
| `closed_at`      | TIMESTAMP       | 否   | NULL           | 关闭时间（NULL=仍在运行/未启动）                        |
| `input_summary`  | JSON            | 否   | NULL           |                                                         |
| `result_summary` | JSON            | 否   | NULL           |                                                         |
| `failure_reason` | VARCHAR(1024)   | 否   | NULL           |                                                         |
| `retry_count`    | INT             | 是   | 0              | 已发生重试次数（首次执行为 0）                          |
| `created_at`     | TIMESTAMP       | 是   | NOW()          |                                                         |

**索引**：`PRIMARY(id)` / `UNIQUE(workflow_id, run_id)` / `idx_config_id_started_at` / `idx_status_started_at`

**外键**：无（config_id 软外键）

---

## 9. Casbin 模块

### 9.1 `casbin_rule` — Casbin policy

完全采用 `casbin/mysql-adapter` v2 标准表，admin 业务代码**不**直接 CRUD。

| 字段    | 类型            | 必填 | 默认           | 说明        |
| ------- | --------------- | ---- | -------------- | ----------- |
| `id`    | BIGINT UNSIGNED | 是   | AUTO_INCREMENT | 主键        |
| `ptype` | VARCHAR(255)    | 是   | -              | policy type |
| `v0`    | VARCHAR(255)    | 否   | NULL           |             |
| `v1`    | VARCHAR(255)    | 否   | NULL           |             |
| `v2`    | VARCHAR(255)    | 否   | NULL           |             |
| `v3`    | VARCHAR(255)    | 否   | NULL           |             |
| `v4`    | VARCHAR(255)    | 否   | NULL           |             |
| `v5`    | VARCHAR(255)    | 否   | NULL           |             |

**索引**：`PRIMARY(id)` / `idx_ptype_v0_v1`

**外键**：无
