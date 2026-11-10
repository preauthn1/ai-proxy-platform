# 后台管理 DB 约定 (v11)

> 本文件是 `backend/db/schema.sql` 的**配套约定文档**。开发者写 admin 后端代码（model / repository / service）时请遵守。
>
> 对齐 schema 当前态：v5 基线 + `dict_data` v8/v9/v10 + `sys_blacklist` v11。本文件**不**修改 `.trellis/spec/backend/database-guidelines.md`——那是 `00-bootstrap-guidelines` 任务的职责。

---

## 1. 字符集与排序规则

- **字符集**：`utf8mb4`（4 字节，能存 emoji 与生僻汉字）
- **排序规则**：`utf8mb4_0900_ai_ci`（MySQL 8 默认，Unicode 9.0）
- **引擎**：`InnoDB`（事务 + 行锁 + FK）

所有表与列显式声明上述三项，**不**依赖数据库默认。

---

## 2. 命名

| 维度       | 约定                                                                                                      | 反例                                                          |
| ---------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 表名       | `snake_case`，业务表 `sys_` 前缀                                                                          | `SysUser` / `sysUser` / `user`                                |
| 主键       | `id` 统一 `BIGINT UNSIGNED AUTO_INCREMENT`                                                                | 自定义主键名 / UUID 主键                                      |
| 业务字段   | `snake_case`                                                                                              | `userId` / `permissionCode`                                   |
| 枚举字段   | `VARCHAR(32) + 应用层 enum`                                                                               | `TINYINT` 存魔法值                                            |
| 时间字段   | `TIMESTAMP`（除非需要 2038+，否则不用 `DATETIME`）                                                        | `DATETIME`(3) 存毫秒                                          |
| 布尔字段   | `TINYINT(1) NOT NULL DEFAULT 0`                                                                           | `BOOLEAN` / `BIT(1)`                                          |
| JSON 字段  | `JSON`（MySQL 8 原生）                                                                                    | `TEXT` 存 JSON 字符串                                         |
| 软删时间戳 | `deleted_at BIGINT UNSIGNED NOT NULL DEFAULT 0`（毫秒；0=未删）                                           | `is_deleted TINYINT`（需配合 `is_active` 生成列才能感知唯一） |
| 启停标志   | `is_enabled TINYINT(1) NOT NULL DEFAULT 1`                                                                | 与 `deleted_at` 混用（丢失三态语义）                          |
| 备注       | `remark VARCHAR(512) NOT NULL DEFAULT ''`                                                                 | 留 `NULL` → 应用层判空繁琐                                    |
| 审计       | `created_by` / `updated_by`：`BIGINT UNSIGNED NOT NULL DEFAULT 0`（0=系统操作；非0=软引用 `sys_user.id`） | `DEFAULT NULL`（需应用层特判）                                |
| 索引       | `idx_<table>_<col1>_<col2>`                                                                               | `idx_username`（太短）                                        |
| 唯一索引   | `uniq_<table>_<col1>_<col2>`                                                                              | `unique_username`                                             |
| 外键约束   | `fk_<table>_<col>`                                                                                        | `user_role_fk`                                                |

---

## 3. 主键 ID 策略

- **统一**：`BIGINT UNSIGNED AUTO_INCREMENT`
- 单库单表容量：`2^64`，足够任何业务
- 单租户下分库分表友好度低（未来需要分库时可改雪花 ID）

---

## 4. 审计 + 启停 + 软删字段（核心表）

每张**核心表**（共 11 张）必须包含以下 **7 个字段**，**顺序固定**：

```sql
remark          VARCHAR(512)    NOT NULL DEFAULT ''                -- 管理员备注
is_enabled      TINYINT(1)      NOT NULL DEFAULT 1                  -- 启用/禁用
deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0                  -- 软删时间戳(毫秒; 0=未删;非0=删除时刻)
created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0                  -- 0=系统操作;非0=软引用 sys_user.id
updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0                  -- 0=系统操作;非0=软引用 sys_user.id
```

