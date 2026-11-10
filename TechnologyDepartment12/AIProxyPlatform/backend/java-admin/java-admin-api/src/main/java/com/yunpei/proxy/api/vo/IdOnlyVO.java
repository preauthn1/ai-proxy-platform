package com.yunpei.proxy.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 仅返回资源 id 的响应（如重置密码）。
 *
 * @author yunpei
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "仅含 id")
public class IdOnlyVO {

    @Schema(description = "资源 ID", example = "1")
    private Long id;
}
