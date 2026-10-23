package com.gallop.fastddd.identity.pg;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W21c（复评 F1）：装配期拒绝"适配器数据源为预置 autoCommit=false 的 Hikari 池"。
 *
 * <p>该配置下门禁第三判据（绑定连接 autoCommit=false）无法区分"因参与事务为 false"
 * 与"因池预置为 false"——异源事务 + 目标源预读取形态会经同步绑定非事务连接放行写入，
 * 写返回成功、数据被池在归还时回滚（复评 S2a 实测静默丢失，含 revokeAllSessions）。
 * 门禁层不存在不误伤的公开 API 判别子（isSynchronizedWithTransaction 双路径均置 true、
 * doBegin 的 mustRestoreAutoCommit 在 DSTM 私有对象上），故处置上移到装配期：
 * PG 自动配置解析适配器数据源后即拒绝该配置并给出可执行的修正指引。</p>
 *
 * <p>修复前（无装配期检查）该配置正常启动——两个负向用例修复前失败即证据；
 * 默认 autoCommit=true 的池必须不受影响（防误伤）。</p>
 */
class FastDddIdentityPgAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddIdentityPgAutoConfiguration.class))
            .withPropertyValues("fastddd.identity.enabled=true");

    @Test
    void presetAutoCommitFalseHikariPoolFailsStartup() {
        runner.withBean("dataSource", DataSource.class, () -> Support.hikariPool(false, "w21c-f1-negative"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    // 契约文本在根因（BeanCreationException 外壳之下）
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .getRootCause()
                            .hasMessageContaining("autoCommit=false")
                            .hasMessageContaining("Hikari")
                            .hasMessageContaining("静默丢失")
                            .hasMessageContaining("fastddd.identity.transaction.data-source-bean");
                });
    }

    @Test
    void delegatingWrapperAroundPresetAutoCommitFalseHikariPoolAlsoFailsStartup() {
        HikariDataSource pool = Support.hikariPool(false, "w21c-f1-wrapped");
        try {
            runner.withBean("dataSource", DataSource.class, () -> new DelegatingDataSource(pool))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .hasRootCauseInstanceOf(IllegalStateException.class)
                                .getRootCause()
                                .hasMessageContaining("autoCommit=false")
                                .hasMessageContaining("Hikari");
                    });
        } finally {
            pool.close();
        }
    }

    @Test
    void defaultAutoCommitHikariPoolStartsWithWriteAdapters() {
        runner.withBean("dataSource", DataSource.class, () -> Support.hikariPool(true, "w21c-f1-positive"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // 缺省 retention=3600s 经装配路径构建 loginStateStore（W22-R2-N4 正向锚定）
                    assertThat(context).hasBean("loginStateStore");
                    assertThat(context).hasBean("identityBindingStore");
                    assertThat(context).hasBean("localSessionPort");
                    assertThat(context).hasBean("identityLifecyclePort");
                    assertThat(context).hasBean("identityAuditWriter");
                });
    }

    @Test
    void negativeLoginStateRetentionSecondsFailsStartup() {
        // W22-R2-N4：负值 retention 经 @Value 装配路径触发 PgLoginStateStore 构造器守卫
        runner.withBean("dataSource", DataSource.class, () -> Support.hikariPool(true, "w22r2-n4-negative"))
                .withPropertyValues("fastddd.identity.pg.login-state-retention-seconds=-1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .getRootCause()
                            .hasMessageContaining("login_state retention")
                            .hasMessageContaining("非负");
                });
    }
}
