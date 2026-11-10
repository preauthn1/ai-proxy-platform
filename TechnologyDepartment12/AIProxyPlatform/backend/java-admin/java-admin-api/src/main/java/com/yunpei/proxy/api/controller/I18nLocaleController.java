package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateI18nLocaleRequest;
import com.yunpei.proxy.api.dto.I18nBatchRequest;
import com.yunpei.proxy.api.dto.I18nExportBatchRequest;
import com.yunpei.proxy.api.dto.UpdateI18nLocaleRequest;
import com.yunpei.proxy.api.vo.I18nBatchResultVO;
import com.yunpei.proxy.api.vo.I18nExportBatchVO;
import com.yunpei.proxy.api.vo.I18nLocaleVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nLocaleService;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportBatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportBatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ExportCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.LocaleView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateLocaleCommand;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
import io.github.linpeilie.Converter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * 语言管理（路径对齐前端 {@code /api/system/i18n-locale/*}）。
 *
 * @author yunpei
 */
@Tag(name = "国际化-语言", description = "分页/all/CRUD/软删/batch/导出")
@RestController
@RequestMapping("/api/system/i18n-locale")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class I18nLocaleController {

    private final I18nLocaleService localeService;
    private final I18nTranslationService translationService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询语言", description = "data={items,total}；code 单值模糊、多值精确")
    public Result<PageData<I18nLocaleVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) List<String> code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        PageData<LocaleView> pageData = localeService.page(LocaleListQuery.of(page, pageSize, code, name, status));
        List<I18nLocaleVO> items = converter.convert(pageData.getItems(), I18nLocaleVO.class);
        return Result.ok(PageData.of(items, pageData.getTotal()));
    }

    @GetMapping("/all")
    @Operation(summary = "全量语言", description = "支持与 list 相同过滤项")
    public Result<List<I18nLocaleVO>> all(
            @RequestParam(required = false) List<String> code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        List<I18nLocaleVO> items = converter.convert(
                localeService.listAll(LocaleListQuery.allFilter(code, name, status)), I18nLocaleVO.class);
        return Result.ok(items);
    }

    @GetMapping("/{id}")
    @Operation(summary = "语言详情")
    public Result<I18nLocaleVO> detail(@PathVariable Long id) {
        return Result.ok(converter.convert(localeService.getById(id), I18nLocaleVO.class));
    }

    @PostMapping
    @Operation(summary = "创建语言", description = "isDefault=1 时清除其它默认")
    public Result<I18nLocaleVO> create(@Valid @RequestBody CreateI18nLocaleRequest req) {
        CreateLocaleCommand cmd = converter.convert(req, CreateLocaleCommand.class);
        return Result.ok(converter.convert(localeService.create(cmd), I18nLocaleVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新语言")
    public Result<I18nLocaleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateI18nLocaleRequest req) {
        // id 来自路径，映射体只覆盖可改字段
        UpdateLocaleCommand body = converter.convert(req, UpdateLocaleCommand.class);
        UpdateLocaleCommand cmd = new UpdateLocaleCommand(
                id, body.code(), body.name(), body.sort(), body.remark(), body.isDefault(), body.isEnabled());
        return Result.ok(converter.convert(localeService.update(cmd), I18nLocaleVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删语言", description = "默认语言禁止删除；仍有翻译则拒绝")
    public Result<I18nLocaleVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(localeService.softDelete(id), I18nLocaleVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<I18nBatchResultVO> batch(@RequestBody I18nBatchRequest req) {
        BatchResult result = localeService.batch(converter.convert(req, BatchCommand.class));
        return Result.ok(converter.convert(result, I18nBatchResultVO.class));
    }

    @PostMapping("/export-batch")
    @Operation(summary = "批量导出（每语言一文件）")
    public Result<I18nExportBatchVO> exportBatch(@RequestBody I18nExportBatchRequest req) {
        ExportBatchResult result =
                translationService.exportBatch(new ExportBatchCommand(req.getIds(), req.getFormat()));
        List<I18nExportBatchVO.FileItem> files = result.files().stream()
                .map(f -> new I18nExportBatchVO.FileItem(f.code(), f.format(), f.content()))
                .toList();
        return Result.ok(new I18nExportBatchVO(files));
    }

    @GetMapping("/export")
    @Operation(summary = "导出（raw/simple 单包）")
    public Result<Map<String, Object>> export(
            @RequestParam(required = false) String ids, @RequestParam(required = false) String type) {
        List<Long> idList = parseIds(ids);
        return Result.ok(translationService.export(new ExportCommand(idList, type)));
    }

    private static List<Long> parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toList());
    }
}
