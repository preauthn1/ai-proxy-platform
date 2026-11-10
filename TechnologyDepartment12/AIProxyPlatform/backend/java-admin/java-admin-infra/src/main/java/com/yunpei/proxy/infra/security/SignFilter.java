package com.yunpei.proxy.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.constant.SecurityConstants;
import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.crypto.CryptoService;
import com.yunpei.proxy.infra.crypto.EncryptFilter;
import com.yunpei.proxy.infra.crypto.ServerKeyPairProvider;
import com.yunpei.proxy.infra.crypto.SessionEncryptKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 独立 Sign 校验：仅在 Encrypt 关闭且 Sign 开启时生效。
 *
 * <p>对齐 Go {@code SignMiddleware}：RSA 解 AES key，AAD = 排序后的 Request-ID / Timestamp / query /
 * body({@code signData})，用 AES-GCM（空 ciphertext + tagIv）校验签名。
 *
 * @author yunpei
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public final class SignFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CryptoService cryptoService;
    private final ServerKeyPairProvider serverKeyPairProvider;
    private final SecurityProperties securityProperties;

    public SignFilter(
            CryptoService cryptoService,
            ServerKeyPairProvider serverKeyPairProvider,
            SecurityProperties securityProperties) {
        this.cryptoService = cryptoService;
        this.serverKeyPairProvider = serverKeyPairProvider;
        this.securityProperties = securityProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldBypass(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String encryptedKey = request.getHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY);
        String sign = firstHeader(request, SecurityHeaders.REQUEST_SIGNATURE, SecurityHeaders.SIGN_LEGACY);
        if (encryptedKey == null || encryptedKey.isEmpty() || sign == null || sign.isEmpty()) {
            log.debug("Sign 已开启，缺少加密密钥头或签名头");
            writeError(response, ResultCode.REQUEST_ERROR);
            return;
        }

        String aesKeyBase64 = decryptAesKey(encryptedKey, request, response);
        if (aesKeyBase64 == null) {
            return;
        }

        byte[] rawBody = readBodyBytes(request);
        HttpServletRequest requestToUse = new EncryptFilter.CachedBodyRequestWrapper(request, rawBody);
        String aad = buildSignAad(requestToUse, rawBody);
        if (!cryptoService.verifySign(sign, aesKeyBase64, aad)) {
            log.debug("Sign 校验失败");
            writeError(response, ResultCode.REQUEST_SIGN_FAILED);
            return;
        }

        filterChain.doFilter(requestToUse, response);
    }

    private boolean shouldBypass(HttpServletRequest request) {
        // Encrypt 优先：开启时由 EncryptFilter 完成 body/AAD 完整性，不重复独立 Sign
        if (securityProperties.getEncrypt().isEnabled()
                || !securityProperties.getSign().isEnabled()) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || SecurityPathMatcher.isWhitelisted(request, securityProperties.getWhitelist())) {
            return true;
        }
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            return true;
        }
        return request.getRequestURI().endsWith("/events");
    }

    private String decryptAesKey(String encryptedKey, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String privateKeyPem = SessionEncryptKeys.resolvePrivateKeyPem(request, serverKeyPairProvider);
            return cryptoService.rsaDecrypt(encryptedKey, CryptoService.parsePrivateKeyPem(privateKeyPem));
        } catch (Exception e) {
            log.atDebug().addKeyValue("msg", e.getMessage()).log("Sign 路径 RSA 解密 AES key 失败");
            writeError(response, ResultCode.REQUEST_KEY_FAILED);
            return null;
        }
    }

    private String buildSignAad(HttpServletRequest request, byte[] rawBody) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, SecurityHeaders.REQUEST_ID, firstHeader(request, SecurityHeaders.REQUEST_ID));
        putTimestamp(params, request);
        putQueryParams(params, request);
        if (rawBody.length > 0) {
            params.put(SecurityConstants.SIGN_DATA_AAD_KEY, new String(rawBody, StandardCharsets.UTF_8));
        }
        return cryptoService.buildAad(params);
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }

    private static void putTimestamp(Map<String, String> params, HttpServletRequest request) {
        String timestamp = firstHeader(request, SecurityHeaders.REQUEST_TIMESTAMP, SecurityHeaders.TIMESTAMP_LEGACY);
        if (timestamp == null || timestamp.isEmpty()) {
            return;
        }
        String frontendTimestamp = request.getHeader(SecurityHeaders.REQUEST_TIMESTAMP);
        if (frontendTimestamp != null && !frontendTimestamp.isEmpty()) {
            params.put(SecurityHeaders.REQUEST_TIMESTAMP, timestamp);
        } else {
            params.put(SecurityHeaders.TIMESTAMP_LEGACY, timestamp);
        }
    }

    private static void putQueryParams(Map<String, String> params, HttpServletRequest request) {
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            String[] values = e.getValue();
            if (values != null && values.length > 0) {
                params.put(e.getKey(), values[0]);
            }
        }
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

    private static byte[] readBodyBytes(HttpServletRequest request) throws IOException {
        try (InputStream inputStream = request.getInputStream();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            inputStream.transferTo(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void writeError(HttpServletResponse response, ResultCode code) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Result<Void> error = Result.error(code);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(error));
    }
}
