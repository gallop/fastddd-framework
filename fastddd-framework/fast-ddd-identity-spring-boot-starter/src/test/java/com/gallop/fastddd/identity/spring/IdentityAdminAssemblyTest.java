package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import com.gallop.fastddd.identity.pg.PgDirectoryMirrorStore;
import com.gallop.fastddd.identity.spring.directory.DirectoryDiffReporter;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncManualTrigger;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.IdentityAdminController;
import com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-c 管理端装配测试（W3 设计 v3 §9.2–9.4）：C 模式 + admin.enabled + 四端口
 * 齐备才注册 {@link IdentityAdminController}；admin 关闭或非 C 模式零注册；
 * admin.enabled=true 而缺审计写入 bean 由终检拒绝启动（§9.3 fail-closed，
 * 见 {@code IdentityDirectoryAssemblyTest#adminEnabled_withoutAuditWriterFailsStartup}）；
 * directory.enabled=true 装配手动触发与差异报表组件（真实 PG 参考存储）。
 */
class IdentityAdminAssemblyTest {

    /** C 模式全链（真实 PG：reference-pg 同时提供 IdentityAuditWriter 终端 writer）。 */
    private final WebApplicationContextRunner cModeRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    IdentityAdminAutoConfiguration.class))
            .withUserConfiguration(CModeAdminApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.identity.admin.enabled=true",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false");

    /** 非 C（login.mode 缺省 NONE）+ 纯替身端口（无 PG——writer 由 AuditWriterDouble 提供）。 */
    private final WebApplicationContextRunner nonCRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityAdminAutoConfiguration.class))
            .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class,
                    DirectoryAssemblyDoubles.AdminPolicyDouble.class,
                    DirectoryAssemblyDoubles.AuditWriterDouble.class)
            .withPropertyValues("fastddd.identity.enabled=true",
                    "fastddd.identity.admin.enabled=true");

    @Configuration(proxyBeanMethods = false)
    static class CModeAdminApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProvider();
        }

        /** 测试夹具显式放行（W21-01：生产宿主必须自带准入策略）。 */
        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        /** 允许一切管理操作的宿主策略桩（仅测试夹具；生产无默认放行）。 */
        @Bean
        com.gallop.fastddd.identity.core.port.IdentityAdminPolicy identityAdminPolicy() {
            return (operation, actor, targetPrincipalId) -> {
            };
        }
    }

    @Test
    void cModeWithAdminEnabledAndAllPorts_registersAdminController() {
        cModeRunner.run(context -> {
            assertThat(context).hasNotFailed();
            // 端点注册（C ∧ admin.enabled ∧ PrincipalLifecycleUseCase/策略/审计/Actor 齐备）
            assertThat(context).hasSingleBean(IdentityAdminController.class);
            assertThat(context).hasSingleBean(PrincipalLifecycleUseCase.class);
            // reference-pg 终端 writer 满足 §9.3 终检（真实审计写入面，非替身）
            assertThat(context).hasSingleBean(IdentityAuditWriter.class);
            // admin 开 ≠ 目录开：目录组件零装配（两开关相互独立）
            assertThat(context).doesNotHaveBean(DirectorySyncUseCase.class);
            assertThat(context).doesNotHaveBean(DirectorySyncManualTrigger.class);
        });
    }

    @Test
    void cModeWithoutAdminSwitch_doesNotRegisterAdminController() {
        cModeRunner
                .withPropertyValues("fastddd.identity.admin.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // admin 缺省关：端点零注册，其余 C 链完整
                    assertThat(context).doesNotHaveBean(IdentityAdminController.class);
                    assertThat(context).hasSingleBean(PrincipalLifecycleUseCase.class);
                });
    }

    @Test
    void nonCModeWithAdminEnabled_controllerNotRegistered() {
        nonCRunner.run(context -> {
            assertThat(context).hasNotFailed();
            // 非 C：终检只核策略/审计 bean（在场），端点按 CMode 条件零注册
            assertThat(context).doesNotHaveBean(IdentityAdminController.class);
            assertThat(context).doesNotHaveBean(PrincipalLifecycleUseCase.class);
        });
    }

    @Test
    void directoryEnabledAssemblesManualTriggerAndDiffReporterOverRealPgStore() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        IdentityDirectoryAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(PagedSourceApp.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("directorySyncUseCase");
                    // W3-c 追加组件：手动触发（§9.4，与调度共用租约互斥）+ 差异报表（§6.6）
                    assertThat(context).hasSingleBean(DirectorySyncManualTrigger.class);
                    assertThat(context).hasSingleBean(DirectoryDiffReporter.class);
                    // 真实参考存储（非测试替身）+ web 形态零调度线程
                    assertThat(context.getBean(DirectorySyncManualTrigger.class)).isNotNull();
                    assertThat(context).hasBean("directoryMirrorStore");
                    assertThat(context.getBean("directoryMirrorStore"))
                            .isInstanceOf(PgDirectoryMirrorStore.class);
                    assertThat(context).doesNotHaveBean(
                            com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler.class);
                    // 目录开 ≠ admin 开：管理端点零注册
                    assertThat(context).doesNotHaveBean(IdentityAdminController.class);
                });
    }

    /** 宿主源替身（真实存储来自 PG 自动配置；源仍属宿主/W3-b 适配器）。 */
    @Configuration(proxyBeanMethods = false)
    static class PagedSourceApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        com.gallop.fastddd.identity.core.port.DirectorySource directorySource() {
            return new DirectoryAssemblyDoubles.PagedSource();
        }
    }
}
