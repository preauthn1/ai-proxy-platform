package com.yunpei.proxy.service.dict;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictTypeCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictTypeCommand;
import com.yunpei.proxy.service.entity.DictType;
import com.yunpei.proxy.service.repository.DictDataRepository;
import com.yunpei.proxy.service.repository.DictTypeRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 字典类型 Service：分页/all/CRUD/软删/batch。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class DictTypeService {

    private static final Pattern CODE_RE = Pattern.compile(DictManageModels.CODE_PATTERN);

    private final DictTypeRepository dictTypeRepository;
    private final DictDataRepository dictDataRepository;
    private final Converter converter;

    public PageData<DictTypeView> page(DictTypeListQuery query) {
        EasyPageResult<DictType> page = dictTypeRepository.page(
                query.page(), query.pageSize(), query.codeExact(), query.codeLike(), query.name(), query.status());
        List<DictType> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(converter.convert(rows, DictTypeView.class), page.getTotal());
    }

    public List<DictTypeView> listAll(DictTypeListQuery query) {
        return converter.convert(
                dictTypeRepository.listFiltered(query.codeExact(), query.codeLike(), query.name(), query.status()),
                DictTypeView.class);
    }

    public DictTypeView getById(Long id) {
        return converter.convert(requireType(id), DictTypeView.class);
    }

    public DictTypeView create(CreateDictTypeCommand cmd) {
        String code = requireValidCode(cmd.code());
        String name = requireNonBlank(cmd.name(), "name");
        if (name.length() > 64) {
            throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
        }
        if (dictTypeRepository.existsByCode(code, null)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
        }

        DictType type = new DictType();
        type.setCode(code);
        type.setName(name);
        type.setRemark(DictManageModels.nullToEmpty(cmd.remark()).trim());
        type.setIsEnabled(DictManageModels.normalize01(cmd.isEnabled(), 1));
        dictTypeRepository.insert(type);
        return converter.convert(requireType(type.getId()), DictTypeView.class);
    }

    public DictTypeView update(UpdateDictTypeCommand cmd) {
        DictType type = requireType(cmd.id());
        if (cmd.code() != null) {
            String code = requireValidCode(cmd.code());
            if (dictTypeRepository.existsByCode(code, type.getId())) {
                throw BizException.of(ResultCode.PARAM_INVALID, "code " + code + " already exists");
            }
            type.setCode(code);
        }
        if (cmd.name() != null) {
            String name = cmd.name().trim();
            if (name.isEmpty()) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name cannot be empty");
            }
            if (name.length() > 64) {
                throw BizException.of(ResultCode.PARAM_INVALID, "name must be ≤ 64 chars");
            }
            type.setName(name);
        }
        if (cmd.remark() != null) {
            type.setRemark(cmd.remark());
        }
        if (cmd.isEnabled() != null) {
            type.setIsEnabled(DictManageModels.normalize01(cmd.isEnabled(), 1));
        }
        dictTypeRepository.update(type);
        return converter.convert(requireType(type.getId()), DictTypeView.class);
    }

    /**
     * 软删字典类型；若仍有字典项则拒绝。
     */
    public DictTypeView softDelete(Long id) {
        DictType type = requireType(id);
        if (dictDataRepository.existsActiveByTypeId(id)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "请先清空字典项");
        }
        DictTypeView snapshot = converter.convert(type, DictTypeView.class);
        long rows = dictTypeRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-type " + id + " not found");
        }
        long deletedAt = System.currentTimeMillis();
        return new DictTypeView(
                snapshot.id(),
                snapshot.code(),
                snapshot.name(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
    }

    public DictBatchResult batch(DictBatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!Set.of("enable", "disable", "delete").contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<DictType> targets = dictTypeRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active dict-type found for given ids");
        }

        if ("delete".equals(action)) {
            for (DictType t : targets) {
                if (dictDataRepository.existsActiveByTypeId(t.getId())) {
                    throw BizException.of(ResultCode.PARAM_INVALID, "字典类型 " + t.getCode() + " 仍有字典项，请先清空");
                }
            }
            List<Long> deleted = new ArrayList<>();
            for (DictType t : targets) {
                dictTypeRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            return new DictBatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (DictType t : targets) {
            dictTypeRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
        }
        return new DictBatchResult(action, affected.size(), affected);
    }

    private DictType requireType(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        DictType type = dictTypeRepository.findById(id);
        if (type == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-type " + id + " not found");
        }
        return type;
    }

    private String requireValidCode(String raw) {
        String code = requireNonBlank(raw, "code");
        if (!CODE_RE.matcher(code).matches()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code must match ^[a-z][a-z0-9_]{0,63}$");
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
