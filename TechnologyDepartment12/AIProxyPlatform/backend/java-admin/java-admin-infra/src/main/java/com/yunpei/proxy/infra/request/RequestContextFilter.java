package com.yunpei.proxy.infra.request;

import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.request.RequestContext;
import com.yunpei.proxy.common.util.ClientIpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求 ThreadLocal 生命周期：入口 open，出口 close；并预填 requestId / URI / 客户端 IP。
 *
 * <p>顺序紧随 {@link com.yunpei.proxy.infra.log.TraceIdFilter}（{@code HIGHEST_PRECEDENCE}），
 * 用户 id / language 由后续 Sa + Language 拦截器写入。
 *
 * <p>Bean 名必须避开 Spring Boot WebMvc 自带的 {@code requestContextFilter}
 * （{@code OrderedRequestContextFilter}），否则会触发
 * {@code BeanDefinitionOverrideException}。
 *
 * @author yunpei
 */
@Component("appRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class RequestContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RequestContext.open();
        try {
            String requestId = request.getHeader(SecurityHeaders.REQUEST_ID);
            if (requestId != null && !requestId.isEmpty()) {
                RequestContext.setRequestId(requestId);
            }
            RequestContext.setRequestUri(request.getRequestURI());
            RequestContext.setClientIp(resolveClientIp(request));
            filterChain.doFilter(request, response);
        } finally {
            RequestContext.close();
        }
    }

    /** 多代理头 + 合法性校验，见 {@link ClientIpUtils}。 */
    private static String resolveClientIp(HttpServletRequest request) {
        return ClientIpUtils.resolve(
                request.getHeader(SecurityHeaders.FORWARDED_FOR),
                request.getHeader(SecurityHeaders.REAL_IP),
                request.getRemoteAddr(),
                request.getHeader(SecurityHeaders.PROXY_CLIENT_IP),
                request.getHeader(SecurityHeaders.WL_PROXY_CLIENT_IP));
    }
}
