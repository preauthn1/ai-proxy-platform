package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateDictDataRequest;
import com.yunpei.proxy.api.dto.DictBatchRequest;
import com.yunpei.proxy.api.dto.UpdateDictDataRequest;
import com.yunpei.proxy.api.vo.DictBatchResultVO;
import com.yunpei.proxy.api.vo.DictDataVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.dict.DictDataService;
import com.yunpei.proxy.service.dict.DictManageModels.CreateDictDataCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchCommand;
import com.yunpei.proxy.service.dict.DictManageModels.DictBatchResult;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataListQuery;
import com.yunpei.proxy.service.dict.DictManageModels.DictDataView;
import com.yunpei.proxy.service.dict.DictManageModels.UpdateDictDataCommand;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典数据管理（路径对齐前端 {@code /api/system/dict-data/*}）。
 *
 * @author yunpei
 */
@Tag(name = "字典数据", description = "分页/by-type/CRUD/软删/batch；唯一键含 platform")
@RestController
@RequestMapping("/api/system/dict-data")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DictDataController {

    private final DictDataService dictDataService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询字典数据", description = "platform 精确过滤；includeGeneral=true 时并入 general；list 附 typeCode")
    public Result<PageData<DictDataVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) List<String> typeCode,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) Boolean includeGeneral) {
        PageData<DictDataView> pageData = dictDataService.page(
                DictDataListQuery.of(page, pageSize, typeId, typeCode, label, value, status, platform, includeGeneral));
        List<DictDataVO> items = converter.convert(pageData.getItems(), DictDataVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/by-type/{code}")
    @Operation(summary = "按类型 code 取启用字典项", description = "供前端下拉；类型不存在返回参数错误")
    public Result<List<DictDataVO>> byType(@PathVariable String code) {
        List<DictDataVO> items = converter.convert(dictDataService.listByTypeCode(code), DictDataVO.class);
        return Result.ok(items);
    }

    @PostMapping
    @Operation(summary = "创建字典数据", description = "唯一键 (typeId,value,platform)")
    public Result<DictDataVO> create(@Valid @RequestBody CreateDictDataRequest req) {
        CreateDictDataCommand cmd = converter.convert(req, CreateDictDataCommand.class);
        return Result.ok(converter.convert(dictDataService.create(cmd), DictDataVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新字典数据")
    public Result<DictDataVO> update(@PathVariable Long id, @Valid @RequestBody UpdateDictDataRequest req) {
        // id 来自路径，映射体只覆盖可改字段
        UpdateDictDataCommand body = converter.convert(req, UpdateDictDataCommand.class);
        UpdateDictDataCommand cmd = new UpdateDictDataCommand(
                id,
                body.value(),
                body.label(),
                body.sort(),
                body.isDefault(),
                body.platform(),
                body.tagType(),
                body.isEnabled(),
                body.remark());
        return Result.ok(converter.convert(dictDataService.update(cmd), DictDataVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删字典数据")
    public Result<DictDataVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(dictDataService.softDelete(id), DictDataVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<DictBatchResultVO> batch(@RequestBody DictBatchRequest req) {
        DictBatchResult result = dictDataService.batch(converter.convert(req, DictBatchCommand.class));
        return Result.ok(converter.convert(result, DictBatchResultVO.class));
    }
}
