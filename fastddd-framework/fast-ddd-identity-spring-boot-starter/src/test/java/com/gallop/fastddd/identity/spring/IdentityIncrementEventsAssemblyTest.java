package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryEventChannel;
import com.gallop.fastddd.identity.core.port.DirectoryEventInbox;
import com.gallop.fastddd.identity.core.port.DirectoryEventInboxAdmin;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.directory.DirectoryEventWorkerScheduler;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.IdentityAdminAutoConfiguration;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsAdminController;
import com.gallop.fastddd.identity.spring.web.IdentityDirectoryEventsController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5-d 增量事件链装配测试（镜像 {@link IdentityDirectoryAssemblyTest} 惯例）：
 * 关闭零装配、increment.enabled=true 六端口 fail-closed（缺收件箱/缺通道分别
 * 拒启）、web 形态装端点+用例+调度器而与 directory.enabled 相互独立（增量链
 * 不依赖全量同步端口）、非 web 形态装用例不装 HTTP 端点、replay 管理端点
 * 三开关（feishu-web ∧ admin.enabled ∧ increment.enabled）逐一缺一即不装配。
 * 替身全部为测试夹具，不入生产自动配置。
 */
class IdentityIncrementEventsAssemblyTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityDirectoryAutoConfiguration.class));

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityDirectoryAutoConfiguration.class));

    /** 管理端点条件专测 mini runner（只装 admin 自动配置——条件面不拖入完整模式栈）。 */
    private final WebApplicationContextRunner eventsAdminRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityAdminAutoConfiguration.class))
            .withUserConfiguration(EventsAdminPorts.class)
            .withPropertyValues("fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web");

    // ------------------------------------------------------------- 关闭 = 零装配

    @Test
    void incrementDisabled_zeroIncrementBeansEvenWithPortsPresent() {
        webRunner
                .withUserConfiguration(IncrementPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DirectoryEventWorkerUseCase.class);
                    assertThat(context).doesNotHaveBean(IdentityDirectoryEventsController.class);
                    assertThat(context).doesNotHaveBean(DirectoryEventWorkerScheduler.class);
                });
    }

    // --------------------------------------------- 开启 = 装配 + 独立于全量链

    @Test
    void incrementEnabled_webAssemblesUseCaseAndEndpointWithoutScheduler() {
        webRunner
                .withUserConfiguration(IncrementPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("directoryEventWorkerUseCase");
                    assertThat(context).hasSingleBean(IdentityDirectoryEventsController.class);
                    // web 非 Worker 进程不装调度（零调度线程——W3 sync scheduler 同款语义：
                    // 收件箱由独立 worker 进程消费，端点所在 web 进程只承担入箱）
                    assertThat(context).doesNotHaveBean(DirectoryEventWorkerScheduler.class);
                    // 增量开 ≠ 目录全量开：两链相互独立，无全量同步用例
                    assertThat(context).doesNotHaveBean(DirectorySyncUseCase.class);
                });
    }

    // --------------------------------------------------- 开启 = 六端口 fail-closed

    @Test
    void incrementEnabled_withoutInboxFailsStartup() {
        webRunner
                .withUserConfiguration(IncrementPortsWithoutInbox.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("DirectoryEventInbox bean"));
    }

    @Test
    void incrementEnabled_withoutChannelFailsStartup() {
        webRunner
                .withUserConfiguration(IncrementPortsWithoutChannel.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("DirectoryEventChannel bean"));
    }

    // ---------------------------------- Worker 进程 = 用例+调度器在、HTTP 端点不在

    @Test
    void incrementEnabled_nonWebWorkerAssemblesSchedulerWithoutHttpEndpoint() {
        nonWebRunner
                .withUserConfiguration(IncrementPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true",
                        // §4.3 条 5：非 web 进程启用 identity 须显式 worker=true（validator 终检）；
                        // Worker 进程守卫：mode=none（缺省）+ 显式关旧 JWT/本地认证
                        "fastddd.identity.worker=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false",
                        "fastddd.identity.directory.increment.worker.poll-interval=10m")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("directoryEventWorkerUseCase");
                    // 端点配置 @ConditionalOnWebApplication(SERVLET)：非 web 形态零 HTTP 面
                    assertThat(context).doesNotHaveBean(IdentityDirectoryEventsController.class);
                    // 调度器无 web 条件：消费面与传输面正交
                    assertThat(context).hasSingleBean(DirectoryEventWorkerScheduler.class);
                });
    }

    // -------------------------------------- replay 管理端点三开关（缺一即不装配）

    @Test
    void replayAdminController_assemblesWhenAllThreeSwitchesOn() {
        eventsAdminRunner
                .withPropertyValues("fastddd.identity.admin.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdentityDirectoryEventsAdminController.class);
                });
    }

    @Test
    void replayAdminController_absentWithoutIncrementSwitch() {
        eventsAdminRunner
                .withPropertyValues("fastddd.identity.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdentityDirectoryEventsAdminController.class);
                });
    }

    @Test
    void replayAdminController_absentWithoutAdminSwitch() {
        eventsAdminRunner
                .withPropertyValues("fastddd.identity.directory.increment.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(IdentityDirectoryEventsAdminController.class);
                });
    }

    // ---- 替身（仅测试夹具，绝不进入生产自动配置） ----

    /** 六端口全量替身 + 事务桩（装配级验证；方法体不可达——本测试零请求零轮次）。 */
    @Configuration(proxyBeanMethods = false)
    static class IncrementPorts {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return DirectoryAssemblyDoubles.txRunner();
        }

        @Bean
        DirectoryEventChannel directoryEventChannel() {
            return new NoopChannel();
        }

        @Bean
        DirectoryEventInbox directoryEventInbox() {
            return new NoopInbox();
        }

        @Bean
        DirectoryRecheckQuery directoryRecheckQuery() {
            return (objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactReader directoryFactReader() {
            return (scope, objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactMatcher directoryFactMatcher() {
            return noopMatcher();
        }

        /** 双重载接口无 lambda 形态——匿名类共享替身。 */
        static DirectoryFactMatcher noopMatcher() {
            return new DirectoryFactMatcher() {
                @Override
                public boolean matches(String storedFactJson,
                                       com.gallop.fastddd.identity.core.domain.DirectoryPerson observed) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean matches(String storedFactJson,
                                       com.gallop.fastddd.identity.core.domain.DirectoryDepartment observed) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Bean
        DirectoryRecheckCommitter directoryRecheckCommitter() {
            return (scope, evidence) -> null;
        }
    }

    /** 缺收件箱形态（其余五端口在——validator 终检必须拒启）。 */
    @Configuration(proxyBeanMethods = false)
    static class IncrementPortsWithoutInbox {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return DirectoryAssemblyDoubles.txRunner();
        }

        @Bean
        DirectoryEventChannel directoryEventChannel() {
            return new NoopChannel();
        }

        @Bean
        DirectoryRecheckQuery directoryRecheckQuery() {
            return (objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactReader directoryFactReader() {
            return (scope, objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactMatcher directoryFactMatcher() {
            return IncrementPorts.noopMatcher();
        }

        @Bean
        DirectoryRecheckCommitter directoryRecheckCommitter() {
            return (scope, evidence) -> null;
        }
    }

    /** 缺通道形态（其余五端口在——validator 终检必须拒启）。 */
    @Configuration(proxyBeanMethods = false)
    static class IncrementPortsWithoutChannel {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return DirectoryAssemblyDoubles.txRunner();
        }

        @Bean
        DirectoryEventInbox directoryEventInbox() {
            return new NoopInbox();
        }

        @Bean
        DirectoryRecheckQuery directoryRecheckQuery() {
            return (objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactReader directoryFactReader() {
            return (scope, objectType, externalId) -> java.util.Optional.empty();
        }

        @Bean
        DirectoryFactMatcher directoryFactMatcher() {
            return IncrementPorts.noopMatcher();
        }

        @Bean
        DirectoryRecheckCommitter directoryRecheckCommitter() {
            return (scope, evidence) -> null;
        }
    }

    /** replay 管理端点四端口替身（mini runner 专用）。 */
    @Configuration(proxyBeanMethods = false)
    static class EventsAdminPorts {

        @Bean
        DirectoryEventInboxAdmin directoryEventInboxAdmin() {
            return eventId -> false;
        }

        @Bean
        IdentityAdminPolicy identityAdminPolicy() {
            return (operation, actor, targetPrincipalId) -> {
            };
        }

        @Bean
        IdentityAuditWriter identityAuditWriter() {
            return record -> {
            };
        }

        @Bean
        ActorResolver actorResolver() {
            Actor actor = new Actor("tenant-1", PrincipalId.of("ou-admin-1"), PrincipalType.HUMAN);
            return new ActorResolver() {
                @Override
                public Actor currentActor() {
                    return actor;
                }

                @Override
                public java.util.Optional<Actor> tryCurrentActor() {
                    return java.util.Optional.of(actor);
                }
            };
        }
    }

    static final class NoopChannel implements DirectoryEventChannel {
        @Override
        public com.gallop.fastddd.identity.core.domain.WebhookReceipt receive(
                com.gallop.fastddd.identity.core.domain.DirectoryWebhookRequest request) {
            throw new UnsupportedOperationException("装配测试零请求");
        }

        @Override
        public java.util.Optional<com.gallop.fastddd.identity.core.domain.RecheckTarget> resolveObject(
                String eventType, String payloadJson) {
            throw new UnsupportedOperationException("装配测试零轮次");
        }
    }

    static final class NoopInbox implements DirectoryEventInbox {
        @Override
        public boolean persist(com.gallop.fastddd.identity.core.domain.EventEnvelope envelope) {
            throw new UnsupportedOperationException("装配测试零请求");
        }

        @Override
        public java.util.List<com.gallop.fastddd.identity.core.domain.EventEnvelope> claim(
                int limit, java.time.Duration lease) {
            throw new UnsupportedOperationException("装配测试零轮次");
        }

        @Override
        public int reclaimExpired() {
            throw new UnsupportedOperationException("装配测试零轮次");
        }

        @Override
        public void markProcessed(String eventId) {
            throw new UnsupportedOperationException("装配测试零轮次");
        }

        @Override
        public void markFailed(String eventId, String reason) {
            throw new UnsupportedOperationException("装配测试零轮次");
        }
    }
}
