package com.yunpei.proxy.service.api;

import java.util.List;

/**
 * 接口同步清单（对齐 schema_data / mock API_SYNC_MANIFEST；permission_code 已消歧）。
 *
 * @author yunpei
 */
public final class ApiSyncManifest {

    private ApiSyncManifest() {}

    public record Entry(String name, String method, String path, String permissionCode, String apiGroup) {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("权限码列表", "GET", "/api/auth/codes", "auth:codes", "会话"),
            new Entry("当前用户信息", "GET", "/api/user/info", "user:info", "会话"),
            new Entry("用户菜单路由", "GET", "/api/menu/all", "menu:all", "会话"),
            new Entry("文件上传", "POST", "/api/upload", "system:upload", "会话"),
            new Entry("用户分页列表", "GET", "/api/system/user/list", "system:user:list", "用户管理"),
            new Entry("创建用户", "POST", "/api/system/user", "system:user:create", "用户管理"),
            new Entry("更新用户", "PUT", "/api/system/user/:id", "system:user:update", "用户管理"),
            new Entry("删除用户", "DELETE", "/api/system/user/:id", "system:user:delete", "用户管理"),
            new Entry("启停用户", "PUT", "/api/system/user/:id/status", "system:user:status", "用户管理"),
            new Entry("重置用户密码", "POST", "/api/system/user/:id/password", "system:user:password", "用户管理"),
            new Entry("角色分页列表", "GET", "/api/system/role/list", "system:role:list", "角色管理"),
            new Entry("角色全量列表", "GET", "/api/system/role/all", "system:role:list__system_role_all", "角色管理"),
            new Entry("创建角色", "POST", "/api/system/role", "system:role:create", "角色管理"),
            new Entry("更新角色", "PUT", "/api/system/role/:id", "system:role:update", "角色管理"),
            new Entry("删除角色", "DELETE", "/api/system/role/:id", "system:role:delete", "角色管理"),
            new Entry("角色已绑菜单", "GET", "/api/system/role/:id/menus", "system:role:menu", "角色管理"),
            new Entry(
                    "分配角色菜单", "POST", "/api/system/role/:id/menus", "system:role:menu__system_role__id_menus", "角色管理"),
            new Entry("角色已绑接口", "GET", "/api/system/role/:id/apis", "system:role:api", "角色管理"),
            new Entry("分配角色接口", "POST", "/api/system/role/:id/apis", "system:role:api__system_role__id_apis", "角色管理"),
            new Entry("菜单分页列表", "GET", "/api/system/menu/list", "system:menu:list", "菜单管理"),
            new Entry("菜单全量列表", "GET", "/api/system/menu/all", "system:menu:list__system_menu_all", "菜单管理"),
            new Entry("创建菜单", "POST", "/api/system/menu", "system:menu:create", "菜单管理"),
            new Entry("更新菜单", "PUT", "/api/system/menu/:id", "system:menu:update", "菜单管理"),
            new Entry("删除菜单", "DELETE", "/api/system/menu/:id", "system:menu:delete", "菜单管理"),
            new Entry("批量操作菜单", "POST", "/api/system/menu/batch", "system:menu:batch", "菜单管理"),
            new Entry(
                    "菜单名是否存在",
                    "GET",
                    "/api/system/menu/name-exists",
                    "system:menu:list__system_menu_name-exists",
                    "菜单管理"),
            new Entry(
                    "菜单路径是否存在",
                    "GET",
                    "/api/system/menu/path-exists",
                    "system:menu:list__system_menu_path-exists",
                    "菜单管理"),
            new Entry("菜单已绑接口", "GET", "/api/system/menu/:id/apis", "system:menu:api", "菜单管理"),
            new Entry("设置菜单接口", "POST", "/api/system/menu/:id/apis", "system:menu:api__system_menu__id_apis", "菜单管理"),
            new Entry("接口分页列表", "GET", "/api/system/api/list", "system:api:list", "接口管理"),
            new Entry("接口全量列表", "GET", "/api/system/api/all", "system:api:list__system_api_all", "接口管理"),
            new Entry("接口分组列表", "GET", "/api/system/api/groups", "system:api:list__system_api_groups", "接口管理"),
            new Entry("创建接口", "POST", "/api/system/api", "system:api:create", "接口管理"),
            new Entry("更新接口", "PUT", "/api/system/api/:id", "system:api:update", "接口管理"),
            new Entry("删除接口", "DELETE", "/api/system/api/:id", "system:api:delete", "接口管理"),
            new Entry("批量操作接口", "POST", "/api/system/api/batch", "system:api:batch", "接口管理"),
            new Entry("同步接口", "POST", "/api/system/api/sync", "system:api:sync", "接口管理"),
            new Entry("字典类型分页", "GET", "/api/system/dict-type/list", "system:dict:list", "字典管理"),
            new Entry("字典类型全量", "GET", "/api/system/dict-type/all", "system:dict:list__system_dict-type_all", "字典管理"),
            new Entry("字典类型详情", "GET", "/api/system/dict-type/:id", "system:dict:list__system_dict-type__id", "字典管理"),
            new Entry("创建字典类型", "POST", "/api/system/dict-type", "system:dict:create", "字典管理"),
            new Entry("更新字典类型", "PUT", "/api/system/dict-type/:id", "system:dict:update", "字典管理"),
            new Entry("删除字典类型", "DELETE", "/api/system/dict-type/:id", "system:dict:delete", "字典管理"),
            new Entry("批量操作字典类型", "POST", "/api/system/dict-type/batch", "system:dict:batch", "字典管理"),
            new Entry("字典数据分页", "GET", "/api/system/dict-data/list", "system:dict:data:list", "字典管理"),
            new Entry(
                    "按类型查字典数据",
                    "GET",
                    "/api/system/dict-data/by-type/:code",
                    "system:dict:data:list__system_dict-data_by-type__code",
                    "字典管理"),
            new Entry("创建字典数据", "POST", "/api/system/dict-data", "system:dict:data:create", "字典管理"),
            new Entry("更新字典数据", "PUT", "/api/system/dict-data/:id", "system:dict:data:update", "字典管理"),
            new Entry("删除字典数据", "DELETE", "/api/system/dict-data/:id", "system:dict:data:delete", "字典管理"),
            new Entry("批量操作字典数据", "POST", "/api/system/dict-data/batch", "system:dict:data:batch", "字典管理"),
            new Entry("语言分页列表", "GET", "/api/system/i18n-locale/list", "system:i18n:list", "国际化"),
            new Entry(
                    "语言全量列表", "GET", "/api/system/i18n-locale/all", "system:i18n:list__system_i18n-locale_all", "国际化"),
            new Entry("语言详情", "GET", "/api/system/i18n-locale/:id", "system:i18n:list__system_i18n-locale__id", "国际化"),
            new Entry("创建语言", "POST", "/api/system/i18n-locale", "system:i18n:create", "国际化"),
            new Entry("更新语言", "PUT", "/api/system/i18n-locale/:id", "system:i18n:update", "国际化"),
            new Entry("删除语言", "DELETE", "/api/system/i18n-locale/:id", "system:i18n:delete", "国际化"),
            new Entry("批量操作语言", "POST", "/api/system/i18n-locale/batch", "system:i18n:batch", "国际化"),
            new Entry("导出语言", "GET", "/api/system/i18n-locale/export", "system:i18n:export", "国际化"),
            new Entry(
                    "批量导出语言",
                    "POST",
                    "/api/system/i18n-locale/export-batch",
                    "system:i18n:export__system_i18n-locale_export-batch",
                    "国际化"),
            new Entry(
                    "翻译分页列表",
                    "GET",
                    "/api/system/i18n-translation/list",
                    "system:i18n:list__system_i18n-translation_list",
                    "国际化"),
            new Entry(
                    "按语言查翻译",
                    "GET",
                    "/api/system/i18n-translation/by-locale/:code",
                    "system:i18n:list__system_i18n-translation_by-locale__code",
                    "国际化"),
            new Entry(
                    "按 key 查翻译",
                    "GET",
                    "/api/system/i18n-translation/by-key/:key",
                    "system:i18n:list__system_i18n-translation_by-key__key",
                    "国际化"),
            new Entry(
                    "创建翻译",
                    "POST",
                    "/api/system/i18n-translation",
                    "system:i18n:create__system_i18n-translation",
                    "国际化"),
            new Entry(
                    "更新翻译",
                    "PUT",
                    "/api/system/i18n-translation/:id",
                    "system:i18n:update__system_i18n-translation__id",
                    "国际化"),
            new Entry(
                    "删除翻译",
                    "DELETE",
                    "/api/system/i18n-translation/:id",
                    "system:i18n:delete__system_i18n-translation__id",
                    "国际化"),
            new Entry(
                    "批量操作翻译",
                    "POST",
                    "/api/system/i18n-translation/batch",
                    "system:i18n:batch__system_i18n-translation_batch",
                    "国际化"),
            new Entry(
                    "按 key 批量 upsert 翻译",
                    "POST",
                    "/api/system/i18n-translation/batch-upsert-by-key",
                    "system:i18n:update__system_i18n-translation_batch-upsert-by-key",
                    "国际化"),
            new Entry("导入翻译预览", "POST", "/api/system/i18n-translation/import-preview", "system:i18n:import", "国际化"),
            new Entry(
                    "批量导入翻译",
                    "POST",
                    "/api/system/i18n-translation/import-batch",
                    "system:i18n:import__system_i18n-translation_import-batch",
                    "国际化"),
            new Entry("登录日志分页列表", "GET", "/api/system/login-log/list", "log:login-log:list", "日志审计"),
            new Entry("API 日志分页列表", "GET", "/api/system/api-log/list", "log:api-log:list", "日志审计"),
            new Entry("任务配置分页", "GET", "/api/system/task-config/list", "task:config:list", "任务调度"),
            new Entry(
                    "工作流类型选项",
                    "GET",
                    "/api/system/task-config/workflow-types",
                    "task:config:list__system_task-config_workflow-types",
                    "任务调度"),
            new Entry(
                    "任务队列选项",
                    "GET",
                    "/api/system/task-config/task-queues",
                    "task:config:list__system_task-config_task-queues",
                    "任务调度"),
            new Entry(
                    "任务配置详情", "GET", "/api/system/task-config/:id", "task:config:list__system_task-config__id", "任务调度"),
            new Entry("创建任务配置", "POST", "/api/system/task-config", "task:config:create", "任务调度"),
            new Entry("更新任务配置", "PUT", "/api/system/task-config/:id", "task:config:update", "任务调度"),
            new Entry("删除任务配置", "DELETE", "/api/system/task-config/:id", "task:config:delete", "任务调度"),
            new Entry("批量操作任务配置", "POST", "/api/system/task-config/batch", "task:config:batch", "任务调度"),
            new Entry("手动触发任务配置", "POST", "/api/system/task-config/:id/trigger", "task:config:trigger", "任务调度"),
            new Entry("任务执行分页", "GET", "/api/system/task-execution/list", "task:execution:list", "任务调度"),
            new Entry(
                    "任务执行详情",
                    "GET",
                    "/api/system/task-execution/:id",
                    "task:execution:list__system_task-execution__id",
                    "任务调度"));
}
