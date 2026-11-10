package com.yunpei.proxy.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Nonce 防重放：以 {@code X-Request-ID} 为 nonce，TTL 内第二次出现返回 {@link ResultCode#REQUEST_NONCE_CONFLICT}。
 *
 * <p>顺序：Timestamp 之后、Encrypt/Sign 之前。无 Request-ID 时不校验。
 *
 * @author yunpei
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public final class NonceFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecurityProperties securityProperties;
    private final NonceStore nonceStore;

    public NonceFilter(SecurityProperties securityProperties, NonceStore nonceStore) {
        this.securityProperties = securityProperties;
        this.nonceStore = nonceStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.getNonce().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = request.getHeader(SecurityHeaders.REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        long ttlMs = securityProperties.resolveNonceExpireMs();
        try {
            if (!nonceStore.tryAcquire(requestId, ttlMs)) {
                log.atDebug().addKeyValue("requestId", requestId).log("Nonce 冲突");
                writeError(response, ResultCode.REQUEST_NONCE_CONFLICT);
                return;
            }
        } catch (Exception e) {
            log.atError().addKeyValue("msg", e.getMessage()).setCause(e).log("Nonce 存储失败");
            writeError(response, ResultCode.INTERNAL_ERROR);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, ResultCode code) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Result<Void> error = Result.error(code);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(error));
    }
}
