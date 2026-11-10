-- Flyway V1: 全量 schema（原 backend/db/schema.sql；java-admin 内单源，dev/prod 共用）

-- ============================================================
-- 后台管理系统 MySQL Schema  (v5)
-- 文件:       backend/db/schema.sql
-- 数据库:     <admin_db> （由各 admin 后端自行创建与配置）
-- 字符集:     utf8mb4 / utf8mb4_unicode_ci
-- 引擎:       InnoDB
-- 版本要求:   MySQL 5.7.8+ 及兼容发行版（prod 不用 utf8mb4_0900_ai_ci；该 collation 仅官方 MySQL 8.0+）
-- 表数:       23 张
--             核心 14（含 sys_data_permission / sys_blacklist）
--             关联 4（sys_user_role / sys_role_api / sys_role_menu / sys_menu_api）
--             记录 4（3 张日志 + temporal_task_execution）
--             归档 3（api_log_archive / sys_login_log_archive / operation_log_archive）
--             casbin 1（casbin_rule）
-- 执行顺序:   按依赖顺序；FK 引用先建；自引用外键用 ALTER 后置
-- 部署:       本文件可独立执行；如使用迁移工具，按本文件顺序切分版本脚本
-- 边界:
--   1. admin 业务 schema 与 Temporal server 的 temporal schema 隔离
--   2. casbin_rule 由 casbin/mysql-adapter 自动管理，业务代码不直接 CRUD
--   3. 归档表与对应热表同结构，由 admin 后端 TTL 作业搬运
--   4. 软删: deleted_at BIGINT UNSIGNED (毫秒时间戳; 0=未删;非0=删除时刻)
--   5. 软删感知唯一: UNIQUE(col, deleted_at) — 0 与非0 视作不同值,天然支持"软删后重建"
--   6. 关联表（4 张）保留复合 PK + 无软删（解绑 = DELETE 物理删除）
-- NULL 策略（v5+）:
--   - NOT NULL + DEFAULT '' : VARCHAR/CHAR 业务字段
--   - TEXT/MEDIUMTEXT：MySQL 8.x 禁止 DEFAULT，用 NULL 可空（应用层写空串）
--   - NULL                  : TIMESTAMP（最后登录/关闭时间等真实未发生）
--   - NULL                  : JSON（metadata / input_summary / 条件快照等按需填）
--   - NOT NULL + DEFAULT 0  : BIGINT UNSIGNED 主外键占位（created_by / updated_by;0=系统操作/无用户上下文）
--   - NULL                  : BIGINT UNSIGNED 真软外键（language_code / parent_id / config_id / target_id / sys_user_id-in-logs / subject_id）
--   - NULL                  : 业务语义 NULL（cron_expr=仅手动;reason=未失败;closed_at=仍在运行;path=BUTTON/DIR 无路径）
-- v5 相对 v4 的改动:
--   1. sys_user: 移除 dept_id 及其索引(原为 DEPT 类数据权限锚点;现交由 sys_data_permission 承担)
--   2. 4 张表: 移除 description 字段(只保留 remark 统一语义):
--        - sys_role, sys_api, dict_type, temporal_task_config
--   3. api_log: 字段扩充对齐 PG 风格(参考 sys_api_log):
--        - 新增: referer / request_uri / request_header / status_code / reason / success / location
--                / browser_name / browser_version / os_name / os_version / client_id / client_name
--                / sys_user_id / cost_time / format_change / before_change / after_change
--        - 改名: request_method→method, request_path→path, response_body→response
--                , duration_ms→cost_time, user_id→sys_user_id, response_status→status_code
--        - 放宽: request_id 64→128, module 64→255, client_ip 45→64, user_agent VARCHAR(512)→TEXT
--   4. sys_login_log: 字段扩充对齐 PG 风格(参考 PG sys_login_log):
--        - 新增: login_mac / login_time / status_code / location
--                / browser_name / browser_version / os_name / os_version / client_id / client_name
--                / reason / sys_user_id
--        - 改名: client_ip→login_ip, failure_reason→reason, user_id→sys_user_id
--        - 移除: device / os / browser / country / province / city
--                (由 os_name/version + browser_name/version + location 替代)
--        - 放宽: user_agent VARCHAR(512)→TEXT
--   5. api_log_archive / sys_login_log_archive: 同步与热表同结构
-- v8 (仅 dict_data):
--   1. dict_data: 重新加 platform VARCHAR(32) NOT NULL DEFAULT 'general'(字典项归属平台;
--        与前端的 VITE_APP_PLATFORM 配合做"前端只看自己+通用"过滤;enum={general,react-admin,vue-admin})
--   2. dict_data: 加 idx_dict_data_platform 索引
--   注: dict_type 保持 v7(无 platform);v6→v7→v8 形成"加 → 删 → 加"的明确取舍记录
-- v9 (仅 dict_data):
--   1. dict_data: 加 tag_type VARCHAR(32) NOT NULL DEFAULT 'default'(预设样式标识;
--        default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color;
--        enum={default,primary,success,warning,error,processing,magenta,red,
--              volcano,orange,gold,lime,green,cyan,blue,geekblue,purple})
-- v10 (仅 dict_data):
--   1. dict_data: UNIQUE 由 (type_id, value, deleted_at) 改为
--        (type_id, value, platform, deleted_at)
--        — 支持同类型同 value 在不同 platform 各有一条活跃行(配合 v8 平台过滤)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';


