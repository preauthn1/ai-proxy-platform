package com.yunpei.proxy.api.controller;

import com.yunpei.proxy.api.vo.PublicKeyVO;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.infra.crypto.ServerKeyPairProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 传输加密公钥接口。
 *
 * @author yunpei
 */
@Tag(name = "加密", description = "RSA 公钥拉取")
@RestController
@RequestMapping("/api/encrypt")
@RequiredArgsConstructor
public class EncryptController {

    private final ServerKeyPairProvider serverKeyPairProvider;

    /**
     * 获取全局 RSA 公钥（SPKI base64），供客户端加密 AES 会话密钥。
     */
    @GetMapping("/public/key")
    @Operation(summary = "获取加密公钥", description = "返回 data.publicKey（X.509 SPKI base64）")
    public Result<PublicKeyVO> publicKey() {
        return Result.ok(new PublicKeyVO(serverKeyPairProvider.getPublicKey()));
    }
}
