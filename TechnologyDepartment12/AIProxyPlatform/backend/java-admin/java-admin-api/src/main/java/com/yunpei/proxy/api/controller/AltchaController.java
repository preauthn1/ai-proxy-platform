package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.service.auth.AltchaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.altcha.altcha.v2.Altcha;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ALTCHA challenge 端点。
 *
 * <p>返回<strong>原始</strong> challenge JSON（不可包 {@code code/msg/data}），
 * 供前端 {@code <altcha-widget challenge="/api/altcha/challenge">} 直接解析。
 *
 * @author yunpei
 */
@Tag(name = "ALTCHA", description = "人机校验挑战")
@RestController
@RequestMapping("/api/altcha")
@RequiredArgsConstructor
public class AltchaController {

    private final AltchaService altchaService;

    /**
     * 签发 ALTCHA challenge（原始 JSON，非 Result 包装）。
     */
    @GetMapping(value = "/challenge", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "签发 ALTCHA challenge", description = "返回原始 challenge 对象，非 Result 包装")
    public ResponseEntity<String> challenge() {
        Altcha.Challenge challenge = altchaService.createChallenge();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(challenge.toJson());
    }
}
