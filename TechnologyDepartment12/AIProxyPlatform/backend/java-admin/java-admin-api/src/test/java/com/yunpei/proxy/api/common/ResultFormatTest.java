package com.yunpei.proxy.api.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.result.ListResult;
import com.yunpei.proxy.common.result.ObjectResult;
import com.yunpei.proxy.common.result.Result;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Result} 序列化测试（Q13 决策）。
 *
 * <p>验证响应体严格 3 字段：{@code code}, {@code msg}, {@code data}。
 * <p>{@code traceId} <strong>不</strong>出现在 body 中。
 * <p>同时覆盖 {@link ObjectResult} / {@link ListResult} 的 null 回退语义。
 *
 * @author yunpei
 */
class ResultFormatTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void result_ok_hasOnlyThreeFields() throws Exception {
        Result<String> result = Result.ok("hello");

        String json = mapper.writeValueAsString(result);

        // 严格 3 字段
        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        assertThat(json).contains("\"data\":\"hello\"");
        // 验证不存在的字段
        assertThat(json).doesNotContain("\"traceId\"");
        assertThat(json).doesNotContain("\"message\""); // 旧字段名
    }

    @Test
    void result_error_hasOnlyThreeFields() throws Exception {
        Result<Object> result = Result.error(2002, "凭证错误");

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"code\":2002");
        assertThat(json).contains("\"msg\":\"凭证错误\"");
        assertThat(json).doesNotContain("\"data\""); // error 不带 data
        assertThat(json).doesNotContain("\"traceId\"");
        assertThat(json).doesNotContain("\"message\"");
    }

    @Test
    void result_isSuccess() {
        assertThat(Result.ok().isSuccess()).isTrue();
        assertThat(Result.error(1, "x").isSuccess()).isFalse();
    }

    // ---------------- ObjectResult ----------------

    @Test
    void objectResult_of_dataNull_serializesAsEmptyObject() throws Exception {
        ObjectResult<Map<String, Object>> result = ObjectResult.of();

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        // null 回退为空 Map → 序列化为 {}
        assertThat(json).contains("\"data\":{}");
    }

    @Test
    void objectResult_of_withMap_serializesEntries() throws Exception {
        ObjectResult<Map<String, Object>> result = ObjectResult.of(Map.of("name", "admin"));

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"data\":{\"name\":\"admin\"}");
    }

    @Test
    void objectResult_of_withPojo_serializesFields() throws Exception {
        ObjectResult<UserView> result = ObjectResult.of(new UserView(1L, "admin"));

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"data\":{");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"username\":\"admin\"");
    }

    @Test
    void objectResult_isStillResultSubclass() {
        ObjectResult<Map<String, Object>> result = ObjectResult.of();
        assertThat(result).isInstanceOf(Result.class);
        assertThat(result.isSuccess()).isTrue();
    }

    // ---------------- ListResult ----------------

    @Test
    void listResult_of_dataNull_serializesAsEmptyArray() throws Exception {
        ListResult<String> result = ListResult.of();

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"code\":0");
        assertThat(json).contains("\"msg\":\"ok\"");
        // null 回退为空 List → 序列化为 []
        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void listResult_of_withList_serializesElements() throws Exception {
        ListResult<String> result = ListResult.of(List.of("a", "b"));

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"data\":[\"a\",\"b\"]");
    }

    @Test
    void listResult_of_emptyList_remainsEmptyArray() throws Exception {
        ListResult<String> result = ListResult.of(List.of());

        String json = mapper.writeValueAsString(result);

        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void listResult_isStillResultSubclass() {
        ListResult<String> result = ListResult.of();
        assertThat(result).isInstanceOf(Result.class);
        assertThat(result.isSuccess()).isTrue();
    }

    // ---------------- Result.okObj / Result.okList 入口 ----------------

    @Test
    void result_okObj_returnsObjectResultSubclass_nullDataAsEmptyObject() throws Exception {
        ObjectResult<Map<String, Object>> result = Result.okObj();

        assertThat(result).isInstanceOf(ObjectResult.class);
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"data\":{}");
    }

    @Test
    void result_okObj_withPojo_serializesFields() throws Exception {
        ObjectResult<UserView> result = Result.okObj(new UserView(2L, "alice"));

        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"id\":2");
        assertThat(json).contains("\"username\":\"alice\"");
    }

    @Test
    void result_okList_returnsListResultSubclass_nullDataAsEmptyArray() throws Exception {
        ListResult<String> result = Result.okList();

        assertThat(result).isInstanceOf(ListResult.class);
        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"data\":[]");
    }

    @Test
    void result_okList_withList_serializesElements() throws Exception {
        ListResult<String> result = Result.okList(List.of("x", "y"));

        String json = mapper.writeValueAsString(result);
        assertThat(json).contains("\"data\":[\"x\",\"y\"]");
    }

    /** 测试用 POJO。 */
    record UserView(Long id, String username) {}
}
