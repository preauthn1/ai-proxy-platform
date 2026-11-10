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
 * 校验 {@code X-Request-Timestamp}（兼容 {@code X-Timestamp}）是否在时间窗内。
 *
 * <p>有头才校验；开关关闭时直接放行。
 *
 * @author yunpei
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class TimestampFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SecurityProperties securityProperties;

    public TimestampFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!securityProperties.getTimestamp().isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String timestampHeader =
                firstHeader(request, SecurityHeaders.REQUEST_TIMESTAMP, SecurityHeaders.TIMESTAMP_LEGACY);
        if (timestampHeader != null && !timestampHeader.isEmpty()) {
            try {
                long timestamp = Long.parseLong(timestampHeader);
                long now = System.currentTimeMillis();
                long expireMs = securityProperties.getTimestamp().getExpireMs();
                if (Math.abs(now - timestamp) > expireMs) {
                    log.atDebug()
                            .addKeyValue("timestamp", timestamp)
                            .addKeyValue("now", now)
                            .log("请求时间戳过期");
                    writeError(response, ResultCode.REQUEST_EXPIRED);
                    return;
                }
            } catch (NumberFormatException e) {
                log.atDebug().addKeyValue("header", timestampHeader).log("非法时间戳头");
                writeError(response, ResultCode.REQUEST_ERROR);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String firstHeader(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static void writeError(HttpServletResponse response, ResultCode code) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Result<Void> error = Result.error(code);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(error));
    }
}
