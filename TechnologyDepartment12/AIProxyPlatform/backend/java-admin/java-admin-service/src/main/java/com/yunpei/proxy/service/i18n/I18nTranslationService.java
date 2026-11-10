package com.yunpei.proxy.service.i18n;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.I18nLocale;
import com.yunpei.proxy.service.entity.I18nTranslation;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertAffected;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertError;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportBatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportBatchFile;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportBatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchAffected;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPerFileResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationByKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateTranslationCommand;
import com.yunpei.proxy.service.repository.I18nLocaleRepository;
import com.yunpei.proxy.service.repository.I18nTranslationRepository;
import io.github.linpeilie.Converter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 翻译 Service：CRUD/list/by-key/by-locale/batch/batch-upsert/导入导出。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class I18nTranslationService {

    private final I18nTranslationRepository translationRepository;
    private final I18nLocaleRepository localeRepository;
    private final Converter converter;

    public PageData<?> page(TranslationListQuery query) {
        if (query.byKey()) {
            return pageByKey(query);
        }
        Long localeId = resolveLocaleId(query.localeId(), query.localeCode());
        // localeCode 无效 → 空页
        if (query.localeCode() != null && query.localeId() == null && localeId == null) {
            return PageData.of(List.of(), 0L);
        }
        EasyPageResult<I18nTranslation> page =
                translationRepository.page(query.page(), query.pageSize(), localeId, query.value(), query.status());
        List<I18nTranslation> rows = page.getData() == null ? List.of() : page.getData();
        Map<Long, String> codeMap =
                loadLocaleCodes(rows.stream().map(I18nTranslation::getLocaleId).toList());
        List<TranslationView> items =
                rows.stream().map(r -> toView(r, codeMap.get(r.getLocaleId()))).toList();
        return PageData.of(items, page.getTotal());
    }

    /**
     * byKey=true：忽略 localeId/localeCode，按 translationKey 聚合后内存分页。
     */
    private PageData<TranslationKeyView> pageByKey(TranslationListQuery query) {
        List<I18nTranslation> all = translationRepository.listFiltered(null, query.value(), query.status());
        Map<Long, String> codeMap = loadLocaleCodes(
                all.stream().map(I18nTranslation::getLocaleId).distinct().toList());

        Map<String, TranslationKeyView> byKey = new LinkedHashMap<>();
        for (I18nTranslation row : all) {
            TranslationKeyView existing = byKey.get(row.getTranslationKey());
            if (existing == null) {
                byKey.put(
                        row.getTranslationKey(),
                        new TranslationKeyView(
                                row.getTranslationKey(),
                                1,
                                row.getId(),
                                row.getLocaleId(),
                                codeMap.get(row.getLocaleId()),
                                row.getUpdatedAt()));
            } else {
                LocalDateTime sampleUpdated = existing.sampleUpdatedAt();
                if (row.getUpdatedAt() != null
                        && (sampleUpdated == null || row.getUpdatedAt().isAfter(sampleUpdated))) {
                    sampleUpdated = row.getUpdatedAt();
                }
                byKey.put(
                        row.getTranslationKey(),
                        new TranslationKeyView(
                                existing.translationKey(),
                                existing.localeCount() + 1,
                                existing.sampleRowId(),
                                existing.sampleLocaleId(),
                                existing.sampleLocaleCode(),
                                sampleUpdated));
            }
        }
        List<TranslationKeyView> allKeys = new ArrayList<>(byKey.values());
        long total = allKeys.size();
        int from = Math.max(0, (query.page() - 1) * query.pageSize());
        if (from >= allKeys.size()) {
            return PageData.of(List.of(), total);
        }
        int to = Math.min(allKeys.size(), from + query.pageSize());
        return PageData.of(allKeys.subList(from, to), total);
    }

    public List<TranslationView> listByLocaleCode(String code) {
        String localeCode = I18nManageModels.trimToNull(code);
        if (localeCode == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code is required");
        }
        I18nLocale locale = localeRepository.findByCode(localeCode);
        if (locale == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-locale " + localeCode + " not found");
        }
        return translationRepository.listEnabledByLocaleId(locale.getId()).stream()
                .map(t -> toView(t, locale.getCode()))
                .toList();
    }

    /**
     * 公开翻译包：按 locale code 返回启用中的 KV，并支持 hash 增量。
     *
     * <p>hash 算法对齐 mock {@code computeI18nHash}：对 {@code key=value} 按 key 排序后用 {@code \n}
     * 拼接，SHA-256 取 hex 前 8 位。
     *
     * @param code BCP-47 语言码（如 zh-CN）
     * @param clientHash 前端缓存 hash；一致时返回 {@link I18nManageModels.PublicI18nBundle#noChange()}
     */
    public I18nManageModels.PublicI18nBundle getPublicBundle(String code, String clientHash) {
        String localeCode = I18nManageModels.trimToNull(code);
        if (localeCode == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code is required");
        }
        I18nLocale locale = localeRepository.findByCode(localeCode);
        if (locale == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-locale " + localeCode + " not found");
        }

        Map<String, String> data = new TreeMap<>();
        for (I18nTranslation row : translationRepository.listEnabledByLocaleId(locale.getId())) {
            if (row.getTranslationKey() == null) {
                continue;
            }
            data.put(row.getTranslationKey(), row.getValue() == null ? "" : row.getValue());
        }

        String serverHash = computeI18nHash(data);
        String normalizedClient = clientHash == null ? "" : clientHash.trim();
        if (!normalizedClient.isEmpty() && normalizedClient.equals(serverHash)) {
            return I18nManageModels.PublicI18nBundle.noChange();
        }
        return I18nManageModels.PublicI18nBundle.of(serverHash, data);
    }

    /**
     * 与 mock {@code apps/backend-mock-template/utils/i18n-hash.ts} 一致。
     */
    static String computeI18nHash(Map<String, String> kvMap) {
        String sorted = kvMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + (e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("\n"));
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sorted.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            // 前 4 字节 → 8 位 hex
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public TranslationByKeyView getByKey(String key) {
        String translationKey = key == null ? "" : key.trim();
        if (translationKey.isEmpty()) {
            return new TranslationByKeyView("", List.of());
        }
        List<I18nTranslation> rows = translationRepository.listByTranslationKey(translationKey);
        Map<Long, String> codeMap =
                loadLocaleCodes(rows.stream().map(I18nTranslation::getLocaleId).toList());
        List<TranslationView> values =
                rows.stream().map(r -> toView(r, codeMap.get(r.getLocaleId()))).toList();
        return new TranslationByKeyView(translationKey, values);
    }

    public TranslationView create(CreateTranslationCommand cmd) {
        Long localeId = cmd.localeId();
        if (localeId == null || localeId <= 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "localeId is required");
        }
        String translationKey = requireNonBlank(cmd.translationKey(), "translationKey");
        if (translationKey.length() > 255) {
            throw BizException.of(ResultCode.PARAM_INVALID, "translationKey must be ≤ 255 chars");
        }
        String value = requireNonBlank(cmd.value(), "value");
        I18nLocale locale = localeRepository.findById(localeId);
        if (locale == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "locale " + localeId + " not found");
        }
        if (translationRepository.existsByLocaleAndKey(localeId, translationKey, null)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "translation_key " + translationKey + " already exists for " + locale.getCode());
        }

        I18nTranslation row = new I18nTranslation();
        row.setLocaleId(localeId);
        row.setTranslationKey(translationKey);
        row.setValue(value);
        row.setRemark(I18nManageModels.nullToEmpty(cmd.remark()).trim());
        row.setIsEnabled(I18nManageModels.normalize01(cmd.isEnabled(), 1));
        translationRepository.insert(row);
        I18nTranslation saved = translationRepository.findById(row.getId());
        return toView(saved, locale.getCode());
    }

    public TranslationView update(UpdateTranslationCommand cmd) {
        I18nTranslation row = requireTranslation(cmd.id());
        if (cmd.translationKey() != null) {
            String next = cmd.translationKey().trim();
            if (next.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "translationKey cannot be empty");
            }
            if (next.length() > 255) {
                throw BizException.of(ResultCode.PARAM_INVALID, "translationKey must be ≤ 255 chars");
            }
            if (translationRepository.existsByLocaleAndKey(row.getLocaleId(), next, row.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "translation_key " + next + " already exists");
            }
            row.setTranslationKey(next);
        }
        if (cmd.value() != null) {
            String v = cmd.value().trim();
            if (v.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "value cannot be empty");
            }
            row.setValue(v);
        }
        if (cmd.remark() != null) {
            row.setRemark(cmd.remark());
        }
        if (cmd.isEnabled() != null) {
            row.setIsEnabled(I18nManageModels.normalize01(cmd.isEnabled(), 1));
        }
        translationRepository.update(row);
        I18nTranslation saved = requireTranslation(row.getId());
        I18nLocale locale = localeRepository.findById(saved.getLocaleId());
        return toView(saved, locale == null ? null : locale.getCode());
    }

    public TranslationView softDelete(Long id) {
        I18nTranslation row = requireTranslation(id);
        I18nLocale locale = localeRepository.findById(row.getLocaleId());
        TranslationView snapshot = toView(row, locale == null ? null : locale.getCode());
        long rows = translationRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-translation " + id + " not found");
        }
        long deletedAt = System.currentTimeMillis();
        return new TranslationView(
                snapshot.id(),
                snapshot.localeId(),
                snapshot.translationKey(),
                snapshot.value(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy(),
                snapshot.localeCode());
    }

    public BatchResult batch(BatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!Set.of("enable", "disable", "delete").contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<I18nTranslation> targets = translationRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active i18n-translation found for given ids");
        }
        if ("delete".equals(action)) {
            List<Long> deleted = new ArrayList<>();
            for (I18nTranslation t : targets) {
                translationRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            return new BatchResult(action, deleted.size(), deleted);
        }
        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (I18nTranslation t : targets) {
            translationRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
        }
        return new BatchResult(action, affected.size(), affected);
    }

    /**
     * 单 key 多语言 upsert：rename → delete → upsert。
     *
     * <p>失败时返回 {@code ok=false + errors}（HTTP 仍 200 + Result.ok，与前端 onSuccess 分支对齐）。
     */
    public BatchUpsertByKeyResult batchUpsertByKey(BatchUpsertByKeyCommand cmd) {
        String translationKey = requireNonBlank(cmd.translationKey(), "translationKey");
        if (translationKey.length() > 255) {
            throw BizException.of(ResultCode.PARAM_INVALID, "translationKey must be ≤ 255 chars");
        }
        String newKey =
                cmd.newTranslationKey() == null ? null : cmd.newTranslationKey().trim();
        if (newKey != null && (newKey.isEmpty() || newKey.length() > 255)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "newTranslationKey must be 1..255 chars");
        }

        List<BatchUpsertError> errors = new ArrayList<>();
        int renamed = 0;
        int created = 0;
        int updated = 0;
        int deleted = 0;

        // Stage 1: rename
        if (newKey != null && !newKey.equals(translationKey)) {
            List<I18nTranslation> renameTargets = translationRepository.listByTranslationKey(translationKey);
            for (I18nTranslation row : renameTargets) {
                if (translationRepository.existsByLocaleAndKey(row.getLocaleId(), newKey, row.getId())) {
                    errors.add(new BatchUpsertError(
                            "Conflict",
                            "translation_key " + newKey + " already exists for locale " + row.getLocaleId(),
                            row.getLocaleId(),
                            null));
                }
            }
            if (!errors.isEmpty()) {
                return new BatchUpsertByKeyResult(false, null, null, errors);
            }
            for (I18nTranslation row : renameTargets) {
                row.setTranslationKey(newKey);
                translationRepository.update(row);
                renamed++;
            }
        }

        // Stage 2: delete
        List<Long> deletedIds = normalizeIds(cmd.deletedIds());
        for (Long id : deletedIds) {
            I18nTranslation row = translationRepository.findById(id);
            if (row == null) {
                errors.add(new BatchUpsertError("NotFound", "i18n-translation " + id + " not found", null, id));
                continue;
            }
            translationRepository.softDeleteById(id);
            deleted++;
        }
        if (!errors.isEmpty()) {
            return new BatchUpsertByKeyResult(false, null, null, errors);
        }

        // Stage 3: upsert
        String effectiveKey = newKey != null && !newKey.equals(translationKey) ? newKey : translationKey;
        List<BatchUpsertItem> items = cmd.items() == null ? List.of() : cmd.items();
        for (BatchUpsertItem rawItem : items) {
            Long localeId = rawItem.localeId();
            if (localeId == null || localeId <= 0) {
                errors.add(new BatchUpsertError("BadRequest", "localeId is required", null, null));
                continue;
            }
            String value = rawItem.value() == null ? "" : rawItem.value().trim();
            if (value.isEmpty()) {
                // 「空白不动」
                continue;
            }
            int isEnabled = I18nManageModels.normalize01(rawItem.isEnabled(), 1);
            String remark = I18nManageModels.nullToEmpty(rawItem.remark()).trim();

            I18nLocale locale = localeRepository.findById(localeId);
            if (locale == null) {
                errors.add(new BatchUpsertError("BadRequest", "locale " + localeId + " not found", localeId, null));
                continue;
            }

            I18nTranslation existing = translationRepository.findByLocaleAndKey(localeId, effectiveKey);
            if (existing != null) {
                existing.setValue(value);
                existing.setRemark(remark);
                existing.setIsEnabled(isEnabled);
                translationRepository.update(existing);
                updated++;
            } else {
                I18nTranslation row = new I18nTranslation();
                row.setLocaleId(localeId);
                row.setTranslationKey(effectiveKey);
                row.setValue(value);
                row.setRemark(remark);
                row.setIsEnabled(isEnabled);
                translationRepository.insert(row);
                created++;
            }
        }
        if (!errors.isEmpty()) {
            return new BatchUpsertByKeyResult(false, null, null, errors);
        }

        TranslationByKeyView refreshed = getByKey(effectiveKey);
        return new BatchUpsertByKeyResult(
                true, new BatchUpsertAffected(renamed, created, updated, deleted), refreshed.values(), null);
    }

    // ---------- export ----------

    public ExportBatchResult exportBatch(ExportBatchCommand cmd) {
        if (cmd.ids() == null || cmd.ids().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids array is required");
        }
        String format = "raw".equals(cmd.format()) ? "raw" : "simple";
        List<Long> ids = normalizeIds(cmd.ids());
        List<I18nLocale> selected = localeRepository.listByIds(ids);
        if (selected.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active locales found for given ids");
        }
        List<I18nTranslation> allTranslations = translationRepository.listByLocaleIds(
                selected.stream().map(I18nLocale::getId).toList());
        Map<Long, List<I18nTranslation>> byLocale =
                allTranslations.stream().collect(Collectors.groupingBy(I18nTranslation::getLocaleId));

        List<ExportBatchFile> files = new ArrayList<>();
        for (I18nLocale locale : selected) {
            List<I18nTranslation> mine = byLocale.getOrDefault(locale.getId(), List.of());
            if ("raw".equals(format)) {
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("@type", "raw");
                content.put("locale", localeToMap(converter.convert(locale, LocaleView.class)));
                List<Map<String, Object>> translations = new ArrayList<>();
                for (I18nTranslation t : mine) {
                    Map<String, Object> tr = new LinkedHashMap<>();
                    tr.put("id", t.getId());
                    tr.put("translationKey", t.getTranslationKey());
                    tr.put("value", t.getValue());
                    tr.put("remark", t.getRemark());
                    tr.put("isEnabled", t.getIsEnabled());
                    translations.add(tr);
                }
                content.put("translations", translations);
                files.add(new ExportBatchFile(locale.getCode(), "raw", content));
            } else {
                List<Map.Entry<String, String>> flat = mine.stream()
                        .map(t -> Map.entry(t.getTranslationKey(), t.getValue()))
                        .toList();
                Map<String, Object> content = new LinkedHashMap<>();
                content.put("@type", "simple");
                content.putAll(I18nManageModels.flattenToDict(flat));
                files.add(new ExportBatchFile(locale.getCode(), "simple", content));
            }
        }
        return new ExportBatchResult(files);
    }

    public Map<String, Object> export(ExportCommand cmd) {
        if (cmd.ids() == null || cmd.ids().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids is required");
        }
        String type = "raw".equals(cmd.type()) ? "raw" : "simple";
        List<Long> ids = normalizeIds(cmd.ids());
        List<I18nLocale> selected = localeRepository.listByIds(ids);
        if (selected.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active locales found for given ids");
        }
        List<I18nTranslation> allTranslations = translationRepository.listByLocaleIds(
                selected.stream().map(I18nLocale::getId).toList());
        Map<Long, String> idToCode =
                selected.stream().collect(Collectors.toMap(I18nLocale::getId, I18nLocale::getCode, (a, b) -> a));

        Map<String, Object> result = new LinkedHashMap<>();
        if ("raw".equals(type)) {
            result.put("@type", "raw");
            result.put(
                    "locales",
                    selected.stream()
                            .map(l -> localeToMap(converter.convert(l, LocaleView.class)))
                            .toList());
            List<Map<String, Object>> translations = new ArrayList<>();
            for (I18nTranslation t : allTranslations) {
                Map<String, Object> tr = new LinkedHashMap<>();
                tr.put("id", t.getId());
                tr.put("localeId", t.getLocaleId());
                tr.put("translationKey", t.getTranslationKey());
                tr.put("value", t.getValue());
                tr.put("remark", t.getRemark());
                tr.put("isEnabled", t.getIsEnabled());
                tr.put("deletedAt", t.getDeletedAt() == null ? 0L : t.getDeletedAt());
                tr.put("createdAt", t.getCreatedAt());
                tr.put("updatedAt", t.getUpdatedAt());
                tr.put("createdBy", t.getCreatedBy() == null ? 0L : t.getCreatedBy());
                tr.put("updatedBy", t.getUpdatedBy() == null ? 0L : t.getUpdatedBy());
                tr.put("localeCode", idToCode.get(t.getLocaleId()));
                translations.add(tr);
            }
            result.put("translations", translations);
        } else {
            result.put("@type", "simple");
            Map<String, Map<String, String>> locales = new LinkedHashMap<>();
            for (I18nLocale l : selected) {
                locales.put(l.getCode(), new LinkedHashMap<>());
            }
            for (I18nTranslation t : allTranslations) {
                String code = idToCode.get(t.getLocaleId());
                if (code == null) {
                    continue;
                }
                locales.computeIfAbsent(code, k -> new LinkedHashMap<>()).put(t.getTranslationKey(), t.getValue());
            }
            result.put("locales", locales);
        }
        return result;
    }

    // ---------- import ----------

    public ImportPreviewResult importPreview(ImportPreviewCommand cmd) {
        if (cmd.items() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "items array is required");
        }
        Set<String> wanted = new HashSet<>();
        Set<String> codes = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ImportPreviewItem item : cmd.items()) {
            if (item.localeCode() == null || item.keys() == null) {
                continue;
            }
            String code = item.localeCode();
            codes.add(code);
            for (String k : item.keys()) {
                if (k == null || k.isBlank()) {
                    continue;
                }
                keys.add(k);
                wanted.add(code + "\0" + k);
            }
        }
        if (wanted.isEmpty()) {
            return new ImportPreviewResult(List.of());
        }
        List<I18nLocale> locales = localeRepository.listAllActive().stream()
                .filter(l -> codes.contains(l.getCode()))
                .toList();
        if (locales.isEmpty()) {
            return new ImportPreviewResult(List.of());
        }
        Map<Long, String> idToCode =
                locales.stream().collect(Collectors.toMap(I18nLocale::getId, I18nLocale::getCode, (a, b) -> a));
        List<I18nTranslation> rows = translationRepository.listByLocaleIdsAndKeys(
                locales.stream().map(I18nLocale::getId).toList(), keys);
        List<TranslationView> current = new ArrayList<>();
        for (I18nTranslation row : rows) {
            String code = idToCode.get(row.getLocaleId());
            if (code == null) {
                continue;
            }
            if (!wanted.contains(code + "\0" + row.getTranslationKey())) {
                continue;
            }
            current.add(toView(row, code));
        }
        return new ImportPreviewResult(current);
    }

    /**
     * 多文件导入：每文件独立 try/catch，单文件失败不影响其它文件。
     */
    @SuppressWarnings("unchecked")
    public ImportBatchResult importBatch(ImportBatchCommand cmd) {
        if (cmd.items() == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "items array is required");
        }
        int totalCreatedLocales = 0;
        int totalSoftDeleted = 0;
        int totalCreatedTranslations = 0;
        List<ImportPerFileResult> perFile = new ArrayList<>();

        for (ImportBatchItem item : cmd.items()) {
            ImportPerFileResult r = processOneImport(item);
            perFile.add(r);
            totalCreatedLocales += r.createdLocales();
            totalSoftDeleted += r.softDeleted();
            totalCreatedTranslations += r.createdTranslations();
        }
        boolean ok = perFile.stream().allMatch(ImportPerFileResult::ok);
        return new ImportBatchResult(
                ok, new ImportBatchAffected(totalCreatedLocales, totalSoftDeleted, totalCreatedTranslations, perFile));
    }

    @SuppressWarnings("unchecked")
    private ImportPerFileResult processOneImport(ImportBatchItem item) {
        String name = item.name() == null ? "(unnamed)" : item.name();
        try {
            if (item.localeCode() == null || item.localeCode().isBlank()) {
                throw new IllegalArgumentException("缺少 localeCode");
            }
            if (!"raw".equals(item.format()) && !"simple".equals(item.format())) {
                throw new IllegalArgumentException("format 必须是 raw 或 simple");
            }
            if (item.payload() == null) {
                throw new IllegalArgumentException("缺少 payload");
            }
            String localeCode = item.localeCode().trim();
            String prefix = item.prefix() == null ? "" : item.prefix().replaceAll("^\\.+|\\.+$", "");
            int createdLocales = 0;
            int softDeleted = 0;
            int createdTranslations = 0;

            if ("raw".equals(item.format())) {
                Map<String, Object> payload = asMap(item.payload());
                Map<String, Object> localeMeta = payload.get("locale") instanceof Map<?, ?> m
                        ? (Map<String, Object>) m
                        : Map.of("code", localeCode, "name", localeCode);
                EnsureLocaleResult ensured = ensureLocale(localeCode, localeMeta);
                if (ensured.created()) {
                    createdLocales++;
                }
                I18nLocale locale = localeRepository.findByCode(localeCode);
                if (locale == null) {
                    throw new IllegalArgumentException("locale 创建失败");
                }
                Object trObj = payload.get("translations");
                if (trObj instanceof List<?> list) {
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> tm)) {
                            continue;
                        }
                        Map<String, Object> t = (Map<String, Object>) tm;
                        Object keyObj = t.get("translationKey");
                        Object valObj = t.get("value");
                        if (keyObj == null || !(valObj instanceof String)) {
                            continue;
                        }
                        String finalKey = prefix.isEmpty() ? String.valueOf(keyObj) : prefix + "." + keyObj;
                        String remark = t.get("remark") == null ? "" : String.valueOf(t.get("remark"));
                        int isEnabled = parse01(t.get("isEnabled"), 1);
                        SoftCreate sc =
                                replaceTranslation(locale.getId(), finalKey, String.valueOf(valObj), remark, isEnabled);
                        softDeleted += sc.softDeleted();
                        createdTranslations += sc.created();
                    }
                }
            } else {
                Map<String, Object> payload = asMap(item.payload());
                EnsureLocaleResult ensured = ensureLocale(localeCode, null);
                if (ensured.created()) {
                    createdLocales++;
                }
                I18nLocale locale = localeRepository.findByCode(localeCode);
                if (locale == null) {
                    throw new IllegalArgumentException("locale 创建失败");
                }
                List<Map.Entry<String, String>> flat = I18nManageModels.unflatten(payload, "");
                for (Map.Entry<String, String> e : flat) {
                    String finalKey = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                    SoftCreate sc = replaceTranslation(locale.getId(), finalKey, e.getValue(), "", 1);
                    softDeleted += sc.softDeleted();
                    createdTranslations += sc.created();
                }
            }
            return new ImportPerFileResult(name, true, null, createdLocales, softDeleted, createdTranslations);
        } catch (Exception ex) {
            return new ImportPerFileResult(
                    name, false, ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage(), 0, 0, 0);
        }
    }

    private record EnsureLocaleResult(Long id, boolean created) {}

    private record SoftCreate(int softDeleted, int created) {}

    @SuppressWarnings("unchecked")
    private EnsureLocaleResult ensureLocale(String code, Map<String, Object> meta) {
        I18nLocale existing = localeRepository.findByCode(code);
        if (existing != null) {
            if (meta != null) {
                if (meta.get("name") != null) {
                    existing.setName(String.valueOf(meta.get("name")));
                }
                if (meta.get("isDefault") != null) {
                    int isDefault = parse01(meta.get("isDefault"), existing.getIsDefault());
                    if (isDefault == 1) {
                        localeRepository.clearDefaultExcept(existing.getId());
                    }
                    existing.setIsDefault(isDefault);
                }
                if (meta.get("sort") != null) {
                    existing.setSort(toInt(meta.get("sort"), existing.getSort()));
                }
                if (meta.get("remark") != null) {
                    existing.setRemark(String.valueOf(meta.get("remark")));
                }
                if (meta.get("isEnabled") != null) {
                    existing.setIsEnabled(parse01(meta.get("isEnabled"), existing.getIsEnabled()));
                }
                localeRepository.update(existing);
            }
            return new EnsureLocaleResult(existing.getId(), false);
        }
        I18nLocale locale = new I18nLocale();
        locale.setCode(code);
        locale.setName(meta != null && meta.get("name") != null ? String.valueOf(meta.get("name")) : code);
        int isDefault = meta == null ? 0 : parse01(meta.get("isDefault"), 0);
        if (isDefault == 1) {
            localeRepository.clearDefaultExcept(null);
        }
        locale.setIsDefault(isDefault);
        locale.setSort(meta == null ? 0 : toInt(meta.get("sort"), 0));
        locale.setRemark(meta != null && meta.get("remark") != null ? String.valueOf(meta.get("remark")) : "");
        locale.setIsEnabled(meta == null ? 1 : parse01(meta.get("isEnabled"), 1));
        localeRepository.insert(locale);
        return new EnsureLocaleResult(locale.getId(), true);
    }

    /**
     * 导入替换：先软删同 (locale,key) 活跃行，再插入新行（与 mock 一致，规避软删唯一键）。
     */
    private SoftCreate replaceTranslation(
            Long localeId, String translationKey, String value, String remark, int isEnabled) {
        int softDeleted = 0;
        I18nTranslation existing = translationRepository.findByLocaleAndKey(localeId, translationKey);
        if (existing != null) {
            translationRepository.softDeleteById(existing.getId());
            softDeleted = 1;
        }
        I18nTranslation row = new I18nTranslation();
        row.setLocaleId(localeId);
        row.setTranslationKey(translationKey);
        row.setValue(value);
        row.setRemark(remark == null ? "" : remark);
        row.setIsEnabled(isEnabled);
        translationRepository.insert(row);
        return new SoftCreate(softDeleted, 1);
    }

    // ---------- helpers ----------

    private Long resolveLocaleId(Long localeId, String localeCode) {
        if (localeId != null) {
            return localeId;
        }
        if (localeCode == null) {
            return null;
        }
        I18nLocale locale = localeRepository.findByCode(localeCode);
        return locale == null ? null : locale.getId();
    }

    private Map<Long, String> loadLocaleCodes(List<Long> localeIds) {
        if (localeIds == null || localeIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct =
                localeIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (I18nLocale l : localeRepository.listByIds(distinct)) {
            map.put(l.getId(), l.getCode());
        }
        return map;
    }

    private I18nTranslation requireTranslation(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        I18nTranslation row = translationRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-translation " + id + " not found");
        }
        return row;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        return value.trim();
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                set.add(id);
            }
        }
        return List.copyOf(set);
    }

    private TranslationView toView(I18nTranslation t, String localeCode) {
        TranslationView base = converter.convert(t, TranslationView.class);
        return new TranslationView(
                base.id(),
                base.localeId(),
                base.translationKey(),
                base.value(),
                base.remark(),
                base.isEnabled(),
                base.deletedAt(),
                base.createdAt(),
                base.updatedAt(),
                base.createdBy(),
                base.updatedBy(),
                localeCode);
    }

    private static Map<String, Object> localeToMap(LocaleView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.id());
        m.put("code", v.code());
        m.put("name", v.name());
        m.put("isDefault", v.isDefault());
        m.put("sort", v.sort());
        m.put("remark", v.remark());
        m.put("isEnabled", v.isEnabled());
        m.put("deletedAt", v.deletedAt());
        m.put("createdAt", v.createdAt());
        m.put("updatedAt", v.updatedAt());
        m.put("createdBy", v.createdBy());
        m.put("updatedBy", v.updatedBy());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object payload) {
        if (payload instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IllegalArgumentException("payload 必须是对象");
    }

    private static int parse01(Object v, Integer defaultValue) {
        int def = defaultValue == null ? 0 : defaultValue;
        if (v == null) {
            return def;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (v instanceof Number n) {
            return n.intValue() == 0 ? 0 : 1;
        }
        String s = String.valueOf(v).trim();
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return 0;
        }
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return 1;
        }
        return def;
    }

    private static int toInt(Object v, Integer defaultValue) {
        int def = defaultValue == null ? 0 : defaultValue;
        if (v == null) {
            return def;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
