package com.yunpei.proxy.infra.casbin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 验证 Root 通配 policy 与 classpath {@code casbin/model.conf} 匹配语义。
 *
 * <p>对齐 seed：{@code p, 1, /*, *} — 路径 keyMatch2 + method '*'.
 */
class CasbinRootWildcardPolicyTest {

    private Enforcer enforcer;

    @BeforeEach
    void before() throws Exception {
        String modelText = loadClasspath("casbin/model.conf");
        Model model = new Model();
        model.loadModelFromText(modelText);
        enforcer = new Enforcer(model);
        enforcer.addPolicy("1", "/*", "*");
    }

    @Test
    void rootWildcard_allowsArbitraryApiPathAndMethod() {
        assertThat(enforcer.enforce("1", "/api/auth/info", "GET")).isTrue();
        assertThat(enforcer.enforce("1", "/api/system/user/list", "POST")).isTrue();
        assertThat(enforcer.enforce("1", "/api/system/role/1", "DELETE")).isTrue();
    }

    @Test
    void otherUser_isDeniedByDefault() {
        assertThat(enforcer.enforce("2", "/api/auth/info", "GET")).isFalse();
    }

    private static String loadClasspath(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
