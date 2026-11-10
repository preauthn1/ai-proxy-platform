package com.yunpei.proxy.infra.casbin;

import com.yunpei.proxy.service.port.CasbinPolicyPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * {@link CasbinPolicyPort} 的 jcasbin 实现。
 *
 * @author yunpei
 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class CasbinPolicyPortImpl implements CasbinPolicyPort {

    private final CasbinService casbinService;

    @Override
    public void replaceUserPolicies(String subject, List<ApiPolicy> policies, boolean keepWildcard) {
        casbinService.removePoliciesForSubject(subject);
        if (policies != null) {
            for (ApiPolicy p : policies) {
                if (p == null || p.path() == null || p.method() == null) {
                    continue;
                }
                casbinService.addPolicy(subject, p.path(), p.method());
            }
        }
        if (keepWildcard) {
            casbinService.addPolicy(subject, "/*", "*");
        }
        log.atInfo()
                .addKeyValue("subject", subject)
                .addKeyValue("count", policies == null ? 0 : policies.size())
                .addKeyValue("keepWildcard", keepWildcard)
                .addKeyValue("logType", "CASBIN")
                .log("replaced policies");
    }

    @Override
    public boolean enforce(String subject, String obj, String act) {
        return casbinService.enforce(subject, obj, act);
    }
}
