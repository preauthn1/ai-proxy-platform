package com.yunpei.proxy.infra.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunpei.proxy.common.exception.AuthException;
import com.yunpei.proxy.common.exception.BizException;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link GlobalExceptionHandler} 单元测试。
 *
 * <p>Q13 决策验证：响应体严格 3 字段，traceId 不在 body。
 *
 * @author yunpei
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBiz_returnsCorrectCodeAndStatus() {
        BizException ex = new BizException(4001, "用户已存在");

        ResponseEntity<Result<Object>> resp = handler.handleBiz(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(4001);
        assertThat(resp.getBody().getMsg()).isEqualTo("用户已存在");
        assertThat(resp.getBody().getData()).isNull();
    }

    @Test
    void handleAuth_returns401_forInvalidCredentials() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.invalidCredentials());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.AUTH_INVALID_CREDENTIALS.getCode());
    }

    @Test
    void handleAuth_returns403_forForbidden() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.forbidden());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.AUTH_FORBIDDEN.getCode());
    }

    @Test
    void handleAuth_returns403_forAccessBlocked_withFixedMsg() {
        ResponseEntity<Result<Object>> resp = handler.handleAuth(AuthException.accessBlocked());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.ACCESS_BLOCKED.getCode());
        assertThat(resp.getBody().getMsg()).isEqualTo("Access Blocked");
    }

    @Test
    void handleAny_returns500_andHidesRealMessage() {
        ResponseEntity<Result<Object>> resp =
                handler.handleAny(new RuntimeException("SQL: SELECT * FROM x WHERE id=1"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo(ResultCode.INTERNAL_ERROR.getCode());
        assertThat(resp.getBody().getMsg()).isEqualTo("内部错误");
        // 真实异常 message 不会泄露
        assertThat(resp.getBody().getMsg()).doesNotContain("SQL");
    }
}
