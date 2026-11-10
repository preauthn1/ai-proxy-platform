package com.yunpei.proxy.infra.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.result.Result;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.security.SecurityPathMatcher;
import com.yunpei.proxy.infra.security.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 请求体解密 / 响应体加密；Encrypt 开启时除白名单外强制要求加密头。
 *
 * @author yunpei
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class EncryptFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CryptoService cryptoService;
    private final ServerKeyPairProvider serverKeyPairProvider;
    private final SecurityProperties securityProperties;

    public EncryptFilter(
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
        if (encryptedKey == null || encryptedKey.isEmpty()) {
            handleMissingEncryptedKey(request, response, filterChain);
            return;
        }

        String aesKeyBase64 = decryptAesKey(encryptedKey, request, response);
        if (aesKeyBase64 == null) {
            return;
        }

        HttpServletRequest requestToUse = decryptRequestBodyIfPresent(request, response, aesKeyBase64);
        if (requestToUse == null) {
            return;
        }

        encryptResponseAndContinue(requestToUse, response, filterChain, aesKeyBase64);
    }

    private boolean shouldBypass(HttpServletRequest request) {
        if (!securityProperties.getEncrypt().isEnabled()) {
            return true;
        }
        if (request.getRequestURI().endsWith("/events")) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.startsWith("multipart/form-data");
    }

    private void handleMissingEncryptedKey(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isWhitelisted(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        // 强制加密：缺头拒绝
        log.atDebug()
                .addKeyValue("header", SecurityHeaders.REQUEST_ENCRYPTED_KEY)
                .log("Encrypt 已开启且路径不在白名单，缺少加密密钥头");
        writeError(response, ResultCode.REQUEST_ERROR);
    }

    private String decryptAesKey(String encryptedKey, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // 已登录：会话专属私钥；未登录/无会话钥：全局私钥（登录前公钥路径等）
        String privateKeyPem = SessionEncryptKeys.resolvePrivateKeyPem(request, serverKeyPairProvider);
        try {
            return cryptoService.rsaDecrypt(encryptedKey, CryptoService.parsePrivateKeyPem(privateKeyPem));
        } catch (Exception e) {
            log.atDebug().addKeyValue("msg", e.getMessage()).log("RSA 解密 AES key 失败");
            writeError(response, ResultCode.REQUEST_KEY_FAILED);
            return null;
        }
    }

    /**
     * 解密请求体（若有）；失败时已写错误响应并返回 {@code null}。
     */
    private HttpServletRequest decryptRequestBodyIfPresent(
            HttpServletRequest request, HttpServletResponse response, String aesKeyBase64) throws IOException {
        String aad = buildAadFromRequest(request);
        byte[] rawBody = readBodyBytes(request);
        if (rawBody.length == 0) {
            return request;
        }
        String sign = firstHeader(request, SecurityHeaders.REQUEST_SIGNATURE, SecurityHeaders.SIGN_LEGACY);
        if (sign == null || sign.isEmpty()) {
            // Encrypt 开启时有 body 必须带签名并 AES-GCM 解密，避免明文 body 绕过
            log.atDebug()
                    .addKeyValue("header", SecurityHeaders.REQUEST_SIGNATURE)
                    .log("Encrypt 已开启且请求体非空，缺少签名头");
            writeError(response, ResultCode.REQUEST_ERROR);
            return null;
        }
        try {
            String ciphertextBody = new String(rawBody, StandardCharsets.UTF_8);
            byte[] decrypted = cryptoService.aesDecryptCiphertextAndTag(ciphertextBody, sign, aesKeyBase64, aad);
            return new CachedBodyRequestWrapper(request, decrypted);
        } catch (Exception e) {
            log.atDebug().addKeyValue("msg", e.getMessage()).log("请求体解密失败");
            writeError(response, ResultCode.REQUEST_KEY_FAILED);
            return null;
        }
    }

    private void encryptResponseAndContinue(
            HttpServletRequest requestToUse, HttpServletResponse response, FilterChain filterChain, String aesKeyBase64)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(requestToUse, wrappedResponse);
            writeEncryptedResponseIfNeeded(wrappedResponse, aesKeyBase64);
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void writeEncryptedResponseIfNeeded(ContentCachingResponseWrapper wrappedResponse, String aesKeyBase64)
            throws IOException {
        byte[] responseBody = wrappedResponse.getContentAsByteArray();
        if (responseBody.length == 0) {
            return;
        }
        try {
            String plainResponse = new String(responseBody, StandardCharsets.UTF_8);
            CryptoService.EncryptResult result = cryptoService.aesEncrypt(plainResponse, aesKeyBase64, "");
            byte[] encryptedResponse = result.combined().getBytes(StandardCharsets.UTF_8);

            wrappedResponse.resetBuffer();
            wrappedResponse.setHeader(SecurityHeaders.RESPONSE_IS_ENCRYPT, "true");
            wrappedResponse.setContentType("application/json");
            wrappedResponse.setCharacterEncoding("UTF-8");
            wrappedResponse.setContentLength(encryptedResponse.length);
            wrappedResponse.getOutputStream().write(encryptedResponse);
        } catch (Exception e) {
            log.atError().addKeyValue("msg", e.getMessage()).setCause(e).log("响应加密失败");
            wrappedResponse.resetBuffer();
            writeError(wrappedResponse, ResultCode.INTERNAL_ERROR);
        }
    }

    private boolean isWhitelisted(HttpServletRequest request) {
        return SecurityPathMatcher.isWhitelisted(request, securityProperties.getWhitelist());
    }

    private String buildAadFromRequest(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();

        String requestId = firstHeader(request, SecurityHeaders.REQUEST_ID);
        if (requestId != null && !requestId.isEmpty()) {
            params.put(SecurityHeaders.REQUEST_ID, requestId);
        }

        String timestamp = firstHeader(request, SecurityHeaders.REQUEST_TIMESTAMP, SecurityHeaders.TIMESTAMP_LEGACY);
        if (timestamp != null && !timestamp.isEmpty()) {
            params.put(timestampHeaderName(request), timestamp);
        }

        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            String[] values = e.getValue();
            if (values != null && values.length > 0) {
                params.put(e.getKey(), values[0]);
            }
        }

        return cryptoService.buildAad(params);
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

    private static String timestampHeaderName(HttpServletRequest request) {
        String frontendTimestamp = request.getHeader(SecurityHeaders.REQUEST_TIMESTAMP);
        if (frontendTimestamp != null && !frontendTimestamp.isEmpty()) {
            return SecurityHeaders.REQUEST_TIMESTAMP;
        }
        return SecurityHeaders.TIMESTAMP_LEGACY;
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

    /** 供同模块 SignFilter 等复用：缓存 body 供多次读取。 */
    public static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        public CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bis = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return bis.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return bis.read(b, off, len);
                }

                @Override
                public boolean isFinished() {
                    return bis.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
