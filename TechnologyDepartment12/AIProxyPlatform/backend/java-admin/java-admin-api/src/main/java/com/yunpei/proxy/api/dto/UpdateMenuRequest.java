package com.yunpei.proxy.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * 更新菜单请求。
 *
 * <p>多数字段：null = 不改（JSON 未出现时保持 null）。
 * {@code parentId}/{@code metadata}：需区分「未传」与「显式 null」，由 setter 置 {@code *Present}。
 *
 * @author yunpei
 */
@Getter
@Schema(description = "更新菜单")
public class UpdateMenuRequest {

    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();

    @Schema(description = "父菜单 ID；显式 null 置为根")
    private Long parentId;

    /** 请求体是否包含 parentId 字段。 */
    @JsonIgnore
    private boolean parentIdPresent;

    @Size(max = 64)
    private String name;

    @Size(max = 16)
    private String type;

    @Size(max = 255)
    private String path;

    @Size(max = 255)
    private String component;

    @Size(max = 64)
    private String icon;

    @Size(max = 255)
    private String redirect;

    @Size(max = 128)
    private String permissionCode;

    private String metadata;

    @JsonIgnore
    private boolean metadataPresent;

    private Integer sort;

    private Integer isHidden;

    private Integer isEnabled;

    @Size(max = 512)
    private String remark;

    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdPresent = true;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }

    public void setPermissionCode(String permissionCode) {
        this.permissionCode = permissionCode;
    }

    /**
     * metadata 可为 JSON 字符串或对象；对象序列化为 JSON 文本。
     * 方法参数用 {@link Object} 以便 Jackson 绑定 object/array/string/null。
     */
    public void setMetadata(Object metadata) {
        this.metadataPresent = true;
        if (metadata == null) {
            this.metadata = null;
        } else if (metadata instanceof String s) {
            this.metadata = s;
        } else {
            try {
                this.metadata = METADATA_MAPPER.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                this.metadata = String.valueOf(metadata);
            }
        }
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public void setIsHidden(Integer isHidden) {
        this.isHidden = isHidden;
    }

    public void setIsEnabled(Integer isEnabled) {
        this.isEnabled = isEnabled;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
