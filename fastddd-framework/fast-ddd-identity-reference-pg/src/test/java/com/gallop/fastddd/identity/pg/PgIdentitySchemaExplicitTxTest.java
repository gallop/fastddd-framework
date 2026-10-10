package com.gallop.fastddd.identity.pg;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.jdbc.datasource.init.ScriptException;

import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R-9（W5-f f-3 §7.2；M-18/F4 修复轮验证）：坏 SQL 注入脚本在显式事务内
 * → 异常 + 回滚零残留——<b>测试自管连接直读 classpath 脚本资源，零生产面
 * 改动</b>（{@code PgIdentitySchema.executeScript} 每脚本一条 autocommit
 * 连接 = F4 已登记已知边界，非本测对象；本测证明的是 PG/脚本引擎行为面：
 * 显式事务内单语句失败 → 整脚本原子回滚——F4 若未来需要的修复杠杆）。
 *
 * <p><b>隔离形态</b>：注入目标 schema（r9_probe）而非 public——V1 全部
 * CREATE IF NOT EXISTS，与 Support 集群上既有 public 表零碰撞；断言面 =
 * to_regclass（schema 限定名）+ pg_namespace 计数（schema 本体零残留）。
 * 对照组（正例）：同弧线干净脚本显式事务 → commit → 三表全落（证明失败
 * 路径的"零残留"来自回滚而非语句序巧合）。</p>
 */
class PgIdentitySchemaExplicitTxTest {

    private static final String POISONED_SUFFIX = "\n\nINJECTED_BAD_SQL THIS IS NOT VALID SQL;\n";

    /** 毒化脚本：V1 全文 + 注入坏语句——执行必炸且已写语句全部待回滚。 */
    @Test
    void poisonedScriptInsideExplicitTransactionAbortsAndRollsBackWithZeroResidue() throws Exception {
        String script = readCoreDdl();
        try (Connection connection = Support.dataSource().getConnection()) {
            connection.setAutoCommit(false); // 测试自管显式事务
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE SCHEMA r9_probe");
                st.execute("SET LOCAL search_path TO r9_probe, public");
            }
            // 坏 SQL 注入脚本：前段 DDL 已执行、尾语句炸 → ScriptException
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(connection,
                    new ByteArrayResource((script + POISONED_SUFFIX)
                            .getBytes(StandardCharsets.UTF_8))))
                    .isInstanceOf(ScriptException.class);
            connection.rollback(); // 回滚整脚本（含 CREATE SCHEMA）
        }

        // 零残留双面：schema 本体（pg_namespace）+ 三核心表（to_regclass 全 null）
        assertThat(new JdbcTemplate(Support.dataSource()).queryForObject(
                "SELECT count(*) FROM pg_namespace WHERE nspname = 'r9_probe'", Integer.class))
                .isZero(); // schema 本体零残留（回滚原子性）
        assertThat(regclass("r9_probe.local_session")).isNull();
        assertThat(regclass("r9_probe.external_binding")).isNull();
        assertThat(regclass("r9_probe.principal")).isNull();
    }

    /** 对照正例：干净脚本同弧线显式事务 → commit → 三表全落（隔离 schema 内）。 */
    @Test
    void cleanScriptInExplicitTransactionCommitsFully() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(Support.dataSource());
        jdbc.execute("DROP SCHEMA IF EXISTS r9_commit_probe CASCADE"); // 防御性清场
        try {
            String script = readCoreDdl();
            try (Connection connection = Support.dataSource().getConnection()) {
                connection.setAutoCommit(false);
                try (Statement st = connection.createStatement()) {
                    st.execute("CREATE SCHEMA r9_commit_probe");
                    st.execute("SET LOCAL search_path TO r9_commit_probe, public");
                }
                ScriptUtils.executeSqlScript(connection,
                        new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)));
                connection.commit();
            }
            assertThat(regclass("r9_commit_probe.local_session")).isNotNull();
            assertThat(regclass("r9_commit_probe.external_binding")).isNotNull();
            assertThat(regclass("r9_commit_probe.principal")).isNotNull();
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS r9_commit_probe CASCADE"); // 清场
        }
    }

    /** 直读 classpath 脚本资源（与 PgIdentitySchema.CORE_DDL_PATH 同源）。 */
    private static String readCoreDdl() throws Exception {
        try (var in = PgIdentitySchemaExplicitTxTest.class
                .getResourceAsStream("/" + PgIdentitySchema.CORE_DDL_PATH)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** 三核心表在指定 schema 的 to_regclass（缺表返回 null——断言零残留面）。 */
    private static String regclass(String qualifiedName) {
        return new JdbcTemplate(Support.dataSource())
                .queryForObject("SELECT to_regclass(?)::text", String.class, qualifiedName);
    }
}