-- ============================================================
-- Section 1: casbin_rule
-- 与 casbin/mysql-adapter v2 完全兼容;admin 业务代码不直接读写
-- ============================================================
CREATE TABLE casbin_rule (
                             id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             ptype VARCHAR(255)    NOT NULL,
                             v0    VARCHAR(255)    DEFAULT NULL,
                             v1    VARCHAR(255)    DEFAULT NULL,
                             v2    VARCHAR(255)    DEFAULT NULL,
                             v3    VARCHAR(255)    DEFAULT NULL,
                             v4    VARCHAR(255)    DEFAULT NULL,
                             v5    VARCHAR(255)    DEFAULT NULL,
                             PRIMARY KEY (id),
                             INDEX idx_casbin_rule_ptype_v0_v1 (ptype, v0, v1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Casbin policy 存储（casbin/mysql-adapter v2 标准表）';


-- ============================================================
-- Section 2: RBAC 核心 — sys_user
-- v2: 去 status(改 is_enabled);加 language_code;加 remark;UNIQUE 软删感知
-- v5: 移除 dept_id(原为 DEPT 类数据权限锚点;现由 sys_data_permission 承担,详见 v4 NULL 策略注释)
-- ============================================================
CREATE TABLE sys_user (
                          id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          username        VARCHAR(64)     NOT NULL  COMMENT '登录名',
                          password_hash   VARCHAR(128)    NOT NULL  COMMENT '密码哈希(bcrypt/argon2 输出)',
                          nickname        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '展示名',
                          email           VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '邮箱',
                          phone           VARCHAR(32)     NOT NULL DEFAULT ''  COMMENT '手机号',
                          avatar          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '头像 URL',
                          language_code   VARCHAR(16)     DEFAULT NULL  COMMENT '用户默认语言(软外键 → i18n_locale.code)',
                          last_login_at   TIMESTAMP       NULL DEFAULT NULL  COMMENT '最近登录时间',
                          last_login_ip   VARCHAR(45)     NOT NULL DEFAULT ''  COMMENT '最近登录 IP(IPv6 兼容)',
                          remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
                          is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用(独立于 deleted_at;三态:已删/禁用/正常)',
                          deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻(应用层写 UNIX_TIMESTAMP()*1000)',
                          created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                          updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                          PRIMARY KEY (id),
                          UNIQUE KEY uniq_sys_user_username (username, deleted_at),
                          INDEX idx_sys_user_is_enabled (is_enabled),
                          INDEX idx_sys_user_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';


-- ============================================================
-- Section 3: RBAC 核心 — sys_role
-- v2: 加 parent_id(自引用,角色层级);加 is_enabled/remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE sys_role (
                          id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          code            VARCHAR(32)     NOT NULL  COMMENT '角色编码(如 admin/user/viewer)',
                          name            VARCHAR(64)     NOT NULL  COMMENT '角色名(展示用)',
                          parent_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '父角色 ID(自引用;支持角色层级继承)',
                          sort            INT             NOT NULL DEFAULT 0  COMMENT '排序',
                          remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
                          is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                          deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                          created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                          updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                          PRIMARY KEY (id),
                          UNIQUE KEY uniq_sys_role_code (code, deleted_at),
                          INDEX idx_sys_role_parent_id (parent_id),
                          INDEX idx_sys_role_is_enabled (is_enabled),
                          INDEX idx_sys_role_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色表(支持父子层级继承)';


-- ============================================================
-- Section 4: API 管理 — sys_api
-- v2: 加 remark;UNIQUE 软删感知(method+path 与 permission_code)
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE sys_api (
                         id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                         name            VARCHAR(64)     NOT NULL  COMMENT '接口名(展示用)',
                         method          VARCHAR(8)      NOT NULL  COMMENT 'HTTP method: GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD',
                         path            VARCHAR(255)    NOT NULL  COMMENT '接口路径(支持 :id 占位,不含 host)',
                         permission_code VARCHAR(128)    NOT NULL  COMMENT '权限码(与按钮权限码同构,后端 Casbin 鉴权 + 前端按钮控制)',
                         api_group       VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '分组(便于管理后台分组展示)',
                         remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
                         is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                         deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                         created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                         created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                         updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                         PRIMARY KEY (id),
                         UNIQUE KEY uniq_sys_api_method_path (method, path, deleted_at),
                         UNIQUE KEY uniq_sys_api_permission_code (permission_code, deleted_at),
                         INDEX idx_sys_api_group (api_group),
                         INDEX idx_sys_api_is_enabled (is_enabled),
                         INDEX idx_sys_api_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API/接口管理(HTTP 路由 + 权限码)';


-- ============================================================
-- Section 5: 菜单管理 — sys_menu
-- v2: 加 tree_path(物化路径);加 metadata(前端扩展字段);加 remark
-- ============================================================
CREATE TABLE sys_menu (
                          id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                          parent_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '父菜单 ID(自引用,NULL=根)',
                          name            VARCHAR(64)     NOT NULL  COMMENT '菜单名(展示用)',
                          type            VARCHAR(16)     NOT NULL  COMMENT '类型: DIR=目录 / MENU=菜单/路由 / BUTTON=按钮',
                          path            VARCHAR(255)    DEFAULT NULL  COMMENT '路由路径(仅 MENU 类型;BUTTON/DIR 不需要)',
                          component       VARCHAR(255)    DEFAULT NULL  COMMENT '前端组件路径(仅 MENU 类型)',
                          icon            VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '图标(前端展示)',
                          redirect        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '路由重定向(仅 MENU 类型;vue-vben-admin 习惯)',
                          permission_code VARCHAR(128)    DEFAULT NULL  COMMENT '权限码;BUTTON 类型必填,MENU/DIR 可空(NULL=无需权限码)',
                          tree_path       VARCHAR(1024)   DEFAULT NULL  COMMENT '物化路径(如 /1/3/7/),便于查祖先/子树(应用层在 INSERT 时维护)',
                          metadata        JSON            DEFAULT NULL  COMMENT '前端扩展字段(badge / hideInBreadcrumb / keepAlive 等)',
                          sort            INT             NOT NULL DEFAULT 0  COMMENT '同级排序',
                          is_hidden       TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否隐藏(仅前端控制)',
                          is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                          deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                          remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员备注',
                          created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                          updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                          PRIMARY KEY (id),
                          INDEX idx_sys_menu_parent_id (parent_id),
    -- utf8mb4 下 InnoDB 索引最长 3072 字节；VARCHAR(1024) 全列=4096，须前缀索引（768*4=3072）
                          INDEX idx_sys_menu_tree_path (tree_path(768)),
                          INDEX idx_sys_menu_permission_code (permission_code),
                          INDEX idx_sys_menu_type (type),
                          INDEX idx_sys_menu_is_enabled (is_enabled),
                          INDEX idx_sys_menu_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='菜单表(树形 + 物化路径 + 按钮级权限码)';


-- ============================================================
-- Section 6: I18n — i18n_locale
-- v2: 加 remark;UNIQUE 软删感知
-- ============================================================
CREATE TABLE i18n_locale (
                             id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                             code            VARCHAR(16)     NOT NULL  COMMENT '语言/区域代码(如 zh-CN / en-US)',
                             name            VARCHAR(64)     NOT NULL  COMMENT '展示名(如 简体中文 / English)',
                             is_default      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否默认语言(应用层保证最多一条)',
                             sort            INT             NOT NULL DEFAULT 0,
                             remark          VARCHAR(512)    NOT NULL DEFAULT '',
                             is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                             deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                             created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                             updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                             PRIMARY KEY (id),
                             UNIQUE KEY uniq_i18n_locale_code (code, deleted_at),
                             INDEX idx_i18n_locale_is_enabled (is_enabled),
                             INDEX idx_i18n_locale_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='I18n 语言/区域';


-- ============================================================
-- Section 7: 字典 — dict_type
-- v2: 加 remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- v6: 加 platform(字典类型归属平台)
-- v7: 移除 platform(字典域回归无平台归属)
-- ============================================================
CREATE TABLE dict_type (
                           id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                           code            VARCHAR(64)     NOT NULL  COMMENT '字典类型编码(如 user_status)',
                           name            VARCHAR(64)     NOT NULL  COMMENT '字典类型名(展示用)',
                           remark          VARCHAR(512)    NOT NULL DEFAULT '',
                           is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                           deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                           created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                           updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                           PRIMARY KEY (id),
                           UNIQUE KEY uniq_dict_type_code (code, deleted_at),
                           INDEX idx_dict_type_is_enabled (is_enabled),
                           INDEX idx_dict_type_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='字典类型';


-- ============================================================
-- Section 8: 任务调度 — temporal_task_config
-- v2: 加 remark;UNIQUE 软删感知
-- v5: 移除 description(只保留 remark 统一语义)
-- ============================================================
CREATE TABLE temporal_task_config (
                                      id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                      code            VARCHAR(64)     NOT NULL  COMMENT '任务编码(如 report_daily)',
                                      name            VARCHAR(128)    NOT NULL  COMMENT '任务名(展示用)',
                                      workflow_type   VARCHAR(128)    NOT NULL  COMMENT 'Temporal workflow 类名',
                                      task_queue      VARCHAR(128)    NOT NULL  COMMENT 'Temporal task queue',
                                      cron_expr       VARCHAR(64)     DEFAULT NULL  COMMENT 'cron 表达式(NULL=仅手动触发)',
                                      retry_policy    JSON            DEFAULT NULL  COMMENT '重试策略 JSON(最大尝试/初始间隔/退避系数等)',
                                      timeout_seconds INT UNSIGNED    DEFAULT NULL  COMMENT '超时(秒)',
                                      remark          VARCHAR(512)    NOT NULL DEFAULT '',
                                      is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                                      deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                                      created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                                      updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                                      PRIMARY KEY (id),
                                      UNIQUE KEY uniq_temporal_task_config_code (code, deleted_at),
                                      INDEX idx_temporal_task_config_is_enabled (is_enabled),
                                      INDEX idx_temporal_task_config_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Temporal 任务调度配置(workflow/activity 注册)';


-- ============================================================
-- Section 9: I18n 翻译 — i18n_translation (FK → i18n_locale)
-- v2: 加 remark;UNIQUE 软删感知
-- ============================================================
CREATE TABLE i18n_translation (
                                  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                  locale_id       BIGINT UNSIGNED NOT NULL  COMMENT '所属语言',
                                  translation_key VARCHAR(255)    NOT NULL  COMMENT '翻译键(如 menu.user.create)',
                                  value           TEXT            NOT NULL  COMMENT '翻译值',
                                  remark          VARCHAR(512)    NOT NULL DEFAULT '',
                                  is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                                  deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                                  created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                  created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                                  updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uniq_i18n_translation_locale_key (locale_id, translation_key, deleted_at),
                                  INDEX idx_i18n_translation_key (translation_key),
                                  INDEX idx_i18n_translation_deleted_at (deleted_at),
                                  CONSTRAINT fk_i18n_translation_locale_id FOREIGN KEY (locale_id) REFERENCES i18n_locale (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='I18n 翻译(UI 字符串键值对)';


-- ============================================================
-- Section 10: 字典数据 — dict_data (FK → dict_type)
-- v2: 加 remark;UNIQUE 软删感知
-- v7: 跟随 dict_type 移除 platform 注释
-- v8: 重新加 platform(字典项归属平台;general = 跨平台通用)
-- v9: 加 tag_type(预设样式标识;default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color)
-- v10: UNIQUE 纳入 platform — 同 (type_id, value) 可在不同 platform 各有一条活跃行
-- ============================================================
CREATE TABLE dict_data (
                           id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                           type_id         BIGINT UNSIGNED NOT NULL  COMMENT '所属字典类型',
                           value           VARCHAR(64)     NOT NULL  COMMENT '字典值',
                           label           VARCHAR(128)    NOT NULL  COMMENT '字典标签(展示用)',
                           sort            INT             NOT NULL DEFAULT 0  COMMENT '同类型内排序',
                           is_default      TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '是否该类型的默认值',
                           platform        VARCHAR(32)     NOT NULL DEFAULT 'general'  COMMENT '归属平台(general=通用 / react-admin / vue-admin)',
                           tag_type        VARCHAR(32)     NOT NULL DEFAULT 'default'  COMMENT '预设样式标识(default=无样式;前端按标识映射 ant Tag 颜色 / vben Tag color;enum={default,primary,success,warning,error,processing,magenta,red,volcano,orange,gold,lime,green,cyan,blue,geekblue,purple})',
                           is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                           deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                           remark          VARCHAR(512)    NOT NULL DEFAULT '',
                           created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                           updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                           PRIMARY KEY (id),
                           UNIQUE KEY uniq_dict_data_type_value_platform (type_id, value, platform, deleted_at),
                           INDEX idx_dict_data_type_sort (type_id, sort),
                           INDEX idx_dict_data_platform (platform),
                           INDEX idx_dict_data_is_enabled (is_enabled),
                           INDEX idx_dict_data_deleted_at (deleted_at),
                           CONSTRAINT fk_dict_data_type_id FOREIGN KEY (type_id) REFERENCES dict_type (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='字典数据项';


-- ============================================================
-- Section 11: RBAC 关联 — sys_user_role (v2 不变)
-- ============================================================
CREATE TABLE sys_user_role (
                               user_id         BIGINT UNSIGNED NOT NULL,
                               role_id         BIGINT UNSIGNED NOT NULL,
                               created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (user_id, role_id),
                               INDEX idx_sys_user_role_role_id (role_id),
                               CONSTRAINT fk_sys_user_role_user_id FOREIGN KEY (user_id) REFERENCES sys_user (id),
                               CONSTRAINT fk_sys_user_role_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户-角色关联(无软删,无 updated_at/updated_by)';


-- ============================================================
-- Section 12: RBAC 关联 — sys_role_api (v2 不变)
-- ============================================================
CREATE TABLE sys_role_api (
                              role_id         BIGINT UNSIGNED NOT NULL,
                              api_id          BIGINT UNSIGNED NOT NULL,
                              created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              PRIMARY KEY (role_id, api_id),
                              INDEX idx_sys_role_api_api_id (api_id),
                              CONSTRAINT fk_sys_role_api_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id),
                              CONSTRAINT fk_sys_role_api_api_id  FOREIGN KEY (api_id)  REFERENCES sys_api (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色-API 授权关联(无软删)';


-- ============================================================
-- Section 13: RBAC 关联 — sys_role_menu (v2 不变)
-- ============================================================
CREATE TABLE sys_role_menu (
                               role_id         BIGINT UNSIGNED NOT NULL,
                               menu_id         BIGINT UNSIGNED NOT NULL,
                               created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (role_id, menu_id),
                               INDEX idx_sys_role_menu_menu_id (menu_id),
                               CONSTRAINT fk_sys_role_menu_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id),
                               CONSTRAINT fk_sys_role_menu_menu_id FOREIGN KEY (menu_id) REFERENCES sys_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='角色-菜单授权关联(无软删)';


-- ============================================================
-- Section 14: 菜单-API 快捷绑定 — sys_menu_api (v2 不变)
-- ============================================================
CREATE TABLE sys_menu_api (
                              menu_id         BIGINT UNSIGNED NOT NULL,
                              api_id          BIGINT UNSIGNED NOT NULL,
                              created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                              PRIMARY KEY (menu_id, api_id),
                              INDEX idx_sys_menu_api_api_id (api_id),
                              CONSTRAINT fk_sys_menu_api_menu_id FOREIGN KEY (menu_id) REFERENCES sys_menu (id),
                              CONSTRAINT fk_sys_menu_api_api_id  FOREIGN KEY (api_id)  REFERENCES sys_api (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='菜单-API 快捷绑定(非授权,便于按菜单批量赋权)';


-- ============================================================
-- Section 15: ABAC 数据权限 — sys_data_permission (v2 不变)
-- ============================================================
CREATE TABLE sys_data_permission (
                                     id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- 主体多态
                                     subject_type    VARCHAR(16)     NOT NULL  COMMENT '主体类型(USER/ROLE/ANY_USER/ANY_ROLE)',
                                     subject_id      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '主体 ID;ANY_* 时为 0',

    -- 资源
                                     resource_table  VARCHAR(32)     NOT NULL  COMMENT '资源表名(如 orders/users)',

    -- 动作(JSON 数组 + 规范化字符串)
                                     action          JSON            NOT NULL  COMMENT '操作列表(如 ["read","write"])',
                                     action_key      VARCHAR(64)     NOT NULL DEFAULT 'read'
                                         COMMENT 'action 排序后拼接(如 "read,write"),用于唯一约束',

    -- 作用域
                                     scope_type      VARCHAR(32)     NOT NULL DEFAULT 'none'
                                         COMMENT '作用域类型(all/none/include/exclude/custom)',
                                     scope_field     VARCHAR(64)     NOT NULL DEFAULT 'id'
                                         COMMENT '用于匹配 scope_values 的字段',
                                     scope_values    JSON            NOT NULL  COMMENT '作用域值列表',

    -- 行过滤条件(自由 JSON)
                                     conditions      JSON            NOT NULL  COMMENT '行过滤条件(K=V map,应用层解释)',

    -- 冲突优先级
                                     priority        INT             NOT NULL DEFAULT 0  COMMENT '多主体冲突时的优先级(降序)',

    -- 备注/启停
                                     remark          VARCHAR(512)    NOT NULL DEFAULT '',
                                     is_enabled      TINYINT(1)      NOT NULL DEFAULT 1,
                                     deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                                     created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                     created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                                     updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',

                                     PRIMARY KEY (id),

    -- 软删感知唯一:同 (主体,资源,动作) 至多一条"未删"行(deleted_at=0)
                                     UNIQUE KEY uniq_sys_data_permission_subject_resource_action_active
                                         (subject_type, subject_id, resource_table, action_key, deleted_at),

    -- 高频查询索引
                                     INDEX idx_sys_data_permission_subject (subject_type, subject_id),
                                     INDEX idx_sys_data_permission_subject_resource (subject_type, subject_id, resource_table),
                                     INDEX idx_sys_data_permission_resource (resource_table),
                                     INDEX idx_sys_data_permission_is_enabled (is_enabled),
                                     INDEX idx_sys_data_permission_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='ABAC 数据权限(主体多态 + 多 action + 多 scope,行级授权)';


-- ============================================================
-- Section 15b: 访问黑名单 — sys_blacklist (v11)
-- 多态 target(IP/USER/DEVICE) + scope(LOGIN/API/ALL) + 生效窗
-- 命中语义: deleted_at=0 AND is_enabled=1 AND starts_at<=NOW()
--           AND (expires_at IS NULL OR expires_at>NOW())
--           AND scope IN (请求场景, 'ALL')；多行 OR
-- 弱唯一: 防「同 target+scope+完全相同时间窗」重复行；区间重叠不由 DB 约束
-- MySQL 注意: UNIQUE 中 expires_at=NULL 时多行可并存(NULL 互不等)，应用层需防永久窗重复
-- ============================================================
CREATE TABLE sys_blacklist (
                               id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               target_type     VARCHAR(16)     NOT NULL  COMMENT '目标类型: IP / USER / DEVICE',
                               target_value    VARCHAR(128)    NOT NULL  COMMENT '目标值(IP 文本; USER=sys_user.id 十进制字符串软引用; DEVICE=客户端 deviceId 原样)',
                               scope           VARCHAR(16)     NOT NULL DEFAULT 'ALL'
                                   COMMENT '限制范围: LOGIN=仅登录 / API=仅已认证 API / ALL=全部(命中时 scope IN (场景, ALL))',
                               reason          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '封禁原因(对用户/审计可见;可空串)',
                               starts_at       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   COMMENT '生效开始时间(含)',
                               expires_at      TIMESTAMP       NULL DEFAULT NULL
                                    COMMENT '生效结束时间(不含;NULL=永不过期)',
                               remark          VARCHAR(512)    NOT NULL DEFAULT ''  COMMENT '管理员内部备注',
                               is_enabled      TINYINT(1)      NOT NULL DEFAULT 1  COMMENT '启用/禁用(独立于 deleted_at 与时间窗)',
                               deleted_at      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '软删时间戳(毫秒);0=未删;非0=删除时刻',
                               created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               created_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '创建人(0=系统操作;非0=软引用 sys_user.id)',
                               updated_by      BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '最后修改人(0=系统操作;非0=软引用 sys_user.id)',
                               PRIMARY KEY (id),
    -- 弱唯一: 同 (target, scope, 完全相同时间窗) 活跃行至多一条; 区间重叠不检测
                               UNIQUE KEY uniq_sys_blacklist_target_scope_window
                                   (target_type, target_value, scope, starts_at, expires_at, deleted_at),
                               INDEX idx_sys_blacklist_target (target_type, target_value),
                               INDEX idx_sys_blacklist_expires_at (expires_at),
                               INDEX idx_sys_blacklist_is_enabled (is_enabled),
                               INDEX idx_sys_blacklist_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='访问黑名单(多态 target + scope + 时间窗;运行时拦截本波未实现)';


-- ============================================================
-- Section 16: API 调用日志 — api_log (v5: 字段扩充对齐 PG sys_api_log)
-- request_id 全链路串联 api_log ↔ operation_log ↔ 链路追踪
-- 新增: 客户端指纹 / UA 解析 / IP 解析 / 变更前后 / 头信息
-- ============================================================
CREATE TABLE api_log (
                         id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    -- 调用结果
                         method          VARCHAR(16)     NOT NULL  COMMENT 'HTTP method(GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD)',
                         module          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '业务模块(如 user/role/menu/order)',
                         path            VARCHAR(255)    NOT NULL  COMMENT '请求路径(不含 query)',
                         status_code     INT UNSIGNED    DEFAULT NULL  COMMENT 'HTTP 状态码(连接早期失败时可能未设置)',
                         success         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '业务级成功(由中间件按 status_code 判定,2xx=1)',
                         reason          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '失败原因(若有;无错时为空串)',
                         cost_time       BIGINT UNSIGNED NOT NULL DEFAULT 0  COMMENT '耗时(毫秒)',

    -- 关联
                         request_id      VARCHAR(128)    NOT NULL  COMMENT '请求唯一 ID(中间件生成;串联 api_log ↔ operation_log ↔ 链路追踪)',
                         sys_user_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '操作用户(未登录请求为 NULL)',
                         username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '冗余:请求时刻的用户名(避免 JOIN;未登录请求为空串)',

    -- 请求侧
                         request_uri     TEXT            NULL  COMMENT '完整 URI(含 query;便于回放)',
                         request_query   TEXT            NULL  COMMENT 'query string',
                         request_body    MEDIUMTEXT      NULL  COMMENT '请求 body(应用层截断 64KB)',
                         request_header  MEDIUMTEXT      NULL  COMMENT '请求头(应用层序列化,敏感字段脱敏后存储)',
                         referer         VARCHAR(2048)   NOT NULL DEFAULT ''  COMMENT '来源页',

    -- 响应侧 / 变更
                         response        MEDIUMTEXT      NULL  COMMENT '响应 body(应用层截断 64KB)',
                         before_change   MEDIUMTEXT      NULL  COMMENT '操作前数据快照(写操作场景;与应用层 before/after 钩子配合)',
                         after_change    MEDIUMTEXT      NULL  COMMENT '操作后数据快照',
                         format_change   TEXT            NULL  COMMENT '格式化变更摘要(人读;如 "name: A→B;status: 0→1")',

    -- 客户端
                         client_id       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端 ID(如 web-admin-vue3 / mobile-app-ios)',
                         client_name     VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端名(展示用)',
                         client_ip       VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '客户端 IP(IPv6 兼容;PG 用 64 字符)',
                         user_agent      TEXT            NULL  COMMENT 'User Agent(完整;可能很长)',
                         browser_name    VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器名(由 UA 解析)',
                         browser_version VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器版本',
                         os_name         VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统名(由 UA 解析)',
                         os_version      VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统版本',
                         location        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT 'IP 解析地理位置(应用层负责;格式可自由)',

                         created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         PRIMARY KEY (id),
                         UNIQUE KEY uniq_api_log_request_id (request_id),
                         INDEX idx_api_log_sys_user_id_created_at (sys_user_id, created_at),
                         INDEX idx_api_log_module_created_at (module, created_at),
                         INDEX idx_api_log_path_created_at (path, created_at),
                         INDEX idx_api_log_status_code_created_at (status_code, created_at),
                         INDEX idx_api_log_success_created_at (success, created_at),
                         INDEX idx_api_log_client_ip_created_at (client_ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API 调用日志(记录型,只增不改;含 UA/IP 解析与数据变更快照)';


-- ============================================================
-- Section 17: API 日志归档 — api_log_archive (v5: 与 api_log 同结构)
-- ============================================================
CREATE TABLE api_log_archive (
                                 id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

                                 method          VARCHAR(16)     NOT NULL,
                                 module          VARCHAR(255)    NOT NULL DEFAULT '',
                                 path            VARCHAR(255)    NOT NULL,
                                 status_code     INT UNSIGNED    DEFAULT NULL,
                                 success         TINYINT(1)      NOT NULL DEFAULT 0,
                                 reason          VARCHAR(255)    NOT NULL DEFAULT '',
                                 cost_time       BIGINT UNSIGNED NOT NULL DEFAULT 0,

                                 request_id      VARCHAR(128)    NOT NULL,
                                 sys_user_id     BIGINT UNSIGNED DEFAULT NULL,
                                 username        VARCHAR(64)     NOT NULL DEFAULT '',

                                 request_uri     TEXT            NULL,
                                 request_query   TEXT            NULL,
                                 request_body    MEDIUMTEXT      NULL,
                                 request_header  MEDIUMTEXT      NULL,
                                 referer         VARCHAR(2048)   NOT NULL DEFAULT '',

                                 response        MEDIUMTEXT      NULL,
                                 before_change   MEDIUMTEXT      NULL,
                                 after_change    MEDIUMTEXT      NULL,
                                 format_change   TEXT            NULL,

                                 client_id       VARCHAR(128)    NOT NULL DEFAULT '',
                                 client_name     VARCHAR(128)    NOT NULL DEFAULT '',
                                 client_ip       VARCHAR(64)     NOT NULL DEFAULT '',
                                 user_agent      TEXT            NULL,
                                 browser_name    VARCHAR(128)    NOT NULL DEFAULT '',
                                 browser_version VARCHAR(128)    NOT NULL DEFAULT '',
                                 os_name         VARCHAR(128)    NOT NULL DEFAULT '',
                                 os_version      VARCHAR(128)    NOT NULL DEFAULT '',
                                 location        VARCHAR(255)    NOT NULL DEFAULT '',

                                 created_at      TIMESTAMP       NOT NULL  COMMENT '原始 created_at(便于跨表查询)',
                                 archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '归档时间',
                                 PRIMARY KEY (id),
                                 UNIQUE KEY uniq_api_log_archive_request_id (request_id),
                                 INDEX idx_api_log_archive_sys_user_id_created_at (sys_user_id, created_at),
                                 INDEX idx_api_log_archive_module_created_at (module, created_at),
                                 INDEX idx_api_log_archive_path_created_at (path, created_at),
                                 INDEX idx_api_log_archive_status_code_created_at (status_code, created_at),
                                 INDEX idx_api_log_archive_success_created_at (success, created_at),
                                 INDEX idx_api_log_archive_client_ip_created_at (client_ip, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='API 日志归档(由 TTL 作业从 api_log 搬运;与热表同结构)';


-- ============================================================
-- Section 18: 登录日志 — sys_login_log (v5: 字段扩充对齐 PG sys_login_log)
-- 新增: MAC / UA 解析 / IP 解析 / 状态码 / 客户端指纹
-- 移除: device / os / browser / country / province / city
--       (由 os_name/version + browser_name/version + location + client_* 替代)
-- ============================================================
CREATE TABLE sys_login_log (
                               id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '登录用户名',
                               success         TINYINT(1)      NOT NULL DEFAULT 0  COMMENT '1=成功 0=失败',
                               reason          VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT '失败原因(若失败;成功时为空串;旧字段 failure_reason 改名为 reason 与 PG 对齐)',
                               status_code     INT UNSIGNED    DEFAULT NULL  COMMENT 'HTTP 状态码(200=成功;其他=失败)',

    -- 关联
                               sys_user_id     BIGINT UNSIGNED DEFAULT NULL  COMMENT '关联用户(登录成功后)',
                               login_method    VARCHAR(32)     NOT NULL DEFAULT 'PASSWORD'
                                   COMMENT 'PASSWORD/SSO/OAUTH/SMS',

    -- 时间
                               login_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   COMMENT '登录尝试时间(应用层可与 created_at 区分;异步上报时可能略晚)',

    -- 客户端
                               login_ip        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '登录 IP(IPv6 兼容)',
                               login_mac       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '登录 MAC(若有;CS 场景下多为空)',
                               client_id       VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端 ID',
                               client_name     VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '客户端名',
                               user_agent      TEXT            NULL  COMMENT 'User Agent(完整)',
                               browser_name    VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器名(由 UA 解析)',
                               browser_version VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '浏览器版本',
                               os_name         VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统名',
                               os_version      VARCHAR(128)    NOT NULL DEFAULT ''  COMMENT '操作系统版本',
                               location        VARCHAR(255)    NOT NULL DEFAULT ''  COMMENT 'IP 解析地理位置(应用层负责;格式可自由)',

                               created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               PRIMARY KEY (id),
                               INDEX idx_sys_login_log_username_created_at (username, created_at),
                               INDEX idx_sys_login_log_success_created_at (success, created_at),
                               INDEX idx_sys_login_log_sys_user_id (sys_user_id),
                               INDEX idx_sys_login_log_login_ip_created_at (login_ip, created_at),
                               INDEX idx_sys_login_log_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='登录日志(记录型,只增不改;含 UA/IP 解析与客户端指纹)';


-- ============================================================
-- Section 19: 登录日志归档 — sys_login_log_archive (v5: 与 sys_login_log 同结构)
-- ============================================================
CREATE TABLE sys_login_log_archive (
                                       id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                       username        VARCHAR(64)     NOT NULL DEFAULT '',
                                       success         TINYINT(1)      NOT NULL DEFAULT 0,
                                       reason          VARCHAR(255)    NOT NULL DEFAULT '',
                                       status_code     INT UNSIGNED    DEFAULT NULL,

                                       sys_user_id     BIGINT UNSIGNED DEFAULT NULL,
                                       login_method    VARCHAR(32)     NOT NULL DEFAULT 'PASSWORD',

                                       login_time      TIMESTAMP       NOT NULL,

                                       login_ip        VARCHAR(64)     NOT NULL DEFAULT '',
                                       login_mac       VARCHAR(128)    NOT NULL DEFAULT '',
                                       client_id       VARCHAR(128)    NOT NULL DEFAULT '',
                                       client_name     VARCHAR(128)    NOT NULL DEFAULT '',
                                       user_agent      TEXT            NULL,
                                       browser_name    VARCHAR(128)    NOT NULL DEFAULT '',
                                       browser_version VARCHAR(128)    NOT NULL DEFAULT '',
                                       os_name         VARCHAR(128)    NOT NULL DEFAULT '',
                                       os_version      VARCHAR(128)    NOT NULL DEFAULT '',
                                       location        VARCHAR(255)    NOT NULL DEFAULT '',

                                       created_at      TIMESTAMP       NOT NULL,
                                       archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY (id),
                                       INDEX idx_sys_login_log_archive_username_created_at (username, created_at),
                                       INDEX idx_sys_login_log_archive_success_created_at (success, created_at),
                                       INDEX idx_sys_login_log_archive_sys_user_id (sys_user_id),
                                       INDEX idx_sys_login_log_archive_login_ip_created_at (login_ip, created_at),
                                       INDEX idx_sys_login_log_archive_login_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='登录日志归档(与热表同结构)';


-- ============================================================
-- Section 20: 操作日志 — operation_log (v2 不变)
-- ============================================================
CREATE TABLE operation_log (
                               id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                               user_id         BIGINT UNSIGNED DEFAULT NULL  COMMENT '操作人(系统级操作为 NULL)',
                               username        VARCHAR(64)     NOT NULL DEFAULT ''  COMMENT '冗余:操作时刻的用户名(系统级写为 system)',
                               module          VARCHAR(64)     NOT NULL  COMMENT '业务模块(如 user/role/menu/dict)',
                               action          VARCHAR(64)     NOT NULL  COMMENT '动作(create/update/delete/import/export/...)',
                               target_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '被操作对象 ID',
                               before_value    JSON            DEFAULT NULL  COMMENT '操作前数据快照(仅 UPDATE 有值)',
                               after_value     JSON            DEFAULT NULL  COMMENT '操作后数据快照(仅 UPDATE 有值)',
                               request_id      VARCHAR(64)     DEFAULT NULL  COMMENT '关联 api_log 的 request_id',
                               source          VARCHAR(16)     NOT NULL DEFAULT 'AUTO'  COMMENT 'AUTO=AOP 拦截 / EXPLICIT=显式打标',
                               remark          VARCHAR(512)    NOT NULL DEFAULT '',
                               client_ip       VARCHAR(45)     NOT NULL DEFAULT '',
                               user_agent      VARCHAR(512)    NOT NULL DEFAULT '',
                               created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               PRIMARY KEY (id),
                               INDEX idx_operation_log_user_id_created_at (user_id, created_at),
                               INDEX idx_operation_log_module_action_created_at (module, action, created_at),
                               INDEX idx_operation_log_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作日志(AOP 拦截 + 显式打标;记录型)';


-- ============================================================
-- Section 21: 操作日志归档 — operation_log_archive (v2 不变)
-- ============================================================
CREATE TABLE operation_log_archive (
                                       id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                       user_id         BIGINT UNSIGNED DEFAULT NULL,
                                       username        VARCHAR(64)     NOT NULL DEFAULT '',
                                       module          VARCHAR(64)     NOT NULL,
                                       action          VARCHAR(64)     NOT NULL,
                                       target_id       BIGINT UNSIGNED DEFAULT NULL,
                                       before_value    JSON            DEFAULT NULL,
                                       after_value     JSON            DEFAULT NULL,
                                       request_id      VARCHAR(64)     DEFAULT NULL,
                                       source          VARCHAR(16)     NOT NULL DEFAULT 'AUTO',
                                       remark          VARCHAR(512)    NOT NULL DEFAULT '',
                                       client_ip       VARCHAR(45)     NOT NULL DEFAULT '',
                                       user_agent      VARCHAR(512)    NOT NULL DEFAULT '',
                                       created_at      TIMESTAMP       NOT NULL,
                                       archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       PRIMARY KEY (id),
                                       INDEX idx_operation_log_archive_user_id_created_at (user_id, created_at),
                                       INDEX idx_operation_log_archive_module_action_created_at (module, action, created_at),
                                       INDEX idx_operation_log_archive_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作日志归档';


-- ============================================================
-- Section 22: Temporal 执行记录 — temporal_task_execution (v2 不变)
-- ============================================================
CREATE TABLE temporal_task_execution (
                                         id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                         config_id       BIGINT UNSIGNED DEFAULT NULL  COMMENT '软外键 → temporal_task_config.id',
                                         workflow_id     VARCHAR(128)    NOT NULL  COMMENT 'Temporal 原生 workflow_id',
                                         run_id          VARCHAR(128)    NOT NULL  COMMENT 'Temporal 原生 run_id',
                                         workflow_type   VARCHAR(128)    NOT NULL,
                                         task_queue      VARCHAR(128)    NOT NULL,
                                         status          VARCHAR(32)     NOT NULL
                                             COMMENT 'PENDING/RUNNING/RETRYING/COMPLETED/FAILED/CANCELLED/TERMINATED/TIMED_OUT/CONTINUED_AS_NEW',
                                         pending_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '进入等待中(PENDING)的时间',
                                         started_at      TIMESTAMP       NULL DEFAULT NULL  COMMENT '真正运行开始时间(NULL=尚未真正运行)',
                                         closed_at       TIMESTAMP       NULL DEFAULT NULL  COMMENT '关闭时间(NULL=仍在运行/未启动)',
                                         input_summary   JSON            DEFAULT NULL  COMMENT '输入摘要(避免存大对象)',
                                         result_summary  JSON            DEFAULT NULL  COMMENT '结果摘要',
                                         failure_reason  VARCHAR(1024)   DEFAULT NULL,
                                         retry_count     INT             NOT NULL DEFAULT 0  COMMENT '已发生重试次数(首次执行为0)',
                                         created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         PRIMARY KEY (id),
                                         UNIQUE KEY uniq_temporal_task_execution_workflow_run (workflow_id, run_id),
                                         INDEX idx_temporal_task_execution_config_started_at (config_id, started_at),
                                         INDEX idx_temporal_task_execution_status_started_at (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Temporal 执行记录(应用层镜像,只存摘要)';


SET FOREIGN_KEY_CHECKS = 1;


-- ============================================================
-- Section 23: 自引用外键后置
-- CREATE TABLE 内无法引用自身,用 ALTER TABLE 补充
-- ============================================================
ALTER TABLE sys_menu
    ADD CONSTRAINT fk_sys_menu_parent_id
        FOREIGN KEY (parent_id) REFERENCES sys_menu (id);

ALTER TABLE sys_role
    ADD CONSTRAINT fk_sys_role_parent_id
        FOREIGN KEY (parent_id) REFERENCES sys_role (id);


-- ============================================================
-- End of schema.sql (v5 基线 + dict_data v8/v9/v10 + sys_blacklist v11)
-- ============================================================
