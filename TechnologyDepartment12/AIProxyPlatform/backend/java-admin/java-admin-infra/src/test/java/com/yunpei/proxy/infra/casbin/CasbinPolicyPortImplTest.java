package com.yunpei.proxy.infra.casbin;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunpei.proxy.service.port.CasbinPolicyPort.ApiPolicy;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 用户策略替换后 enforce 立即一致。
 */
class CasbinPolicyPortImplTest {

    private CasbinPolicyPortImpl port;
    private Enforcer enforcer;

    @BeforeEach
    void initPort() throws Exception {
        String modelText = loadClasspath("casbin/model.conf");
        Model model = new Model();
        model.loadModelFromText(modelText);
        enforcer = new Enforcer(model);
        CasbinService casbinService = new CasbinService(enforcer);
        port = new CasbinPolicyPortImpl(casbinService);
    }

    @Test
    void replaceUserPolicies_expandsApis_andEnforces() {
        port.replaceUserPolicies(
                "2",
                List.of(new ApiPolicy("/api/system/user/list", "GET"), new ApiPolicy("/api/system/user", "POST")),
                false);

        assertThat(port.enforce("2", "/api/system/user/list", "GET")).isTrue();
        assertThat(port.enforce("2", "/api/system/user", "POST")).isTrue();
        assertThat(port.enforce("2", "/api/system/user/list", "POST")).isFalse();
        assertThat(port.enforce("2", "/api/system/role/list", "GET")).isFalse();
    }

    @Test
    void replaceUserPolicies_keepWildcard_allowsAll() {
        port.replaceUserPolicies("1", List.of(), true);
        assertThat(port.enforce("1", "/api/system/user/99", "DELETE")).isTrue();
    }

    @Test
    void replaceUserPolicies_clearsPrevious() {
        port.replaceUserPolicies("2", List.of(new ApiPolicy("/api/a", "GET")), false);
        assertThat(port.enforce("2", "/api/a", "GET")).isTrue();

        port.replaceUserPolicies("2", List.of(new ApiPolicy("/api/b", "GET")), false);
        assertThat(port.enforce("2", "/api/a", "GET")).isFalse();
        assertThat(port.enforce("2", "/api/b", "GET")).isTrue();
    }

    private static String loadClasspath(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