### 4.1 `deleted_at` 语义

- `0` = 活跃（最常见）
- `> 0` = 软删时刻（毫秒时间戳，Unix 纪元 ×1000）

应用层写入：

- 软删：`UPDATE ... SET deleted_at = UNIX_TIMESTAMP() * 1000 WHERE id = ?`
- 复活：`UPDATE ... SET deleted_at = 0 WHERE id = ?`（仅限授权操作）

### 4.2 `is_enabled` × `deleted_at` 的三态

| `deleted_at` | `is_enabled` | 语义                           |
| ------------ | ------------ | ------------------------------ |
| 0            | 1            | 正常（最常见）                 |
| 0            | 0            | 禁用（管理员手动停用，可恢复） |
| > 0          | \*           | 已删除（终态）                 |

业务查询默认 `WHERE deleted_at = 0`；是否同时 `AND is_enabled = 1` 由具体接口决定（如"获取所有可登录用户"需要加，`"获取所有用户（包括禁用）"不加`）。

### 4.3 `created_by` / `updated_by` 语义

- `0` = 系统级操作（无用户上下文；如定时任务、初始 seed、admin 启动初始化等）
- `> 0` = 真实用户 ID（软引用 `sys_user.id`；不建 FK，删除用户时不级联）

应用层责任：

- 写入时由 AOP 拦截器从 session 取当前用户 id 注入；无 session 时写 0
- 查询时若要排除系统级操作 `AND created_by > 0`

### 4.4 不同表的覆盖

| 表类                                                                                                                                                                                                 | 字段                                                                                                                             |
| ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| 核心表 11 张（`sys_user` / `sys_role` / `sys_api` / `sys_menu` / `i18n_locale` / `i18n_translation` / `dict_type` / `dict_data` / `temporal_task_config` / `sys_data_permission` / `sys_blacklist`） | 上述 7 字段全有                                                                                                                  |
| 关联表 4 张（`sys_user_role` / `sys_role_api` / `sys_role_menu` / `sys_menu_api`）                                                                                                                   | **只**加 `created_at`（`sys_menu_api` 额外加 `created_by`，其余关联表也不加 `created_by` / `updated_by`——"解绑"= `DELETE` 整行） |
| 记录型表 4 张（3 张日志 + `temporal_task_execution`）                                                                                                                                                | **只**加 `created_at`——只增不改，写入人即操作人，已被日志主体（`user_id` / `username`）记录                                      |
| 归档表 3 张（`*_archive`）                                                                                                                                                                           | 镜像热表（多 `archived_at`）                                                                                                     |

---

## 5. 软删

### 5.1 软删字段

`deleted_at BIGINT UNSIGNED NOT NULL DEFAULT 0`

- `0` = 活跃
- `> 0` = 软删时刻（毫秒 Unix 时间戳）

### 5.2 软删感知唯一约束

**问题**：`UNIQUE (col)` 在软删场景下——活跃行（`deleted_at=0`）只能有一条；软删后想重建会冲突。

**解决方案**：将 `deleted_at` 纳入唯一键：

```sql
UNIQUE KEY uniq_sys_user_username (username, deleted_at)
```

原理：0 与非 0 是不同的值，所以唯一键视作不同的行。`username = 'alice'` 的活跃行（`deleted_at=0`）和软删行（`deleted_at=1781416216000`）可共存；同 `(subject, resource, action_key)` 同 `deleted_at=0` 至多一条。

**应用层语义**：

- 软删：`UPDATE ... SET deleted_at = UNIX_TIMESTAMP() * 1000 WHERE id = ?`
- 重建：直接 `INSERT` 即可（同 `username, deleted_at=0` 的活跃行已被软删让位）

**覆盖范围**（v4+ 起核心表 UNIQUE 全部软删感知；v11+ 含 blacklist）：

