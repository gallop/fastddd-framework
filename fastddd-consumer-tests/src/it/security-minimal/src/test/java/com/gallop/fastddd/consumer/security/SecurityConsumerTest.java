package com.gallop.fastddd.consumer.security;

import com.gallop.fastddd.security.FastDddSecurityAutoConfiguration;
import com.gallop.fastddd.security.controller.AuthController;
import com.gallop.fastddd.security.jwt.JwtSigner;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FRM-002：Security 模块最小消费者（H2 + 默认启用 JWT）。
 *
 * <p>依赖仅 {@code fast-ddd-security-starter}（+ H2/测试设施）：fast-ddd-web 为
 * security-starter 的 optional 依赖，不在本消费者 classpath——据此断言两件事：</p>
 * <ol>
 *   <li><b>装配</b>：默认 JWT 链完整装配（{@link JwtSigner}/{@link JwtTokenProvider}/
 *       框架默认 {@link SecurityFilterChain}），且 {@link AuthController} 因
 *       {@code ApiResponse} 不在 classpath 而<b>不</b>装配（模块边界不越界）。</li>
 *   <li><b>行为</b>：经 MockMvc 走真实默认链——无 token 401（信封 {@code $.code}=401）、
 *       候选制品 {@link JwtTokenProvider} 签发的 token 200、无效 token 401。
 *       （SEC-002 关闭开关的装配契约由框架内 JwtEnableConditionWiringTest 覆盖。）</li>
 * </ol>
 */
@SpringBootTest(classes = SecurityConsumerTest.App.class)
@AutoConfigureMockMvc
class SecurityConsumerTest {

    @SpringBootApplication
    @Import(ProbeController.class)
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    /** 候选制品装配出的真实 provider——签发与过滤器验签同一 Bean。 */
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void frameworkDefaultJwtChainIsAssembled() {
        // 消费者未自定义安全链 → 框架默认链必须是唯一链
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtSigner.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtTokenProvider.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(FastDddSecurityAutoConfiguration.class)).hasSize(1);
    }

    @Test
    void authEndpointIsNotAssembledWithoutFastDddWeb() {
        // ApiResponse（fast-ddd-web）不在 classpath → 认证端点不得装配（optional 边界）
        assertThat(context.getBeanNamesForType(AuthController.class)).isEmpty();
    }

    @Test
    void securedEndpointWithoutTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void securedEndpointWithCandidateTokenReturns200() throws Exception {
        String token = jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc.perform(get("/api/probe/secured")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("secured ok"));
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/probe/secured")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    record ProbeResponse(String message) {
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/secured")
        public ProbeResponse secured() {
            return new ProbeResponse("secured ok");
        }
    }
}
