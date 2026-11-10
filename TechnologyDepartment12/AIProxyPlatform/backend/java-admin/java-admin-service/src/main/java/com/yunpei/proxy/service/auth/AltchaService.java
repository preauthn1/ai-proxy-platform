package com.yunpei.proxy.service.auth;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.altcha.altcha.v2.Altcha;
import org.springframework.stereotype.Service;

/**
 * ALTCHA PoW 挑战签发与校验。
 *
 * <p>协议 v2（PBKDF2/SHA-256），与 mock / 前端 {@code altcha} widget 对齐。
 * challenge 接口返回原始 challenge 对象（不可包 {@code Result}）。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AltchaService {

    private final AltchaProperties altchaProperties;

    private static final String ALGORITHM = "PBKDF2/SHA-256";

    /** 已消费 signature，防重放（进程内；多实例部署需换 Redis）。 */
    private final Set<String> consumedSignatures = ConcurrentHashMap.newKeySet();

    /**
     * 签发新 challenge。
     *
     * @return 库原始 Challenge（JSON 字段：parameters / signature）
     */
    public Altcha.Challenge createChallenge() {
        try {
            Altcha.CreateChallengeOptions options = new Altcha.CreateChallengeOptions()
                    .algorithm(ALGORITHM)
                    .cost(altchaProperties.getCost())
                    .hmacSignatureSecret(altchaProperties.getHmacSecret())
                    .expiresInSeconds(altchaProperties.getExpiresSeconds());
            return Altcha.createChallenge(options);
        } catch (Exception e) {
            log.atError().addKeyValue("logType", "ALTCHA").setCause(e).log("createChallenge failed");
            throw new IllegalStateException("ALTCHA challenge 签发失败", e);
        }
    }

    /**
     * 校验 widget 提交的 Base64 payload。
     *
     * @param payloadBase64 登录体 {@code altcha} 字段
     * @return true=通过
     */
    public boolean verify(String payloadBase64) {
        if (payloadBase64 == null || payloadBase64.isBlank()) {
            return false;
        }
        try {
            Altcha.Payload payload = Altcha.parsePayload(payloadBase64);
            if (payload == null
                    || payload.challenge() == null
                    || payload.challenge().signature() == null) {
                return false;
            }
            String signature = payload.challenge().signature();
            if (consumedSignatures.contains(signature)) {
                log.atWarn()
                        .addKeyValue("logType", "ALTCHA")
                        .addKeyValue("signature", signature)
                        .log("replay detected");
                return false;
            }
            Altcha.VerifySolutionResult result =
                    Altcha.verifySolution(payloadBase64, altchaProperties.getHmacSecret(), Altcha.kdf(ALGORITHM));
            if (result.verified()) {
                consumedSignatures.add(signature);
                return true;
            }
            log.atWarn()
                    .addKeyValue("logType", "ALTCHA")
                    .addKeyValue("expired", result.expired())
                    .addKeyValue("invalidSig", result.invalidSignature())
                    .addKeyValue("invalidSol", result.invalidSolution())
                    .log("verify failed");
            return false;
        } catch (Exception e) {
            log.atWarn()
                    .addKeyValue("logType", "ALTCHA")
                    .addKeyValue("msg", e.getMessage())
                    .setCause(e)
                    .log("verify exception");
            return false;
        }
    }
}
