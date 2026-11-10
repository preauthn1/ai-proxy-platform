# ER 关系与基数 (v11)

> 本文件是 `backend/db/schema.sql` 的**关系总览**。本文件**不**解释字段——字段速查见 `tables.md`；本文件**不**解释为什么这样设计——设计动机见 `db-conventions.md`。

---

## 1. 总览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│                              RBAC 核心                                       │
│                                                                             │
│   ┌──────────┐  N      M  ┌──────────────┐                                  │
│   │ sys_user │◄──────────►│  sys_role    │◄──┐                              │
│   │          │  via       │  (parent_id) │   │ 自引用 (v4+)                │
│   │          │  sys_user_ │              │───┘                              │
│   │          │  role      │              │                                  │
│   └──────────┘           └──────┬───────┘                                  │
│                                  │                                          │
│              ┌───────────────────┼──────────────────────┐                   │
│              │ N                 │ N                    │ N                │
│              │                   │                      │                  │
│       ┌──────┴───────┐   ┌───────┴──────┐   ┌────────────┴────────┐         │
│       │ sys_role_api │   │sys_role_menu │   │ sys_data_permission │         │
│       │   (M:N 角色  │   │  (M:N 角色   │   │  (ABAC: subject_type │         │
│       │    ↔ API)    │   │   ↔ 菜单)    │   │   = ROLE 关联角色)   │         │
│       └──────┬───────┘   └───────┬──────┘   └─────────────────────┘         │
│              │ N                 │ N                                       │
│              ▼                   ▼                                         │
│       ┌──────────────┐   ┌──────────────┐                                  │
│       │  sys_api     │   │  sys_menu    │◄──┐  self-ref parent_id            │
│       │ method+path  │   │ tree_path    │   │                                │
│       │ permission   │   │ metadata     │───┘                                │
│       └──────┬───────┘   └──────┬───────┘                                    │
│              │ N                │ M                                        │
│              └────────┬─────────┘                                          │
│                       │                                                    │
│                ┌──────┴───────┐                                            │
│                │ sys_menu_api │  (M:N 菜单↔API, 快捷绑定, 非授权)          │
│                └──────────────┘                                            │
│                                                                             │
│                       访问黑名单 (v11)                                        │
│                                                                             │
│   ┌────────────────┐  soft ref when target_type=USER                        │
│   │ sys_blacklist  │ ··············► sys_user.id (target_value 字符串)     │
│   │ IP/USER/DEVICE │  IP/DEVICE 无 FK；scope=LOGIN/API/ALL                  │
│   └────────────────┘                                                        │
│                                                                             │
│                          I18n                                               │
│                                                                             │
│   ┌──────────────┐  1     N  ┌─────────────────────┐                       │
│   │ i18n_locale  │──────────►│ i18n_translation    │                       │
│   └──────────────┘           └─────────────────────┘                       │
│                                                                             │
│                          字典                                                │
│                                                                             │
│   ┌──────────────┐  1     N  ┌─────────────────────┐                       │
│   │  dict_type   │──────────►│  dict_data          │                       │
│   │  (无 platform)│           │  platform/tag_type  │  (v8/v9 项级平台与样式) │
│   └──────────────┘           └─────────────────────┘                       │
│                                                                             │
│                       Temporal 任务                                         │
│                                                                             │
│   ┌────────────────────┐  1     N  ┌──────────────────────────┐             │
│   │temporal_task_config│──────────►│ temporal_task_execution  │             │
│   └────────────────────┘ (软外键)  └──────────────────────────┘             │
│                                                                             │
│                       Casbin（外部管理）                                     │
│                                                                             │
│   ┌────────────────────┐                                                  │
│   │   casbin_rule      │  ← casbin/mysql-adapter 自动管理                   │
│   └────────────────────┘                                                  │
│                                                                             │
│                       日志（记录型）                                         │
│                                                                             │
│   ┌──────────────┐       ┌──────────────┐       ┌──────────────┐          │
│   │  api_log     │       │sys_login_log │       │operation_log │          │
│   │ request_id   │       │  username    │       │  request_id  │          │
│   │ UNIQUE       │       │  success     │       │  source      │          │
│   └──────────────┘       └──────────────┘       └──────────────┘          │
│         │                       │                       │                  │
│         │ request_id 关联(可选)│                       │                  │
│         └───────────────────────┴───────────────────────┘                  │
│                                                                             │
│   ┌────────────────┐ ┌────────────────────┐ ┌────────────────┐            │
│   │ api_log_archive│ │sys_login_log_archive│ │operation_log_  │            │
│   │                │ │                    │ │    archive    │            │
│   └────────────────┘ └────────────────────┘ └────────────────┘            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 基数汇总

