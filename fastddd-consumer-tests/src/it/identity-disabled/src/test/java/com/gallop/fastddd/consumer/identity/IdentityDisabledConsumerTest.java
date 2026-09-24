package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.spring.actor.SecurityContextActorResolver;
import com.gallop.fastddd.security.controller.AuthController;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-1（§4.5/§七）：B 形态消费者——identity 全家桶在 classpath、{@code fastddd.identity.enabled}
 * 缺省（false）。
 *
 * <p>断言与引入 identity 之前完全一致（B 零差异）：</p>
 * <ol>
 *   <li>JWT 验证链照常：无 token 401 / 携带签名 token 200 / 无效 token 401。</li>
 *   <li>本地认证族照常（local-auth 缺省 true）：AuthController 装配。</li>
 *   <li>CurrentUserProvider 竞争结果不变：web+crud 同 classpath 时 crud 默认提供者胜出
 *       （1.0.0 既有顺序行为），接口恰有一个实现。</li>
 *   <li>identity 功能件一概不装配：无登录用例、无 Actor 解析、无 identity 会话链；
 *       guard 仅做校验不产生副作用。</li>
 * </ol>
 */
@SpringBootTest(classes = IdentityDisabledConsumerTest.App.class)
@AutoConfigureMockMvc
class IdentityDisabledConsumerTest {

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

    @Test
    void legacyJwtChainBehavesAsBefore() throws Exception {
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);

        mockMvc.perform(get("/api/probe/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        String token = jwtTokenProvider.generateAccessToken(
                new UsernamePasswordAuthenticationToken(
                        new User("consumer", "password",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc.perform(get("/api/probe/secured").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("secured ok"));

        mockMvc.perform(get("/api/probe/secured").header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void legacyLocalAuthFamilyStillAssembles() {
        assertThat(context.getBeanNamesForType(AuthController.class)).hasSize(1);
    }

    @Test
    void currentUserProviderRaceUnchanged() {
        // B 形态竞争结果与 1.0.0 一致：crud 默认提供者胜出、security 桥接退让、identity 不参赛
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).hasSize(1);
        assertThat(context.containsBean("defaultCurrentUserProvider")).isTrue();
        assertThat(context.containsBean("securityCurrentUserProvider")).isFalse();
        assertThat(context.containsBean("identityCurrentUserProvider")).isFalse();
    }

    @Test
    void identityFunctionalityStaysOff() {
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityContextActorResolver.class)).isEmpty();
        assertThat(context.containsBean("identitySecurityFilterChain")).isFalse();
        // guard 本身无条件装配（仅校验），不产生任何功能 Bean
        assertThat(context.containsBean("identityModeGuard")).isTrue();
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
