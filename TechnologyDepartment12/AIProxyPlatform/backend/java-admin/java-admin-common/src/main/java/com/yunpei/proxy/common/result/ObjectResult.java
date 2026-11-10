package com.yunpei.proxy.common.result;

import java.util.Collections;

/**
 * JSON 对象响应体。{@code data} 为 null 时回退到空 Map（序列化为 <code>{}</code>）。
 *
 * <p>用途：让 Swagger/OpenAPI 通过具体子类泛型 {@code ObjectResult<UserVO>}
 * 直接展开 {@code data} schema，而不是 {@code Result<T>} 这种 schema 抽象的形式。
 *
 * <p>约束：{@code T} 应为 JSON 对象类型（Map 或可 JSON 化的 POJO），<strong>不要</strong>
 * 用于标量（int / long / String / boolean），否则 null 回退到 <code>{}</code> 语义不正确。
 *
 * @param <T> 业务数据类型（Map 或可 JSON 化的 POJO）
 * @author yunpei
 */
public class ObjectResult<T> extends Result<T> {

    public ObjectResult() {}

    /** 等价于 {@code of(null)}：data 回退为空 Map。 */
    public static <T> ObjectResult<T> of() {
        return of(null);
    }

    /** 构造成功响应；{@code data} 为 null 时回退为空 Map（序列化为 <code>{}</code>）。 */
    @SuppressWarnings("unchecked")
    public static <T> ObjectResult<T> of(T data) {
        ObjectResult<T> result = new ObjectResult<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMsg(ResultCode.SUCCESS.getMsg());
        result.setData(data != null ? data : (T) Collections.emptyMap());
        return result;
    }
}
