package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RSA 密钥对响应（仅 dev：mock 联调同步；字段 publicKey + privateKey）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RSA 密钥对（dev）")
public class RsaKeyPairVO {

    @Schema(description = "X.509 SPKI base64 公钥")
    private String publicKey;

    @Schema(description = "PKCS#8 PEM 私钥")
    private String privateKey;
}