- `sys_user.username`
- `sys_role.code`
- `sys_api.(method, path)` 与 `permission_code`（2 个）
- `i18n_locale.code`
- `i18n_translation.(locale_id, translation_key)`
- `dict_type.code`
- `dict_data.(type_id, value, platform)`（v10+；平台是隔离维度）
- `temporal_task_config.code`
- `sys_data_permission.(subject_type, subject_id, resource_table, action_key)`
- `sys_blacklist.(target_type, target_value, scope, starts_at, expires_at)`（v11+；弱唯一，见 §18；允许多条不同时间窗）

---

## 6. 索引策略

### 6.1 通用原则

- **主键**：`id`
- **唯一**：仅业务上确实唯一的字段
- **二级索引**：高频查询路径
- **不**为 `TEXT` / `MEDIUMTEXT` / `JSON` 列建索引
- 单表索引数 ≤ 6（避免写入放大）

### 6.2 高频索引模式

| 场景                    | 索引                                                                                           |
| ----------------------- | ---------------------------------------------------------------------------------------------- |
| 按用户 + 时间查日志     | `(sys_user_id, created_at)`（v5+ `api_log` / `sys_login_log`；`operation_log` 仍为 `user_id`） |
| 按用户 + 模块 + 时间    | `(sys_user_id, module, created_at)`（`api_log` 特有；v5+）                                     |
| 按状态 + 时间筛选       | `(status_code, created_at)`（`api_log`）/ `(status, created_at)`（`temporal_task_execution`）  |
| 关联表反向查询          | `(xxx_id, role_id)`（与 PK 反向）                                                              |
| 字典/枚举筛选           | `(type_id, sort)`                                                                              |
| 字典按平台过滤          | `idx_dict_data_platform`（`dict_data.platform`；v8+）                                          |
| 软删过滤                | `idx_*_deleted_at` 单列                                                                        |
| 菜单物化路径查祖先/子树 | `(tree_path)` 前缀匹配                                                                         |

### 6.3 索引命名

`idx_<table>_<col1>_<col2>` / `uniq_<table>_<col1>_<col2>`

例：`idx_api_log_user_id_created_at` / `uniq_sys_user_username`

---

## 7. 外键策略

### 7.1 建外键的场景

| 引用                                            | 外键约束 | 说明                 |
| ----------------------------------------------- | -------- | -------------------- |
| `i18n_translation.locale_id` → `i18n_locale.id` | ✅       | 翻译必须依赖语言     |
| `dict_data.type_id` → `dict_type.id`            | ✅       | 字典数据必须依赖类型 |
| `sys_user_role.user_id` → `sys_user.id`         | ✅       | 关联关系强一致       |
| `sys_user_role.role_id` → `sys_role.id`         | ✅       | 同上                 |
| `sys_role_api.role_id` → `sys_role.id`          | ✅       | 授权关系强一致       |
| `sys_role_api.api_id` → `sys_api.id`            | ✅       | 同上                 |
| `sys_role_menu.role_id` → `sys_role.id`         | ✅       | 同上                 |
| `sys_role_menu.menu_id` → `sys_menu.id`         | ✅       | 同上                 |
| `sys_menu.parent_id` → `sys_menu.id`            | ✅       | 自引用树形           |
| `sys_role.parent_id` → `sys_role.id`            | ✅       | 自引用角色层级       |
| `sys_menu_api.menu_id` → `sys_menu.id`          | ✅       | 快捷绑定强一致       |
| `sys_menu_api.api_id` → `sys_api.id`            | ✅       | 同上                 |

### 7.2 不建外键（软引用，`NULL` 或 `0` 占位）

