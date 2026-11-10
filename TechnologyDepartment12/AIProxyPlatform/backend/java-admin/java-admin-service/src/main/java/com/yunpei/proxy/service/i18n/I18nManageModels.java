package com.yunpei.proxy.service.i18n;

import com.google.common.base.Splitter;
import com.yunpei.proxy.service.entity.I18nLocale;
import com.yunpei.proxy.service.entity.I18nTranslation;
import io.github.linpeilie.annotations.AutoMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * i18n_locale / i18n_translation 领域模型（service 层，不绑 HTTP 注解）。
 *
 * @author yunpei
 */
public final class I18nManageModels {

    private I18nManageModels() {}

    private static final Splitter KEY_SEGMENTS = Splitter.on('.');

    /** BCP-47 风格：如 zh-CN / en-US / ja-JP。 */
    public static final String LOCALE_CODE_PATTERN = "^[A-Za-z]{2,3}(-[A-Za-z]{2,4})?$";

    // ---------- locale ----------

    public record LocaleListQuery(
            int page, int pageSize, List<String> codeExact, String codeLike, String name, Integer status) {

        public static LocaleListQuery of(
                Integer page, Integer pageSize, List<String> code, String name, Integer status) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            CodeFilter filter = parseCodeFilter(code);
            return new LocaleListQuery(pageNo, size, filter.exact(), filter.like(), trimToNull(name), status);
        }

