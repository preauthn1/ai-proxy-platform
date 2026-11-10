package com.yunpei.proxy.service.blacklist;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.common.time.TimeZones;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchResult;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistListQuery;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistView;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.CreateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.UpdateBlacklistCommand;
import com.yunpei.proxy.service.entity.SysBlacklist;
import com.yunpei.proxy.service.repository.SysBlacklistRepository;
import io.github.linpeilie.Converter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 访问黑名单 Service：分页/all/CRUD/软删/batch；同窗拒绝、重叠允许。
 *
 * @author yunpei
 */
@Service
@RequiredArgsConstructor
public class BlacklistService {

    private final SysBlacklistRepository blacklistRepository;
    private final Converter converter;

    public PageData<BlacklistView> page(BlacklistListQuery query) {
        EasyPageResult<SysBlacklist> page = blacklistRepository.page(
                query.page(), query.pageSize(), query.targetType(), query.targetValue(), query.scope(), query.status());
        List<SysBlacklist> rows = page.getData() == null ? List.of() : page.getData();
        return PageData.of(converter.convert(rows, BlacklistView.class), page.getTotal());
    }

    public List<BlacklistView> listAll(BlacklistListQuery query) {
        return converter.convert(
                blacklistRepository.listFiltered(
                        query.targetType(), query.targetValue(), query.scope(), query.status()),
                BlacklistView.class);
    }

    public BlacklistView getById(Long id) {
        return converter.convert(requireRow(id), BlacklistView.class);
    }

    public BlacklistView create(CreateBlacklistCommand cmd) {
        String targetType = requireTargetType(cmd.targetType());
        String targetValue = requireTargetValue(targetType, cmd.targetValue());
        String scope = requireScope(cmd.scope());
        LocalDateTime startsAt = cmd.startsAt() == null ? TimeZones.now() : cmd.startsAt();
        LocalDateTime expiresAt = cmd.expiresAt();
        validateWindow(startsAt, expiresAt);
        rejectIfExactDuplicate(targetType, targetValue, scope, startsAt, expiresAt, null);

        SysBlacklist row = new SysBlacklist();
        row.setTargetType(targetType);
        row.setTargetValue(targetValue);
        row.setScope(scope);
        row.setReason(clip(BlacklistManageModels.nullToEmpty(cmd.reason()).trim(), 512));
        row.setStartsAt(startsAt);
        row.setExpiresAt(expiresAt);
        row.setRemark(clip(BlacklistManageModels.nullToEmpty(cmd.remark()).trim(), 512));
        row.setIsEnabled(BlacklistManageModels.normalize01(cmd.isEnabled(), 1));
        blacklistRepository.insert(row);
        return converter.convert(requireRow(row.getId()), BlacklistView.class);
    }

    public BlacklistView update(UpdateBlacklistCommand cmd) {
        SysBlacklist row = requireRow(cmd.id());

        if (cmd.targetType() != null) {
            row.setTargetType(requireTargetType(cmd.targetType()));
        }
        if (cmd.targetValue() != null) {
            row.setTargetValue(requireTargetValue(row.getTargetType(), cmd.targetValue()));
        }
        if (cmd.scope() != null) {
            row.setScope(requireScope(cmd.scope()));
        }
        if (cmd.reason() != null) {
            row.setReason(clip(cmd.reason().trim(), 512));
        }
        if (cmd.startsAt() != null) {
            row.setStartsAt(cmd.startsAt());
        }
        if (Boolean.TRUE.equals(cmd.clearExpiresAt())) {
            row.setExpiresAt(null);
        } else if (cmd.expiresAt() != null) {
            row.setExpiresAt(cmd.expiresAt());
        }
        if (cmd.remark() != null) {
            row.setRemark(clip(cmd.remark().trim(), 512));
        }
        if (cmd.isEnabled() != null) {
            row.setIsEnabled(BlacklistManageModels.normalize01(cmd.isEnabled(), 1));
        }

        validateWindow(row.getStartsAt(), row.getExpiresAt());
        rejectIfExactDuplicate(
                row.getTargetType(),
                row.getTargetValue(),
                row.getScope(),
                row.getStartsAt(),
                row.getExpiresAt(),
                row.getId());

        blacklistRepository.update(row);
        return converter.convert(requireRow(row.getId()), BlacklistView.class);
    }

    public BlacklistView softDelete(Long id) {
        SysBlacklist row = requireRow(id);
        BlacklistView snapshot = converter.convert(row, BlacklistView.class);
        long rows = blacklistRepository.softDeleteById(id);
        if (rows == 0) {
            throw BizException.of(ResultCode.PARAM_INVALID, "blacklist " + id + " not found");
        }
        long deletedAt = System.currentTimeMillis();
        return new BlacklistView(
                snapshot.id(),
                snapshot.targetType(),
                snapshot.targetValue(),
                snapshot.scope(),
                snapshot.reason(),
                snapshot.startsAt(),
                snapshot.expiresAt(),
                snapshot.remark(),
                snapshot.isEnabled(),
                deletedAt,
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.createdBy(),
                snapshot.updatedBy());
    }

