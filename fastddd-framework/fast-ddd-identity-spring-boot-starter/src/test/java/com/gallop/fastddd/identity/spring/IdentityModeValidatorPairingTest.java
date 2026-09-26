package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.IssuedSession;
import com.gallop.fastddd.identity.core.domain.LoginStateEntry;
import com.gallop.fastddd.identity.core.domain.SessionId;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W2-2 修复轮 W22-RV-08：{@code IdentityModeValidator} 新增配对校验分支的专项测试
 * （复评指出该分支此前零测试覆盖——重构误删时无报警，静默 404 面回归）。
 *
 * <p>正例：存在 {@code ExternalAuthorizePort} 而缺 {@code LoginStateStore} →
 * 启动失败并报告配对缺口；反例：两者齐备 → 正常启动。另覆盖 W22-RV-06 的
 * 反向缺口 WARN（provider 在、authorize port 缺——W2-1 既有 C 场景合法形态，
 * 不失败但留诊断线索）。</p>
 *
 * <p>装配面刻意不含 reference-pg 自动配置（否则 LoginStateStore 必然存在），
 * 由用户配置提供最小桩（会话端口为过滤链必需；其余端口按被测分支取舍）。</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class IdentityModeValidatorPairingTest {

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class))
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false");
    }

    /** 正例（W22-RV-08）：authorize port 在而 state 存储缺 → 启动失败报告配对缺口。 */
    @Test
    void authorizePortWithoutLoginStateStoreFailsStartup() {
        runner().withUserConfiguration(AuthorizePortOnlyApp.class)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure()
                        .hasMessageContaining("ExternalAuthorizePort")
                        .hasMessageContaining("LoginStateStore"));
    }

    /** 反例（W22-RV-08）：authorize port 与 state 存储齐备 → 正常启动（无误报）。 */
    @Test
    void authorizePortWithLoginStateStoreStarts() {
        runner().withUserConfiguration(AuthorizePortAndStoreApp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalAuthorizePort.class);
                    assertThat(context).hasSingleBean(LoginStateStore.class);
                });
    }

    /**
     * 反向缺口（W22-RV-06）：provider 在、authorize port 缺（W2-1 既有 C 形态——
     * 宿主自有登录入口/仅会话复验）→ 不失败，但打 WARN 留"登录端点不会注册"诊断线索。
     */
    @Test
    void providerWithoutAuthorizePortStartsWithWarn(CapturedOutput output) {
        runner().withUserConfiguration(StoreOnlyApp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ExternalAuthorizePort.class);
                    assertThat(output).contains("ExternalAuthorizePort");
                    assertThat(output).contains("不会注册");
                });
    }

    // ==== 最小装配桩（不含 reference-pg，LoginStateStore 由被测分支决定有无）====

    @Configuration(proxyBeanMethods = false)
    static class BaseApp {

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

        /** 过滤链必需（identitySessionAuthenticationFilter 构造注入）；与被测分支无关。 */
        @Bean
        LocalSessionPort localSessionPort() {
            return new LocalSessionPort() {
                @Override
                public IssuedSession issueSession(VerifiedPrincipal principal, Duration ttl) {
                    throw new UnsupportedOperationException("测试桩");
                }

                @Override
                public Optional<VerifiedPrincipal> validateSession(SessionId sessionId) {
                    return Optional.empty();
                }

                @Override
                public void revokeSession(SessionId sessionId) {
                }

                @Override
                public int revokeAllSessions(String principalId) {
                    return 0;
                }
            };
        }
    }

    /** authorize port 在、state 存储缺（被测违例形态）。 */
    @Configuration(proxyBeanMethods = false)
    static class AuthorizePortOnlyApp extends BaseApp {

        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp.example/authorize?state=" + state;
        }
    }

    /** authorize port 与 state 存储齐备。 */
    @Configuration(proxyBeanMethods = false)
    static class AuthorizePortAndStoreApp extends BaseApp {

        @Bean
        ExternalAuthorizePort externalAuthorizePort() {
            return (state, challenge) -> "https://idp.example/authorize?state=" + state;
        }

        @Bean
        LoginStateStore loginStateStore() {
            return new LoginStateStore() {
                @Override
                public void save(LoginStateEntry entry) {
                }

                @Override
                public Optional<LoginStateEntry> consume(String state) {
                    return Optional.empty();
                }
            };
        }
    }

    /** state 存储在、authorize port 缺（W2-1 既有 C 形态 + RV-06 WARN）。 */
    @Configuration(proxyBeanMethods = false)
    static class StoreOnlyApp extends BaseApp {

        @Bean
        LoginStateStore loginStateStore() {
            return new LoginStateStore() {
                @Override
                public void save(LoginStateEntry entry) {
                }

                @Override
                public Optional<LoginStateEntry> consume(String state) {
                    return Optional.empty();
                }
            };
        }
    }
}
