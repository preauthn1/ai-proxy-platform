-- V2: 初始化种子数据（字典 + RBAC + Root + Casbin）
-- 数据来源: backend/db/schema_data.sql（角色/用户收窄为仅 root）
-- 执行顺序: 依赖 V1__schema.sql（先建表，再插数据）
--
-- 约定:
--   - 软删业务表数据均为未删状态（deleted_at = 0）
--   - 字典: sys_user_sex / sys_yes_no / sys_menu_type / sys_notice_type
--           sys_switch_status / sys_default_status / sys_platform
--   - RBAC: sys_api / sys_menu 全量；角色/用户仅 root
--   - Root 密码明文 123456（BCrypt cost=10）
--   - Casbin subject = 用户 id 字符串；Root 通配 p, 1, /*, * 保证首登不被 deny-by-default
--     matcher: r.sub == p.sub && keyMatch2(r.obj, p.obj) && (p.act == "*" || r.act == p.act)
--
-- 环境: 未配置 spring.flyway.target 时各 profile 均执行本脚本

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Section 1: dict_type
-- ============================================================

INSERT INTO dict_type (code, name, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    ('sys_user_sex', '用户性别', '用户性别字典', 1, 0, 0, 0),
    ('sys_yes_no', '系统是否', '通用 Y/N', 1, 0, 0, 0),
    ('sys_menu_type', '菜单类型', 'DIR / MENU / BUTTON', 1, 0, 0, 0),
    ('sys_notice_type', '通知类型', '通知/公告/提醒', 1, 0, 0, 0),
    ('sys_switch_status', '开关状态', '跨模块通用启用/禁用状态', 1, 0, 0, 0),
    ('sys_default_status', '默认状态', '是否默认值（用于字典项 / 语言等场景的「默认/否」列）', 1, 0, 0, 0),
    ('sys_platform', '归属平台', '前端按 platform 过滤通用/平台专属字典项', 1, 0, 0, 0);


-- ============================================================
-- Section 2: dict_data
-- 依赖 dict_type.id；先插入 dict_type 后由 SELECT 取 id 绑定
-- ============================================================

-- 2.1 用户性别(sys_user_sex)
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '0', '男', 0, 1, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_user_sex' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '1', '女', 1, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_user_sex' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '2', '未知', 2, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_user_sex' AND t.deleted_at = 0;

-- 2.2 系统是否(sys_yes_no)
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'Y', '是', 0, 1, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_yes_no' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'N', '否', 1, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_yes_no' AND t.deleted_at = 0;

-- 2.3 菜单类型(sys_menu_type)
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'DIR', '目录', 0, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_menu_type' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'MENU', '菜单', 1, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_menu_type' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'BUTTON', '按钮', 2, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_menu_type' AND t.deleted_at = 0;

-- 2.4 通知类型(sys_notice_type)
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '1', '通知', 0, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_notice_type' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '2', '公告', 1, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_notice_type' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, '3', '提醒', 2, 0, 'general', 'default', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_notice_type' AND t.deleted_at = 0;

-- 2.5 开关状态(sys_switch_status) - sort 单调递增 1..6(对齐 design.md / mock)
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'enabled',  '启用',  1, 0, 'general', '', 1, 0, '启用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'disabled', '禁用',  2, 1, 'general', '', 1, 0, '禁用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'enabled',  '启用',  3, 0, 'react-admin', 'success', 1, 0, '启用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'disabled', '禁用',  4, 1, 'react-admin', 'error',   1, 0, '禁用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'enabled',  '启用',  5, 0, 'vue-admin',   'success', 1, 0, '启用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'disabled', '禁用',  6, 1, 'vue-admin',   'error',   1, 0, '禁用', 0, 0
FROM dict_type t WHERE t.code = 'sys_switch_status' AND t.deleted_at = 0;

-- 2.6 默认状态(sys_default_status)
-- value: default = 默认 / not-default = 否；三端各一组，tag_type 对齐 mock
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'default',     '默认', 0, 1, 'general',     'processing', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'not-default', '-',    1, 0, 'general',     'default',    1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'default',     '默认', 2, 1, 'react-admin', 'processing', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'not-default', '-',    3, 0, 'react-admin', 'default',    1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'default',     '默认', 4, 1, 'vue-admin',   'processing', 1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'not-default', '-',    5, 0, 'vue-admin',   'default',    1, 0, '', 0, 0
FROM dict_type t WHERE t.code = 'sys_default_status' AND t.deleted_at = 0;

-- 2.7 归属平台(sys_platform)
-- platform 字段 = value（自洽）；tag_type 置空，平台 CellTag 不着色
INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'general',     '通用',     1, 1, 'general',     '', 1, 0, '跨平台通用', 0, 0
FROM dict_type t WHERE t.code = 'sys_platform' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'react-admin', 'React Admin', 2, 0, 'react-admin', '', 1, 0, 'React Admin 端专属', 0, 0
FROM dict_type t WHERE t.code = 'sys_platform' AND t.deleted_at = 0;

INSERT INTO dict_data (type_id, value, label, sort, is_default, platform, tag_type, is_enabled, deleted_at, remark, created_by, updated_by)
SELECT t.id, 'vue-admin',   'Vue Admin', 3, 0, 'vue-admin',   '', 1, 0, 'Vue Admin 端专属', 0, 0
FROM dict_type t WHERE t.code = 'sys_platform' AND t.deleted_at = 0;

-- ============================================================
-- Section 3: sys_api（对齐 mock API_SYNC_MANIFEST）
-- 注: schema UNIQUE(permission_code, deleted_at) 比 mock 更严；
--     重复 permissionCode 的后续项用 path 派生后缀消歧（__slug）。
-- ============================================================

INSERT INTO sys_api (id, name, method, path, permission_code, api_group, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (1, '权限码列表', 'GET', '/api/auth/codes', 'auth:codes', '会话', '', 1, 0, 0, 0),
    (2, '当前用户信息', 'GET', '/api/user/info', 'user:info', '会话', '', 1, 0, 0, 0),
    (3, '用户菜单路由', 'GET', '/api/menu/all', 'menu:all', '会话', '', 1, 0, 0, 0),
    (4, '文件上传', 'POST', '/api/upload', 'system:upload', '会话', '', 1, 0, 0, 0),
    (5, '用户分页列表', 'GET', '/api/system/user/list', 'system:user:list', '用户管理', '', 1, 0, 0, 0),
    (6, '创建用户', 'POST', '/api/system/user', 'system:user:create', '用户管理', '', 1, 0, 0, 0),
    (7, '更新用户', 'PUT', '/api/system/user/:id', 'system:user:update', '用户管理', '', 1, 0, 0, 0),
    (8, '删除用户', 'DELETE', '/api/system/user/:id', 'system:user:delete', '用户管理', '', 1, 0, 0, 0),
    (9, '启停用户', 'PUT', '/api/system/user/:id/status', 'system:user:status', '用户管理', '', 1, 0, 0, 0),
    (10, '重置用户密码', 'POST', '/api/system/user/:id/password', 'system:user:password', '用户管理', '', 1, 0, 0, 0),
    (11, '角色分页列表', 'GET', '/api/system/role/list', 'system:role:list', '角色管理', '', 1, 0, 0, 0),
    (12, '角色全量列表', 'GET', '/api/system/role/all', 'system:role:list__system_role_all', '角色管理', '', 1, 0, 0, 0),
    (13, '创建角色', 'POST', '/api/system/role', 'system:role:create', '角色管理', '', 1, 0, 0, 0),
    (14, '更新角色', 'PUT', '/api/system/role/:id', 'system:role:update', '角色管理', '', 1, 0, 0, 0),
    (15, '删除角色', 'DELETE', '/api/system/role/:id', 'system:role:delete', '角色管理', '', 1, 0, 0, 0),
    (16, '角色已绑菜单', 'GET', '/api/system/role/:id/menus', 'system:role:menu', '角色管理', '', 1, 0, 0, 0),
    (17, '分配角色菜单', 'POST', '/api/system/role/:id/menus', 'system:role:menu__system_role__id_menus', '角色管理', '', 1, 0, 0, 0),
    (18, '角色已绑接口', 'GET', '/api/system/role/:id/apis', 'system:role:api', '角色管理', '', 1, 0, 0, 0),
    (19, '分配角色接口', 'POST', '/api/system/role/:id/apis', 'system:role:api__system_role__id_apis', '角色管理', '', 1, 0, 0, 0),
    (20, '菜单分页列表', 'GET', '/api/system/menu/list', 'system:menu:list', '菜单管理', '', 1, 0, 0, 0),
    (21, '菜单全量列表', 'GET', '/api/system/menu/all', 'system:menu:list__system_menu_all', '菜单管理', '', 1, 0, 0, 0),
    (22, '创建菜单', 'POST', '/api/system/menu', 'system:menu:create', '菜单管理', '', 1, 0, 0, 0),
    (23, '更新菜单', 'PUT', '/api/system/menu/:id', 'system:menu:update', '菜单管理', '', 1, 0, 0, 0),
    (24, '删除菜单', 'DELETE', '/api/system/menu/:id', 'system:menu:delete', '菜单管理', '', 1, 0, 0, 0),
    (25, '批量操作菜单', 'POST', '/api/system/menu/batch', 'system:menu:batch', '菜单管理', '', 1, 0, 0, 0),
    (26, '菜单名是否存在', 'GET', '/api/system/menu/name-exists', 'system:menu:list__system_menu_name-exists', '菜单管理', '', 1, 0, 0, 0),
    (27, '菜单路径是否存在', 'GET', '/api/system/menu/path-exists', 'system:menu:list__system_menu_path-exists', '菜单管理', '', 1, 0, 0, 0),
    (28, '菜单已绑接口', 'GET', '/api/system/menu/:id/apis', 'system:menu:api', '菜单管理', '', 1, 0, 0, 0),
    (29, '设置菜单接口', 'POST', '/api/system/menu/:id/apis', 'system:menu:api__system_menu__id_apis', '菜单管理', '', 1, 0, 0, 0),
    (30, '接口分页列表', 'GET', '/api/system/api/list', 'system:api:list', '接口管理', '', 1, 0, 0, 0),
    (31, '接口全量列表', 'GET', '/api/system/api/all', 'system:api:list__system_api_all', '接口管理', '', 1, 0, 0, 0),
    (32, '接口分组列表', 'GET', '/api/system/api/groups', 'system:api:list__system_api_groups', '接口管理', '', 1, 0, 0, 0),
    (33, '创建接口', 'POST', '/api/system/api', 'system:api:create', '接口管理', '', 1, 0, 0, 0),
    (34, '更新接口', 'PUT', '/api/system/api/:id', 'system:api:update', '接口管理', '', 1, 0, 0, 0),
    (35, '删除接口', 'DELETE', '/api/system/api/:id', 'system:api:delete', '接口管理', '', 1, 0, 0, 0),
    (36, '批量操作接口', 'POST', '/api/system/api/batch', 'system:api:batch', '接口管理', '', 1, 0, 0, 0),
    (37, '同步接口', 'POST', '/api/system/api/sync', 'system:api:sync', '接口管理', '', 1, 0, 0, 0),
    (38, '字典类型分页', 'GET', '/api/system/dict-type/list', 'system:dict:list', '字典管理', '', 1, 0, 0, 0),
    (39, '字典类型全量', 'GET', '/api/system/dict-type/all', 'system:dict:list__system_dict-type_all', '字典管理', '', 1, 0, 0, 0),
    (40, '字典类型详情', 'GET', '/api/system/dict-type/:id', 'system:dict:list__system_dict-type__id', '字典管理', '', 1, 0, 0, 0),
    (41, '创建字典类型', 'POST', '/api/system/dict-type', 'system:dict:create', '字典管理', '', 1, 0, 0, 0),
    (42, '更新字典类型', 'PUT', '/api/system/dict-type/:id', 'system:dict:update', '字典管理', '', 1, 0, 0, 0),
    (43, '删除字典类型', 'DELETE', '/api/system/dict-type/:id', 'system:dict:delete', '字典管理', '', 1, 0, 0, 0),
    (44, '批量操作字典类型', 'POST', '/api/system/dict-type/batch', 'system:dict:batch', '字典管理', '', 1, 0, 0, 0),
    (45, '字典数据分页', 'GET', '/api/system/dict-data/list', 'system:dict:data:list', '字典管理', '', 1, 0, 0, 0),
    (46, '按类型查字典数据', 'GET', '/api/system/dict-data/by-type/:code', 'system:dict:data:list__system_dict-data_by-type__code', '字典管理', '', 1, 0, 0, 0),
    (47, '创建字典数据', 'POST', '/api/system/dict-data', 'system:dict:data:create', '字典管理', '', 1, 0, 0, 0),
    (48, '更新字典数据', 'PUT', '/api/system/dict-data/:id', 'system:dict:data:update', '字典管理', '', 1, 0, 0, 0),
    (49, '删除字典数据', 'DELETE', '/api/system/dict-data/:id', 'system:dict:data:delete', '字典管理', '', 1, 0, 0, 0),
    (50, '批量操作字典数据', 'POST', '/api/system/dict-data/batch', 'system:dict:data:batch', '字典管理', '', 1, 0, 0, 0),
    (51, '语言分页列表', 'GET', '/api/system/i18n-locale/list', 'system:i18n:list', '国际化', '', 1, 0, 0, 0),
    (52, '语言全量列表', 'GET', '/api/system/i18n-locale/all', 'system:i18n:list__system_i18n-locale_all', '国际化', '', 1, 0, 0, 0),
    (53, '语言详情', 'GET', '/api/system/i18n-locale/:id', 'system:i18n:list__system_i18n-locale__id', '国际化', '', 1, 0, 0, 0),
    (54, '创建语言', 'POST', '/api/system/i18n-locale', 'system:i18n:create', '国际化', '', 1, 0, 0, 0),
    (55, '更新语言', 'PUT', '/api/system/i18n-locale/:id', 'system:i18n:update', '国际化', '', 1, 0, 0, 0),
    (56, '删除语言', 'DELETE', '/api/system/i18n-locale/:id', 'system:i18n:delete', '国际化', '', 1, 0, 0, 0),
    (57, '批量操作语言', 'POST', '/api/system/i18n-locale/batch', 'system:i18n:batch', '国际化', '', 1, 0, 0, 0),
    (58, '导出语言', 'GET', '/api/system/i18n-locale/export', 'system:i18n:export', '国际化', '', 1, 0, 0, 0),
    (59, '批量导出语言', 'POST', '/api/system/i18n-locale/export-batch', 'system:i18n:export__system_i18n-locale_export-batch', '国际化', '', 1, 0, 0, 0),
    (60, '翻译分页列表', 'GET', '/api/system/i18n-translation/list', 'system:i18n:list__system_i18n-translation_list', '国际化', '', 1, 0, 0, 0),
    (61, '按语言查翻译', 'GET', '/api/system/i18n-translation/by-locale/:code', 'system:i18n:list__system_i18n-translation_by-locale__code', '国际化', '', 1, 0, 0, 0),
    (62, '按 key 查翻译', 'GET', '/api/system/i18n-translation/by-key/:key', 'system:i18n:list__system_i18n-translation_by-key__key', '国际化', '', 1, 0, 0, 0),
    (63, '创建翻译', 'POST', '/api/system/i18n-translation', 'system:i18n:create__system_i18n-translation', '国际化', '', 1, 0, 0, 0),
    (64, '更新翻译', 'PUT', '/api/system/i18n-translation/:id', 'system:i18n:update__system_i18n-translation__id', '国际化', '', 1, 0, 0, 0),
    (65, '删除翻译', 'DELETE', '/api/system/i18n-translation/:id', 'system:i18n:delete__system_i18n-translation__id', '国际化', '', 1, 0, 0, 0),
    (66, '批量操作翻译', 'POST', '/api/system/i18n-translation/batch', 'system:i18n:batch__system_i18n-translation_batch', '国际化', '', 1, 0, 0, 0),
    (67, '按 key 批量 upsert 翻译', 'POST', '/api/system/i18n-translation/batch-upsert-by-key', 'system:i18n:update__system_i18n-translation_batch-upsert-by-key', '国际化', '', 1, 0, 0, 0),
    (68, '导入翻译预览', 'POST', '/api/system/i18n-translation/import-preview', 'system:i18n:import', '国际化', '', 1, 0, 0, 0),
    (69, '批量导入翻译', 'POST', '/api/system/i18n-translation/import-batch', 'system:i18n:import__system_i18n-translation_import-batch', '国际化', '', 1, 0, 0, 0),
    (70, '登录日志分页列表', 'GET', '/api/system/login-log/list', 'log:login-log:list', '日志审计', '', 1, 0, 0, 0),
    (71, 'API 日志分页列表', 'GET', '/api/system/api-log/list', 'log:api-log:list', '日志审计', '', 1, 0, 0, 0),
    (72, '任务配置分页', 'GET', '/api/system/task-config/list', 'task:config:list', '任务调度', '', 1, 0, 0, 0),
    (73, '任务配置详情', 'GET', '/api/system/task-config/:id', 'task:config:list__system_task-config__id', '任务调度', '', 1, 0, 0, 0),
    (74, '创建任务配置', 'POST', '/api/system/task-config', 'task:config:create', '任务调度', '', 1, 0, 0, 0),
    (75, '更新任务配置', 'PUT', '/api/system/task-config/:id', 'task:config:update', '任务调度', '', 1, 0, 0, 0),
    (76, '删除任务配置', 'DELETE', '/api/system/task-config/:id', 'task:config:delete', '任务调度', '', 1, 0, 0, 0),
    (77, '批量操作任务配置', 'POST', '/api/system/task-config/batch', 'task:config:batch', '任务调度', '', 1, 0, 0, 0),
    (78, '手动触发任务配置', 'POST', '/api/system/task-config/:id/trigger', 'task:config:trigger', '任务调度', '', 1, 0, 0, 0),
    (79, '任务执行分页', 'GET', '/api/system/task-execution/list', 'task:execution:list', '任务调度', '', 1, 0, 0, 0),
    (80, '任务执行详情', 'GET', '/api/system/task-execution/:id', 'task:execution:list__system_task-execution__id', '任务调度', '', 1, 0, 0, 0),
    (81, '工作流类型选项', 'GET', '/api/system/task-config/workflow-types', 'task:config:list__system_task-config_workflow-types', '任务调度', '', 1, 0, 0, 0),
    (82, '任务队列选项', 'GET', '/api/system/task-config/task-queues', 'task:config:list__system_task-config_task-queues', '任务调度', '', 1, 0, 0, 0),
    (83, '黑名单分页', 'GET', '/api/system/blacklist/list', 'system:blacklist:list', '访问黑名单', '', 1, 0, 0, 0),
    (84, '黑名单全量', 'GET', '/api/system/blacklist/all', 'system:blacklist:list__system_blacklist_all', '访问黑名单', '', 1, 0, 0, 0),
    (85, '黑名单详情', 'GET', '/api/system/blacklist/:id', 'system:blacklist:list__system_blacklist__id', '访问黑名单', '', 1, 0, 0, 0),
    (86, '创建黑名单', 'POST', '/api/system/blacklist', 'system:blacklist:create', '访问黑名单', '', 1, 0, 0, 0),
    (87, '更新黑名单', 'PUT', '/api/system/blacklist/:id', 'system:blacklist:update', '访问黑名单', '', 1, 0, 0, 0),
    (88, '删除黑名单', 'DELETE', '/api/system/blacklist/:id', 'system:blacklist:delete', '访问黑名单', '', 1, 0, 0, 0),
    (89, '批量操作黑名单', 'POST', '/api/system/blacklist/batch', 'system:blacklist:batch', '访问黑名单', '', 1, 0, 0, 0);

ALTER TABLE sys_api AUTO_INCREMENT = 90;

-- ============================================================
-- Section 4: sys_menu（对齐 mock buildSysMenuSeeds，固定 id 便于 tree_path / 授权）
-- ============================================================

INSERT INTO sys_menu (id, parent_id, name, type, path, component, icon, redirect, permission_code, tree_path, metadata, sort, is_hidden, is_enabled, deleted_at, remark, created_by, updated_by)
VALUES
    (100, NULL, 'page.dashboard.title', 'DIR', '/dashboard', NULL, 'lucide:layout-dashboard', '/analytics', NULL, '/100/', '{"routeName":"Dashboard","order":-1}', -1, 0, 1, 0, '', 0, 0),
    (101, 100, 'page.dashboard.analytics', 'MENU', '/analytics', '/dashboard/analytics/index', 'lucide:area-chart', '', NULL, '/100/101/', '{"routeName":"Analytics","affixTab":true,"order":1}', 1, 0, 1, 0, '', 0, 0),
    (102, 100, 'page.dashboard.workspace', 'MENU', '/workspace', '/dashboard/workspace/index', 'carbon:workspace', '', NULL, '/100/102/', '{"routeName":"Workspace","order":2}', 2, 0, 1, 0, '', 0, 0),
    (200, NULL, 'system.title', 'DIR', '/system', NULL, 'lucide:settings', '/system/user', NULL, '/200/', '{"routeName":"System","order":2005}', 2005, 0, 1, 0, '', 0, 0),
    (201, 200, 'system.user.title', 'MENU', '/system/user', '/system/user/index', 'lucide:user-cog', '', 'system:user:list', '/200/201/', '{"routeName":"SystemUser","order":1}', 1, 0, 1, 0, '', 0, 0),
    (2011, 201, '新增用户', 'BUTTON', NULL, NULL, '', '', 'system:user:create', '/200/201/2011/', NULL, 1, 0, 1, 0, '', 0, 0),
    (2012, 201, '编辑用户', 'BUTTON', NULL, NULL, '', '', 'system:user:update', '/200/201/2012/', NULL, 2, 0, 1, 0, '', 0, 0),
    (2013, 201, '删除用户', 'BUTTON', NULL, NULL, '', '', 'system:user:delete', '/200/201/2013/', NULL, 3, 0, 1, 0, '', 0, 0),
    (202, 200, 'system.role.title', 'MENU', '/system/role', '/system/role/index', 'lucide:shield-user', '', 'system:role:list', '/200/202/', '{"routeName":"SystemRole","order":2}', 2, 0, 1, 0, '', 0, 0),
    (2021, 202, '分配菜单', 'BUTTON', NULL, NULL, '', '', 'system:role:menu', '/200/202/2021/', NULL, 1, 0, 1, 0, '', 0, 0),
    (203, 200, 'system.dict.title', 'MENU', '/system/dict', '/system/dict/index', 'lucide:book-marked', '', 'system:dict:list', '/200/203/', '{"routeName":"SystemDict","order":3}', 3, 0, 1, 0, '', 0, 0),
    (204, 200, 'system.i18n.title', 'MENU', '/system/i18n', '/system/i18n/index', 'lucide:languages', '', 'system:i18n:list', '/200/204/', '{"routeName":"SystemI18n","order":4}', 4, 0, 1, 0, '', 0, 0),
    (205, 200, 'system.menu.title', 'MENU', '/system/menu', '/system/menu/index', 'lucide:menu', '', 'system:menu:list', '/200/205/', '{"routeName":"SystemMenu","order":5}', 5, 0, 1, 0, '', 0, 0),
    (2051, 205, '新增菜单', 'BUTTON', NULL, NULL, '', '', 'system:menu:create', '/200/205/2051/', NULL, 1, 0, 1, 0, '', 0, 0),
    (2052, 205, '编辑菜单', 'BUTTON', NULL, NULL, '', '', 'system:menu:update', '/200/205/2052/', NULL, 2, 0, 1, 0, '', 0, 0),
    (2053, 205, '删除菜单', 'BUTTON', NULL, NULL, '', '', 'system:menu:delete', '/200/205/2053/', NULL, 3, 0, 1, 0, '', 0, 0),
    (206, 200, 'system.api.title', 'MENU', '/system/api', '/system/api/index', 'lucide:terminal', '', 'system:api:list', '/200/206/', '{"routeName":"SystemApi","order":6}', 6, 0, 1, 0, '', 0, 0),
    (2061, 206, '同步接口', 'BUTTON', NULL, NULL, '', '', 'system:api:sync', '/200/206/2061/', NULL, 1, 0, 1, 0, '', 0, 0),
    (207, 200, 'system.blacklist.title', 'MENU', '/system/blacklist', '/system/blacklist/index', 'lucide:shield-ban', '', 'system:blacklist:list', '/200/207/', '{"routeName":"SystemBlacklist","order":7}', 7, 0, 1, 0, '', 0, 0),
    (300, NULL, 'log.title', 'MENU', '/log', '/log/index', 'lucide:logs', '', NULL, '/300/', '{"routeName":"Log","order":2004,"fullPathKey":false}', 2004, 0, 1, 0, '', 0, 0),
    (301, 300, 'log.loginLog.title', 'BUTTON', NULL, NULL, '', '', 'log:login-log:list', '/300/301/', NULL, 1, 0, 1, 0, '', 0, 0),
    (302, 300, 'log.apiLog.title', 'BUTTON', NULL, NULL, '', '', 'log:api-log:list', '/300/302/', NULL, 2, 0, 1, 0, '', 0, 0),
    (400, NULL, 'task.title', 'MENU', '/task', '/task/index', 'lucide:timer', '', NULL, '/400/', '{"routeName":"Task","order":2003,"fullPathKey":false}', 2003, 0, 1, 0, '', 0, 0),
    (401, 400, 'task.config.title', 'BUTTON', NULL, NULL, '', '', 'task:config:list', '/400/401/', NULL, 1, 0, 1, 0, '', 0, 0),
    (402, 400, 'task.execution.title', 'BUTTON', NULL, NULL, '', '', 'task:execution:list', '/400/402/', NULL, 2, 0, 1, 0, '', 0, 0);

ALTER TABLE sys_menu AUTO_INCREMENT = 1000;

-- ============================================================
-- Section 5: sys_role（仅 root）
-- ============================================================

INSERT INTO sys_role (id, code, name, parent_id, sort, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (1, 'root', '超级管理员', NULL, 1, '系统内置 Root 角色，不可删除', 1, 0, 0, 0);

ALTER TABLE sys_role AUTO_INCREMENT = 100;

-- ============================================================
-- Section 6: sys_user（仅 root；密码明文 123456，BCrypt）
-- ============================================================

INSERT INTO sys_user (id, username, password_hash, nickname, email, phone, avatar, language_code, last_login_at, last_login_ip, remark, is_enabled, deleted_at, created_by, updated_by)
VALUES
    (1, 'root', '$2a$10$mzKVO0J.OxnOhHBO8AgBset0LzVRTLv285BJzaTfxpps1Jx7hrXom', 'Root', 'root@trellis.local', '', '', 'zh-CN', NULL, '', '系统内置超级管理员', 1, 0, 0, 0);

ALTER TABLE sys_user AUTO_INCREMENT = 100;

-- ============================================================
-- Section 7: sys_user_role（root 用户 → root 角色）
-- ============================================================

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

-- ============================================================
-- Section 8: sys_role_menu（root 全量菜单，含日志/任务按钮）
-- ============================================================

INSERT INTO sys_role_menu (role_id, menu_id) VALUES
    (1, 100),
    (1, 101),
    (1, 102),
    (1, 300),
    (1, 301),
    (1, 302),
    (1, 400),
    (1, 401),
    (1, 402),
    (1, 200),
    (1, 201),
    (1, 2011),
    (1, 2012),
    (1, 2013),
    (1, 202),
    (1, 2021),
    (1, 203),
    (1, 204),
    (1, 205),
    (1, 2051),
    (1, 2052),
    (1, 2053),
    (1, 206),
    (1, 2061),
    (1, 207);

-- ============================================================
-- Section 9: sys_role_api（root 全量接口）
-- ============================================================

INSERT INTO sys_role_api (role_id, api_id) VALUES
    (1, 1),
    (1, 2),
    (1, 3),
    (1, 4),
    (1, 5),
    (1, 6),
    (1, 7),
    (1, 8),
    (1, 9),
    (1, 10),
    (1, 11),
    (1, 12),
    (1, 13),
    (1, 14),
    (1, 15),
    (1, 16),
    (1, 17),
    (1, 18),
    (1, 19),
    (1, 20),
    (1, 21),
    (1, 22),
    (1, 23),
    (1, 24),
    (1, 25),
    (1, 26),
    (1, 27),
    (1, 28),
    (1, 29),
    (1, 30),
    (1, 31),
    (1, 32),
    (1, 33),
    (1, 34),
    (1, 35),
    (1, 36),
    (1, 37),
    (1, 38),
    (1, 39),
    (1, 40),
    (1, 41),
    (1, 42),
    (1, 43),
    (1, 44),
    (1, 45),
    (1, 46),
    (1, 47),
    (1, 48),
    (1, 49),
    (1, 50),
    (1, 51),
    (1, 52),
    (1, 53),
    (1, 54),
    (1, 55),
    (1, 56),
    (1, 57),
    (1, 58),
    (1, 59),
    (1, 60),
    (1, 61),
    (1, 62),
    (1, 63),
    (1, 64),
    (1, 65),
    (1, 66),
    (1, 67),
    (1, 68),
    (1, 69),
    (1, 70),
    (1, 71),
    (1, 72),
    (1, 73),
    (1, 74),
    (1, 75),
    (1, 76),
    (1, 77),
    (1, 78),
    (1, 79),
    (1, 80),
    (1, 81),
    (1, 82),
    (1, 83),
    (1, 84),
    (1, 85),
    (1, 86),
    (1, 87),
    (1, 88),
    (1, 89);

-- ============================================================
-- Section 10: sys_menu_api（菜单-接口快捷绑定）
-- ============================================================

INSERT INTO sys_menu_api (menu_id, api_id, created_by) VALUES
    (201, 5, 0),
    (202, 11, 0),
    (203, 38, 0),
    (204, 51, 0),
    (205, 20, 0),
    (206, 30, 0),
    (206, 37, 0),
    (207, 83, 0),
    (301, 70, 0),
    (302, 71, 0),
    (401, 72, 0),
    (401, 81, 0),
    (401, 82, 0),
    (402, 79, 0);

-- ============================================================
-- Section 11: casbin_rule（java-admin 专用；Root 通配 policy）
-- ptype=p, v0=sub(userId), v1=obj(path), v2=act(method)
-- model 支持 keyMatch2(path) 与 act='*' 通配（见 casbin/model.conf）
-- ============================================================

INSERT INTO casbin_rule (ptype, v0, v1, v2, v3, v4, v5)
VALUES ('p', '1', '/*', '*', NULL, NULL, NULL);

-- ============================================================
-- Section 12: temporal_task_config（dev 测试任务）
-- log_count_tick：单次 Workflow = Activity 将 count+1 并 log；节拍由 Schedule 按 cron/interval 驱动
-- 启动时 TemporalTaskScheduleSync 同步为 Schedule（scheduleId=task-{code}；秒级 cron 转 Interval）
-- 联调：dev 默认连 127.0.0.1:4723 + start-workers；日志搜 log_count_tick count=
-- ============================================================

INSERT INTO temporal_task_config (
    code, name, workflow_type, task_queue, cron_expr, retry_policy, timeout_seconds,
    remark, is_enabled, deleted_at, created_by, updated_by
) VALUES (
    'log_count_tick',
    '日志计数+1(每10s)',
    'LogCountTickWorkflow',
    'demo',
    '0/10 * * * * ?',
    JSON_OBJECT('maxAttempts', 1, 'initialInterval', '5s', 'backoff', 1.0),
    NULL,
    'dev 测试：触发后 Workflow 循环 sleep 10s，Activity 将 count+1 并 log',
    1,
    0,
    0,
    0
);

SET FOREIGN_KEY_CHECKS = 1;

