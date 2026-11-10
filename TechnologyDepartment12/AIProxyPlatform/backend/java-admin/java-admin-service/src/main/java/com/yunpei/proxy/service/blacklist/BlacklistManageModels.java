package com.yunpei.proxy.service.blacklist;

import com.yunpei.proxy.service.entity.SysBlacklist;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 访问黑名单领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class BlacklistManageModels {

    private BlacklistManageModels() {}

    public static final Set<String> TARGET_TYPES = Set.of("IP", "USER", "DEVICE");
    public static final Set<String> SCOPES = Set.of("LOGIN", "API", "ALL");

    public record BlacklistListQuery(
            int page, int pageSize, String targetType, String targetValue, String scope, Integer status) {

        public static BlacklistListQuery of(
                Integer page, Integer pageSize, String targetType, String targetValue, String scope, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            return new BlacklistListQuery(
                    pageNo,
                    size,
                    normalizeFilterEnum(targetType),
                    trimToNull(targetValue),
                    normalizeFilterEnum(scope),
                    status);
        }

        public static BlacklistListQuery allFilter(
                String targetType, String targetValue, String scope, Integer status) {
            return new BlacklistListQuery(
                    1,
                    Integer.MAX_VALUE,
                    normalizeFilterEnum(targetType),
                    trimToNull(targetValue),
                    normalizeFilterEnum(scope),
                    status);
        }
    }

    public record CreateBlacklistCommand(
            String targetType,
            String targetValue,
            String scope,
            String reason,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            String remark,
            Integer isEnabled) {}

    /** 更新命令；字段 null 表示不改。 */
    public record UpdateBlacklistCommand(
            Long id,
            String targetType,
            String targetValue,
            String scope,
            String reason,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Boolean clearExpiresAt,
            String remark,
            Integer isEnabled) {}

    @AutoMapper(target = SysBlacklist.class)
    public record BlacklistView(
            Long id,
            String targetType,
            String targetValue,
            String scope,
            String reason,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public BlacklistView {
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
            reason = reason == null ? "" : reason;
            remark = remark == null ? "" : remark;
        }
    }

    public record BlacklistBatchCommand(String action, List<Long> ids) {}

    public record BlacklistBatchResult(String action, int affected, List<Long> ids) {}

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 列表筛选用：trim + 大写，与写入路径枚举规范化一致。 */
    static String normalizeFilterEnum(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static int normalize01(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 0 ? 0 : 1;
    }

    static String normalizeTargetType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    static String normalizeScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ALL";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化 target_value：USER/DEVICE 原样 trim；IP 去端口倾向的尾缀并小写化 IPv6。
     */
    static String normalizeTargetValue(String targetType, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if ("IP".equals(targetType)) {
            // 去可能的 :port（仅 IPv4 host:port；IPv6 带端口通常为 [addr]:port）
            if (value.startsWith("[") && value.contains("]:")) {
                value = value.substring(1, value.indexOf("]:"));
            } else if (value.chars().filter(c -> c == ':').count() == 1 && value.contains(".")) {
                value = value.substring(0, value.indexOf(':'));
            }
            return value.toLowerCase(Locale.ROOT);
        }
        return value;
    }
}
