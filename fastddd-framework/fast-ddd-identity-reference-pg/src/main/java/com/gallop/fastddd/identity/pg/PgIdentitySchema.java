package com.gallop.fastddd.identity.pg;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * 身份参考 schema 初始化器：执行 db/identity/V1__identity_core.sql（幂等 CREATE IF NOT EXISTS）。
 *
 * <p>宿主拥有表与迁移（FI-04）：默认关闭，测试与"空库独立消费者"验证经
 * {@code fastddd.identity.pg.initialize-schema=true} 显式开启。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PgIdentitySchema {

    static final String CORE_DDL_PATH = "db/identity/V1__identity_core.sql";
    /** W2-2 追加式增量（state 上下文列；不改写 V1，已跑过 V1 的库可安全追加）。 */
    static final String LOGIN_STATE_DDL_PATH = "db/identity/V2__login_state_browser_binding.sql";
    /** W3-c 目录镜像六表（幂等；不依赖 V2，已跑过 V1/V2 的库可安全追加）。 */
    static final String DIRECTORY_DDL_PATH = "db/identity/V3__directory_mirror.sql";
    static final String AUDIT_ACTOR_DDL_PATH = "db/identity/optional/V1__sys_audit_log_actor_columns.sql";

    /** 基表形态与 security-starter AuditLogServiceImpl 一致（PG 方言直书）。 */
    static final String AUDIT_BASE_TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS sys_audit_log (
                id BIGSERIAL PRIMARY KEY,
                username VARCHAR(50),
                operation VARCHAR(200),
                operation_type VARCHAR(50),
                method VARCHAR(200),
                request_url VARCHAR(500),
                request_params TEXT,
                result TEXT,
                client_ip VARCHAR(50),
                user_agent VARCHAR(500),
                execution_time BIGINT,
                success BOOLEAN DEFAULT TRUE,
                error_message TEXT,
                create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )""";

    private PgIdentitySchema() {
    }

    /** 初始化身份核心表（幂等；V1 后依次执行 V2/V3 追加增量）。 */
    public static void initialize(DataSource dataSource) {
        executeScript(dataSource, CORE_DDL_PATH);
        executeScript(dataSource, LOGIN_STATE_DDL_PATH);
        executeScript(dataSource, DIRECTORY_DDL_PATH);
    }

    /** 初始化审计基表 + 可选 actor 列（幂等；供测试/空库消费者，宿主可自行管理迁移）。 */
    public static void initializeAuditTable(DataSource dataSource, boolean actorColumns) {
        executeStatement(dataSource, AUDIT_BASE_TABLE_DDL);
        if (actorColumns) {
            executeScript(dataSource, AUDIT_ACTOR_DDL_PATH);
        }
    }

    private static void executeScript(DataSource dataSource, String path) {
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(path));
        } catch (Exception e) {
            throw new IllegalStateException("身份 schema 初始化失败：" + path, e);
        }
    }

    private static void executeStatement(DataSource dataSource, String sql) {
        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("身份 schema 初始化失败：" + sql, e);
        }
    }
}
