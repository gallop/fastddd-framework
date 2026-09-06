package com.gallop.fastddd.security;

import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 框架默认安全链端到端测试（ECO-006B）。
 *
 * <p>以真实自动配置启动：经 {@code AutoConfiguration.imports} 装配
 * {@link FastDddSecurityAutoConfiguration} → 默认 {@code SecurityFilterChain}
 * （无状态 + anyRequest().authenticated + 配置化 permitAll）+
 * {@code JwtAuthenticationFilter} + {@link JwtTokenProvider} 全链真实 Bean，
 * 不自定义安全链、不关闭 filters、不使用 {@code @WithMockUser}。
 * Token 由容器内的 {@link JwtTokenProvider}（与过滤器同一实例）签发，
 * 走完整「Authorization 头 → 过滤器验签 → SecurityContext → 授权」链路。</p>
 *
 * <p>历史注记：本类曾因上下文装配问题被 {@code @Disabled}（2025-01-10 起）。
 * 根因有二，均已消除：① fast-ddd-web → fast-ddd-crud 传递 druid 自动配置在无数据源时
 * 启动失败（现测试域提供 H2，与真实最小消费者 security-minimal 同契约）；
 * 装配契约另由 {@link JwtEnableConditionWiringTest} 切片覆盖。
 * ② 2026-09-06 重写时发现 fast-ddd-web 的 GlobalExceptionHandler 兜底会把
 * {@code AccessDeniedException} 吞成 500（产品缺陷），已同步修复。</p>
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@SpringBootTest(classes = SecurityIntegrationTest.TestApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "fastddd.security.jwt.secret=test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /** 容器内真实 provider：过滤器验签与测试签发使用同一 secret/signer，验证的是装配产物本身。 */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String accessToken(String username, String role) {
        UserDetails userDetails = new User(username, "password",
                Arrays.asList(new SimpleGrantedAuthority(role)));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        return jwtTokenProvider.generateAccessToken(authentication);
    }

    @Test
    void testAccessSecuredEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void testAccessSecuredEndpointWithValidToken() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                        .header("Authorization", "Bearer " + accessToken("testuser", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Secured endpoint accessed successfully"));
    }

    @Test
    void testAccessAdminEndpointWithAdminRole() throws Exception {
        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer " + accessToken("admin", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Admin endpoint accessed successfully"));
    }

    @Test
    void testAccessAdminEndpointWithUserRole() throws Exception {
        // 方法安全（@PreAuthorize）拒绝必须经 ExceptionTranslationFilter → JwtAccessDeniedHandler
        // 产出 403 信封；修复前被 GlobalExceptionHandler 兜底吞成 500
        mockMvc.perform(get("/api/test/admin")
                        .header("Authorization", "Bearer " + accessToken("testuser", "ROLE_USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void testAccessPublicEndpoint() throws Exception {
        // /public/** 是默认 permit-all 路径（SecurityHttpProperties），匿名可访问
        mockMvc.perform(get("/public/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Public endpoint accessed"));
    }

    @Test
    void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    /**
     * 最小测试应用：仅开启真实自动配置 + 导入探针控制器。
     * 不注册任何安全 Bean——默认安全链必须全部来自框架自动装配。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApplication {
    }

    /** 探针响应（DTO 返回，断言不依赖具体消息转换器选择）。 */
    record ProbeResponse(String message) {
    }

    /**
     * 测试控制器
     */
    @RestController
    static class TestController {

        @GetMapping("/api/test/secured")
        public ProbeResponse securedEndpoint() {
            return new ProbeResponse("Secured endpoint accessed successfully");
        }

        @GetMapping("/api/test/admin")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        public ProbeResponse adminEndpoint() {
            return new ProbeResponse("Admin endpoint accessed successfully");
        }

        @GetMapping("/public/test")
        public ProbeResponse publicEndpoint() {
            return new ProbeResponse("Public endpoint accessed");
        }
    }
}
