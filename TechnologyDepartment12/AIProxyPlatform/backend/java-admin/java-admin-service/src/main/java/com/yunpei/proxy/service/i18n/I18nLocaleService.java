package com.yunpei.proxy.service.i18n;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.entity.I18nLocale;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateLocaleCommand;
import com.yunpei.proxy.service.repository.I18nLocaleRepository;
import com.yunpei.proxy.service.repository.I18nTranslationRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 语言/区域 Service：分页/all/CRUD/软删/batch；默认语言唯一与禁删。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class I18nLocaleService {

    private static final Pattern CODE_RE = Pattern.compile(I18nManageModels.LOCALE_CODE_PATTERN);

    private final I18nLocaleRepository localeRepository;
    private final I18nTranslationRepository translationRepository;
    private final Converter converter;

    public PageData<LocaleView> page(LocaleListQuery query) {
        EasyPageResult<I18nLocale> page = localeRepository.page(
                query.page(), query.pageSize(), query.codeExact(), query.codeLike(), query.name(), query.status());
        List<I18nLocale> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(converter.convert(rows, LocaleView.class), page.getTotal());
    }

    public List<LocaleView> listAll(LocaleListQuery query) {
        return converter.convert(
                localeRepository.listFiltered(query.codeExact(), query.codeLike(), query.name(), query.status()),
                LocaleView.class);
    }

    public LocaleView getById(Long id) {
        return converter.convert(requireLocale(id), LocaleView.class);
    }

    public LocaleView create(CreateLocaleCommand cmd) {
        String code = requireValidCode(cmd.code());
        String name = requireNonBlank(cmd.name(), "name");
        if (name.length() > 64) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
        }
        if (localeRepository.existsByCode(code, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
        }

        int isDefault = I18nManageModels.normalize01(cmd.isDefault(), 0);
        int isEnabled = I18nManageModels.normalize01(cmd.isEnabled(), 1);
        int sort = cmd.sort() == null ? 0 : cmd.sort();

        if (isDefault == 1) {
            localeRepository.clearDefaultExcept(null);
        }

        I18nLocale locale = new I18nLocale();
        locale.setCode(code);
        locale.setName(name);
        locale.setIsDefault(isDefault);
        locale.setSort(sort);
        locale.setRemark(I18nManageModels.nullToEmpty(cmd.remark()).trim());
        locale.setIsEnabled(isEnabled);
        localeRepository.insert(locale);
        return converter.convert(requireLocale(locale.getId()), LocaleView.class);
    }

    public LocaleView update(UpdateLocaleCommand cmd) {
        I18nLocale locale = requireLocale(cmd.id());
        if (cmd.code() != null) {
            String code = requireValidCode(cmd.code());
            if (localeRepository.existsByCode(code, locale.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
            }
            locale.setCode(code);
        }
        if (cmd.name() != null) {
            String name = cmd.name().trim();
            if (name.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name cannot be empty");
            }
            if (name.length() > 64) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
            }
            locale.setName(name);
        }
        if (cmd.remark() != null) {
            locale.setRemark(cmd.remark());
        }
        if (cmd.sort() != null) {
            locale.setSort(cmd.sort());
        }
        if (cmd.isEnabled() != null) {
            locale.setIsEnabled(I18nManageModels.normalize01(cmd.isEnabled(), 1));
        }
        if (cmd.isDefault() != null) {
            int isDefault = I18nManageModels.normalize01(cmd.isDefault(), 0);
            if (isDefault == 1) {
                localeRepository.clearDefaultExcept(locale.getId());
            }
            locale.setIsDefault(isDefault);
        }
        localeRepository.update(locale);
        return converter.convert(requireLocale(locale.getId()), LocaleView.class);
    }

    /**
     * 软删语言：默认语言禁止删除；仍有翻译则拒绝。
     */
    public LocaleView softDelete(Long id) {
        I18nLocale locale = requireLocale(id);
        if (locale.getIsDefault() != null && locale.getIsDefault() == 1) {
            throw BizException.of(ResultCode.PARAM_INVALID, "默认语言禁止删除");
        }
        if (translationRepository.existsActiveByLocaleId(id)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "请先清空该语言的翻译");
        }
        LocaleView snapshot = converter.convert(locale, LocaleView.class);
        long rows = localeRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-locale " + id + " not found");
        }
        long deletedAt = System.currentTimeMillis();
        return new LocaleView(
                snapshot.id(),
                snapshot.code(),
                snapshot.name(),
                snapshot.isDefault(),
                snapshot.sort(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
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
        List<I18nLocale> targets = localeRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active i18n-locale found for given ids");
        }

        if ("delete".equals(action)) {
            for (I18nLocale t : targets) {
                if (t.getIsDefault() != null && t.getIsDefault() == 1) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "默认语言禁止删除");
                }
                if (translationRepository.existsActiveByLocaleId(t.getId())) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "语言 " + t.getCode() + " 仍有翻译，请先清空");
                }
            }
            List<Long> deleted = new ArrayList<>();
            for (I18nLocale t : targets) {
                localeRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            return new BatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (I18nLocale t : targets) {
            localeRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
        }
        return new BatchResult(action, affected.size(), affected);
    }

    private I18nLocale requireLocale(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        I18nLocale locale = localeRepository.findById(id);
        if (locale == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "i18n-locale " + id + " not found");
        }
        return locale;
    }

    private String requireValidCode(String raw) {
        String code = requireNonBlank(raw, "code");
        if (!CODE_RE.matcher(code).matches()) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "code must look like a BCP-47 tag (e.g. zh-CN / en-US / ja-JP)");
        }
        return code;
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
}
