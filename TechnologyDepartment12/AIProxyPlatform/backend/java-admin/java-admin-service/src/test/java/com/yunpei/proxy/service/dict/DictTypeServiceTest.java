package com.yunpei.proxy.service.dict;

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
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictTypeCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictTypeView;
import com.yunpei.proxy.service.entity.DictType;
import com.yunpei.proxy.service.repository.DictDataRepository;
import com.yunpei.proxy.service.repository.DictTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link DictTypeService} 业务行为。
 */
class DictTypeServiceTest {

    private final DictTypeRepository typeRepo = mock(DictTypeRepository.class);
    private final DictDataRepository dataRepo = mock(DictDataRepository.class);
    private DictTypeService service;

    @BeforeEach
    void init() {
        service = new DictTypeService(typeRepo, dataRepo, new io.github.linpeilie.Converter());
    }

    @Test
    void page_mapsRows() {
        DictType row = type(1L, "sys_yes_no");
        EasyPageResult<DictType> easyPage = new EasyPageResult<>() {
            @Override
            public List<DictType> getData() {
                return List.of(row);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(typeRepo.page(1, 20, null, null, null, null)).thenReturn(easyPage);

        PageData<DictTypeView> page = service.page(DictTypeListQuery.of(1, 20, null, null, null));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getItems().get(0).code()).isEqualTo("sys_yes_no");
    }

    @Test
    void create_invalidCode_throws() {
        assertThatThrownBy(() -> service.create(new CreateDictTypeCommand("Bad-Code", "x", "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("code must match");
        verify(typeRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_duplicateCode_throws() {
        when(typeRepo.existsByCode("sys_yes_no", null)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateDictTypeCommand("sys_yes_no", "是否", "", 1)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_success() {
        when(typeRepo.existsByCode("sys_demo", null)).thenReturn(false);
        doAnswer(inv -> {
                    DictType t = inv.getArgument(0);
                    t.setId(99L);
                    t.setDeletedAt(0L);
                    t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    t.setCreatedBy(0L);
                    t.setUpdatedBy(0L);
                    return null;
                })
                .when(typeRepo)
                .insert(ArgumentMatchers.any(DictType.class));
        when(typeRepo.findById(99L)).thenReturn(type(99L, "sys_demo"));

        DictTypeView view = service.create(new CreateDictTypeCommand("sys_demo", "演示", "r", 1));

        assertThat(view.id()).isEqualTo(99L);
        assertThat(view.code()).isEqualTo("sys_demo");
    }

    @Test
    void softDelete_blockedWhenHasData() {
        when(typeRepo.findById(1L)).thenReturn(type(1L, "sys_yes_no"));
        when(dataRepo.existsActiveByTypeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.softDelete(1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("请先清空字典项");
        verify(typeRepo, never()).softDeleteById(1L);
    }

    @Test
    void softDelete_okWhenEmpty() {
        when(typeRepo.findById(1L)).thenReturn(type(1L, "sys_yes_no"));
        when(dataRepo.existsActiveByTypeId(1L)).thenReturn(false);
        when(typeRepo.softDeleteById(1L)).thenReturn(1L);

        DictTypeView view = service.softDelete(1L);

        assertThat(view.deletedAt()).isPositive();
    }

    @Test
    void batch_delete_blockedWhenAnyHasData() {
        when(typeRepo.listByIds(List.of(1L, 2L))).thenReturn(List.of(type(1L, "a"), type(2L, "b")));
        when(dataRepo.existsActiveByTypeId(1L)).thenReturn(false);
        when(dataRepo.existsActiveByTypeId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.batch(new DictBatchCommand("delete", List.of(1L, 2L))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仍有字典项");
    }

    @Test
    void batch_enable() {
        when(typeRepo.listByIds(List.of(1L))).thenReturn(List.of(type(1L, "a")));

        DictBatchResult result = service.batch(new DictBatchCommand("enable", List.of(1L)));

        assertThat(result.affected()).isEqualTo(1);
        verify(typeRepo).updateIsEnabled(1L, 1);
    }

    private static DictType type(Long id, String code) {
        DictType t = new DictType();
        t.setId(id);
        t.setCode(code);
        t.setName("n");
        t.setRemark("");
        t.setIsEnabled(1);
        t.setDeletedAt(0L);
        t.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        t.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        t.setCreatedBy(0L);
        t.setUpdatedBy(0L);
        return t;
    }
}
