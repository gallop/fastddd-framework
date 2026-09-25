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
 * W2-1 修复轮 W21-03 门禁反例（真实 PG）：MANDATORY 门禁绑定适配器数据源——
 *
 * <ul>
 *   <li>事务开在另一 DataSource 上（同一 PG 集群、两个独立 DataSource 实例）：
 *       首次写入前拒绝、零残留（外层回滚不留下自动提交行）；</li>
 *   <li>仅有同步绑定的连接资源而无实际事务（白盒：绑定 ConnectionHolder、
 *       全局无实际事务——如同步激活后的共享读连接）：以"无实际事务"拒绝，
 *       不以"存在绑定资源"顶替事务判据；</li>
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
