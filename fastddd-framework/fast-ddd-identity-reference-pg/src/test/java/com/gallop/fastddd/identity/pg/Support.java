package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/**
 * PG 测试共用支撑：每 JVM 一个 zonky embedded PostgreSQL（真实 PG 二进制，专用一次性），
 * 两个独立 DataSource 模拟共享同一 PG 的两个服务实例；
 * 测试本地事务 runner 与 starter 桥接同契约（入口已有事务拒绝 + READ_COMMITTED）。
 */
final class Support {

    /** JVM 级单例：zonky 默认每 builder 一个集群，测试类共用一个以控制耗时。 */
    static final io.zonky.test.db.postgres.embedded.EmbeddedPostgres PG;

    static {
        try {
            PG = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start();
        } catch (Exception e) {
            throw new IllegalStateException("embedded PostgreSQL 启动失败", e);
        }
    }

    private Support() {
    }

    /** 独立 DataSource = 独立连接面（同一 PG 集群）：两实例交错测试用。 */
    static DataSource dataSource() {
        return PG.getDatabase("postgres", "postgres");
    }

    /** 预置 autoCommit 的 Hikari 池（复评 F1/S2 形态，同一 zonky PG）；调用方负责 close。 */
    static com.zaxxer.hikari.HikariDataSource hikariPool(boolean autoCommit, String poolName) {
        com.zaxxer.hikari.HikariDataSource pool = new com.zaxxer.hikari.HikariDataSource();
        pool.setJdbcUrl(PG.getJdbcUrl("postgres", "postgres"));
        pool.setUsername("postgres");
        pool.setPassword("postgres");
        pool.setAutoCommit(autoCommit);
        pool.setMaximumPoolSize(2);
        pool.setPoolName(poolName);
        return pool;
    }

    /** 与 starter SpringIdentityTransactionRunner 同契约的测试 runner。 */
    static IdentityTransactionRunner runner(DataSource dataSource) {
        var manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(10);
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
                if (org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive()) {
                    throw new IdentityTransactionActiveException("入口已有活动事务，runner 拒绝加入");
                }
                return template.execute(status -> callback.doInTransaction());
            }
        };
    }

    /** 确保核心 schema 存在（幂等 CREATE IF NOT EXISTS，首个测试类在空库上执行即为迁移验证）。 */
    static void ensureIdentitySchema(DataSource dataSource) {
        PgIdentitySchema.initialize(dataSource);
    }

    /** 清空身份表（FK 顺序），保持 schema 复用。 */
    static void cleanIdentityTables(DataSource dataSource) {
        ensureIdentitySchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE local_session, external_binding, principal CASCADE");
    }

    /** 清空登录授权上下文表（W2-2 state 测试隔离；其他测试经 cleanIdentityTables 复用）。 */
    static void cleanLoginState(DataSource dataSource) {
        ensureIdentitySchema(dataSource);
        new JdbcTemplate(dataSource).execute("TRUNCATE TABLE login_state");
    }

    /** 清空目录镜像六表 + W5-c 增量三表（inbox/change_log/rejection；单条 TRUNCATE 原子清空）。 */
    static void cleanDirectoryTables(DataSource dataSource) {
        ensureIdentitySchema(dataSource);
        new JdbcTemplate(dataSource).execute(
                "TRUNCATE TABLE directory_fact, directory_relation_mirror, directory_department_mirror, "
                        + "directory_person_mirror, directory_generation, directory_publish_metadata, "
                        + "directory_event_inbox, directory_change_log, external_rejection");
    }

    static int count(DataSource dataSource, String table) {
        return new JdbcTemplate(dataSource).queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