### 2.1 M:N（多对多）

| 关系                    | 通过            | 备注                                                |
| ----------------------- | --------------- | --------------------------------------------------- |
| `sys_user` ↔ `sys_role` | `sys_user_role` | 复合 PK `(user_id, role_id)`                        |
| `sys_role` ↔ `sys_api`  | `sys_role_api`  | 复合 PK `(role_id, api_id)`；授权                   |
| `sys_role` ↔ `sys_menu` | `sys_role_menu` | 复合 PK `(role_id, menu_id)`；授权                  |
| `sys_menu` ↔ `sys_api`  | `sys_menu_api`  | 复合 PK `(menu_id, api_id)`；**非授权**，结构化绑定 |

### 2.2 1:N（一对多）

| 父                     | 子                        | 外键                                      | 备注                    |
| ---------------------- | ------------------------- | ----------------------------------------- | ----------------------- |
| `sys_menu`             | `sys_menu`                | `parent_id`                               | 自引用树形（v1+）       |
| `sys_role`             | `sys_role`                | `parent_id`                               | 自引用角色层级（v4+）   |
| `i18n_locale`          | `i18n_translation`        | `locale_id`                               | FK                      |
| `dict_type`            | `dict_data`               | `type_id`                                 | FK                      |
| `temporal_task_config` | `temporal_task_execution` | `config_id`                               | **软外键**（不建 FK）   |
| `sys_role`             | `sys_data_permission`     | `subject_id`（当 `subject_type='ROLE'`）  | **软关联**（多态主体）  |
| `sys_user`             | `sys_data_permission`     | `subject_id`（当 `subject_type='USER'`）  | **软关联**              |
| `sys_user`             | `sys_blacklist`           | `target_value`（当 `target_type='USER'`） | **软关联**（字符串 id） |

### 2.3 1:1 / 0..1

**无**——所有实体通过 PK 关联，无 1:1 设计

---

## 3. 软引用清单（不建 FK）

| 字段                        | 表                          | 类型                 | 软引用目标                    | 原因                                                          |
| --------------------------- | --------------------------- | -------------------- | ----------------------------- | ------------------------------------------------------------- |
| `created_by` / `updated_by` | 所有 11 张核心表            | `NOT NULL DEFAULT 0` | `sys_user.id`（0=系统操作）   | 用户删除时不应级联清空历史                                    |
| `language_code`             | `sys_user`                  | `NULL`               | `i18n_locale.code`            | i18n_locale.code 软引用                                       |
| `subject_id`                | `sys_data_permission`       | `NOT NULL DEFAULT 0` | `sys_user.id` / `sys_role.id` | 多态主体（`ANY_*` 时为 0）                                    |
| `target_value`              | `sys_blacklist`             | `VARCHAR(128)`       | `sys_user.id`（仅 `USER`）    | 多态 target；IP/DEVICE 无实体引用（v11+）                     |
| `config_id`                 | `temporal_task_execution`   | `NULL`               | `temporal_task_config.id`     | 执行可能先于配置存在                                          |
| `sys_user_id`               | `api_log` / `sys_login_log` | `NULL`               | `sys_user.id`                 | 日志保留用户删除前痕迹（v5+；`operation_log` 仍为 `user_id`） |

---

## 4. 关联表（无软删、无 `updated_by`）

| 表              | PK                   | 备注                                                  |
| --------------- | -------------------- | ----------------------------------------------------- |
| `sys_user_role` | `(user_id, role_id)` | 仅 `created_at`                                       |
| `sys_role_api`  | `(role_id, api_id)`  | 仅 `created_at`                                       |
| `sys_role_menu` | `(role_id, menu_id)` | 仅 `created_at`                                       |
| `sys_menu_api`  | `(menu_id, api_id)`  | `created_at` + `created_by`（结构化绑定，可能要追责） |

