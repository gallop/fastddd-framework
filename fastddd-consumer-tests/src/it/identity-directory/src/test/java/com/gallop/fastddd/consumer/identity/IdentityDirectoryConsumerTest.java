package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
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
 * W2-1 目录单开消费者（§4.3 条 5，A 形态）：web 宿主仅启用目录——
 * 宿主 JWT 默认链沿用（无 token 401 / 签名 token 200），identity 登录链与
 * 员工 provider 不装配（员工身份属 C/Worker），Actor 桥可用（W3 目录作业消费）。
 * 目录同步本体（Person 镜像/写端口调用）属 W3，本场景只锁装配面。
 */
@SpringBootTest(classes = IdentityDirectoryConsumerTest.App.class)
@AutoConfigureMockMvc
class IdentityDirectoryConsumerTest {

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
    void hostSecurityStaysAndIdentityLoginSideDoesNotAssemble() {
        // 宿主默认链沿用：唯一链来自 security-starter
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.containsBean("jwtAuthenticationFilter")).isTrue();
        assertThat(context.containsBean("identitySecurityFilterChain")).isFalse();
        // 员工身份 provider 不装配（C/Worker 专属）；B 竞争结果保持 crud 默认胜出
        assertThat(context.containsBean("identityCurrentUserProvider")).isFalse();
        assertThat(context.getBeanNamesForType(CurrentUserProvider.class)).hasSize(1);
        assertThat(context.containsBean("defaultCurrentUserProvider")).isTrue();
        // 登录用例不装配；Actor 桥可用（目录作业 SYSTEM_JOB 上下文，W3）
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(ActorResolver.class)).hasSize(1);
    }

    @Test
    void hostJwtChainStillAuthenticates() throws Exception {
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
