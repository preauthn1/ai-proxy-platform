package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加密公钥响应（字段名对齐前端：publicKey）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "RSA 公钥")
public class PublicKeyVO {

    @Schema(description = "X.509 SPKI base64 公钥")
    private String publicKey;
}