**"解绑"语义**：直接 `DELETE` 关联行；想留痕请走 `operation_log`（AOP 会自动写 DELETE 操作）

---

## 5. RBAC + ABAC 双层数据流

```
┌─────────────────────────────────────────────────────────────┐
│                       请求处理流                              │
│                                                             │
│  1. 鉴权: Casbin.Enforce(user, "/api/orders", "read")       │
│     ├── YES → 继续                                          │
│     └── NO  → 403 Forbidden                                │
│                                                             │
│  2. 加载当前用户的 sys_data_permission:                      │
│     - subject_type='USER' + subject_id=user.id              │
│     - subject_type='ROLE' + subject_id IN (user.role_ids)   │
│     - subject_type='ANY_USER' + subject_id=0                │
│     - subject_type='ANY_ROLE' + subject_id=0                │
│     WHERE resource_table='orders'                           │
│       AND is_enabled=1                                     │
│       AND deleted_at=0                                     │
│                                                             │
│  3. 多条取 priority 最高的胜出（按 deleted_at=0 唯一定位）  │
│     scope_type:                                             │
│     - all    → 不加 WHERE                                   │
│     - none   → WHERE 1=0                                    │
│     - include → WHERE scope_field IN scope_values           │
│     - exclude → WHERE scope_field NOT IN scope_values       │
│     - custom → 解析 conditions 应用层自定义 SQL             │
│                                                             │
│  4. 执行业务查询 + 注入的 WHERE                              │
│     SELECT * FROM orders WHERE <injected_filter> ...        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**关键不变量**：

- Casbin 与 `sys_data_permission` **正交**：前者决定"能否调用"，后者决定"看到哪些行"
- 同 `(subject_type, subject_id, resource_table, action_key, deleted_at=0)` 至多一条活跃行（`UNIQUE(col, deleted_at)` 软删感知）
- 多角色用户：各角色的 permission 独立查，按 `priority` 取胜

---

## 6. 日志三表关联

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│   api_log (1 行)  ───request_id───  operation_log (0..N 行) │
│       │                                │                     │
│       │                                │ request_id          │
│       │                                │ (可选关联)            │
│       │                                │                     │
│       └──────────────┬─────────────────┘                     │
│                      │                                       │
│              一次 HTTP 请求                                  │
│              触发 1 个 api_log + N 个 operation_log          │
│                                                              │
│   sys_login_log: 独立表(不通过 request_id 关联;按 username/user_id)
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

应用层用法：

- JOIN 一次请求的完整审计轨迹：`api_log` JOIN `operation_log` ON `request_id`
- 登录审计：`sys_login_log` 单独查（按 username / user_id / 时间窗口）

---

## 7. Temporal 边界

```
┌──────────────────────────────────┐    ┌──────────────────────────────────┐
│  admin 业务 schema               │    │  Temporal Server schema         │
│                                  │    │  (数据库 temporal)               │
│  ┌─────────────────────────────┐ │    │                                  │
│  │ temporal_task_config        │ │    │  Temporal Server 自管:           │
│  │  - workflow_type            │ │    │  - executions                   │
│  │  - task_queue               │ │    │  - task_queues                  │
│  │  - cron_expr                │ │    │  - history_*                    │
│  │  - retry_policy             │ │    │  - activity_info_maps           │
│  │  - is_enabled               │ │    │  - timer_info_maps              │
│  │  - deleted_at               │ │    │  - ...                          │
│  │                             │ │    │                                  │
│  │  由 admin 后端 CRUD         │ │    │  由 temporal-sql-tool 管理       │
│  └──────────────┬──────────────┘ │    │                                  │
│                 │ 软外键          │    │                                  │
│                 │ (config_id)     │    │                                  │
│                 ▼                │    │                                  │
│  ┌─────────────────────────────┐ │    │                                  │
│  │ temporal_task_execution     │ │    │                                  │
│  │  - workflow_id              │ │    │                                  │
│  │  - run_id                   │ │    │                                  │
│  │  - status                   │ │    │                                  │
│  │  - pending_at / started_at  │ │    │                                  │
│  │  - closed_at                │ │    │                                  │
│  │  - input/result summary     │ │    │                                  │
│  │  - 仅 created_at（记录型）  │ │    │                                  │
│  │                             │ │    │                                  │
│  │  由 admin 后端异步镜像       │◄┼────┼─ Temporal SDK Listen           │
│  │  (只存摘要)                  │ │    │  事件流 → 写入镜像             │
│  └─────────────────────────────┘ │    │                                  │
│                                  │    │                                  │
│  业务代码查全量状态走 Temporal SDK │    │                                  │
│  业务代码查摘要走 temporal_task_execution │                  │
└──────────────────────────────────┘    └──────────────────────────────────┘
```

**关键不变量**：

- admin **不**直接读写 Temporal 内部表
- admin 通过 Temporal SDK（Go: `go.temporal.io/sdk/client`；Java: `io.temporal.sdk`）调 Temporal Server
- admin 后端通过系统 Schedule `sys-execution-mirror`（约 3s / overlap=SKIP）跑 `ExecutionMirrorTickWorkflow`，
  用 Temporal SDK **list/describe** 将执行状态**异步**镜像到 `temporal_task_execution`
- 手动触发：start 业务 WF 后立即插入 RUNNING 种子行；Schedule 触发靠 Visibility 发现 upsert
- 镜像有最终一致性窗口（秒级）；admin UI 如需实时状态，可另调 Temporal SDK 直查

---

## 8. 物化路径：`sys_menu.tree_path`

v4+ 起 `sys_menu` 增加 `tree_path VARCHAR(1024)` 字段，存全路径字符串（如 `/1/3/7/`）。

应用层责任：

- INSERT 菜单时计算 `tree_path` = `parent.tree_path + '/' + new_id + '/'`
- UPDATE 移动菜单时同步更新所有后代
- 删除菜单时检查是否有后代

查询优化：

- 查祖先：`WHERE tree_path LIKE '/1/3/%'`
- 查子树：`WHERE tree_path LIKE '/1/%'`
- 比递归 CTE 快，但写时维护是负担——只在大数据量场景使用

---

## 9. 一致性窗口

| 关系                                             | 一致性   | 备注                                        |
| ------------------------------------------------ | -------- | ------------------------------------------- |
| `sys_user_role` 等关联表                         | 强一致   | 事务内                                      |
| `sys_data_permission`                            | 强一致   | 同上                                        |
| `sys_blacklist`                                  | 强一致   | 管理端 CRUD；运行时读（实现波次另定）       |
| `temporal_task_execution` ↔ Temporal Server      | 最终一致 | 异步镜像                                    |
| `casbin_rule` ↔ `sys_role_api` / `sys_role_menu` | 弱一致   | 两套独立写，admin 写 role 时应同步刷 casbin |
| 日志 `_archive` ↔ 热表                           | 最终一致 | 每日 TTL 作业                               |
| `sys_menu.tree_path` ↔ `parent_id`               | 强一致   | 应用层在事务内维护                          |
| `sys_role` 父子层级 ↔ Casbin policy              | 弱一致   | 加载 policy 时应用层展开父角色              |

---

## 10. 软删时间戳 `deleted_at` 模式

所有核心表（11 张，含 v11 `sys_blacklist`）通过 `deleted_at BIGINT UNSIGNED NOT NULL DEFAULT 0` 实现软删：

- `0` = 活跃行
- `> 0` = 软删时刻（毫秒 Unix 时间戳）

应用层语义：

- 软删：`UPDATE ... SET deleted_at = UNIX_TIMESTAMP() * 1000`
- 查询：`WHERE deleted_at = 0`
- 复活：`UPDATE ... SET deleted_at = 0`（仅限授权场景）
- 重建同名记录：直接 `INSERT` 即可，无需硬删旧行

**软删感知唯一**：10 张表的 UNIQUE 全部把 `deleted_at` 纳入键：

- `UNIQUE(col, deleted_at)` —— 0 与非 0 视为不同值，活跃行与软删行可共存

详细语义见 `db-conventions.md §5`。