| 引用                                                                   | 类型                     | 原因                                                              |
| ---------------------------------------------------------------------- | ------------------------ | ----------------------------------------------------------------- |
| `created_by` / `updated_by` → `sys_user.id`                            | `NOT NULL DEFAULT 0`     | 0=系统操作；用户删除时不应级联清空历史                            |
| `sys_user.language_code` → `i18n_locale.code`                          | `NULL`                   | 同上（i18n_locale.code 软引用）                                   |
| `sys_data_permission.subject_id` → `sys_user.id` / `sys_role.id`       | `NOT NULL DEFAULT 0`     | 多态主体（`ANY_*` 时为 0），无法 FK                               |
| `sys_blacklist.target_value`（当 `target_type='USER'`）→ `sys_user.id` | `VARCHAR` 存十进制字符串 | 多态 target，无法 FK；IP/DEVICE 无实体引用                        |
| `temporal_task_execution.config_id` → `temporal_task_config.id`        | `NULL`                   | 执行可能先于配置存在                                              |
| `api_log.sys_user_id` / `sys_login_log.sys_user_id` → `sys_user.id`    | `NULL`                   | 日志应保留用户删除前的痕迹（v5+；`operation_log` 仍为 `user_id`） |

### 7.3 自引用外键

`sys_menu.parent_id` 与 `sys_role.parent_id` 必须在 `CREATE TABLE` 之后用 `ALTER TABLE` 补充：

```sql
ALTER TABLE sys_menu
    ADD CONSTRAINT fk_sys_menu_parent_id
    FOREIGN KEY (parent_id) REFERENCES sys_menu (id);

ALTER TABLE sys_role
    ADD CONSTRAINT fk_sys_role_parent_id
    FOREIGN KEY (parent_id) REFERENCES sys_role (id);
```

---

## 8. 枚举字段

**存储**：`VARCHAR(32) NOT NULL DEFAULT 'XXX'`

**应用层**：用 enum 校验（Go / Java 都支持）。

**反例**：

- `TINYINT` 存魔法值——可读性差、改值困难
- `ENUM('A','B','C')`——MySQL ENUM 添加新值需要 ALTER TABLE，迁移不友好

**约定取值**：

- 登录方式：`PASSWORD` / `SSO` / `OAUTH` / `SMS`
- 菜单类型：`DIR` / `MENU` / `BUTTON`
- API method：`GET` / `POST` / `PUT` / `DELETE` / `PATCH` / `OPTIONS` / `HEAD`
- 操作日志 source：`AUTO` / `EXPLICIT`
- 数据权限 subject_type：`USER` / `ROLE` / `ANY_USER` / `ANY_ROLE`
- 数据权限 scope_type：`all` / `none` / `include` / `exclude` / `custom`
- Temporal status：`PENDING` / `RUNNING` / `RETRYING` / `COMPLETED` / `FAILED` / `CANCELLED` / `TERMINATED` / `TIMED_OUT` / `CONTINUED_AS_NEW`
- Temporal 执行 `retry_count`：已发生重试次数（首次执行为 `0`，非剩余次数）
- 字典项平台（`dict_data.platform`，v8+）：`general` / `react-admin` / `vue-admin`
- 字典项样式（`dict_data.tag_type`，v9+）：`default` / `primary` / `success` / `warning` / `error` / `processing` / `magenta` / `red` / `volcano` / `orange` / `gold` / `lime` / `green` / `cyan` / `blue` / `geekblue` / `purple`
- 黑名单目标类型（`sys_blacklist.target_type`，v11+）：`IP` / `USER` / `DEVICE`
- 黑名单限制范围（`sys_blacklist.scope`，v11+）：`LOGIN` / `API` / `ALL`

---

## 9. JSON 字段

**用 JSON 存储**：

- `api_log.request_body` / `api_log.response`（限 64KB，应用层截断；`NOT NULL DEFAULT ('')`；v5+ 改名 `response_body`→`response`）
- `temporal_task_config.retry_policy`
- `temporal_task_execution.input_summary` / `result_summary`
- `operation_log.before_value` / `after_value`（数据快照）
- `sys_data_permission.action` / `scope_values` / `conditions`
- `sys_menu.metadata`（前端扩展字段）

