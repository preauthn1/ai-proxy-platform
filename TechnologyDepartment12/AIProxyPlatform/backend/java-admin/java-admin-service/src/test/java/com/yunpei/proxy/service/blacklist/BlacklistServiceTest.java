package com.yunpei.proxy.service.blacklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easy.query.core.api.pagination.EasyPageResult;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistBatchResult;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistListQuery;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.BlacklistView;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.CreateBlacklistCommand;
import com.yunpei.proxy.service.blacklist.BlacklistManageModels.UpdateBlacklistCommand;
import com.yunpei.proxy.service.entity.SysBlacklist;
import com.yunpei.proxy.service.repository.SysBlacklistRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link BlacklistService} 业务行为（S3 + 支撑 S1 查询入口）。
 */
class BlacklistServiceTest {

    private final SysBlacklistRepository repo = mock(SysBlacklistRepository.class);
    private BlacklistService service;

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 3, 1, 10, 0);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 3, 10, 10, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 3, 20, 10, 0);

    @BeforeEach
    void init() {
        service = new BlacklistService(repo, new io.github.linpeilie.Converter());
    }

    @Test
    void page_mapsRows() {
        SysBlacklist row = row(1L, "IP", "1.2.3.4", "ALL", T0, null);
        EasyPageResult<SysBlacklist> easyPage = new EasyPageResult<>() {
            @Override
            public List<SysBlacklist> getData() {
                return List.of(row);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(repo.page(1, 20, null, null, null, null)).thenReturn(easyPage);

        PageData<BlacklistView> page = service.page(BlacklistListQuery.of(1, 20, null, null, null, null));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems().get(0).targetValue()).isEqualTo("1.2.3.4");
    }

    @Test
    void create_rejectsInvalidTargetType() {
        assertThatThrownBy(() -> service.create(new CreateBlacklistCommand("HOST", "x", "ALL", "", T0, null, "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("targetType");
        verify(repo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_rejectsExactWindowDuplicate() {
        when(repo.existsExactWindow("IP", "1.2.3.4", "ALL", T0, null, null)).thenReturn(true);

        assertThatThrownBy(
                        () -> service.create(new CreateBlacklistCommand("IP", "1.2.3.4", "ALL", "r", T0, null, "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
        verify(repo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_allowsOverlappingWindow() {
        // 重叠窗不走 existsExactWindow=true；重叠查询不用于拒绝
        when(repo.existsExactWindow("IP", "1.2.3.4", "ALL", T0, T2, null)).thenReturn(false);
        doAnswer(inv -> {
                    SysBlacklist t = inv.getArgument(0);
                    t.setId(10L);
                    t.setDeletedAt(0L);
                    t.setCreatedAt(T0);
                    t.setUpdatedAt(T0);
                    t.setCreatedBy(0L);
                    t.setUpdatedBy(0L);
                    return null;
                })
                .when(repo)
                .insert(ArgumentMatchers.any(SysBlacklist.class));
        when(repo.findById(10L)).thenReturn(row(10L, "IP", "1.2.3.4", "ALL", T0, T2));

        BlacklistView view =
                service.create(new CreateBlacklistCommand("IP", "1.2.3.4", "ALL", "overlap-ok", T0, T2, "", 1));

        assertThat(view.id()).isEqualTo(10L);
        verify(repo).insert(ArgumentMatchers.any(SysBlacklist.class));
        // 创建路径不调用 existsOverlappingWindow 做拒绝
        verify(repo, never())
                .existsOverlappingWindow(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void create_normalizesIpAndDefaultsScope() {
        when(repo.existsExactWindow("IP", "1.2.3.4", "ALL", T0, null, null)).thenReturn(false);
        doAnswer(inv -> {
                    SysBlacklist t = inv.getArgument(0);
                    assertThat(t.getTargetType()).isEqualTo("IP");
                    assertThat(t.getTargetValue()).isEqualTo("1.2.3.4");
                    assertThat(t.getScope()).isEqualTo("ALL");
                    t.setId(11L);
                    t.setDeletedAt(0L);
                    t.setCreatedAt(T0);
                    t.setUpdatedAt(T0);
                    t.setCreatedBy(0L);
                    t.setUpdatedBy(0L);
                    return null;
                })
                .when(repo)
                .insert(ArgumentMatchers.any(SysBlacklist.class));
        when(repo.findById(11L)).thenReturn(row(11L, "IP", "1.2.3.4", "ALL", T0, null));

        service.create(new CreateBlacklistCommand("ip", "1.2.3.4:8080", null, "", T0, null, "", 1));

        verify(repo).insert(ArgumentMatchers.any(SysBlacklist.class));
    }

    @Test
    void create_rejectsExpiresNotAfterStarts() {
        assertThatThrownBy(() -> service.create(new CreateBlacklistCommand("IP", "1.2.3.4", "ALL", "", T1, T0, "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    void softDelete_ok() {
        when(repo.findById(1L)).thenReturn(row(1L, "IP", "1.2.3.4", "ALL", T0, null));
        when(repo.softDeleteById(1L)).thenReturn(1L);

        BlacklistView view = service.softDelete(1L);

        assertThat(view.deletedAt()).isPositive();
    }

    @Test
    void batch_enable() {
        when(repo.listByIds(List.of(1L))).thenReturn(List.of(row(1L, "USER", "42", "LOGIN", T0, null)));

        BlacklistBatchResult result = service.batch(new BlacklistBatchCommand("enable", List.of(1L)));

        assertThat(result.affected()).isEqualTo(1);
        verify(repo).updateIsEnabled(1L, 1);
    }

    @Test
    void batch_delete() {
        when(repo.listByIds(List.of(1L, 2L)))
                .thenReturn(List.of(row(1L, "IP", "a", "ALL", T0, null), row(2L, "IP", "b", "ALL", T0, null)));

        BlacklistBatchResult result = service.batch(new BlacklistBatchCommand("delete", List.of(1L, 2L)));

        assertThat(result.affected()).isEqualTo(2);
        verify(repo).softDeleteById(1L);
        verify(repo).softDeleteById(2L);
    }

    @Test
    void update_rejectsDuplicateWindow() {
        SysBlacklist existing = row(5L, "IP", "9.9.9.9", "API", T0, T1);
        when(repo.findById(5L)).thenReturn(existing);
        when(repo.existsExactWindow("IP", "9.9.9.9", "API", T0, T2, 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(
                        new UpdateBlacklistCommand(5L, null, null, null, null, null, T2, false, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
        verify(repo, never()).update(ArgumentMatchers.any());
    }

    @Test
    void isBlocked_delegatesToActiveHitQuery() {
        SysBlacklist hit = row(9L, "IP", "1.2.3.4", "LOGIN", T0, null);
        hit.setReason("brute force");
        when(repo.findActiveHit("IP", "1.2.3.4", "LOGIN", T1)).thenReturn(hit);

        assertThat(service.isBlocked("IP", "1.2.3.4", "LOGIN", T1)).isTrue();
        verify(repo).findActiveHit("IP", "1.2.3.4", "LOGIN", T1);
    }

    @Test
    void findBlockingHit_returnsReasonForServerLog_notEmptyWhenHit() {
        SysBlacklist hit = row(9L, "USER", "42", "ALL", T0, null);
        hit.setReason("account abuse");
        when(repo.findActiveHit("USER", "42", "LOGIN", T1)).thenReturn(hit);

        var found = service.findBlockingHit("USER", "42", "LOGIN", T1);

        assertThat(found).isPresent();
        assertThat(found.get().reason()).isEqualTo("account abuse");
        assertThat(found.get().scope()).isEqualTo("ALL");
    }

    @Test
    void findBlockingHit_emptyWhenNoHit() {
        when(repo.findActiveHit("IP", "8.8.8.8", "API", T1)).thenReturn(null);

        assertThat(service.findBlockingHit("IP", "8.8.8.8", "API", T1)).isEmpty();
        assertThat(service.isBlocked("IP", "8.8.8.8", "API", T1)).isFalse();
    }

    @Test
    void findBlockingHit_normalizesIpAndIgnoresDeviceTypeForInvalidEmpty() {
        when(repo.findActiveHit("IP", "1.2.3.4", "LOGIN", T1)).thenReturn(null);

        // IPv4:port → strip port then lower
        assertThat(service.findBlockingHit("IP", "1.2.3.4:8080", "LOGIN", T1)).isEmpty();
        verify(repo).findActiveHit("IP", "1.2.3.4", "LOGIN", T1);

        assertThat(service.findBlockingHit("DEVICE", "dev-1", "API", T1)).isEmpty();
        assertThat(service.findBlockingHit("HOST", "x", "API", T1)).isEmpty();
    }

    private static SysBlacklist row(
            Long id, String type, String value, String scope, LocalDateTime starts, LocalDateTime expires) {
        SysBlacklist t = new SysBlacklist();
        t.setId(id);
        t.setTargetType(type);
        t.setTargetValue(value);
        t.setScope(scope);
        t.setReason("");
        t.setStartsAt(starts);
        t.setExpiresAt(expires);
        t.setRemark("");
        t.setIsEnabled(1);
        t.setDeletedAt(0L);
        t.setCreatedAt(starts);
        t.setUpdatedAt(starts);
        t.setCreatedBy(0L);
        t.setUpdatedBy(0L);
        return t;
    }
}
