package com.gallop.fastddd.identity.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W1-b 属性级 fail-fast（§4.5 配置选择顺序）：B 零副作用、总开关/子功能组合冲突、
 * C/Worker 双开关显式关闭、Worker/未知 mode/host-owned 语义、死数据源属性拒绝（复评 RV-01/RV-02）。
 */
class IdentityModeGuardTest {

    /** B/目录形态沿用宿主默认 JWT：测试用签名密钥（占位文案校验之外，仅测试源码）。 */
    private static final String JWT_TEST_SECRET =
            "identity-starter-it-jwt-signing-key-not-for-any-production-use-0000000000000";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    com.gallop.fastddd.crud.FastDddCrudAutoConfiguration.class,
                    com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class))
            .withPropertyValues("fastddd.security.jwt.secret=" + JWT_TEST_SECRET);

    @Test
    void bModeDefaultsAreZeroSideEffect() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // identity 功能件一概不装配；guard 本身仅为校验对象
            assertThat(context).doesNotHaveBean("identityActorResolver");
            assertThat(context).doesNotHaveBean("springIdentityTransactionRunner");
            assertThat(context).doesNotHaveBean("identityCurrentUserProvider");
            assertThat(context).doesNotHaveBean(IdentityModeValidator.class);
            // 旧装配不受影响：B 形态（无 identity 参赛）下 @CoM 竞争结果与引入 identity
            // 之前完全一致——crud 默认 provider 先注册，security 桥接退让
            assertThat(context).hasBean("defaultCurrentUserProvider");
            assertThat(context).doesNotHaveBean("securityCurrentUserProvider");
        });
    }

    @Test
    void masterOffWithExplicitSubFeatureFails() {
        runner.withPropertyValues(
                        "fastddd.identity.worker=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.worker=true 显式开启"));

        runner.withPropertyValues("fastddd.identity.directory.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.directory.enabled=true"));

        runner.withPropertyValues("fastddd.identity.login.mode=feishu-web")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.login.mode"));
    }

    @Test
    void cModeRequiresExplicitJwtOff() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.security.jwt.enabled=false"));

        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.security.jwt.enabled=false"));
    }

    @Test
    void cModeRequiresExplicitLocalAuthOff() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.security.local-auth.enabled=false"));
    }

    @Test
    void workerAndLoginAreMutuallyExclusive() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("Worker 冲突"));
    }

    @Test
    void unknownLoginModeFails() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=oidc")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("未知"));
    }

    @Test
    void hostOwnedOutsideCModeFails() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.security.host-owned=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("host-owned"));
    }

    @Test
    void workerRequiresExplicitJwtOff() {
        // §4.3 条 5：Worker 与 C 同语义——漏配 jwt=false 直接放行属复评 RV-02 缺陷
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.security.jwt.enabled=false"));
    }

    @Test
    void workerRequiresExplicitLocalAuthOff() {
        runner.withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.security.jwt.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.security.local-auth.enabled=false"));
    }

    @Test
    void deadPgDataSourcePropertyFailsFast() {
        // 复评 RV-01：pg.data-source-bean 无生产代码读取，显式设置必须失败并指回正确属性
        runner.withPropertyValues("fastddd.identity.pg.data-source-bean=businessDs")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("fastddd.identity.transaction.data-source-bean"));
    }

    @Test
    void singleTransactionDataSourcePropertyPassesGuardInBMode() {
        runner.withPropertyValues("fastddd.identity.transaction.data-source-bean=businessDs")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void webAppWithWorkerFailsAtFinalCheck() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IdentityAutoConfiguration.class))
                .withUserConfiguration(WorkerDataSourceConfig.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("web 运行形态冲突"));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class WorkerDataSourceConfig {

        @org.springframework.context.annotation.Bean
        javax.sql.DataSource dataSource() {
            return PgTestSupport.dataSource();
        }
    }
}
