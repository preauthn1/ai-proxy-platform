package com.yunpei.proxy.common.constant;

/**
 * 鉴权相关常量。
 *
 * @author yunpei
 */
public final class SecurityConstants {

    /** Sa-Token token 名称（请求头名；配合 token-prefix=Bearer） */
    public static final String TOKEN_NAME = "Authorization";

    /** 系统内置 Root 角色 code */
    public static final String ROLE_ROOT = "root";

    /** Sign 独立验签时，AAD 中请求体字段名（对齐 Go {@code SigData}）。 */
    public static final String SIGN_DATA_AAD_KEY = "signData";

    private SecurityConstants() {}
}
