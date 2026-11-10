package com.yunpei.proxy.service.menu;

import com.google.common.base.Splitter;
import com.yunpei.proxy.service.entity.SysMenu;
import com.yunpei.proxy.service.menu.MenuManageModels.RuntimeMenuRoute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 sys_menu 扁平行投影为前端动态路由树（对齐 mock {@code menu-route-project.ts}）。
 *
 * <p>纯函数、无 Spring 依赖，便于单测。metadata 用轻量解析，避免 service 模块依赖 jackson-databind。
 *
 * @author yunpei
 */
public final class RuntimeMenuProjector {

    private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern NUMBER_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    private static final Pattern BOOL_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");
    private static final Pattern STRING_ARRAY_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\[([^\\]]*)]");
    private static final Splitter PATH_SEGMENTS = Splitter.on('/');
    private static final Splitter WHITESPACE = Splitter.onPattern("\\s+").omitEmptyStrings();

    private RuntimeMenuProjector() {}

    /** Include ancestor menus so granted leaves still form a tree. */
    public static Set<Long> expandMenuIdsWithAncestors(Iterable<Long> grantedIds, List<SysMenu> menus) {
        Map<Long, SysMenu> byId = indexById(menus);
        Set<Long> out = new HashSet<>();
        for (Long id : grantedIds) {
            SysMenu current = byId.get(id);
            while (current != null) {
                out.add(current.getId());
                if (current.getParentId() == null) {
                    break;
                }
                current = byId.get(current.getParentId());
            }
        }
        return out;
    }

    /**
     * 自身 + 祖先链均未删除且已启用。
     * 父 DIR 禁用时，子节点不得升为根出现在动态菜单中。
     */
    public static boolean isRuntimeMenuEligible(SysMenu menu, Map<Long, SysMenu> byId) {
        if (menu.getIsEnabled() == null || menu.getIsEnabled() != 1) {
            return false;
        }
        Long parentId = menu.getParentId();
        while (parentId != null) {
            SysMenu parent = byId.get(parentId);
            if (parent == null || parent.getIsEnabled() == null || parent.getIsEnabled() != 1) {
                return false;
            }
            parentId = parent.getParentId();
        }
        return true;
    }

    /**
     * Build nested runtime routes from a flat SysMenu list and a set of allowed ids.
     * BUTTON nodes contribute no route; empty DIR branches are pruned.
     */
    public static List<RuntimeMenuRoute> buildRuntimeMenuTree(List<SysMenu> menus, Set<Long> allowedIds) {
        Map<Long, SysMenu> menuById = indexById(menus);
        List<SysMenu> usable = menus.stream()
                .filter(m -> allowedIds.contains(m.getId()))
                .filter(m -> !MenuManageModels.TYPE_BUTTON.equals(m.getType()))
                .filter(m -> isRuntimeMenuEligible(m, menuById))
                .sorted(Comparator.comparingInt((SysMenu m) -> m.getSort() == null ? 0 : m.getSort())
                        .thenComparing(SysMenu::getId))
                .toList();

        Map<Long, MutableNode> byId = new LinkedHashMap<>();
        for (SysMenu menu : usable) {
            RuntimeMenuRoute route = sysMenuToRouteRecord(menu);
            if (route == null) {
                continue;
            }
            byId.put(
                    menu.getId(),
                    new MutableNode(
                            menu.getParentId(),
                            route.name(),
                            route.path(),
                            route.component(),
                            route.redirect(),
                            route.meta(),
                            new ArrayList<>()));
        }

        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode node : byId.values()) {
            if (node.parentId != null && byId.containsKey(node.parentId)) {
                byId.get(node.parentId).children.add(node);
            } else {
                roots.add(node);
            }
        }
        return strip(roots);
    }

    /** Project one SysMenu row into a runtime route node (DIR/MENU only). */
    public static RuntimeMenuRoute sysMenuToRouteRecord(SysMenu menu) {
        if (MenuManageModels.TYPE_BUTTON.equals(menu.getType())) {
            return null;
        }
        if (menu.getIsEnabled() == null || menu.getIsEnabled() != 1) {
            return null;
        }

        Map<String, Object> metaBag = parseMetadata(menu.getMetadata());
        String path = trimToEmpty(menu.getPath());
        if (path.isEmpty() && MenuManageModels.TYPE_DIR.equals(menu.getType())) {
            path = "/" + routeNameFromPath(null, menu.getName()).toLowerCase(Locale.ROOT);
        }
        if (path.isEmpty()) {
            return null;
        }

        String name;
        Object routeName = metaBag.get("routeName");
        if (routeName instanceof String s && !s.isBlank()) {
            name = s;
        } else {
            name = routeNameFromPath(menu.getPath(), menu.getName());
        }

        String title;
        Object titleObj = metaBag.get("title");
        if (titleObj instanceof String s && !s.isBlank()) {
            title = s;
        } else {
            title = menu.getName();
        }

        String icon = null;
        Object iconObj = metaBag.get("icon");
        if (iconObj instanceof String s && !s.isBlank()) {
            icon = s;
        } else if (menu.getIcon() != null && !menu.getIcon().isBlank()) {
            icon = menu.getIcon();
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", title);
        Object orderObj = metaBag.get("order");
        if (orderObj instanceof Number n) {
            meta.put("order", n.intValue());
        } else {
            meta.put("order", menu.getSort() == null ? 0 : menu.getSort());
        }
        if (icon != null) {
            meta.put("icon", icon);
        }
        if (menu.getIsHidden() != null && menu.getIsHidden() == 1) {
            meta.put("hideInMenu", true);
        }

        // 仅当 metadata 显式声明 authority 时才透传
        Object authority = metaBag.get("authority");
        if (authority instanceof List<?> list && !list.isEmpty()) {
            meta.put("authority", authority);
        }

        putIfBoolean(meta, "affixTab", firstNonNull(metaBag.get("affixTab"), metaBag.get("affix")));
        Object activePath = firstNonNull(metaBag.get("activePath"), metaBag.get("activeMenu"));
        if (activePath instanceof String s && !s.isBlank()) {
            meta.put("activePath", s);
        }
        putIfBoolean(meta, "keepAlive", metaBag.get("keepAlive"));
        putIfBoolean(meta, "hideInBreadcrumb", metaBag.get("hideInBreadcrumb"));
        putIfString(meta, "badge", metaBag.get("badge"));
        putIfString(meta, "badgeType", metaBag.get("badgeType"));
        putIfString(meta, "badgeVariants", metaBag.get("badgeVariants"));

        Set<String> mapped = Set.of(
                "routeName",
                "title",
                "icon",
                "order",
                "authority",
                "affix",
                "affixTab",
                "activeMenu",
                "activePath",
                "keepAlive",
                "hideInBreadcrumb",
                "badge",
                "badgeType",
                "badgeVariants");
        for (Map.Entry<String, Object> e : metaBag.entrySet()) {
            if (mapped.contains(e.getKey())) {
                continue;
            }
            meta.putIfAbsent(e.getKey(), e.getValue());
        }

        String component = normalizeComponent(menu.getComponent());
        String redirect = trimToEmpty(menu.getRedirect());
        if (redirect.isEmpty()) {
            redirect = null;
        }
        return new RuntimeMenuRoute(name, path, component, redirect, meta, null);
    }

    /** /system/user -> SystemUser; dashboard -> Dashboard */
    public static String routeNameFromPath(String path, String fallback) {
        String cleaned = path == null ? "" : path;
        cleaned = cleaned.replaceFirst("^/", "").replaceAll("(?i)/index$", "");
        List<String> parts = new ArrayList<>();
        for (String seg : PATH_SEGMENTS.split(cleaned)) {
            if (seg == null || seg.isBlank()) {
                continue;
            }
            String normalized = seg.replaceAll("[^a-zA-Z0-9]+", " ").trim();
            for (String word : WHITESPACE.split(normalized)) {
                parts.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        if (parts.isEmpty()) {
            String fb = fallback == null ? "" : fallback.replaceAll("\\s+", "");
            return fb.isEmpty() ? "Menu" : fb;
        }
        return String.join("", parts);
    }

    static String normalizeComponent(String component) {
        if (component == null || component.isBlank()) {
            return null;
        }
        String value = component.trim().replace('\\', '/');
        value = value.replaceAll("^(\\.\\./|\\./)+", "");
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        value = value.replaceAll("(?i)^/(src/)?(views|pages)(/app)?/", "/");
        value = value.replaceAll("/+$", "");
        return value.isEmpty() ? null : value;
    }

    /**
     * 轻量解析 metadata JSON 对象：提取 string / number / boolean / string[]。
     * 非法 JSON 返回空 map，不抛异常。
     */
    static Map<String, Object> parseMetadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String text = raw.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Matcher sm = STRING_FIELD.matcher(text);
        while (sm.find()) {
            out.put(sm.group(1), unescapeJson(sm.group(2)));
        }
        Matcher nm = NUMBER_FIELD.matcher(text);
        while (nm.find()) {
            String key = nm.group(1);
            if (out.containsKey(key)) {
                continue;
            }
            String num = nm.group(2);
            if (num.contains(".")) {
                out.put(key, Double.parseDouble(num));
            } else {
                out.put(key, Integer.parseInt(num));
            }
        }
        Matcher bm = BOOL_FIELD.matcher(text);
        while (bm.find()) {
            String key = bm.group(1);
            if (!out.containsKey(key)) {
                out.put(key, Boolean.parseBoolean(bm.group(2)));
            }
        }
        Matcher am = STRING_ARRAY_FIELD.matcher(text);
        while (am.find()) {
            String key = am.group(1);
            if (out.containsKey(key)) {
                continue;
            }
            List<String> items = new ArrayList<>();
            Matcher item = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(am.group(2));
            while (item.find()) {
                items.add(unescapeJson(item.group(1)));
            }
            if (!items.isEmpty()) {
                out.put(key, items);
            }
        }
        return out;
    }

    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static List<RuntimeMenuRoute> strip(List<MutableNode> list) {
        List<RuntimeMenuRoute> out = new ArrayList<>();
        for (MutableNode n : list) {
            List<RuntimeMenuRoute> children = n.children.isEmpty() ? null : strip(n.children);
            if (n.component == null && (children == null || children.isEmpty())) {
                continue;
            }
            String redirect = n.redirect;
            if ((redirect == null || redirect.isBlank()) && children != null && !children.isEmpty()) {
                redirect = children.get(0).path();
            }
            out.add(new RuntimeMenuRoute(n.name, n.path, n.component, redirect, n.meta, children));
        }
        return out;
    }

    private static Map<Long, SysMenu> indexById(List<SysMenu> menus) {
        Map<Long, SysMenu> byId = new HashMap<>();
        for (SysMenu m : menus) {
            byId.put(m.getId(), m);
        }
        return byId;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static void putIfBoolean(Map<String, Object> meta, String key, Object value) {
        if (value instanceof Boolean b) {
            meta.put(key, b);
        }
    }

    private static void putIfString(Map<String, Object> meta, String key, Object value) {
        if (value instanceof String s && !s.isBlank()) {
            meta.put(key, s);
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class MutableNode {
        final Long parentId;
        final String name;
        final String path;
        final String component;
        final String redirect;
        final Map<String, Object> meta;
        final List<MutableNode> children;

        MutableNode(
                Long parentId,
                String name,
                String path,
                String component,
                String redirect,
                Map<String, Object> meta,
                List<MutableNode> children) {
            this.parentId = parentId;
            this.name = name;
            this.path = path;
            this.component = component;
            this.redirect = redirect;
            this.meta = meta;
            this.children = children;
        }
    }
}
