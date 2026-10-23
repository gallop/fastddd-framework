package com.gallop.fastddd.consumer.security;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.security.controller.AuthController;
import com.gallop.fastddd.security.controller.VerificationCodeController;
import com.gallop.fastddd.security.jwt.JwtSigner;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import com.gallop.fastddd.security.strategy.EmailCodeAuthStrategy;
import com.gallop.fastddd.security.strategy.PasswordAuthStrategy;
import com.gallop.fastddd.security.strategy.SmsCodeAuthStrategy;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1（评审 R1/§4.5）：显式关闭本地认证族的消费者。
 *
 * <p>与 security-minimal 的差异：classpath 有 fast-ddd-web（ApiResponse 可见，
 * 控制器在 local-auth=true 时会装配），配置 {@code fastddd.security.local-auth.enabled=false}。
 * 据此断言三件事：</p>
 * <ol>
 *   <li><b>装配</b>：本地认证族 Bean 全部不装配（AuthController/VerificationCodeController/
 *       三策略/AuthStrategyRegistry/SecurityIdentityService/UserDetailsService/验证码设施）。</li>
 *   <li><b>路由</b>：RequestMappingHandlerMapping 无任何 {@code /api/auth} 前缀模式——
 *       路由缺失的权威判据（classpath 含 fast-ddd-web 时无 handler 请求被其
 *       GlobalExceptionHandler 映射为 500，HTTP 状态不作断言）。</li>
 *   <li><b>不受控项保留</b>：JWT 验证基础（JwtSigner/JwtTokenProvider/默认链）照常工作
 *       （无 token 401 / 候选 token 200 / 无效 token 401）；CurrentUserProvider 接口恰有
 *       一个实现（web+crud 同 classpath 时为 crud 默认提供者赢得 @CoM 竞争，
 *       security 桥接退让——1.0.0 既有顺序行为），按接口计数断言。</li>
 * </ol>
 * 本场景为 H2、无任何飞书调用，不验证 identity 能力（W2+）。
 */
@SpringBootTest(classes = SecurityLocalAuthOffConsumerTest.App.class)
@AutoConfigureMockMvc
class SecurityLocalAuthOffConsumerTest {

    @SpringBootApplication
    @Import(ProbeController.class)
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void localAuthFamilyBeansAreNotAssembled() {
        assertThat(context.getBeanNamesForType(AuthController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(VerificationCodeController.class)).isEmpty();
        assertThat(context.getBeanNamesForType(PasswordAuthStrategy.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SmsCodeAuthStrategy.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EmailCodeAuthStrategy.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AuthStrategyRegistry.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityIdentityService.class)).isEmpty();
        // 默认用户名认证回退不装配（框架 userDetailsService 被门控；Boot 默认用户因框架
        // AuthenticationManager bean 定义存在而退让）
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    @Test
    void noAuthRoutesAreMapped() {
        Set<String> patterns = new HashSet<>();
        requestMappingHandlerMapping.getHandlerMethods().keySet()
                .forEach(info -> patterns.addAll(info.getPatternValues()));
        // 路由缺失的权威断言：全量 handler 映射无任何 /api/auth 前缀模式。
        // （HTTP 状态不作断言：fast-ddd-web 的 GlobalExceptionHandler 会把无 handler 请求
        // 映射为 500 信封——web 模块既有行为，与本开关无关。）
        assertThat(patterns).isNotEmpty().noneMatch(p -> p.startsWith("/api/auth"));
    }

    @Test
    void jwtValidationInfraStillWorks() throws Exception {
        // JWT 基础与默认链保留（local-auth 与 jwt 正交）
        assertThat(context.getBeanNamesForType(JwtSigner.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtTokenProvider.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        // CurrentUserProvider 能力不受 local-auth 影响（§4.5 不受控清单）。本组合下
        // crud 的 DefaultCurrentUserProvider 先注册、security 桥接按 @ConditionalOnMissingBean
        // 退让（1.0.0 既有顺序行为）——断言接口有且仅有一个实现，不绑定具体胜者
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).hasSize(1);

        mockMvc.perform(get("/api/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));

        String token = jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc.perform(get("/api/probe/secured")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("secured ok"));

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