    public BlacklistBatchResult batch(BlacklistBatchCommand cmd) {
        String action = cmd.action() == null ? "" : cmd.action().trim();
        if (!Set.of("enable", "disable", "delete").contains(action)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "action must be enable|disable|delete");
        }
        List<Long> ids = normalizeIds(cmd.ids());
        if (ids.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "ids must be a non-empty number[]");
        }
        List<SysBlacklist> targets = blacklistRepository.listByIds(ids);
        if (targets.isEmpty()) {
            throw BizException.of(ResultCode.PARAM_INVALID, "no active blacklist found for given ids");
        }

        if ("delete".equals(action)) {
            List<Long> deleted = new ArrayList<>();
            for (SysBlacklist t : targets) {
                blacklistRepository.softDeleteById(t.getId());
                deleted.add(t.getId());
            }
            return new BlacklistBatchResult(action, deleted.size(), deleted);
        }

        int enabled = "enable".equals(action) ? 1 : 0;
        List<Long> affected = new ArrayList<>();
        for (SysBlacklist t : targets) {
            blacklistRepository.updateIsEnabled(t.getId(), enabled);
            affected.add(t.getId());
        }
        return new BlacklistBatchResult(action, affected.size(), affected);
    }

    /**
     * 运行时命中判定（S1）：是否被拦截。
     *
     * <p>本波 DEVICE 不参与运行时；调用方应只查 IP / USER。
     */
    public boolean isBlocked(String targetType, String targetValue, String requestScope, LocalDateTime now) {
        return findBlockingHit(targetType, targetValue, requestScope, now).isPresent();
    }

    /**
     * 查找当前生效命中（S1）；供 Filter / 登录链路写服务端日志（reason 不对客户端暴露）。
     *
     * @return 命中时含 target / scope / reason；未命中 empty
     */
    public Optional<BlacklistHit> findBlockingHit(
            String targetType, String targetValue, String requestScope, LocalDateTime now) {
        String type = BlacklistManageModels.normalizeTargetType(targetType);
        String value = BlacklistManageModels.normalizeTargetValue(type, targetValue);
        String scope = BlacklistManageModels.normalizeScope(requestScope);
        if (type == null || value == null || !BlacklistManageModels.TARGET_TYPES.contains(type)) {
            return Optional.empty();
        }
        // DEVICE 本波不参与运行时拦截（数据可配置，命中判定跳过）
        if ("DEVICE".equals(type)) {
            return Optional.empty();
        }
        // 请求场景仅 LOGIN/API；容错 ALL
        if (!Set.of("LOGIN", "API", "ALL").contains(scope)) {
            return Optional.empty();
        }
        LocalDateTime at = now == null ? TimeZones.now() : now;
        SysBlacklist row = blacklistRepository.findActiveHit(type, value, scope, at);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new BlacklistHit(
                row.getTargetType(),
                row.getTargetValue(),
                row.getScope(),
                row.getReason() == null ? "" : row.getReason()));
    }

    /** 运行时命中摘要（仅服务端使用）。 */
    public record BlacklistHit(String targetType, String targetValue, String scope, String reason) {}

    private void rejectIfExactDuplicate(
            String targetType,
            String targetValue,
            String scope,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            Long excludeId) {
        if (blacklistRepository.existsExactWindow(targetType, targetValue, scope, startsAt, expiresAt, excludeId)) {
            throw BizException.of(
                    ResultCode.PARAM_INVALID, "active blacklist with the same target/scope/time-window already exists");
        }
    }

    private static void validateWindow(LocalDateTime startsAt, LocalDateTime expiresAt) {
        if (startsAt == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "startsAt is required");
        }
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "expiresAt must be after startsAt");
        }
    }

    private SysBlacklist requireRow(Long id) {
        if (id == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "id 不能为空");
        }
        SysBlacklist row = blacklistRepository.findById(id);
        if (row == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "blacklist " + id + " not found");
        }
        return row;
    }

    private static String requireTargetType(String raw) {
        String type = BlacklistManageModels.normalizeTargetType(raw);
        if (type == null || !BlacklistManageModels.TARGET_TYPES.contains(type)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "targetType must be IP|USER|DEVICE");
        }
        return type;
    }

    private static String requireScope(String raw) {
        String scope = BlacklistManageModels.normalizeScope(raw);
        if (!BlacklistManageModels.SCOPES.contains(scope)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "scope must be LOGIN|API|ALL");
        }
        return scope;
    }

    private static String requireTargetValue(String targetType, String raw) {
        String value = BlacklistManageModels.normalizeTargetValue(targetType, raw);
        if (value == null) {
            throw BizException.of(ResultCode.PARAM_INVALID, "targetValue is required");
        }
        if (value.length() > 128) {
            throw BizException.of(ResultCode.PARAM_INVALID, "targetValue must be ≤ 128 chars");
        }
        return value;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
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
