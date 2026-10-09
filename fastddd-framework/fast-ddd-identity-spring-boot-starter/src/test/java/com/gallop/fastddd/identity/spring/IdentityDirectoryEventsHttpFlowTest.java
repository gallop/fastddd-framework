package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest;
import com.gallop.fastddd.identity.core.domain.WebhookReceipt;
import com.gallop.fastddd.identity.core.error.DirectoryEventRejectedException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import com.gallop.fastddd.identity.spring.directory.DirectoryEventWorkerScheduler;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.IdentityAdminEndpoints;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsController;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5-d 事件端点 HTTP 级测试（真实 PG + 真实默认安全链 + MockMvc，镜像
 * {@code IdentityAdminHttpFlowTest} 形态）：固定端点匿名可达（permitAll）且
 * CSRF 豁免仅覆盖该常量路径（无 CSRF token 的 POST 直达控制器）；鉴别拒收
 * 经全链映射 401；管理重放路径不在豁免面（匿名 + CSRF 放行仍 401——
 * anyRequest().authenticated() 不受增量豁免影响）；worker 用例/调度器装配
 * 且生命周期启动。六步序契约本身在 feishu 模块覆盖，本类冻结安全面接线。
 */
class IdentityDirectoryEventsHttpFlowTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityDirectoryAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityLoginAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
                    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class))
            .withUserConfiguration(EventsFlowApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.identity.directory.increment.enabled=true",
                    "fastddd.identity.login.session.cookie-secure=false",
                    "fastddd.identity.login.allowed-post-login-redirects=/,/home",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    /** 通道桩以具体类型注册（context 内可取回定向 receipt/failure）。 */
    static final class FlowChannel implements DirectoryEventChannel {
        volatile WebhookReceipt receipt = new WebhookReceipt("ck-flow", null, false);
        volatile RuntimeException failure;

        @Override
        public WebhookReceipt receive(DirectoryWebhookRequest request) {
            if (failure != null) {
                throw failure;
            }
            return receipt;
        }

        @Override
        public Optional<com.gallop.fastddd.identity.core.domain.RecheckTarget> resolveObject(
                String eventType, String payloadJson) {
            return Optional.empty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EventsFlowApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProvider();
        }

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp-stub.example/authorize?state=" + state
                    + "&code_challenge=" + challenge + "&code_challenge_method=S256";
        }

        @Bean
        FlowChannel directoryEventChannel() {
            return new FlowChannel();
        }

        @Bean
        DirectoryRecheckQuery directoryRecheckQuery() {
            return (objectType, externalId) -> Optional.empty();
        }
    }

    /** 匿名（无 CSRF token、无会话）POST 直达控制器并回显 challenge——permitAll + CSRF 豁免双证。 */
    @Test
    void eventsPostWithoutCsrfReachesChannelAnonymously() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mvc.perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.challenge").value("ck-flow"));
        });
    }

    /** 鉴别拒收经真实默认链映射 401（结构化错误体，原因码白名单外显）。 */
    @Test
    void rejectedEventGives401ThroughRealChain() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            FlowChannel channel = context.getBean(FlowChannel.class);
            channel.failure = new DirectoryEventRejectedException("signature-mismatch", "签名不符（测试桩）");
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mvc.perform(post(IdentityDirectoryEventsEndpoints.EVENTS_PATH).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("identity_event_rejected"))
                    .andExpect(jsonPath("$.reason").value("signature-mismatch"));
        });
    }

    /** 豁免面 = 固定常量事件路径本身：管理重放路径匿名（带 CSRF）仍 401。 */
    @Test
    void replayPathIsNotPartOfTheExemption() throws Exception {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            mvc.perform(post(IdentityAdminEndpoints.EVENTS_REPLAY_PATH, "ev-1")
                            .with(org.springframework.security.test.web.servlet.request
                                    .SecurityMockMvcRequestPostProcessors.csrf()))
                    .andExpect(status().isUnauthorized());
        });
    }

    /** 消费面装配终检：web 进程装用例+端点、不装调度（收件箱由独立 worker 进程消费）。 */
    @Test
    void workerUseCaseAndEndpointBeansAssembleWithoutSchedulerInWebProcess() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("directoryEventWorkerUseCase");
            assertThat(context).hasSingleBean(IdentityDirectoryEventsController.class);
            // Worker 调度器只装在 worker 进程（mode=none + fastddd.identity.worker=true）——
            // 本上下文是 web 登录进程：零调度线程
            assertThat(context).doesNotHaveBean(DirectoryEventWorkerScheduler.class);
        });
    }
}
