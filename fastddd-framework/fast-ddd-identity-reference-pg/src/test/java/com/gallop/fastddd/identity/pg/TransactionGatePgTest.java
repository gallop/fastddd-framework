package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 门禁反例（真实 PG）：MANDATORY 门禁绑定适配器数据源——
 *
 * <ul>
 *   <li>事务开在另一 DataSource 上（同一 PG 集群、两个独立 DataSource 实例）：
 *       首次写入前拒绝、零残留（外层回滚不留下自动提交行）；</li>
 *   <li>仅有同步绑定的连接资源而无实际事务（白盒：绑定 ConnectionHolder、
 *       全局无实际事务——如同步激活后的共享读连接）：以"无实际事务"拒绝，
 *       不以"存在绑定资源"顶替事务判据；</li>
 *   <li><b>异源事务 + 目标源预读取（W21-03 补修，第三轮反例）</b>：事务开在 A、
 *       B 适配器先经公开读 {@code findPrincipal} 自然绑定同步 ConnectionHolder
 *       （autoCommit=true 的非事务连接）——门禁必须核验绑定连接真实参与事务，
 *       在首次写入前拒绝，外层回滚后三表零残留；</li>
 *   <li>正确数据源事务异常全回滚（与 LoginIssuancePgTest 的 T2 回滚互补，
 *       这里直接经 TransactionTemplate 验证）。</li>
 * </ul>
 */
class TransactionGatePgTest {

    private final DataSource dataSource = Support.dataSource();
    private final DataSource otherDataSource = Support.dataSource();

    @BeforeEach
    void setUp() {
        Support.cleanIdentityTables(dataSource);
    }

    private static ExternalIdentity identity() {
        return new ExternalIdentity("feishu", "tenant-gate", "app-1", "union_id", "ou_gate_1");
    }

