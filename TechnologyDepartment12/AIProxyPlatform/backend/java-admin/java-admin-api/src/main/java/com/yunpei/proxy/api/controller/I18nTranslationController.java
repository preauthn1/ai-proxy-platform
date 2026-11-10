package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.dto.CreateI18nTranslationRequest;
import com.yunpei.proxy.api.dto.I18nBatchRequest;
import com.yunpei.proxy.api.dto.I18nBatchUpsertByKeyRequest;
import com.yunpei.proxy.api.dto.I18nImportBatchRequest;
import com.yunpei.proxy.api.dto.I18nImportPreviewRequest;
import com.yunpei.proxy.api.dto.UpdateI18nTranslationRequest;
import com.yunpei.proxy.api.vo.I18nBatchResultVO;
import com.yunpei.proxy.api.vo.I18nBatchUpsertByKeyVO;
import com.yunpei.proxy.api.vo.I18nImportBatchVO;
import com.yunpei.proxy.api.vo.I18nImportPreviewVO;
import com.yunpei.proxy.api.vo.I18nTranslationByKeyVO;
import com.yunpei.proxy.api.vo.I18nTranslationKeyVO;
import com.yunpei.proxy.api.vo.I18nTranslationVO;
import com.yunpei.proxy.common.result.PageData;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertByKeyResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.BatchUpsertItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.CreateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportBatchResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewCommand;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewItem;
import com.yunpei.proxy.service.i18n.I18nManageModels.ImportPreviewResult;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationByKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationKeyView;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationListQuery;
import com.yunpei.proxy.service.i18n.I18nManageModels.TranslationView;
import com.yunpei.proxy.service.i18n.I18nManageModels.UpdateTranslationCommand;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
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
 * 翻译管理（路径对齐前端 {@code /api/system/i18n-translation/*}）。
 *
 * @author yunpei
 */
@Tag(name = "国际化-翻译", description = "CRUD/list/by-key/by-locale/batch-upsert/导入导出")
@RestController
@RequestMapping("/api/system/i18n-translation")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class I18nTranslationController {

    private final I18nTranslationService translationService;
    private final Converter converter;

    @GetMapping("/list")
    @Operation(summary = "分页查询翻译", description = "byKey=true 时按 translationKey 聚合；否则返回翻译行并附 localeCode")
    public Result<PageData<?>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Long localeId,
            @RequestParam(required = false) String localeCode,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String byKey) {
        PageData<?> pageData = translationService.page(
                TranslationListQuery.of(page, pageSize, localeId, localeCode, value, status, byKey));
        if ("true".equalsIgnoreCase(byKey) || "1".equals(byKey)) {
            @SuppressWarnings("unchecked")
            PageData<TranslationKeyView> keyPage = (PageData<TranslationKeyView>) pageData;
            List<I18nTranslationKeyVO> items = converter.convert(keyPage.getItems(), I18nTranslationKeyVO.class);
            return Result.ok(PageData.of(items, keyPage.getTotal()));
        }
        @SuppressWarnings("unchecked")
        PageData<TranslationView> rowPage = (PageData<TranslationView>) pageData;
        List<I18nTranslationVO> items = converter.convert(rowPage.getItems(), I18nTranslationVO.class);
        return Result.ok(PageData.of(items, rowPage.getTotal()));
    }

    @GetMapping("/by-locale/{code}")
    @Operation(summary = "按语言 code 拉启用翻译")
    public Result<List<I18nTranslationVO>> byLocale(@PathVariable String code) {
        return Result.ok(converter.convert(translationService.listByLocaleCode(code), I18nTranslationVO.class));
    }

    @GetMapping("/by-key/{key}")
    @Operation(summary = "按 translationKey 聚合多语言版本", description = "缺失 key 时 values 为空数组")
    public Result<I18nTranslationByKeyVO> byKey(@PathVariable String key) {
        TranslationByKeyView view = translationService.getByKey(key);
        I18nTranslationByKeyVO vo = new I18nTranslationByKeyVO(
                view.translationKey(), converter.convert(view.values(), I18nTranslationVO.class));
        return Result.ok(vo);
    }

    @PostMapping
    @Operation(summary = "创建翻译")
    public Result<I18nTranslationVO> create(@Valid @RequestBody CreateI18nTranslationRequest req) {
        CreateTranslationCommand cmd = converter.convert(req, CreateTranslationCommand.class);
        return Result.ok(converter.convert(translationService.create(cmd), I18nTranslationVO.class));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新翻译")
    public Result<I18nTranslationVO> update(
            @PathVariable Long id, @Valid @RequestBody UpdateI18nTranslationRequest req) {
        // id 来自路径，映射体只覆盖可改字段
        UpdateTranslationCommand body = converter.convert(req, UpdateTranslationCommand.class);
        UpdateTranslationCommand cmd =
                new UpdateTranslationCommand(id, body.translationKey(), body.value(), body.remark(), body.isEnabled());
        return Result.ok(converter.convert(translationService.update(cmd), I18nTranslationVO.class));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删翻译")
    public Result<I18nTranslationVO> delete(@PathVariable Long id) {
        return Result.ok(converter.convert(translationService.softDelete(id), I18nTranslationVO.class));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量 enable|disable|delete")
    public Result<I18nBatchResultVO> batch(@RequestBody I18nBatchRequest req) {
        BatchResult result = translationService.batch(converter.convert(req, BatchCommand.class));
        return Result.ok(converter.convert(result, I18nBatchResultVO.class));
    }

    @PostMapping("/batch-upsert-by-key")
    @Operation(summary = "单 key 多语言 upsert", description = "顺序：rename → delete → upsert；失败返回 ok=false")
    public Result<I18nBatchUpsertByKeyVO> batchUpsertByKey(@RequestBody I18nBatchUpsertByKeyRequest req) {
        List<BatchUpsertItem> items = req.getItems() == null
                ? List.of()
                : req.getItems().stream()
                        .map(i -> new BatchUpsertItem(i.getLocaleId(), i.getValue(), i.getRemark(), i.getIsEnabled()))
                        .toList();
        BatchUpsertByKeyResult result = translationService.batchUpsertByKey(new BatchUpsertByKeyCommand(
                req.getTranslationKey(), req.getNewTranslationKey(), items, req.getDeletedIds()));
        I18nBatchUpsertByKeyVO vo = new I18nBatchUpsertByKeyVO();
        vo.setOk(result.ok());
        if (result.affected() != null) {
            vo.setAffected(new I18nBatchUpsertByKeyVO.Affected(
                    result.affected().renamed(),
                    result.affected().created(),
                    result.affected().updated(),
                    result.affected().deleted()));
        }
        if (result.values() != null) {
            vo.setValues(converter.convert(result.values(), I18nTranslationVO.class));
        }
        if (result.errors() != null) {
            vo.setErrors(result.errors().stream()
                    .map(e -> new I18nBatchUpsertByKeyVO.ErrorItem(e.code(), e.message(), e.localeId(), e.id()))
                    .toList());
        }
        return Result.ok(vo);
    }

    @PostMapping("/import-preview")
    @Operation(summary = "导入预览：按 (localeCode,key) 查当前行")
    public Result<I18nImportPreviewVO> importPreview(@RequestBody I18nImportPreviewRequest req) {
        List<ImportPreviewItem> items = req.getItems() == null
                ? List.of()
                : req.getItems().stream()
                        .map(i -> new ImportPreviewItem(i.getLocaleCode(), i.getKeys()))
                        .toList();
        ImportPreviewResult result = translationService.importPreview(new ImportPreviewCommand(items));
        return Result.ok(new I18nImportPreviewVO(converter.convert(result.currentRows(), I18nTranslationVO.class)));
    }

    @PostMapping("/import-batch")
    @Operation(summary = "批量导入（多文件、每文件独立）")
    public Result<I18nImportBatchVO> importBatch(@RequestBody I18nImportBatchRequest req) {
        List<ImportBatchItem> items = req.getItems() == null
                ? List.of()
                : req.getItems().stream()
                        .map(i -> new ImportBatchItem(
                                i.getName(), i.getPrefix(), i.getLocaleCode(), i.getFormat(), i.getPayload()))
                        .toList();
        ImportBatchResult result = translationService.importBatch(new ImportBatchCommand(items));
        List<I18nImportBatchVO.PerFile> perFile = result.affected().perFile().stream()
                .map(p -> new I18nImportBatchVO.PerFile(
                        p.name(), p.ok(), p.error(), p.createdLocales(), p.softDeleted(), p.createdTranslations()))
                .toList();
        I18nImportBatchVO.Affected affected = new I18nImportBatchVO.Affected(
                result.affected().createdLocales(),
                result.affected().softDeleted(),
                result.affected().createdTranslations(),
                perFile);
        return Result.ok(new I18nImportBatchVO(result.ok(), affected));
    }
}