        public static LocaleListQuery allFilter(List<String> code, String name, Integer status) {
            CodeFilter filter = parseCodeFilter(code);
            return new LocaleListQuery(1, Integer.MAX_VALUE, filter.exact(), filter.like(), trimToNull(name), status);
        }
    }

    public record CreateLocaleCommand(
            String code, String name, Integer sort, String remark, Integer isDefault, Integer isEnabled) {}

    public record UpdateLocaleCommand(
            Long id, String code, String name, Integer sort, String remark, Integer isDefault, Integer isEnabled) {}

    @AutoMapper(target = I18nLocale.class)
    public record LocaleView(
            Long id,
            String code,
            String name,
            Integer isDefault,
            Integer sort,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy) {
        public LocaleView {
            isDefault = isDefault == null ? 0 : isDefault;
            sort = sort == null ? 0 : sort;
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    public record BatchCommand(String action, List<Long> ids) {}

    public record BatchResult(String action, int affected, List<Long> ids) {}

    // ---------- translation ----------

    public record TranslationListQuery(
            int page, int pageSize, Long localeId, String localeCode, String value, Integer status, boolean byKey) {

        public static TranslationListQuery of(
                Integer page,
                Integer pageSize,
                Long localeId,
                String localeCode,
                String value,
                Integer status,
                String byKey) {
            int pageNo = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
            boolean keyMode = "true".equalsIgnoreCase(byKey) || "1".equals(byKey);
            return new TranslationListQuery(
                    pageNo, size, localeId, trimToNull(localeCode), trimToNull(value), status, keyMode);
        }
    }

    public record CreateTranslationCommand(
            Long localeId, String translationKey, String value, String remark, Integer isEnabled) {}

    public record UpdateTranslationCommand(
            Long id, String translationKey, String value, String remark, Integer isEnabled) {}

    /** localeCode 为 enrich 字段，convert 后由 Service 补入。 */
    @AutoMapper(target = I18nTranslation.class)
    public record TranslationView(
            Long id,
            Long localeId,
            String translationKey,
            String value,
            String remark,
            Integer isEnabled,
            Long deletedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long createdBy,
            Long updatedBy,
            String localeCode) {
        public TranslationView {
            deletedAt = deletedAt == null ? 0L : deletedAt;
            createdBy = createdBy == null ? 0L : createdBy;
            updatedBy = updatedBy == null ? 0L : updatedBy;
        }
    }

    /** list ?byKey=true 聚合行。 */
    public record TranslationKeyView(
            String translationKey,
            int localeCount,
            Long sampleRowId,
            Long sampleLocaleId,
            String sampleLocaleCode,
            LocalDateTime sampleUpdatedAt) {}

    public record TranslationByKeyView(String translationKey, List<TranslationView> values) {}

    /**
     * 公开翻译包（对齐 mock {@code GET /api/public/i18n/:code}）。
     *
     * @param unchanged 客户端 hash 与服务端一致时为 true，此时 hash/data 可为 null
     * @param hash 内容 hash（SHA256 前 8 位 hex）；unchanged 时为 null
     * @param data translationKey → value；unchanged 时为 null
     */
    public record PublicI18nBundle(boolean unchanged, String hash, Map<String, String> data) {
        /** hash 一致：无新数据。 */
        public static PublicI18nBundle noChange() {
            return new PublicI18nBundle(true, null, null);
        }

        public static PublicI18nBundle of(String hash, Map<String, String> data) {
            return new PublicI18nBundle(false, hash, data == null ? Map.of() : Map.copyOf(data));
        }
    }

    public record BatchUpsertItem(Long localeId, String value, String remark, Integer isEnabled) {}

    public record BatchUpsertByKeyCommand(
            String translationKey, String newTranslationKey, List<BatchUpsertItem> items, List<Long> deletedIds) {}

    public record BatchUpsertError(String code, String message, Long localeId, Long id) {}

    public record BatchUpsertAffected(int renamed, int created, int updated, int deleted) {}

    public record BatchUpsertByKeyResult(
            boolean ok, BatchUpsertAffected affected, List<TranslationView> values, List<BatchUpsertError> errors) {}

    // ---------- import / export ----------

    public record ExportBatchCommand(List<Long> ids, String format) {}

    public record ExportBatchFile(String code, String format, Map<String, Object> content) {}

    public record ExportBatchResult(List<ExportBatchFile> files) {}

    public record ExportCommand(List<Long> ids, String type) {}

    public record ImportBatchItem(String name, String prefix, String localeCode, String format, Object payload) {}

    public record ImportBatchCommand(List<ImportBatchItem> items) {}

    public record ImportPerFileResult(
            String name, boolean ok, String error, int createdLocales, int softDeleted, int createdTranslations) {}

    public record ImportBatchAffected(
            int createdLocales, int softDeleted, int createdTranslations, List<ImportPerFileResult> perFile) {}

    public record ImportBatchResult(boolean ok, ImportBatchAffected affected) {}

    public record ImportPreviewItem(String localeCode, List<String> keys) {}

    public record ImportPreviewCommand(List<ImportPreviewItem> items) {}

    public record ImportPreviewResult(List<TranslationView> currentRows) {}

    // ---------- helpers ----------

    private record CodeFilter(List<String> exact, String like) {}

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

    static int normalize01Bool(Boolean value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value ? 1 : 0;
    }

    /** 嵌套字典扁平化为 key/value 列表。 */
    @SuppressWarnings("unchecked")
    static List<Map.Entry<String, String>> unflatten(Map<String, Object> obj, String prefix) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        if (obj == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            String k = e.getKey();
            if ("@type".equals(k)) {
                continue;
            }
            Object v = e.getValue();
            String next = prefix == null || prefix.isEmpty() ? k : prefix + "." + k;
            if (v instanceof Map<?, ?> map) {
                out.addAll(unflatten((Map<String, Object>) map, next));
            } else if (v instanceof String s) {
                out.add(Map.entry(next, s));
            }
        }
        return out;
    }

    /** 扁平 key 组装嵌套字典。 */
    static Map<String, Object> flattenToDict(List<Map.Entry<String, String>> entries) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries) {
            String key = e.getKey();
            String value = e.getValue();
            List<String> parts = KEY_SEGMENTS.splitToList(key);
            Map<String, Object> cur = out;
            for (int i = 0; i < parts.size() - 1; i++) {
                String p = parts.get(i);
                Object next = cur.get(p);
                if (!(next instanceof Map<?, ?>)) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    cur.put(p, child);
                    cur = child;
                } else {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> child = (Map<String, Object>) next;
                    cur = child;
                }
            }
            cur.put(parts.get(parts.size() - 1), value);
        }
        return out;
    }
}
