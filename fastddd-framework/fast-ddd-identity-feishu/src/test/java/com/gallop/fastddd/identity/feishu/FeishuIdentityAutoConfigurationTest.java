package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W2-2 修复轮 W22-RV-07：宿主自备 {@code ExternalLoginProvider} 而
 * {@code ExternalAuthorizePort} 缺失时，feishu 模块的 authorize port Bean 此前以
 * 具体类型注入已退让的 provider → {@code UnsatisfiedDependencyException} 崩溃且
 * 报错不指向根因。改为 {@code ObjectProvider} 显式判定后：fail-closed 保留，
 * 但报错可诊断（指明"宿主自定义 provider 须一并提供 authorize port 或移除本模块"）。
 */
class FeishuIdentityAutoConfigurationTest {

    private static final String[] FEISHU_PROPS = {
            "fastddd.identity.enabled=true",
            "fastddd.identity.login.mode=feishu-web",
            "fastddd.identity.feishu.app-id=cli_test_app",
            "fastddd.identity.feishu.app-secret=secret-test",
            "fastddd.identity.feishu.redirect-uri=https://app.example.com/api/identity/login/callback",
            "fastddd.identity.feishu.expected-tenant-key=tenant-1"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuIdentityAutoConfiguration.class))
            .withPropertyValues(FEISHU_PROPS);

    @Test
    void defaultAssemblyProvidesBothPortsFromSingleProvider() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ExternalLoginProvider.class);
            assertThat(context).hasSingleBean(ExternalAuthorizePort.class);
            // 双端口为同一实例（authorizeUrl 与 verify 同源配置）
            assertThat(context.getBean(ExternalLoginProvider.class))
                    .isSameAs(context.getBean(ExternalAuthorizePort.class));
        });
    }

    /** W22-RV-07 主张：宿主自备 provider 而缺 authorize port → 可诊断失败（不再是注入崩溃）。 */
    @Test
    void hostOwnedProviderWithoutAuthorizePortFailsWithActionableMessage() {
        runner.withUserConfiguration(HostProviderApp.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    // 报错指向根因与修正指引，而非 UnsatisfiedDependencyException 的 Bean 注入细节
                    assertThat(context).getFailure()
                            .hasMessageContaining("宿主自定义 ExternalLoginProvider")
                            .hasMessageContaining("ExternalAuthorizePort");
                    // 宿主自己的 provider 不受影响（失败仅来自 feishuAuthorizePort 的显式判定）
                    assertThat(context).getFailure()
                            .hasMessageNotContaining("UnsatisfiedDependencyException");
                });
    }

    /** 宿主自备 provider 且一并提供 authorize port → feishu 模块双 Bean 全退让。 */
    @Test
    void hostOwnedProviderWithAuthorizePortBacksOffCompletely() {
        runner.withUserConfiguration(HostProviderWithAuthorizePortApp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ExternalLoginProvider.class);
                    assertThat(context).hasSingleBean(ExternalAuthorizePort.class);
                    assertThat(context.getBean(ExternalLoginProvider.class))
                            .isNotInstanceOf(FeishuExternalLoginProvider.class);
                });
    }

    /** identity.enabled=false：模块在 classpath 也不产生任何 Bean（目录单开/Worker 形态）。 */
    @Test
    void disabledIdentityYieldsNoFeishuBeans() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeishuIdentityAutoConfiguration.class))
                .withPropertyValues(
                        "fastddd.identity.enabled=false",
                        "fastddd.identity.login.mode=feishu-web")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ExternalLoginProvider.class);
                    assertThat(context).doesNotHaveBean(ExternalAuthorizePort.class);
                });
    }

    /** 宿主自备提供者（feishu provider 因 @ConditionalOnMissingBean 退让）。 */
    @Configuration(proxyBeanMethods = false)
    static class HostProviderApp {

        @Bean
        ExternalLoginProvider hostExternalLoginProvider() {
            return command -> new VerifiedExternalLogin(
                    new ExternalIdentity("host-idp", "t", "a", "union_id", "ou_host"),
                    "host-login", "Host User");
        }
    }

    /** 宿主一并提供 authorize port（feishu 双 Bean 全退让的完整形态）。 */
    @Configuration(proxyBeanMethods = false)
    static class HostProviderWithAuthorizePortApp extends HostProviderApp {

        @Bean
        ExternalAuthorizePort hostExternalAuthorizePort() {
            return (state, challenge) -> "https://host-idp.example/authorize?state=" + state;
        }
    }
}