**不**用 JSON：

- 主键外键（必须用类型化列）
- 高频筛选条件（JSON 查询性能差、索引难建）

**应用层契约**：

- 写入前用语言库序列化（Go `encoding/json` / Java `ObjectMapper`）
- 读取后做 schema 校验（自定义 validator）
- **永远不要**把用户输入直接拼到 JSON 字符串中

---

## 10. 软删 + 审计：写操作模板

### 10.1 业务表新增

```sql
INSERT INTO sys_user (username, password_hash, nickname, created_by)
VALUES (?, ?, ?, ?);
```

应用层拦截器自动注入 `created_at` / `updated_at`（这里显式传 `created_by` 以示）。系统级操作 `created_by = 0`。

### 10.2 业务表更新

```sql
UPDATE sys_user
SET nickname = ?, updated_by = ?
WHERE id = ? AND deleted_at = 0;
```

应用层拦截器自动更新 `updated_at`。

### 10.3 业务表软删

```sql
UPDATE sys_user
SET deleted_at = UNIX_TIMESTAMP() * 1000, updated_by = ?
WHERE id = ?;
```

**禁止**使用 `DELETE FROM sys_user WHERE id = ?`（业务上不允许硬删）。

### 10.4 业务表查询

```sql
SELECT * FROM sys_user
WHERE username = ? AND deleted_at = 0;
```

**所有**业务查询必须 `WHERE deleted_at = 0`（在 repository 层的 `BaseRepository` 封装）。

### 10.5 软删后重建同名记录

直接 `INSERT` 即可。`UNIQUE(col, deleted_at)` 让 `deleted_at=0` 的新行与已存在的 `deleted_at>0` 软删行共存。

```sql
-- 1. 软删
UPDATE sys_user SET deleted_at = UNIX_TIMESTAMP() * 1000 WHERE username = 'alice';
-- 2. 重建（直接 INSERT，不需要硬删旧行）
INSERT INTO sys_user (username, password_hash) VALUES ('alice', '...');
```

---

## 11. 日志表

### 11.1 三类日志

| 表              | 主体         | 来源                                | 关键字段                                                                                                                                             |
| --------------- | ------------ | ----------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `api_log`       | HTTP 请求    | AOP 拦截 controller                 | `request_id` UNIQUE / `module` / `sys_user_id`（v5+）                                                                                                |
| `sys_login_log` | 登录事件     | 登录 service 显式写                 | `login_method` / `reason`（v5+ 改名 `failure_reason`→`reason`）/ `status_code` / `login_ip`（v5+ 改名 `client_ip`→`login_ip`）/ `sys_user_id`（v5+） |
| `operation_log` | 业务数据变更 | AOP 拦截 service + 显式 `@AuditLog` | `source` AUTO/EXPLICIT / `request_id` 关联                                                                                                           |

### 11.2 写策略

- `api_log`：**全量**自动写入，每次请求一条；中间件生成 `request_id` 唯一标识
- `sys_login_log`：登录 service 显式写（成功/失败都写）
- `operation_log`：AOP 拦截 `UPDATE` / `DELETE` 自动写（`source = 'AUTO'`）；同时支持 `@AuditLog` 注解显式打标（`source = 'EXPLICIT'`）

### 11.3 `api_log` 与 `operation_log` 关联

通过 `request_id` 串联：

- 一次 HTTP 请求 = 1 行 `api_log` + N 行 `operation_log`（N=该请求触发的写操作数）
- 通过 `request_id` JOIN 两表可得"该请求触发了哪些业务变更"

### 11.4 归档

- 每日凌晨 TTL 作业跑一次
- 将 `created_at < NOW() - INTERVAL 30 DAY` 的热表行搬运到对应 `_archive` 表
- 搬运后 `DELETE` 热表
- 归档表超过 365 天可 `TRUNCATE`（业务策略）
- TTL 作业**不**在本任务范围，由 admin 后端各自实现

