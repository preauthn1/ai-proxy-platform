package com.yunpei.proxy.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunpei.proxy.common.constant.SecurityConstants;
import com.yunpei.proxy.common.constant.SecurityHeaders;
import com.yunpei.proxy.common.request.RequestContext;
import com.yunpei.proxy.common.result.ResultCode;
import com.yunpei.proxy.infra.crypto.CryptoService;
import com.yunpei.proxy.infra.crypto.EncryptFilter;
import com.yunpei.proxy.infra.crypto.EncryptKeyPair;
import com.yunpei.proxy.infra.crypto.EncryptKeyPairService;
import com.yunpei.proxy.infra.crypto.ServerKeyPairProvider;
import com.yunpei.proxy.infra.language.LanguageInterceptor;
import com.yunpei.proxy.infra.language.UserLanguageSyncService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * HTTP 安全协议主 seam：Timestamp / Encrypt / Nonce / Sign / Language。
 *
 * <p>通过 Filter 边界断言 Result code/msg、关键响应头与 request 上下文，不绑内部实现。
 *
 * @author yunpei
 */
class SecurityFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SecurityProperties securityProperties;
    private CryptoService cryptoService;
    private ServerKeyPairProvider keyPairProvider;
    private TimestampFilter timestampFilter;
    private EncryptFilter encryptFilter;
    private NonceFilter nonceFilter;
    private SignFilter signFilter;
    private LanguageInterceptor languageInterceptor;
    private MemoryNonceStore nonceStore;
    private RecordingUserLanguageRepo languageRepo;
    private String publicKeyBase64;
    private String privateKeyPem;

    @BeforeEach
    void initFilters() {
        securityProperties = new SecurityProperties();
        securityProperties.getTimestamp().setEnabled(true);
        securityProperties.getEncrypt().setEnabled(true);
        securityProperties.getNonce().setEnabled(true);
        securityProperties.getSign().setEnabled(true);
        securityProperties.getLanguage().setEnabled(true);

        cryptoService = new CryptoService();
        KeyPair keyPair = CryptoService.generateRsaKeyPair();
        publicKeyBase64 = CryptoService.toBase64(keyPair.getPublic());
        privateKeyPem = CryptoService.toPem(keyPair.getPrivate());

        EncryptKeyPairService keyPairService = new EncryptKeyPairService(null) {
            @Override
            public EncryptKeyPair getEncryptKeyPair() {
                return new EncryptKeyPair(publicKeyBase64, privateKeyPem);
            }

            @Override
            public KeyPairResult generateAndCacheKeyPair() {
                return new KeyPairResult(publicKeyBase64, privateKeyPem);
            }
        };
        keyPairProvider = new ServerKeyPairProvider(keyPairService);
        timestampFilter = new TimestampFilter(securityProperties);
        encryptFilter = new EncryptFilter(cryptoService, keyPairProvider, securityProperties);

        nonceStore = new MemoryNonceStore();
        nonceFilter = new NonceFilter(securityProperties, nonceStore);
        signFilter = new SignFilter(cryptoService, keyPairProvider, securityProperties);

        languageRepo = new RecordingUserLanguageRepo();
        Executor syncExecutor = Runnable::run; // 测试中同步执行，便于断言
        UserLanguageSyncService languageSync = new UserLanguageSyncService(languageRepo, syncExecutor);
        languageInterceptor = new LanguageInterceptor(securityProperties, languageSync);
    }

    // ---- Timestamp ----

    @Test
    void timestamp_expired_returnsRequestExpired() throws Exception {
        long expired = System.currentTimeMillis() - (6 * 60 * 1000L);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(expired));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        timestampFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_EXPIRED.getCode());
        assertThat(body.get("msg").asText()).isEqualTo(ResultCode.REQUEST_EXPIRED.getMsg());
    }

    @Test
    void timestamp_valid_passes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        timestampFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void timestamp_missing_passes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        timestampFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void timestamp_disabled_ignoresExpired() throws Exception {
        securityProperties.getTimestamp().setEnabled(false);
        long expired = System.currentTimeMillis() - (6 * 60 * 1000L);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(expired));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        timestampFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    // ---- Encrypt force / whitelist ----

    @Test
    void encrypt_missingKeyOnLogin_rejected() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_ERROR.getCode());
        assertThat(body.get("msg").asText()).isEqualTo(ResultCode.REQUEST_ERROR.getMsg());
    }

    @Test
    void encrypt_publicKeyPath_allowsPlaintext() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/encrypt/public/key");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> {
            chainCalled.set(true);
            s.setContentType("application/json");
            s.getWriter().write("{\"code\":0,\"msg\":\"ok\",\"data\":{\"publicKey\":\"x\"}}");
        });

        assertThat(chainCalled).isTrue();
        assertThat(resp.getHeader(SecurityHeaders.RESPONSE_IS_ENCRYPT)).isNull();
        assertThat(resp.getContentAsString()).contains("publicKey");
    }

    @Test
    void encrypt_altchaPath_allowsPlaintext() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/altcha/challenge");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void encrypt_publicI18nPath_allowsPlaintext() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/i18n/zh-CN");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void encrypt_docStaticResourcePath_allowsPlaintext() throws Exception {
        // doc.html 加载的静态资源（webjars / swagger-resources / csrf）须在白名单内
        for (String path : List.of("/webjars/knife4j-openapi3-ui/app.js", "/swagger-resources", "/csrf")) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse resp = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean(false);

            encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

            assertThat(chainCalled).as("Encrypt 应放行白名单路径 %s", path).isTrue();
        }
    }

    @Test
    void encrypt_disabled_allowsPlainLogin() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(resp.getHeader(SecurityHeaders.RESPONSE_IS_ENCRYPT)).isNull();
    }

    @Test
    void encrypt_validEncryptedRequest_decryptsBodyAndEncryptsResponse() throws Exception {
        String aesKey = CryptoService.generateAesKey();
        String encryptedAesKey = cryptoService.rsaEncrypt(aesKey, CryptoService.parsePublicKeyPem(publicKeyBase64));

        long now = System.currentTimeMillis();
        Map<String, String> aadParams = new LinkedHashMap<>();
        aadParams.put(SecurityHeaders.REQUEST_ID, "req-encrypt-1");
        aadParams.put(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(now));
        String aad = cryptoService.buildAad(aadParams);

        String plainBody = "{\"username\":\"root\",\"password\":\"123456\"}";
        CryptoService.EncryptResult enc = cryptoService.aesEncrypt(plainBody, aesKey, aad);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.addHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY, encryptedAesKey);
        req.addHeader(SecurityHeaders.REQUEST_SIGNATURE, enc.tagIv());
        req.addHeader(SecurityHeaders.REQUEST_ID, aadParams.get(SecurityHeaders.REQUEST_ID));
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, aadParams.get(SecurityHeaders.REQUEST_TIMESTAMP));
        req.setContent(enc.ciphertext().getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<String> bodySeenByBusiness = new AtomicReference<>();

        encryptFilter.doFilter(req, resp, (r, s) -> {
            bodySeenByBusiness.set(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            s.setContentType("application/json");
            s.getWriter().write("{\"code\":0,\"msg\":\"ok\",\"data\":{\"accessToken\":\"t\"}}");
        });

        assertThat(bodySeenByBusiness.get()).isEqualTo(plainBody);
        assertThat(resp.getHeader(SecurityHeaders.RESPONSE_IS_ENCRYPT)).isEqualTo("true");

        String encryptedResponse = resp.getContentAsString();
        String decrypted =
                new String(cryptoService.aesDecryptCombined(encryptedResponse, aesKey, ""), StandardCharsets.UTF_8);
        JsonNode node = MAPPER.readTree(decrypted);
        assertThat(node.get("code").asInt()).isZero();
        assertThat(node.get("data").get("accessToken").asText()).isEqualTo("t");
    }

    @Test
    void encrypt_bodyWithoutSignature_rejected() throws Exception {
        String aesKey = CryptoService.generateAesKey();
        String encryptedAesKey = cryptoService.rsaEncrypt(aesKey, CryptoService.parsePublicKeyPem(publicKeyBase64));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.addHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY, encryptedAesKey);
        req.addHeader(SecurityHeaders.REQUEST_ID, "missing-sign");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        req.setContent("{\"username\":\"root\",\"password\":\"123456\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_ERROR.getCode());
    }

    @Test
    void encrypt_docPath_allowsPlaintext() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void timestamp_invalidFormat_returnsRequestError() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, "not-a-number");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        timestampFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_ERROR.getCode());
    }

    @Test
    void encrypt_stalePublicKey_returnsKeyFailed() throws Exception {
        String aesKey = CryptoService.generateAesKey();
        KeyPair stale = CryptoService.generateRsaKeyPair();
        String encryptedAesKey = cryptoService.rsaEncrypt(aesKey, stale.getPublic());

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.addHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY, encryptedAesKey);
        req.addHeader(SecurityHeaders.REQUEST_ID, "stale");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        encryptFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_KEY_FAILED.getCode());
        assertThat(resp.getHeader(SecurityHeaders.RESPONSE_IS_ENCRYPT)).isNull();
    }

    @Test
    void encryptController_publicKey_returnsResultWithPublicKey() {
        EncryptControllerLike controller = new EncryptControllerLike(keyPairProvider);
        var result = controller.publicKey();
        assertThat(result.getCode()).isZero();
        assertThat(result.getData().get("publicKey")).isEqualTo(publicKeyBase64);
    }

    // ---- Nonce ----

    @Test
    void nonce_sameRequestId_secondFailsWithConflict() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.addHeader(SecurityHeaders.REQUEST_ID, "nonce-1");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        AtomicBoolean firstPassed = new AtomicBoolean(false);
        nonceFilter.doFilter(req1, resp1, (r, s) -> firstPassed.set(true));
        assertThat(firstPassed).isTrue();

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.addHeader(SecurityHeaders.REQUEST_ID, "nonce-1");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        AtomicBoolean secondPassed = new AtomicBoolean(false);
        nonceFilter.doFilter(req2, resp2, (r, s) -> secondPassed.set(true));

        assertThat(secondPassed).isFalse();
        JsonNode body = MAPPER.readTree(resp2.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_NONCE_CONFLICT.getCode());
        assertThat(body.get("msg").asText()).isEqualTo(ResultCode.REQUEST_NONCE_CONFLICT.getMsg());
    }

    @Test
    void nonce_disabled_allowsReplay() throws Exception {
        securityProperties.getNonce().setEnabled(false);
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.addHeader(SecurityHeaders.REQUEST_ID, "nonce-off");
        MockHttpServletResponse resp1 = new MockHttpServletResponse();
        nonceFilter.doFilter(req1, resp1, (r, s) -> {});

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.addHeader(SecurityHeaders.REQUEST_ID, "nonce-off");
        MockHttpServletResponse resp2 = new MockHttpServletResponse();
        AtomicBoolean secondPassed = new AtomicBoolean(false);
        nonceFilter.doFilter(req2, resp2, (r, s) -> secondPassed.set(true));

        assertThat(secondPassed).isTrue();
    }

    @Test
    void nonce_missingRequestId_passes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        nonceFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));
        assertThat(chainCalled).isTrue();
    }

    // ---- Sign (Encrypt off) ----

    @Test
    void sign_encryptOff_missingSignature_rejected() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        securityProperties.getSign().setEnabled(true);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        signFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_ERROR.getCode());
    }

    @Test
    void sign_encryptOff_docStaticResourcePath_allowed() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        securityProperties.getSign().setEnabled(true);

        // doc.html 加载的静态资源须放行（缺签名但不报 Sign 错误）
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/webjars/knife4j-openapi3-ui/app.js");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        signFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void sign_encryptOff_invalidSignature_rejected() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        securityProperties.getSign().setEnabled(true);

        String aesKey = CryptoService.generateAesKey();
        String encryptedAesKey = cryptoService.rsaEncrypt(aesKey, CryptoService.parsePublicKeyPem(publicKeyBase64));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.addHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY, encryptedAesKey);
        req.addHeader(SecurityHeaders.REQUEST_SIGNATURE, "AAAA"); // 非法 tagIv
        req.addHeader(SecurityHeaders.REQUEST_ID, "sign-bad");
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(System.currentTimeMillis()));
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        signFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isFalse();
        JsonNode body = MAPPER.readTree(resp.getContentAsString());
        assertThat(body.get("code").asInt()).isEqualTo(ResultCode.REQUEST_SIGN_FAILED.getCode());
    }

    @Test
    void sign_encryptOff_validSignature_passesAndBodyReadable() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        securityProperties.getSign().setEnabled(true);

        String aesKey = CryptoService.generateAesKey();
        String encryptedAesKey = cryptoService.rsaEncrypt(aesKey, CryptoService.parsePublicKeyPem(publicKeyBase64));
        long now = System.currentTimeMillis();
        String plainBody = "{\"username\":\"root\",\"password\":\"123456\"}";

        Map<String, String> aadParams = new LinkedHashMap<>();
        aadParams.put(SecurityHeaders.REQUEST_ID, "sign-ok");
        aadParams.put(SecurityHeaders.REQUEST_TIMESTAMP, String.valueOf(now));
        aadParams.put(SecurityConstants.SIGN_DATA_AAD_KEY, plainBody);
        String aad = cryptoService.buildAad(aadParams);
        CryptoService.EncryptResult sign = cryptoService.aesEncrypt("", aesKey, aad);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.addHeader(SecurityHeaders.REQUEST_ENCRYPTED_KEY, encryptedAesKey);
        req.addHeader(SecurityHeaders.REQUEST_SIGNATURE, sign.tagIv());
        req.addHeader(SecurityHeaders.REQUEST_ID, aadParams.get(SecurityHeaders.REQUEST_ID));
        req.addHeader(SecurityHeaders.REQUEST_TIMESTAMP, aadParams.get(SecurityHeaders.REQUEST_TIMESTAMP));
        req.setContent(plainBody.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<String> bodySeen = new AtomicReference<>();
        signFilter.doFilter(req, resp, (r, s) -> {
            bodySeen.set(new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        });

        assertThat(bodySeen.get()).isEqualTo(plainBody);
    }

    @Test
    void sign_encryptOn_skipsIndependentSignPath() throws Exception {
        securityProperties.getEncrypt().setEnabled(true);
        securityProperties.getSign().setEnabled(true);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        // Encrypt 开时 SignFilter 不拦截（即使无签名）
        signFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    @Test
    void sign_disabled_allowsUnsignedWhenEncryptOff() throws Exception {
        securityProperties.getEncrypt().setEnabled(false);
        securityProperties.getSign().setEnabled(false);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setContentType("application/json");
        req.setContent("{\"username\":\"root\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        signFilter.doFilter(req, resp, (r, s) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
    }

    // ---- Language ----

    @Test
    void language_xLanguage_preferredAndStoredInRequestContext() {
        RequestContext.open();
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/info");
            req.addHeader(SecurityHeaders.LANGUAGE, "en-US");
            req.addHeader("Accept-Language", "zh-CN,zh;q=0.9");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            assertThat(languageInterceptor.preHandle(req, resp, new Object())).isTrue();
            assertThat(RequestContext.languageOrNull()).isEqualTo("en-US");
        } finally {
            RequestContext.close();
        }
    }

    @Test
    void language_acceptLanguage_fallback() {
        RequestContext.open();
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/info");
            req.addHeader("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            assertThat(languageInterceptor.preHandle(req, resp, new Object())).isTrue();
            assertThat(RequestContext.languageOrNull()).isEqualTo("ja-JP");
        } finally {
            RequestContext.close();
        }
    }

    @Test
    void language_disabled_doesNotSetContext() {
        securityProperties.getLanguage().setEnabled(false);
        RequestContext.open();
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/info");
            req.addHeader(SecurityHeaders.LANGUAGE, "en-US");
            MockHttpServletResponse resp = new MockHttpServletResponse();

            assertThat(languageInterceptor.preHandle(req, resp, new Object())).isTrue();
            assertThat(RequestContext.languageOrNull()).isNull();
        } finally {
            RequestContext.close();
        }
    }

    @Test
    void language_loggedIn_differentCode_asyncUpdates() throws Exception {
        languageRepo.users.put(42L, "zh-CN");

        CountDownLatch latch = new CountDownLatch(1);
        Executor asyncExec = r -> {
            r.run();
            latch.countDown();
        };
        UserLanguageSyncService sync = new UserLanguageSyncService(languageRepo, asyncExec);
        // 可观察仓储：验证不同 languageCode 时异步写库（拦截器登录态由 Sa 保证，此处测 sync 契约）
        sync.syncIfChanged(42L, "en-US");

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(languageRepo.updatedLanguage.get()).isEqualTo("en-US");
        assertThat(languageRepo.updatedUserId.get()).isEqualTo(42L);
    }

    @Test
    void language_loggedIn_sameCode_skipsUpdate() {
        languageRepo.users.put(7L, "zh-CN");
        AtomicBoolean ran = new AtomicBoolean(false);
        Executor exec = r -> {
            ran.set(true);
            r.run();
        };
        UserLanguageSyncService sync = new UserLanguageSyncService(languageRepo, exec);
        sync.syncIfChanged(7L, "zh-CN");

        assertThat(ran).isTrue();
        assertThat(languageRepo.updatedUserId.get()).isNull();
    }

    @Test
    void language_interceptor_triggersSyncWhenUserIdPresent() throws Exception {
        // 通过可注入的 sync spy：拦截器 preHandle 在解析语言后调用 sync（无登录时 userId=null 不写库）
        languageRepo.users.put(99L, "zh-CN");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Long> syncedUser = new AtomicReference<>();
        AtomicReference<String> syncedLang = new AtomicReference<>();
        UserLanguageSyncService spySync =
                new UserLanguageSyncService(languageRepo, r -> {
                    r.run();
                    latch.countDown();
                }) {
                    @Override
                    public void syncIfChanged(Long userId, String languageCode) {
                        syncedUser.set(userId);
                        syncedLang.set(languageCode);
                        super.syncIfChanged(userId, languageCode);
                    }
                };
        LanguageInterceptor interceptor = new LanguageInterceptor(securityProperties, spySync);

        RequestContext.open();
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/user/info");
            req.addHeader(SecurityHeaders.LANGUAGE, "en-US");
            MockHttpServletResponse resp = new MockHttpServletResponse();
            interceptor.preHandle(req, resp, new Object());

            assertThat(RequestContext.languageOrNull()).isEqualTo("en-US");
            // 无 Sa 登录态时 userId 为 null，仍会调用 syncIfChanged 但写库被跳过
            assertThat(syncedLang.get()).isEqualTo("en-US");
            assertThat(syncedUser.get()).isNull();
            assertThat(languageRepo.updatedUserId.get()).isNull();
        } finally {
            RequestContext.close();
        }
    }

    /** 轻量镜像 Controller 行为，避免在 infra 模块依赖 api 包。 */
    private static final class EncryptControllerLike {
        private final ServerKeyPairProvider provider;

        EncryptControllerLike(ServerKeyPairProvider provider) {
            this.provider = provider;
        }

        com.yunpei.proxy.common.result.Result<Map<String, String>> publicKey() {
            return com.yunpei.proxy.common.result.Result.ok(Map.of("publicKey", provider.getPublicKey()));
        }
    }

    /** 测试用内存 NonceStore。 */
    private static final class MemoryNonceStore implements NonceStore {
        private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

        @Override
        public boolean tryAcquire(String nonce, long ttlMs) {
            long now = System.currentTimeMillis();
            Long prev = seen.putIfAbsent(nonce, now + ttlMs);
            if (prev == null) {
                return true;
            }
            if (prev < now) {
                return seen.replace(nonce, prev, now + ttlMs);
            }
            return false;
        }
    }

    /**
     * 可观察的用户语言仓储：绕过 Easy-Query，仅用于 Filter/Service 行为测试。
     */
    private static final class RecordingUserLanguageRepo extends com.yunpei.proxy.service.repository.SysUserRepository {
        private final ConcurrentHashMap<Long, String> users = new ConcurrentHashMap<>();
        private final AtomicReference<Long> updatedUserId = new AtomicReference<>();
        private final AtomicReference<String> updatedLanguage = new AtomicReference<>();

        RecordingUserLanguageRepo() {
            super(null);
        }

        @Override
        public com.yunpei.proxy.service.entity.SysUser findById(Long id) {
            String code = users.get(id);
            if (code == null && !users.containsKey(id)) {
                return null;
            }
            com.yunpei.proxy.service.entity.SysUser user = new com.yunpei.proxy.service.entity.SysUser();
            user.setId(id);
            user.setLanguageCode(code);
            return user;
        }

        @Override
        public long updateLanguageCode(Long userId, String languageCode) {
            users.put(userId, languageCode);
            updatedUserId.set(userId);
            updatedLanguage.set(languageCode);
            return 1L;
        }
    }
}
