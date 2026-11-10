package com.yunpei.proxy.common.result;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

/**
 * 统一响应体（3 字段 Q13 决策）。
 *
 * 严格 3 字段：{@code code}, {@code msg}, {@code data}。
 * {@code traceId} 不 出现在 body 中；通过响应头 {@code X-Trace-Id} 暴露。
 * {@code data} 在 error 时为 {@code null}，Jackson 通过 {@link JsonInclude} 不输出。
 *
 * @param <T> 业务数据类型
 * @author yunpei
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 业务码：0 = 成功；非 0 见 ResultCode 枚举。 */
    @Schema(
            description = "业务码;0=成功,非 0 见 ResultCode 枚举",
            example = "0",
            allowableValues = {
                "0", "1001", "1002", "1003", "1004", "1005", "1006", "1007", "1008", "2001", "2002", "2003", "2004"
            })
    private int code;

    /** 人类可读消息（Q13 决策：原 message → msg） */
    @Schema(description = "人类可读消息", example = "ok")
    private String msg;

    /** 业务数据；error 时为 null（Jackson 不输出该字段）。 */
    @Schema(description = "业务数据;error 时为 null(Jackson 不输出该字段)")
    private T data;

    public Result() {}

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    public static <T> Result<T> ok(T data, String msg) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    /**
     * 成功响应，data 为 JSON 对象（Map 或可 JSON 化 POJO）；
     * {@code data == null} 时回退为空 Map，序列化为 {}。
     *
     * Swagger 通过返回的 {@link ObjectResult} 子类泛型展开 data schema。
     */
    public static <T> ObjectResult<T> okObj() {
        return ObjectResult.of();
    }

    /** 见 {@link #okObj()}。 */
    public static <T> ObjectResult<T> okObj(T data) {
        return ObjectResult.of(data);
    }

    /**
     * 成功响应，data 为 List；{@code data == null} 时回退为空列表，序列化为 []。
     *
     * Swagger 通过返回的 {@link ListResult} 子类泛型展开 array schema。
     */
    public static <T> ListResult<T> okList() {
        return ListResult.of();
    }

    /** 见 {@link #okList()}。 */
    public static <T> ListResult<T> okList(List<T> data) {
        return ListResult.of(data);
    }

    public static <T> Result<T> error(ResultCode code) {
        return new Result<>(code.getCode(), code.getMsg(), null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(ResultCode code, String msg) {
        return new Result<>(code.getCode(), msg, null);
    }

    /** 判断当前响应是否为成功响应（code == ResultCode.SUCCESS.getCode()）。 */
    @JsonIgnore
    public final boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