### 11.5 性能约束

- `api_log` 表快速膨胀；写入需应用层异步化（消息队列 / 异步 goroutine）
- 索引选择（v5+）：`(sys_user_id, created_at)` / `(module, created_at)` / `(path, created_at)` / `(status_code, created_at)` / `(success, created_at)` / `(client_ip, created_at)` 覆盖主要查询路径
- 避免在请求关键路径上同步写日志（会拖慢响应）

---

## 12. ABAC 数据权限 (`sys_data_permission`)

### 12.1 主体多态

- `subject_type='USER'` + `subject_id=具体用户 id`——针对个人
- `subject_type='ROLE'` + `subject_id=具体角色 id`——针对角色
- `subject_type='ANY_USER'` + `subject_id=0`——所有用户默认
- `subject_type='ANY_ROLE'` + `subject_id=0`——所有角色默认

应用层校验 `subject_type` 合法性 + `subject_id` 存在性（多态，无法 FK）。

### 12.2 多主体合并

- 多角色用户：各角色 permission 独立查
- 同 `(subject_type, subject_id, resource_table, action_key, deleted_at=0)` 多条时取 `priority` 最高的胜出
- `ANY_*` 默认 `priority = 0`（最低），具体策略应 `priority >= 100`
- 默认合并策略：**取 `priority` 最高的胜出**（不是 union，也不是 intersect）

### 12.3 scope 表达

| `scope_type` | 含义                              | 应用层 SQL 转换                    |
| ------------ | --------------------------------- | ---------------------------------- |
| `all`        | 不过滤                            | （不加条件）                       |
| `none`       | 完全拒绝                          | `WHERE 1=0`                        |
| `include`    | `scope_field IN scope_values`     | `WHERE {scope_field} IN (...)`     |
| `exclude`    | `scope_field NOT IN scope_values` | `WHERE {scope_field} NOT IN (...)` |
| `custom`     | `conditions` 接管                 | 解析 conditions 应用层自定义 SQL   |

### 12.4 conditions 安全

`conditions` 是自由 JSON map（`{key: value}`）。应用层必须：

- **白名单键**——`conditions` 的 `key` 必须是业务表实际存在的列名，且属于 `allowed_columns` 白名单
- **强制参数化**——`value` 通过 prepared statement 绑定，**禁止**字符串拼接
- **类型校验**——根据列类型做格式校验（如 `INTEGER` / `STRING` / `DATE`）

**反例**：

```go
// 错误:直接拼字符串
query += fmt.Sprintf(" AND %s = '%s'", cond.Key, cond.Value)
// 正确:用 prepared statement
query += fmt.Sprintf(" AND %s = ?", allowedColumns[cond.Key])
queryArgs = append(queryArgs, cond.Value)
```

### 12.5 与 Casbin 边界

- Casbin 处理"能不能调用这个 endpoint"（菜单 / 按钮 / API 权限）
- `sys_data_permission` 处理"调用后能看到哪些行"（行级过滤）
- 两者**正交**，互不替代
- 业务请求处理流：先 Casbin 鉴权 → 再读 `sys_data_permission` 生成 SQL WHERE

---

## 13. Casbin rule 表

`casbin_rule` 表由 `casbin/mysql-adapter` v2 **自动管理**，admin 业务代码**不**直接 CRUD。

写 policy：通过 `casbin.Enforcer.AddPolicy(...)` / `RemovePolicy(...)`（应用层封装）
读 policy：通过 `casbin.Enforcer.GetPolicy()` / `Enforce(...)`（鉴权时调用）

`v0..v5` 字段在 casbin_rule 中保持 `DEFAULT NULL`（Casbin policy 可部分匹配）。

---

## 14. 角色层级 (`sys_role.parent_id`)

v4+ 起 `sys_role` 支持父子层级继承（如"销售经理"继承"销售员"权限）。

