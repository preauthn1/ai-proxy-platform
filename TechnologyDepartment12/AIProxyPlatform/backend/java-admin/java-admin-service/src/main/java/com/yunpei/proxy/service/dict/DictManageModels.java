package com.yunpei.proxy.service.dict;

import com.yunpei.proxy.service.entity.DictData;
import com.yunpei.proxy.service.entity.DictType;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 字典类型 / 字典数据领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class DictManageModels {

    private DictManageModels() {}

    public static final Set<String> ALLOWED_PLATFORMS = Set.of("general", "react-admin", "vue-admin");

    public static final Set<String> ALLOWED_TAG_TYPES = Set.of(
            "default",
            "primary",
            "success",
            "warning",
            "error",
            "processing",
            "magenta",
            "red",
            "volcano",
            "orange",
            "gold",
            "lime",
            "green",
            "cyan",
            "blue",
            "geekblue",
            "purple");

    /** 字典类型编码：小写字母开头，后续可含数字与下划线，最长 64。 */
    public static final String CODE_PATTERN = "^[a-z][a-z0-9_]{0,63}$";

    // ---------- dict-type ----------

    public record DictTypeListQuery(
            int page, int pageSize, List<String> codeExact, String codeLike, String name, Integer status) {

        public static DictTypeListQuery of(
                Integer page, Integer pageSize, List<String> code, String name, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            CodeFilter filter = parseCodeFilter(code);
            return new DictTypeListQuery(pageNo, size, filter.exact(), filter.like(), trimToNull(name), status);
        }

        public static DictTypeListQuery allFilter(List<String> code, String name, Integer status) {
            CodeFilter filter = parseCodeFilter(code);
            return new DictTypeListQuery(1, Integer.MAX_VALUE, filter.exact(), filter.like(), trimToNull(name), status);
        }
    }

    public record CreateDictTypeCommand(String code, String name, String remark, Integer isEnabled) {}

    /** 更新命令；字段 null 表示不改。 */
    public record UpdateDictTypeCommand(Long id, String code, String name, String remark, Integer isEnabled) {}

    @AutoMapper(target = DictType.class)
    public record DictTypeView(
            Long id,
            String code,
            String name,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public DictTypeView {
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record DictBatchCommand(String action, List<Long> ids) {}

    public record DictBatchResult(String action, int affected, List<Long> ids) {}

    // ---------- dict-data ----------

    public record DictDataListQuery(
            int page,
            int pageSize,
            Long typeId,
            List<String> typeCodeExact,
            String typeCodeLike,
            String label,
            String value,
            Integer status,
            String platform,
            boolean includeGeneral) {

        public static DictDataListQuery of(
                Integer page,
                Integer pageSize,
                Long typeId,
                List<String> typeCode,
                String label,
                String value,
                Integer status,
                String platform,
                Boolean includeGeneral) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            CodeFilter filter = parseCodeFilter(typeCode);
            return new DictDataListQuery(
                    pageNo,
                    size,
                    typeId,
                    filter.exact(),
                    filter.like(),
                    trimToNull(label),
                    trimToNull(value),
                    status,
                    trimToNull(platform),
                    Boolean.TRUE.equals(includeGeneral));
        }
    }

    /**
     * 创建命令。{@code isDefault} 用 Boolean 以兼容前端 Switch 提交的 true/false。
     */
    public record CreateDictDataCommand(
            Long typeId,
            String value,
            String label,
            Integer sort,
            Boolean isDefault,
            String platform,
            String tagType,
            Integer isEnabled,
            String remark) {}

    /** 更新命令；字段 null 表示不改。 */
    public record UpdateDictDataCommand(
            Long id,
            String value,
            String label,
            Integer sort,
            Integer isDefault,
            String platform,
            String tagType,
            Integer isEnabled,
            String remark) {}

    /** typeCode 为 enrich 字段，不在 Entity 上；convert 后由 Service 补入。 */
    @AutoMapper(target = DictData.class)
    public record DictDataView(
            Long id,
            Long typeId,
            String value,
            String label,
            Integer sort,
            Integer isDefault,
            String platform,
            String tagType,
            Integer isEnabled,
            Long deletedAt,
            String remark,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            String typeCode) {
        public DictDataView {
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    // ---------- helpers ----------

    private record CodeFilter(List<String> exact, String like) {}

    /**
     * 多值 → 精确匹配；单值 → 模糊包含；空/null → 不过滤。
     */
    private static CodeFilter parseCodeFilter(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new CodeFilter(null, null);
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String t = item.trim();
            if (!t.isEmpty()) {
                cleaned.add(t);
            }
        }
        if (cleaned.isEmpty()) {
            return new CodeFilter(null, null);
        }
        if (cleaned.size() == 1) {
            return new CodeFilter(null, cleaned.get(0));
        }
        return new CodeFilter(List.copyOf(cleaned), null);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    static String normalizePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return "general";
        }
        return platform.trim();
    }

    static String normalizeTagType(String tagType) {
        if (tagType == null || tagType.isBlank()) {
            return "default";
        }
        return tagType.trim().toLowerCase(Locale.ROOT);
    }
}
