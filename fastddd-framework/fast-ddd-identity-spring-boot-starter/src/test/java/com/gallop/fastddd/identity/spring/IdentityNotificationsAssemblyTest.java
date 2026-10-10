package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher;
import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W5-e 通知链装配矩阵七形态测试（设计 §9.2 :583-591，M-20）：逐格翻转——
 * 格 1 通知关（缺省）双链开双 publisher 在场零装配要求（闲置可发布，M1 基线）；
 * 格 2 通知开 ∧ 全量链开缺 {@code IdentityChangePublisher} 拒启（fail-fast）；
 * 格 3 通知开 ∧ 增量链开缺 {@code DirectoryIncrementPublisher} 拒启（两端口
 * 分别校验）；格 6 增量开 ∧ 通知关零通知装配要求（既有
 * {@link IdentityIncrementEventsAssemblyTest} 同格重钉 M-20 格号锚点）；
 * 格 7 增量关 ∧ 通知开（T8-only）：缺增量适配不阻断、无 Inbox/worker 装配。
 * 格 4 资源不一致、格 5 同成同败 = 宿主 Outbox 适配契约测试载体（host 层，
 * 文档腿登记断言清单 + 内嵌示范断言——需真实 Outbox/资源比对，不属装配层）。
 * 替身全部为测试夹具，不入生产自动配置。
 */
class IdentityNotificationsAssemblyTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityDirectoryAutoConfiguration.class));

    // ------------------------------------------------ 格 1：通知关（缺省）= 可发布

    /** 双链全开 ∧ 通知关 ∧ 双 publisher 在场：启动成功，publisher 闲置无装配要求。 */
    @Test
    void matrix20_1_notificationsOffBothChainsRunWithIdlePublishers() {
        runner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPortsOnly.class,
                        IdentityIncrementEventsAssemblyTest.IncrementPorts.class, PublishersBoth.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> {
                    // notifications.enabled 缺省 false：两 publisher 零装配义务（在场仅闲置）
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DirectorySyncUseCase.class);
                    assertThat(context).hasSingleBean(DirectoryEventWorkerUseCase.class);
                });
    }

    // ------------------------------ 格 2：通知开 ∧ 全量链开 ∧ 缺代激活适配 = 拒启

    @Test
    void matrix20_2_notificationsOnFullChainWithoutChangePublisherFailsStartup() {
        runner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.notifications.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("IdentityChangePublisher bean"));
    }

    // ------------------------------ 格 3：通知开 ∧ 增量链开 ∧ 缺增量适配 = 拒启

    @Test
    void matrix20_3_notificationsOnIncrementChainWithoutIncrementPublisherFailsStartup() {
        runner
                .withUserConfiguration(IdentityIncrementEventsAssemblyTest.IncrementPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true",
                        "fastddd.identity.directory.notifications.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("DirectoryIncrementPublisher bean"));
    }

    // -------------------------- 格 6：增量开 ∧ 通知关 = 事实链完整、零通知装配

    @Test
    void matrix20_6_incrementOnNotificationsOffRequiresNoPublishers() {
        runner
                .withUserConfiguration(IdentityIncrementEventsAssemblyTest.IncrementPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.increment.enabled=true")
                .run(context -> {
                    // 双 publisher 全缺仍启动：通知关 = 增量事实链（change_log 恒写入）零装配义务
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DirectoryEventWorkerUseCase.class);
                });
    }

    // ------------------------ 格 7：增量关 ∧ 通知开（T8-only）缺增量适配不阻断

    @Test
    void matrix20_7_incrementOffNotificationsOnIsT8OnlyWithoutIncrementPorts() {
        runner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class, T8OnlyPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.notifications.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // M1 全量链照常 + T8 代激活通知照发（publisher 已接线进 sync 用例）
                    assertThat(context).hasSingleBean(DirectorySyncUseCase.class);
                    // 无 Inbox/worker/增量端口装配要求：DirectoryIncrementPublisher 缺席不阻断
                    assertThat(context).doesNotHaveBean(DirectoryEventWorkerUseCase.class);
                    assertThat(context).doesNotHaveBean(DirectoryIncrementPublisher.class);
                });
    }

    // ---- 替身（仅测试夹具，绝不进入生产自动配置） ----

    /** 双 publisher + 变更日志桩（格 1：在场闲置——方法体不可达，零发布零轮次）。 */
    @Configuration(proxyBeanMethods = false)
    static class PublishersBoth {

        @Bean
        IdentityChangePublisher identityChangePublisher() {
            return event -> {
                throw new UnsupportedOperationException("装配测试零发布");
            };
        }

        @Bean
        DirectoryIncrementPublisher directoryIncrementPublisher() {
            return event -> {
                throw new UnsupportedOperationException("装配测试零发布");
            };
        }

        @Bean
        DirectoryChangeLog directoryChangeLog() {
            return noopChangeLog();
        }
    }

    /**
     * T8-only 形态替身（格 7）：仅代激活 publisher + 变更日志——sync 用例构造
     * 依赖对（publisher 非 null 要求 changeLog 非 null），缺增量端口为形态本体。
     */
    @Configuration(proxyBeanMethods = false)
    static class T8OnlyPorts {

        @Bean
        IdentityChangePublisher identityChangePublisher() {
            return event -> {
                throw new UnsupportedOperationException("装配测试零发布");
            };
        }

        @Bean
        DirectoryChangeLog directoryChangeLog() {
            return noopChangeLog();
        }
    }

    /** 变更日志桩：双方法接口无 lambda 形态，匿名类共享替身。 */
    static DirectoryChangeLog noopChangeLog() {
        return new DirectoryChangeLog() {
            @Override
            public String append(ChangeLogEntry entry) {
                throw new UnsupportedOperationException("装配测试零发布");
            }

            @Override
            public boolean hasGenerationActivation(DirectoryScope scope, String generationId) {
                throw new UnsupportedOperationException("装配测试零发布");
            }
        };
    }
}