- `parent_id` 自引用 FK（`ALTER TABLE` 后置）
- 应用层 Casbin 在加载 policy 时需展开父角色权限（递归 `sys_role`）
- `tree_path` 物化路径**不**在 `sys_role` 上提供——角色层级一般不深，按需在应用层递归即可

---

## 15. 菜单物化路径 (`sys_menu.tree_path`)

v4+ 起 `sys_menu` 加 `tree_path VARCHAR(1024)`（物化路径，如 `/1/3/7/`）。

- 应用层在 INSERT / UPDATE 菜单时维护 `tree_path`（父菜单的 `tree_path` + 当前 `id`）
- 查祖先：`WHERE tree_path LIKE '/1/3/%'`
- 查整棵子树：`WHERE tree_path LIKE '/1/%'`
- 比递归 CTE 快，但写时维护是负担——只在大数据量场景使用

---

## 16. 迁移考虑

本任务交付的 `schema.sql` 是**基线 DDL**，**不**包含迁移工具。后续 go-admin / java-admin 上线时需自选迁移工具：

- Go：`golang-migrate` / `goose` / `atlas`
- Java：Flyway / Liquibase

迁移脚本切分原则：

- 顺序：与 `schema.sql` 一致
- 每文件独立可执行
- 命名：`V<version>__<description>.sql`（Flyway 风格）/ `<version>_<description>.up.sql`（golang-migrate 风格）

---

## 17. 字典平台归属（`dict_data.platform` / `tag_type`）

### 17.1 为什么平台在 data 不在 type

- v6 曾在 `dict_type` 加 `platform`，v7 移除：同一字典类型（如开关状态）可跨平台复用
- v8 改在 **`dict_data`** 落 `platform`：同一 `type` 下可有 general 通用项 + 各端专属项
- v10：唯一键 `UNIQUE(type_id, value, platform, deleted_at)`，同类型同 value 可在不同 platform 各有一条活跃行
- 前端按 `VITE_APP_PLATFORM` 过滤：`WHERE platform IN ('general', :current_platform) AND deleted_at = 0`
- 应用层判重：按 `(type_id, value, platform)` 三元组，**不要**只按 `(type_id, value)`

### 17.2 `tag_type` 语义

- `default`：无样式（不染色）
- 其余值：前端映射到 ant Design Tag color / vben Tag color，**不**在库内存色值
- 库只存预设标识；换肤、主题由前端负责

### 17.3 Seed

初始数据见 `backend/db/schema_data.sql`（依赖 `schema.sql` 先建表；对齐 mock）：

**字典**（`utils/mock/dict.ts`）：

- `sys_user_sex`：`0` 男 / `1` 女 / `2` 未知（general）
- `sys_yes_no`：`Y` 是 / `N` 否（general）
- `sys_menu_type`：`DIR` / `MENU` / `BUTTON`（general）
- `sys_notice_type`：`1` 通知 / `2` 公告 / `3` 提醒（general）
- `sys_switch_status`：按 platform 各一组 enabled/disabled，并带对应 `tag_type`
- `sys_default_status`：按 platform 各一组 default / not-default，并带对应 `tag_type`
- `sys_platform`：`general` / `react-admin` / `vue-admin`

**RBAC**（`utils/mock/menu-api.ts` / `user-role.ts`）：

- `sys_api`：对齐 `API_SYNC_MANIFEST`（schema 对 `permission_code` 唯一，冲突项用 path 消歧后缀）
- `sys_menu`：对齐 `buildSysMenuSeeds`（固定 id + `tree_path`）
- `sys_role`：仅 `root`
- `sys_user`：仅 `root`（密码明文 `123456`，BCrypt）
- 关联：`sys_user_role` / `sys_role_menu` / `sys_role_api` / `sys_menu_api`

---

## 18. 访问黑名单 (`sys_blacklist`，v11+)

### 18.1 多态 target

