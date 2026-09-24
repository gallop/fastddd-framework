package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W2-1 正例（§4.3 条 3）：宿主声明 host-owned=true 且提供自建链（复用 identity 会话
 * 过滤器）——identity 链退让、终检通过、会话复验与公共路径放行按宿主策略工作。
 */
@SpringBootTest(classes = HostOwnedSuccessConsumerTest.App.class,
        properties = "fastddd.identity.security.host-owned=true")
@AutoConfigureMockMvc
class HostOwnedSuccessConsumerTest {

    @SpringBootApplication
    @Import({HostSecuritySupport.class, ProbeController.class})
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalLoginUseCase localLoginUseCase;

    @Test
    void hostChainTakesOverWithIdentitySessionFilter() throws Exception {
        // 宿主链唯一，identity 链按 @CoM 退让（终检认可 host-owned 声明）
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).hasSize(1);
        assertThat(context.containsBean("hostChain")).isTrue();
        assertThat(context.containsBean("identitySecurityFilterChain")).isFalse();

        // 公共路径按宿主策略放行
        mockMvc.perform(get("/api/public/ping")).andExpect(status().isOk());

        // 无会话：401；桩登录签发会话 → 宿主链内每请求权威复验通过，名 = principalId
        LoginResult result = localLoginUseCase.login(HostSecuritySupport.command("ou_host_1"));
        String sessionId = result.session().sessionId().value();
        mockMvc.perform(get("/api/probe/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/probe/me").cookie(new Cookie("FASTDDD_SESSION", sessionId)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").value(result.principal().principalId().value()));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/public/ping")
        public String ping() {
            return "pong";
        }

        @GetMapping("/api/probe/me")
        public String me() {
            return org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getName();
        }
    }
}
