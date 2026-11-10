package com.yunpei.proxy.infra.language;

import com.yunpei.proxy.service.entity.SysUser;
import com.yunpei.proxy.service.repository.SysUserRepository;
import java.util.Objects;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 已登录用户 languageCode 异步收敛：header 语言与库中不同时写库，不阻塞请求线程。
 *
 * @author yunpei
 */
@Slf4j
@Service
public class UserLanguageSyncService {

    private final SysUserRepository sysUserRepository;
    private final Executor languageSyncExecutor;

    public UserLanguageSyncService(
            SysUserRepository sysUserRepository, @Qualifier("loginLogExecutor") Executor languageSyncExecutor) {
        this.sysUserRepository = sysUserRepository;
        this.languageSyncExecutor = languageSyncExecutor;
    }

    /**
     * 若用户已登录且语言偏好与 header 不一致，则异步更新。
     *
     * @param userId       当前用户 id；null 表示未登录，直接跳过
     * @param languageCode 解析后的请求语言
     */
    public void syncIfChanged(Long userId, String languageCode) {
        if (userId == null || languageCode == null || languageCode.isBlank()) {
            return;
        }
        String target = languageCode.trim();
        languageSyncExecutor.execute(() -> updateQuietly(userId, target));
    }

    private void updateQuietly(Long userId, String languageCode) {
        try {
            SysUser user = sysUserRepository.findById(userId);
            if (user == null) {
                return;
            }
            if (Objects.equals(languageCode, user.getLanguageCode())) {
                return;
            }
            sysUserRepository.updateLanguageCode(userId, languageCode);
            log.atDebug()
                    .addKeyValue("userId", userId)
                    .addKeyValue("language", languageCode)
                    .log("异步更新用户 languageCode");
        } catch (Exception e) {
            log.atError().addKeyValue("userId", userId).setCause(e).log("异步更新用户 languageCode 失败");
        }
    }
}
