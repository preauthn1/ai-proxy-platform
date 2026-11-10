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
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictDataCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictDataCommand;
import com.yunpei.proxy.service.entity.DictData;
import com.yunpei.proxy.service.entity.DictType;
import com.yunpei.proxy.service.repository.DictDataRepository;
import com.yunpei.proxy.service.repository.DictTypeRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

/**
 * {@link DictDataService}：platform 过滤与唯一键 (typeId,value,platform)。
 */
class DictDataServiceTest {

    private final DictDataRepository dataRepo = mock(DictDataRepository.class);
    private final DictTypeRepository typeRepo = mock(DictTypeRepository.class);
    private DictDataService service;

    @BeforeEach
    void init() {
        service = new DictDataService(dataRepo, typeRepo, new io.github.linpeilie.Converter());
    }

    @Test
    void page_joinsTypeCode_andForwardsPlatform() {
        DictData row = data(1L, "Y", "react-admin");
        EasyPageResult<DictData> easyPage = new EasyPageResult<>() {
            @Override
            public List<DictData> getData() {
                return List.of(row);
            }

            @Override
            public long getTotal() {
                return 1L;
            }
        };
        when(dataRepo.page(1, 20, null, null, null, null, null, "react-admin", true))
                .thenReturn(easyPage);
        when(typeRepo.listByIds(List.of(10L))).thenReturn(List.of(type(10L, "sys_yes_no")));

        PageData<DictDataView> page =
                service.page(DictDataListQuery.of(1, 20, null, null, null, null, null, "react-admin", true));

        assertThat(page.getItems().get(0).typeCode()).isEqualTo("sys_yes_no");
        assertThat(page.getItems().get(0).platform()).isEqualTo("react-admin");
    }

    @Test
    void create_sameValueDifferentPlatform_allowed() {
        when(typeRepo.findById(10L)).thenReturn(type(10L, "sys_yes_no"));
        when(dataRepo.existsByTypeValuePlatform(10L, "Y", "react-admin", null)).thenReturn(false);
        doAnswer(inv -> {
                    DictData d = inv.getArgument(0);
                    d.setId(100L);
                    d.setDeletedAt(0L);
                    d.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    d.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    d.setCreatedBy(0L);
                    d.setUpdatedBy(0L);
                    return null;
                })
                .when(dataRepo)
                .insert(ArgumentMatchers.any(DictData.class));
        when(dataRepo.findById(100L)).thenReturn(data(100L, "Y", "react-admin"));

        DictDataView view =
                service.create(new CreateDictDataCommand(10L, "Y", "是", 0, false, "react-admin", "default", 1, ""));

        assertThat(view.platform()).isEqualTo("react-admin");
        verify(dataRepo).insert(ArgumentMatchers.any(DictData.class));
    }

    @Test
    void create_sameValueSamePlatform_throws() {
        when(typeRepo.findById(10L)).thenReturn(type(10L, "sys_yes_no"));
        when(dataRepo.existsByTypeValuePlatform(10L, "Y", "general", null)).thenReturn(true);

        assertThatThrownBy(() ->
                        service.create(new CreateDictDataCommand(10L, "Y", "是", 0, false, "general", "default", 1, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists")
                .hasMessageContaining("platform");
        verify(dataRepo, never()).insert(ArgumentMatchers.any());
    }

    @Test
    void create_invalidPlatform_throws() {
        when(typeRepo.findById(10L)).thenReturn(type(10L, "sys_yes_no"));

        assertThatThrownBy(() ->
                        service.create(new CreateDictDataCommand(10L, "Y", "是", 0, false, "mobile", "default", 1, "")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("platform");
    }

    @Test
    void create_defaultsPlatformAndTagType() {
        when(typeRepo.findById(10L)).thenReturn(type(10L, "sys_yes_no"));
        when(dataRepo.existsByTypeValuePlatform(10L, "N", "general", null)).thenReturn(false);
        doAnswer(inv -> {
                    DictData d = inv.getArgument(0);
                    assertThat(d.getPlatform()).isEqualTo("general");
                    assertThat(d.getTagType()).isEqualTo("default");
                    d.setId(101L);
                    d.setDeletedAt(0L);
                    d.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    d.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
                    d.setCreatedBy(0L);
                    d.setUpdatedBy(0L);
                    return null;
                })
                .when(dataRepo)
                .insert(ArgumentMatchers.any(DictData.class));
        when(dataRepo.findById(101L)).thenReturn(data(101L, "N", "general"));

        service.create(new CreateDictDataCommand(10L, "N", "否", null, null, null, null, null, null));

        verify(dataRepo).existsByTypeValuePlatform(10L, "N", "general", null);
    }

    @Test
    void update_platformChange_checksUniqueTriple() {
        when(dataRepo.findById(5L)).thenReturn(data(5L, "Y", "general"));
        when(dataRepo.existsByTypeValuePlatform(10L, "Y", "react-admin", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(
                        new UpdateDictDataCommand(5L, null, null, null, null, "react-admin", null, null, null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("already exists");
        verify(dataRepo, never()).update(ArgumentMatchers.any());
    }

    @Test
    void listByTypeCode_notFound() {
        when(typeRepo.findByCode("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.listByTypeCode("missing"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void listByTypeCode_onlyEnabled() {
        when(typeRepo.findByCode("sys_yes_no")).thenReturn(type(10L, "sys_yes_no"));
        when(dataRepo.listEnabledByTypeId(10L)).thenReturn(List.of(data(1L, "Y", "general")));

        List<DictDataView> items = service.listByTypeCode("sys_yes_no");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).typeCode()).isEqualTo("sys_yes_no");
    }

    private static DictType type(Long id, String code) {
        DictType t = new DictType();
        t.setId(id);
        t.setCode(code);
        t.setName("n");
        t.setRemark("");
        t.setIsEnabled(1);
        t.setDeletedAt(0L);
        return t;
    }

    private static DictData data(Long id, String value, String platform) {
        DictData d = new DictData();
        d.setId(id);
        d.setTypeId(10L);
        d.setValue(value);
        d.setLabel(value);
        d.setSort(0);
        d.setIsDefault(0);
        d.setPlatform(platform);
        d.setTagType("default");
        d.setIsEnabled(1);
        d.setRemark("");
        d.setDeletedAt(0L);
        d.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        d.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        d.setCreatedBy(0L);
        d.setUpdatedBy(0L);
        return d;
    }
}