    @Test
    void wrongDataSourceTransactionIsRejectedBeforeFirstWriteWithZeroResidue() {
        // 适配器（B 数据源）上的写端口；事务开在 A 数据源——即使同库也必须检出
        PgIdentityBindingStore storeOnB =
                new PgIdentityBindingStore(new JdbcTemplate(otherDataSource));
        PgLocalSessionStore sessionsOnB =
                new PgLocalSessionStore(new JdbcTemplate(otherDataSource));

        var managerOnA = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        var templateOnA = new org.springframework.transaction.support.TransactionTemplate(managerOnA);

        templateOnA.executeWithoutResult(status -> {
            assertThatThrownBy(() -> storeOnB.findOrCreatePrincipalWithBinding(
                            identity(), new PrincipalCreationSpec("login", "display")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("适配器数据源")
                    .hasMessageContaining("data-source-bean");
            assertThatThrownBy(() -> sessionsOnB.revokeAllSessions("any-principal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("适配器数据源");
            // 外层正常提交：B 的连接从未写入（不是"写了又被回滚"）
        });

        // 两个连接面都零残留：拒绝发生在首次写入前
        assertThat(Support.count(dataSource, "principal")).isZero();
        assertThat(Support.count(dataSource, "external_binding")).isZero();
        assertThat(Support.count(otherDataSource, "principal")).isZero();
        assertThat(Support.count(otherDataSource, "external_binding")).isZero();
    }

    /**
     * W21-03 补修（第三轮 §2 反例，自然触发路径，不手工设置事务标记/资源绑定）：
     * 事务开在 A，B 适配器先经公开只读 {@code findPrincipal} 触发 Spring 同步绑定
     * autoCommit=true 的非事务 ConnectionHolder——"全局有实际事务 + 目标源有 holder"
     * 双条件同时满足但绑定连接未参与任何事务。门禁必须核验绑定连接的真实事务
     * 参与，三个写端口（绑定/会话/生命周期，共享门禁）均在首次写入前以门禁消息
     * 拒绝（不是任意异常或外层主动抛异常顶替）；外层回滚结束后两个连接面独立
     * 查询三表零残留。
     */
    @Test
    void foreignTransactionWithTargetSourcePreReadIsRejectedBeforeWriteWithZeroResidue() throws Exception {
        PgIdentityBindingStore storeOnB =
                new PgIdentityBindingStore(new JdbcTemplate(otherDataSource));
        PgLocalSessionStore sessionsOnB =
                new PgLocalSessionStore(new JdbcTemplate(otherDataSource));
        PgIdentityLifecycleStore lifecycleOnB =
                new PgIdentityLifecycleStore(new JdbcTemplate(otherDataSource), sessionsOnB);

        var managerOnA = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        var templateOnA = new org.springframework.transaction.support.TransactionTemplate(managerOnA);

        templateOnA.executeWithoutResult(status -> {
            // 公开只读（自然触发）：Spring 为 B 绑定同步 ConnectionHolder，连接 autoCommit=true
            assertThat(storeOnB.findPrincipal("not-present")).isEmpty();

            // 前置形态断言（对应独立探针 PROBE 输出）：Spring 行为变化使触发形态不再
            // 成立时在此失败，而不是让后续"拒绝"断言恒真
            Object bound = TransactionSynchronizationManager.getResource(otherDataSource);
            assertThat(bound).isInstanceOf(ConnectionHolder.class);
            assertThat(autoCommitOf((ConnectionHolder) bound))
                    .as("反例触发形态：B 绑定的同步连接未参与事务（autoCommit=true）")
                    .isTrue();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

            assertThatThrownBy(() -> storeOnB.findOrCreatePrincipalWithBinding(
                            identity(), new PrincipalCreationSpec("login", "display")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("autoCommit=true")
                    .hasMessageContaining("参与外层事务");
            assertThatThrownBy(() -> sessionsOnB.revokeAllSessions("any-principal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("autoCommit=true");
            assertThatThrownBy(() -> lifecycleOnB.deactivatePrincipal("any-principal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("autoCommit=true");

            status.setRollbackOnly();
        });

        // 外层事务结束后独立查询（A/B 两个连接面分别查三表）：拒绝发生在首次写入前，零残留
        assertThat(Support.count(dataSource, "principal")).isZero();
        assertThat(Support.count(dataSource, "external_binding")).isZero();
        assertThat(Support.count(dataSource, "local_session")).isZero();
        assertThat(Support.count(otherDataSource, "principal")).isZero();
        assertThat(Support.count(otherDataSource, "external_binding")).isZero();
        assertThat(Support.count(otherDataSource, "local_session")).isZero();
    }

    private static boolean autoCommitOf(ConnectionHolder holder) {
        try {
            return holder.getConnection().getAutoCommit();
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("无法读取绑定连接 autoCommit（前置形态断言）", e);
        }
    }

    @Test
    void syncBoundResourceWithoutActualTransactionIsRejected() throws Exception {
        // 白盒（W21-03）：适配器数据源上绑定了 ConnectionHolder（同步共享连接形态）
        // 但全局无实际事务 → 以"无实际事务"拒绝；存在绑定资源不能顶替事务判据
        PgIdentityBindingStore store = new PgIdentityBindingStore(new JdbcTemplate(dataSource));

        TransactionSynchronizationManager.initSynchronization();
        Connection connection = dataSource.getConnection();
        TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
        try {
            assertThatThrownBy(() -> store.findOrCreatePrincipalWithBinding(
                            identity(), new PrincipalCreationSpec("login", "display")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("无实际事务");
            assertThat(Support.count(dataSource, "principal")).isZero();
        } finally {
            TransactionSynchronizationManager.unbindResource(dataSource);
            TransactionSynchronizationManager.clearSynchronization();
            connection.close();
        }
    }

    @Test
    void correctDataSourceTransactionRollsBackEveryWrite() {
        // 正确装配（同一 DataSource 实例：runner/事务/适配器一致）异常全回滚
        PgIdentityBindingStore store = new PgIdentityBindingStore(new JdbcTemplate(dataSource));
        PgLocalSessionStore sessions = new PgLocalSessionStore(new JdbcTemplate(dataSource));
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        var template = new org.springframework.transaction.support.TransactionTemplate(manager);

        template.executeWithoutResult(status -> {
            store.findOrCreatePrincipalWithBinding(identity(), new PrincipalCreationSpec("login", "display"));
            sessions.revokeAllSessions("any-principal");
            status.setRollbackOnly();
        });

        assertThat(Support.count(dataSource, "principal")).isZero();
        assertThat(Support.count(dataSource, "external_binding")).isZero();
        assertThat(Support.count(dataSource, "local_session")).isZero();
    }
}
