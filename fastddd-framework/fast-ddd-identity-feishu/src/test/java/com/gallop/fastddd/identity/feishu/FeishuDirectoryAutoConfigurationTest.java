package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-b：飞书目录源自动配置装配——条件独立于 login.mode（目录单开）、
 * 宿主自备 DirectorySource 时退让、凭据缺失 fail-closed 且报错可诊断、
 * 与登录自动配置（feishu-web）同开不冲突。
 * 与 W3-a starter 侧 {@code IdentityDirectoryAutoConfiguration} 的真实顺序衔接
 * 在消费者 identity-directory 场景验证（feishu 模块不依赖 starter）。
 */
class FeishuDirectoryAutoConfigurationTest {

    private static final String[] DIRECTORY_PROPS = {
            "fastddd.identity.enabled=true",
            "fastddd.identity.directory.enabled=true",
            "fastddd.identity.feishu.app-id=cli_test_app",
            "fastddd.identity.feishu.app-secret=secret-test",
            "fastddd.identity.feishu.expected-tenant-key=tenant-1"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeishuDirectoryAutoConfiguration.class))
            .withPropertyValues(DIRECTORY_PROPS);

    /** 目录单开（login.mode=none）：真实 DirectorySource 装配，登录链 Bean 不出现。 */
    @Test
    void directoryAloneAssemblesSourceWithoutLoginChain() {
        runner.withPropertyValues("fastddd.identity.login.mode=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DirectorySource.class);
                    assertThat(context.getBean(DirectorySource.class))
                            .isInstanceOf(FeishuDirectorySource.class);
                    // 目录自动配置不拉起登录链（redirect-uri 等登录键未配置也不要求）
                    assertThat(context).doesNotHaveBean(ExternalLoginProvider.class);
                    assertThat(context).doesNotHaveBean(FeishuExternalLoginProvider.class);
                });
    }

    /** 宿主自备 DirectorySource（其他目录源）→ feishu 适配器退让。 */
    @Test
    void hostOwnedDirectorySourceBacksOff() {
        runner.withUserConfiguration(HostSourceApp.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DirectorySource.class);
                    assertThat(context.getBean(DirectorySource.class))
                            .isNotInstanceOf(FeishuDirectorySource.class);
                });
    }

    /** 凭据缺失：Bean 构造期 fail-closed 启动失败，逐项列出缺失键并给出退让指引。 */
    @Test
    void missingCredentialsFailClosedListingKeys() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeishuDirectoryAutoConfiguration.class))
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.feishu.app-id=cli_test_app")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .hasMessageContaining("fastddd.identity.feishu.app-secret")
                            .hasMessageContaining("fastddd.identity.feishu.expected-tenant-key")
                            .hasMessageContaining("宿主使用其他目录源");
                });
    }

    /** 登录 + 目录同开（feishu-web）：登录适配器与目录源并存，互不干扰。 */
    @Test
    void loginModeFeishuWebCoexistsWithDirectory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        FeishuIdentityAutoConfiguration.class, FeishuDirectoryAutoConfiguration.class))
                .withPropertyValues(DIRECTORY_PROPS)
                .withPropertyValues(
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.identity.feishu.redirect-uri=https://app.example.com/api/identity/login/callback")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DirectorySource.class);
                    assertThat(context).hasSingleBean(ExternalLoginProvider.class);
                });
    }

    /** directory.enabled 缺省（未开目录）：不装配任何源。 */
    @Test
    void directoryDisabledYieldsNoSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FeishuDirectoryAutoConfiguration.class))
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.feishu.app-id=cli_test_app",
                        "fastddd.identity.feishu.app-secret=secret-test",
                        "fastddd.identity.feishu.expected-tenant-key=tenant-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DirectorySource.class);
                });
    }

    /** identity.enabled=false：模块整体静默（与登录自动配置同口径）。 */
    @Test
    void identityDisabledYieldsNothing() {
        runner.withPropertyValues("fastddd.identity.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DirectorySource.class);
                });
    }

    /** 宿主自备目录源（空实现，仅供退让断言）。 */
    @Configuration(proxyBeanMethods = false)
    static class HostSourceApp {

        @Bean
        DirectorySource hostDirectorySource() {
            return new DirectorySource() {
                @Override
                public DirectoryScope scope() {
                    return new DirectoryScope("t", "a");
                }

                @Override
                public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
                    return DirectoryPage.last(List.of());
                }

                @Override
                public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
                    return DirectoryPage.last(List.of());
                }
            };
        }
    }
}
