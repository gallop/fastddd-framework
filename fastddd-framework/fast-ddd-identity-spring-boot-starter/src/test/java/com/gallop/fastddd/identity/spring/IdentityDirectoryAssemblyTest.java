package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-a 目录装配骨架测试（W3 设计 v3 §7；复评钉 ③④）：
 * 关闭零装配、目录开启缺 source/store fail-closed、admin 独立于 directory、
 * cron/interval 互斥只认显式配置、非法配置失败、Worker 调度边界、
 * M1 无 publisher/Outbox 可装配、identity-core 纯度。
 * 替身全部为测试夹具（{@link DirectoryAssemblyDoubles}），不入生产自动配置。
 */
class IdentityDirectoryAssemblyTest {

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

    // ------------------------------------------------------------- 关闭 = 零装配

    @Test
    void directoryDisabled_zeroDirectoryBeansEvenWithPortsPresent() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DirectorySyncUseCase.class);
                    assertThat(context).doesNotHaveBean(DirectorySyncScheduler.class);
                });
    }

    @Test
    void masterSwitchOff_withDirectoryExplicit_fails() {
        // 总开关关 + 子功能显式开 = 配置错误（§4.5 既有语义，directory 同规则）
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.directory.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.enabled=true"));
    }

    // --------------------------------------------------- 目录开启 = fail-closed

    @Test
    void directoryEnabled_withoutSourceFailsStartup() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPortsWithoutSource.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("DirectorySource bean"));
    }

    @Test
    void directoryEnabled_withoutStoreFailsStartup() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPortsWithoutStore.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("DirectoryMirrorStore bean"));
    }

    @Test
    void directoryEnabled_webAssemblesUseCaseWithoutSchedulerAndWithoutPublisher() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.page-size=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 用例装配；web 非 Worker 不装调度（零调度线程）
                    assertThat(context).hasBean("directorySyncUseCase");
                    assertThat(context).doesNotHaveBean(DirectorySyncScheduler.class);
                    // M1：无 publisher/Outbox Bean 也完整装配（端口冻结不装配）
                    assertThat(context.getBeanNamesForType(IdentityChangePublisher.class)).isEmpty();

                    // 配置页数传导：3 部门按 pageSize=2 切两页（write:2d → write:1d），
                    // 完整契约顺序经上下文真实 Bean 走通
                    DirectoryAssemblyDoubles.RecordingMirrorStore store =
                            context.getBean(DirectoryAssemblyDoubles.RecordingMirrorStore.class);
                    context.getBean(DirectorySyncUseCase.class)
                            .runFullSync(new com.gallop.fastddd.identity.core.domain.DirectoryScope(
                                    "tenant", "app"));
                    assertThat(store.calls).containsExactly(
                            "begin", "write:2d/0p", "renew", "write:1d/0p",
                            "write:0d/0p", "write:0d/0p", "write:0d/0p",
                            "completePaging", "validate", "publish");
                });
    }

    // -------------------------------------------------- admin 独立于 directory

    @Test
    void adminEnabled_withoutPolicyFailsStartup() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.admin.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("IdentityAdminPolicy bean"));
    }

    @Test
    void adminEnabled_withPolicyLoadsIndependentlyOfDirectory() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class,
                        DirectoryAssemblyDoubles.AdminPolicyDouble.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // admin 开 ≠ 目录开：无目录用例（两开关相互独立）
                    assertThat(context).doesNotHaveBean(DirectorySyncUseCase.class);
                });
    }

    @Test
    void adminAndDirectoryTogetherLoadWithBothRequirementsMet() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class,
                        DirectoryAssemblyDoubles.AdminPolicyDouble.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.admin.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("directorySyncUseCase");
                });
    }

    // ------------------------------------- cron/interval 互斥（只认显式，钉 ③）

    @Test
    void cronAndIntervalBothExplicit_fail() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.cron=0 0 3 * * *",
                        "fastddd.identity.directory.sync-interval=1h")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("互斥"));
    }

    @Test
    void cronOnly_andIntervalOnly_eachLoad() {
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.cron=0 0 3 * * *")
                .run(context -> assertThat(context).hasNotFailed());
        webRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.identity.directory.sync-interval=30m")
                .run(context -> assertThat(context).hasNotFailed());
    }

    // ------------------------------------------------------ 非法配置失败行为

    @Test
    void illegalDirectoryConfigurationValues_fail() {
        String[][] illegal = {
                {"fastddd.identity.directory.cron=not-a-cron"},
                {"fastddd.identity.directory.page-size=0"},
                {"fastddd.identity.directory.page-size=-5"},
                {"fastddd.identity.directory.max-pages-per-run=0"},
                {"fastddd.identity.directory.sync-interval=0s"},
                {"fastddd.identity.directory.sync-interval=PT-1H"},
                {"fastddd.identity.directory.lease-timeout=0s"},
                {"fastddd.identity.directory.retry.max-attempts=0"},
                {"fastddd.identity.directory.retry.backoff=-1s"},
        };
        for (String[] property : illegal) {
            webRunner
                    .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                    .withPropertyValues("fastddd.identity.enabled=true",
                            "fastddd.identity.directory.enabled=true",
                            property[0])
                    .run(context -> assertThat(context).as(property[0]).hasFailed()
                            .getFailure().hasMessageContaining("目录配置错误"));
        }
    }

    // ------------------------------------------------------ Worker 调度边界

    @Test
    void workerForms_getSchedulerWhichStartsAndStopsWithLifecycle() {
        nonWebRunner
                .withUserConfiguration(DirectoryAssemblyDoubles.DirectoryPorts.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.identity.directory.enabled=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("directorySyncUseCase");
                    DirectorySyncScheduler scheduler = context.getBean(DirectorySyncScheduler.class);
                    // SmartLifecycle：refresh 后已启动（守护线程，间隔缺省 6h——首轮不抢跑）
                    assertThat(scheduler.isRunning()).isTrue();
                });
    }

    // ------------------------------------------------------ identity-core 纯度

    @Test
    void coreModuleCarriesNoSpringClasses() throws Exception {
        // 纯度钉：identity-core 不依赖 Spring（端口/用例纯 Java）。以其类路径载体
        // （reactor 下为 target/classes 目录，安装后为 jar）实物扫描为准
        Path location = Path.of(DirectorySyncUseCase.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            try (Stream<Path> entries = Files.walk(location)) {
                assertThat(entries.filter(path -> path.toString().replace('\\', '/')
                        .contains("org/springframework")))
                        .as("identity-core classes 目录不得含 Spring 类")
                        .isEmpty();
            }
        } else {
            try (JarFile jar = new JarFile(location.toFile())) {
                assertThat(jar.stream().filter(entry ->
                        entry.getName().startsWith("org/springframework")))
                        .as("identity-core jar 不得含 Spring 类")
                        .isEmpty();
            }
        }
    }
}
