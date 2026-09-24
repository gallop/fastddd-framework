package com.gallop.fastddd.identity.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W1-b Worker（E）与目录单开组合（§4.3 条 5/§4.5）：非 web worker 装配 Actor 桥与
 * 员工 provider、不装登录链；非 web 未声明 worker 失败；目录单开 web 沿用宿主安全、
 * 不装员工 provider。
 */
class IdentityWorkerDirectoryTest {

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration.class,
                    com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class,
                    com.gallop.fastddd.crud.FastDddCrudAutoConfiguration.class));

    @Test
    void workerAssemblesActorBridgeAndProviderWithoutLoginChain() {
        nonWebRunner
                .withUserConfiguration(WorkerApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Worker 装配：Actor 桥 + 员工 provider + 事务桥；不装登录用例/过滤链
                    assertThat(context).hasBean("identityActorResolver");
                    assertThat(context).hasBean("identityCurrentUserProvider");
                    assertThat(context).hasBean("springIdentityTransactionRunner");
                    assertThat(context).doesNotHaveBean(
                            com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase.class);
                    assertThat(context).doesNotHaveBean("identitySecurityFilterChain");
                    // jwt/local-auth 双关：旧链与本地认证族不旁路
                    assertThat(context).doesNotHaveBean(
                            org.springframework.security.web.SecurityFilterChain.class);
                    assertThat(context).doesNotHaveBean(
                            org.springframework.security.core.userdetails.UserDetailsService.class);
                });
    }

    @Test
    void nonWebWithoutWorkerDeclarationFails() {
        nonWebRunner
                .withUserConfiguration(WorkerApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.worker=true"));
    }

    @Test
    void directoryOnlyWebKeepsHostSecurityAndSkipsEmployeeProvider() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                        com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration.class,
                        // 宿主默认链用 MvcRequestMatcher：需要 MVC 自动配置提供 introspector
                        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                        com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class,
                        com.gallop.fastddd.crud.FastDddCrudAutoConfiguration.class))
                .withUserConfiguration(DirectoryApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        // 目录单开沿用宿主 JWT 默认链：提供测试签名密钥
                        "fastddd.security.jwt.secret="
                                + "identity-starter-it-jwt-signing-key-not-for-any-production-use-0000000000000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 目录单开 web：沿用宿主（A 形态默认链），不装身份登录链与员工 provider
                    assertThat(context).doesNotHaveBean("identitySecurityFilterChain");
                    assertThat(context).hasBean("jwtAuthenticationFilter");
                    assertThat(context).doesNotHaveBean("identityCurrentUserProvider");
                    // Actor 桥仍可用（目录同步作业以 SYSTEM_JOB 上下文执行，W3 消费）
                    assertThat(context).hasBean("identityActorResolver");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class WorkerApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DirectoryApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }
    }
}