| `target_type` | `target_value` 存法                                      | 匹配                        |
| ------------- | -------------------------------------------------------- | --------------------------- |
| `IP`          | 规范化 IP 文本（建议应用层统一 IPv6 小写、去端口）；≤128 | **精确等值**（本波无 CIDR） |
| `USER`        | `sys_user.id` 的十进制字符串（软引用，不建 FK）          | 等值                        |
| `DEVICE`      | 客户端上报 deviceId **原样**（utf8mb4 比较，大小写敏感） | 等值                        |

应用层校验 `target_type` 合法性；`USER` 时校验用户存在性（可选，封禁可保留已删用户 id）。

### 18.2 scope

| 值      | 含义             |
| ------- | ---------------- |
| `LOGIN` | 仅限制登录       |
| `API`   | 仅限制已认证 API |
| `ALL`   | 全部（默认）     |

请求侧命中条件：`scope IN (:request_scope, 'ALL')`。  
例：登录检查传入 `LOGIN`，则 `LOGIN` 与 `ALL` 行均可命中。

### 18.3 生效判定

```sql
SELECT 1 FROM sys_blacklist
WHERE target_type = ?
  AND target_value = ?
  AND scope IN (?, 'ALL')   -- 第二参为 LOGIN 或 API
  AND deleted_at = 0
  AND is_enabled = 1
  AND starts_at <= CURRENT_TIMESTAMP
  AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
LIMIT 1;
```

- `starts_at`：`NOT NULL DEFAULT CURRENT_TIMESTAMP`（含边界）
- `expires_at`：`NULL` = 永不过期；非 NULL 时为**不含**边界（`>` 比较）
- 多条命中：**OR**（任一命中即限制）
- `reason`：对用户/审计可见原因（可 `''`）；`remark`：内部备注

### 18.4 弱唯一与时间窗

```sql
UNIQUE (target_type, target_value, scope, starts_at, expires_at, deleted_at)
```

- **允许**同一 target 多条活跃行（不同时间窗 / 不同 scope）
- **禁止**完全相同的 `(target, scope, starts_at, expires_at)` 活跃重复行
- **不**在 DB 层检测区间重叠；重叠时命中 OR，或由管理端创建前查询提醒
- **MySQL 注意**：`expires_at` 为 `NULL` 时，UNIQUE 视 NULL 互不相等，多条「永久」且同 `starts_at` 的活跃行可能并存——应用层对永久封应先查再插，或保证 `starts_at` 唯一

### 18.5 与用户禁用 / Casbin 边界

- `sys_user.is_enabled=0`：账号禁用（用户实体生命周期）
- `sys_blacklist`：按 IP/USER/DEVICE 的访问限制（可临时、可多维）
- Casbin：角色能否调用 endpoint
- 三者正交；运行时拦截顺序建议：黑名单 → 鉴权 → 数据权限（实现波次另定）

### 18.6 本波边界

- 已交付：`schema.sql` + 本约定 + `tables.md` / `er.md` + Flyway `V1` 表 + `V2` 菜单/API seed + 管理端 CRUD
- 已交付（java-admin）：运行时 Filter（LOGIN IP / API IP+session USER）+ 登录链路 USER 拦截（Access Blocked）；DEVICE 运行时仍不查

---

## 19. 不在本任务范围

- ORM model 代码（Go struct / Java entity）
- 迁移工具集成（仅交付独立 .sql；`schema.sql` + `schema_data.sql` 可独立执行）
- TTL 归档作业实现
- Casbin policy 文件（`model.conf` / `policy.csv`）
- 全量业务 Seed 之外的数据（日志 / i18n 翻译文案 / Temporal 任务配置等；`schema_data.sql` 已含字典 + RBAC 最小可登录种子）
- 数据库 Docker 编排
- `sys_blacklist` 运行时拦截（见 §18.6；管理 CRUD 与 Flyway 由 java-admin 交付）

以上均**不**在 `backend/db/` 内以 ORM / 迁移框架形式交付。
