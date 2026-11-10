package com.yunpei.proxy.service.dict;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictDataCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictDataCommand;
import com.yunpei.proxy.service.entity.DictData;
import com.yunpei.proxy.service.entity.DictType;
import com.yunpei.proxy.service.repository.DictDataRepository;
import com.yunpei.proxy.service.repository.DictTypeRepository;
import io.github.linpeilie.Converter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 字典数据 Service：分页/by-type/CRUD/软删/batch；唯一键含 platform。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class DictDataService {

    private final DictDataRepository dictDataRepository;
    private final DictTypeRepository dictTypeRepository;
    private final Converter converter;

    public PageData<DictDataView> page(DictDataListQuery query) {
        Collection<Long> typeIds = resolveTypeIdFilter(query);
        EasyPageResult<DictData> page = dictDataRepository.page(
                query.page(),
                query.pageSize(),
                query.typeId(),
                typeIds,
                query.label(),
                query.value(),
                query.status(),
                query.platform(),
                query.includeGeneral());
        List<DictData> rows = page.getData() == null ? List.of() : page.getData();
        Map<Long, String> typeCodeMap =
                loadTypeCodes(rows.stream().map(DictData::getTypeId).toList());
        List<DictDataView> items = rows.stream()
                .map(r -> toView(r, typeCodeMap.get(r.getTypeId())))
                .toList();
        return PageData.of(items, page.getTotal());
    }

    /**
     * 按类型 code 取启用字典项（下拉）；类型不存在 → 404 语义（PARAM_INVALID + not found 文案）。
     */
    public List<DictDataView> listByTypeCode(String code) {
        String typeCode = DictManageModels.trimToNull(code);
        if (typeCode == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "code is required");
        }
        DictType type = dictTypeRepository.findByCode(typeCode);
        if (type == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-type code=" + typeCode + " not found");
        }
        return dictDataRepository.listEnabledByTypeId(type.getId()).stream()
                .map(d -> toView(d, type.getCode()))
                .toList();
    }

    public DictDataView create(CreateDictDataCommand cmd) {
        Long typeId = cmd.typeId();
        if (typeId == null || typeId <= 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "typeId is required");
        }
        DictType type = dictTypeRepository.findById(typeId);
        if (type == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-type " + typeId + " not found or deleted");
        }

        String value = requireBounded(cmd.value(), "value", 64);
        String label = requireBounded(cmd.label(), "label", 128);
        String platform = requireAllowedPlatform(DictManageModels.normalizePlatform(cmd.platform()), true);
        String tagType = requireAllowedTagType(DictManageModels.normalizeTagType(cmd.tagType()), true);

        assertUnique(typeId, value, platform, null);

        DictData data = new DictData();
        data.setTypeId(typeId);
        data.setValue(value);
        data.setLabel(label);
        data.setSort(cmd.sort() == null ? 0 : cmd.sort());
        data.setIsDefault(Boolean.TRUE.equals(cmd.isDefault()) ? 1 : 0);
        data.setPlatform(platform);
        data.setTagType(tagType);
        data.setIsEnabled(DictManageModels.normalize01(cmd.isEnabled(), 1));
        data.setRemark(DictManageModels.nullToEmpty(cmd.remark()));
        dictDataRepository.insert(data);
        return toView(requireData(data.getId()), type.getCode());
    }

    public DictDataView update(UpdateDictDataCommand cmd) {
        DictData data = requireData(cmd.id());
        String nextValue = data.getValue();
        String nextPlatform = data.getPlatform();

        if (cmd.value() != null) {
            nextValue = requireBounded(cmd.value(), "value", 64);
            data.setValue(nextValue);
        }
        if (cmd.label() != null) {
            data.setLabel(requireBounded(cmd.label(), "label", 128));
        }
        if (cmd.sort() != null) {
            data.setSort(cmd.sort());
        }
        if (cmd.isDefault() != null) {
            data.setIsDefault(DictManageModels.normalize01(cmd.isDefault(), 0));
        }
        if (cmd.platform() != null) {
            nextPlatform = requireAllowedPlatform(cmd.platform().trim(), false);
            data.setPlatform(nextPlatform);
        }
        if (cmd.tagType() != null) {
            data.setTagType(requireAllowedTagType(DictManageModels.normalizeTagType(cmd.tagType()), false));
        }
        if (cmd.isEnabled() != null) {
            data.setIsEnabled(DictManageModels.normalize01(cmd.isEnabled(), 1));
        }
        if (cmd.remark() != null) {
            data.setRemark(cmd.remark());
        }

        assertUnique(data.getTypeId(), nextValue, nextPlatform, data.getId());
        dictDataRepository.update(data);

        DictType type = dictTypeRepository.findById(data.getTypeId());
        String typeCode = type == null ? null : type.getCode();
        return toView(requireData(data.getId()), typeCode);
    }

    public DictDataView softDelete(Long id) {
        DictData data = requireData(id);
        DictType type = dictTypeRepository.findById(data.getTypeId());
        DictDataView snapshot = toView(data, type == null ? null : type.getCode());
        long rows = dictDataRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-data " + id + " not found");
        }
        long deletedAt = System.currentTimeMillis();
        return new DictDataView(
                snapshot.id(),
                snapshot.typeId(),
                snapshot.value(),
                snapshot.label(),
                snapshot.sort(),
                snapshot.isDefault(),
                snapshot.platform(),
                snapshot.tagType(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.remark(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy(),
                snapshot.typeCode());
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
        List<DictData> targets = dictDataRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active dict-data found for given ids");
        }

        if ("delete".equals(action)) {
            List<Long> deleted = new ArrayList<>();
            for (DictData d : targets) {
                dictDataRepository.softDeleteById(d.getId());
                deleted.add(d.getId());
            }
            return new DictBatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (DictData d : targets) {
            dictDataRepository.updateIsEnabled(d.getId(), enabled);
            affected.add(d.getId());
        }
        return new DictBatchResult(action, affected.size(), affected);
    }

    /**
     * typeCode 过滤：多值精确 → 命中 id 集合；单值模糊 → contains；无过滤 → null。
     * 多值且无命中 → 空集合（分页结果为空）。
     */
    private Collection<Long> resolveTypeIdFilter(DictDataListQuery query) {
        if (query.typeCodeExact() != null && !query.typeCodeExact().isEmpty()) {
            return dictTypeRepository.findIdsByCodes(query.typeCodeExact());
        }
        if (query.typeCodeLike() != null) {
            return dictTypeRepository.findIdsByCodeContains(query.typeCodeLike());
        }
        return null;
    }

    private Map<Long, String> loadTypeCodes(List<Long> typeIds) {
        if (typeIds == null || typeIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = typeIds.stream().distinct().toList();
        return dictTypeRepository.listByIds(distinct).stream()
                .collect(Collectors.toMap(DictType::getId, DictType::getCode, (a, b) -> a, HashMap::new));
    }

    private void assertUnique(Long typeId, String value, String platform, Long excludeId) {
        if (dictDataRepository.existsByTypeValuePlatform(typeId, value, platform, excludeId)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "value " + value + " already exists in type " + typeId + " platform " + platform);
        }
    }

    private String requireAllowedPlatform(String platform, boolean allowDefault) {
        if (platform == null || platform.isBlank()) {
            if (allowDefault) {
                return "general";
            }
            throw BizException.of(ResultCode.PARAM_INVALID, "platform must be one of general|react-admin|vue-admin");
        }
        if (!DictManageModels.ALLOWED_PLATFORMS.contains(platform)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "platform must be one of general|react-admin|vue-admin");
        }
        return platform;
    }

    private String requireAllowedTagType(String tagType, boolean allowDefault) {
        if (tagType == null || tagType.isBlank()) {
            if (allowDefault) {
                return "default";
            }
            throw BizException.of(ResultCode.PARAM_INVALID, "tagType is invalid");
        }
        if (!DictManageModels.ALLOWED_TAG_TYPES.contains(tagType)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID,
                    "tagType must be one of " + String.join("|", DictManageModels.ALLOWED_TAG_TYPES));
        }
        return tagType;
    }

    private static String requireBounded(String raw, String field, int maxLen) {
        if (raw == null || raw.trim().isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " is required");
        }
        String v = raw.trim();
        if (v.length() > maxLen) {
            throw BizException.of(ResultCode.PARAM_INVALID, field + " must be ≤ " + maxLen + " chars");
        }
        return v;
    }

    private DictData requireData(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        DictData data = dictDataRepository.findById(id);
        if (data == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "dict-data " + id + " not found");
        }
        return data;
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

    private DictDataView toView(DictData d, String typeCode) {
        DictDataView base = converter.convert(d, DictDataView.class);
        return new DictDataView(
                base.id(),
                base.typeId(),
                base.value(),
                base.label(),
                base.sort(),
                base.isDefault(),
                base.platform(),
                base.tagType(),
                base.isEnabled(),
                base.deletedAt(),
                base.remark(),
                base.createdAt(),
                base.updatedAt(),
                base.createdBy(),
                base.updatedBy(),
                typeCode);
    }
}
