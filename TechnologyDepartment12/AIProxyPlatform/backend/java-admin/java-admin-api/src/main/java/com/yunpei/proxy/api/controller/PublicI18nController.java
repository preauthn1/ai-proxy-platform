package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.vo.PublicI18nVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.service.i18n.I18nManageModels.PublicI18nBundle;
import com.yunpei.proxy.service.i18n.I18nTranslationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开 i18n 翻译拉取（路径对齐前端 {@code /api/public/i18n/:code}）。
 *
 * <p>免登录；用于进页合并后端翻译。须在 {@code WebConfig} / {@code SecurityPathMatcher} 白名单中。
 *
 * @author yunpei
 */
@Tag(name = "公开-国际化", description = "免登录拉取翻译包（hash 增量）")
@RestController
@RequestMapping("/api/public/i18n")
@RequiredArgsConstructor
public class PublicI18nController {

    private final I18nTranslationService translationService;

    @GetMapping("/{code}")
    @Operation(summary = "按语言码拉取翻译包", description = "query.hash 与服务端一致时 data.unchanged=true；否则返回 hash + KV map")
    public Result<PublicI18nVO> getByCode(@PathVariable String code, @RequestParam(required = false) String hash) {
        PublicI18nBundle bundle = translationService.getPublicBundle(code, hash);
        return Result.ok(new PublicI18nVO(bundle.unchanged(), bundle.hash(), bundle.data()));
    }
}
