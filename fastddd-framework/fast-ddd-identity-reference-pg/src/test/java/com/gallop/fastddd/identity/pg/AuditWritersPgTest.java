package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.pg.audit.PgActorColumnIdentityAuditWriter;
import com.gallop.fastddd.identity.pg.audit.PgLegacyIdentityAuditWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 两种身份审计 writer 实际 SQL（§5.6）：writer① 仅旧列、username=principalId；
 * writer② 启动列检查失败即拒绝启动、actor 列写入与宿主词表映射
 * （HUMAN→USER / SYSTEM_JOB→SERVICE_ACCOUNT / SYSTEM→SYSTEM），
 * 以及 50 字符 SYSTEM_JOB 协议上限穿过 VARCHAR(50) username 的边界。
 *
 * <p>顺序固定：先验证"缺 actor 列启动失败"，再 ALTER 引入 actor 列（幂等，
 * 引入后无法在本 JVM 内移除）。</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditWritersPgTest {

    private final DataSource dataSource = Support.dataSource();

    @BeforeEach
    void setUp() {
        // 幂等：先保证审计基表存在（不含 actor 列则保持不含）
        PgIdentitySchema.initializeAuditTable(dataSource, false);
        new JdbcTemplate(dataSource).execute("TRUNCATE TABLE sys_audit_log");
    }

    private IdentityAuditRecord record(PrincipalId actorId, PrincipalType type, String operation) {
        return new IdentityAuditRecord("tenant-1", actorId, type, operation,
                "detail-文本", true, null, Instant.parse("2026-09-25T10:15:30Z"));
    }

    @Test
    @Order(1)
    void legacyWriterWritesOldColumnsWithUsernameAsPrincipalId() {
        var writer = new PgLegacyIdentityAuditWriter(new JdbcTemplate(dataSource));
        PrincipalId actorId = PrincipalId.of("01234567-89ab-cdef-0123-456789abcdef");

        writer.write(record(actorId, PrincipalType.HUMAN, "identity.login"));

        Map<String, Object> row = new JdbcTemplate(dataSource).queryForMap(
                "SELECT * FROM sys_audit_log WHERE username = ?", actorId.value());
        assertThat(row.get("username")).isEqualTo(actorId.value());
        assertThat(row.get("operation")).isEqualTo("identity.login");
        assertThat(row.get("operation_type")).isEqualTo("IDENTITY");
        assertThat(row.get("method")).isEqualTo("identity");
        assertThat(row.get("request_params")).isEqualTo("detail-文本");
        assertThat(row.get("success")).isEqualTo(Boolean.TRUE);
        assertThat(row.get("error_message")).isNull();
        assertThat(((java.sql.Timestamp) row.get("create_time")).toInstant())
                .isEqualTo(Instant.parse("2026-09-25T10:15:30Z"));
    }

    @Test
    @Order(2)
    void actorColumnWriterFailsFastWhenColumnsMissing() {
        // 未执行 optional DDL：构造即启动失败并给出迁移提示，不静默降级
        assertThatThrownBy(() -> new PgActorColumnIdentityAuditWriter(
                new JdbcTemplate(dataSource), dataSource))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("actor")
                .hasMessageContaining("V1__sys_audit_log_actor_columns.sql");
        assertThat(Support.count(dataSource, "sys_audit_log")).isZero();
    }

    @Test
    @Order(3)
    void actorColumnWriterMapsHostVocabularyAndPassesFiftyCharBoundary() {
        // 执行 optional DDL（幂等 ALTER ADD IF NOT EXISTS）后 writer② 可用
        PgIdentitySchema.initializeAuditTable(dataSource, true);
        new PgActorColumnIdentityAuditWriter(new JdbcTemplate(dataSource), dataSource)
                .write(record(PrincipalId.of("01234567-89ab-cdef-0123-456789abcdee"),
                        PrincipalType.HUMAN, "identity.login"));

        // 50 字符 SYSTEM_JOB（协议上限，含 11 字符前缀）穿过 username VARCHAR(50) 边界
        PrincipalId boundaryJob = PrincipalId.forSystemJob("j".repeat(39));
        assertThat(boundaryJob.value()).hasSize(50);
        new PgActorColumnIdentityAuditWriter(new JdbcTemplate(dataSource), dataSource)
                .write(record(boundaryJob, PrincipalType.SYSTEM_JOB, "identity.directory-sync"));

        new PgActorColumnIdentityAuditWriter(new JdbcTemplate(dataSource), dataSource)
                .write(record(PrincipalId.system(), PrincipalType.SYSTEM, "identity.bootstrap"));

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT username, actor_principal_id, actor_type FROM sys_audit_log ORDER BY id");
        assertThat(rows).hasSize(3);

        Map<String, Object> human = rows.get(0);
        assertThat(human.get("username")).isEqualTo("01234567-89ab-cdef-0123-456789abcdee");
        assertThat(human.get("actor_principal_id")).isEqualTo(human.get("username"));
        assertThat(human.get("actor_type")).isEqualTo("USER"); // HUMAN→宿主词表 USER

        Map<String, Object> job = rows.get(1);
        assertThat(job.get("username")).isEqualTo(boundaryJob.value()); // 50 字符完整落库不截断
        assertThat(job.get("actor_type")).isEqualTo("SERVICE_ACCOUNT"); // SYSTEM_JOB→SERVICE_ACCOUNT

        Map<String, Object> system = rows.get(2);
        assertThat(system.get("username")).isEqualTo("system");
        assertThat(system.get("actor_type")).isEqualTo("SYSTEM");
    }

    @Test
    @Order(4)
    void legacyWriterStillWorksAfterActorColumnsAdded() {
        // 反向共存：宿主先加 actor 列（自有迁移），writer① 仍只写旧列、actor 列为 NULL
        var writer = new PgLegacyIdentityAuditWriter(new JdbcTemplate(dataSource));
        PrincipalId actorId = PrincipalId.of("01234567-89ab-cdef-0123-456789abcdfd");
        writer.write(record(actorId, PrincipalType.HUMAN, "identity.logout"));

        Map<String, Object> row = new JdbcTemplate(dataSource).queryForMap(
                "SELECT * FROM sys_audit_log WHERE username = ?", actorId.value());
        assertThat(row.get("actor_principal_id")).isNull();
        assertThat(row.get("actor_type")).isNull();
        assertThat(row.get("operation")).isEqualTo("identity.logout");
    }
}
