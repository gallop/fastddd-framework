package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryChangeType;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.IncrementBatch;
import com.gallop.fastddd.identity.core.domain.IncrementResult;
import com.gallop.fastddd.identity.core.domain.Ulids;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.DirectoryIncrementApplier;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * 增量落库 PG 参考适配器（W5 设计 §6.1/§6.2/表 6-1，W5-c 交付）。
 *
 * <p><b>事务模型与步骤链</b>（设计 :177，端口 javadoc 契约）：须在提交事务内调用
 * （首行 {@link PgTransactionGate} 拒绝无事务）；锁 metadata 行 → 路径裁决 →
 * 事实写入 + 水位推进 → 变更日志追加 →（空闲态）事件 id 返回；<b>锁内零远程调用</b>。
 * 路径裁决 = 锁 metadata 时存在非终态构建代（BUILDING/VALIDATING/VALIDATED）→
 * <b>路径 B 预合并</b>（推 committed_seq = H+1 + G.premerged_seq = 新 H，不推
 * applied_seq、不发事件）；空闲 → <b>路径 A 全量面</b>（推 committed/applied =
 * H+1，非空变更按 {@link DirectoryChangeType} 分组返回事件 id——发布动作归调用方，
 * 本适配器零外呼）。</p>
 *
 * <p><b>幂等/零写判定</b>：空批 → 零写零推（metadata 行不存在按 0/0 返回，无自举
 * 写）；逐对象裁决——既有行内容一致（jsonb 语义相等，锚键投影后比较，见
 * {@link #adjudicate}）→ 跳过；墓碑行 → 跳过
 * （复活须权威确认 §7.2，增量不写墓碑行）；全批无有效变更 → 零写零推零事件
 * （表 6-1②：仅持锁、无行变化）。新键 → INSERT（rev=1、origin=INCREMENT）；
 * 内容变更 → UPDATE（rev+1、origin=INCREMENT、change_ref=预铸 ULID、
 * pending_confirm 复位——增量观察到即存在证据，规则 E 同义）。</p>
 *
 * <p><b>首增富化（登记行为）</b>：FULL_SCAN 锚定事实（§6.2 规则 A 形态）不含
 * departmentIds/部门 name——同一对象首次增量必判"内容变更"（富化写），属表 6-1①
 * 变更面；重投递同批后即零写。</p>
 *
 * <p><b>change_ref</b>：本事务预铸 ULID（{@link Ulids#newUlid()}，P2-5 先铸后写），
 * 同时作为 INCREMENT_APPLIED 变更日志条目 id——事实行与日志行同 id 引用，无二次
 * UPDATE。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryIncrementApplier implements DirectoryIncrementApplier {

    private static final String BOOTSTRAP_METADATA_SQL = """
            INSERT INTO directory_publish_metadata (tenant_id, app_scope, committed_seq, applied_seq, updated_at)
            VALUES (?, ?, 0, 0, now())
            ON CONFLICT (tenant_id, app_scope) DO NOTHING
            """;

    private static final String LOCK_METADATA_SQL = """
            SELECT committed_seq, applied_seq
            FROM directory_publish_metadata
            WHERE tenant_id = ? AND app_scope = ?
            FOR UPDATE
            """;

    private static final String READ_METADATA_SQL = """
            SELECT committed_seq, applied_seq FROM directory_publish_metadata
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String SELECT_OPEN_GEN_SQL = """
            SELECT id FROM directory_generation
            WHERE tenant_id = ? AND app_scope = ?
              AND state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
            ORDER BY created_at
            LIMIT 1
            """;

    /** 内容一致性判定：锚键投影（去除 firstSeenGeneration）后 jsonb 语义比较。 */
    private static final String CHECK_PERSON_SQL = """
            SELECT tombstone, (fact - 'firstSeenGeneration') = ?::jsonb AS unchanged
            FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ? AND object_type = 'PERSON' AND external_id = ?
            """;

    private static final String CHECK_DEPARTMENT_SQL = """
            SELECT tombstone, (fact - 'firstSeenGeneration') = ?::jsonb AS unchanged
            FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ? AND object_type = 'DEPARTMENT' AND external_id = ?
            """;

    private static final String INSERT_FACT_SQL = """
            INSERT INTO directory_fact
                (tenant_id, app_scope, object_type, external_id, local_revision, fact,
                 tombstone, origin, change_ref, pending_confirm, source_generation, updated_seq)
            VALUES (?, ?, ?, ?, 1, ?::jsonb, false, 'INCREMENT', ?, false, NULL, ?)
            """;

    private static final String UPDATE_FACT_SQL = """
            UPDATE directory_fact
            SET local_revision = local_revision + 1, fact = ?::jsonb, origin = 'INCREMENT',
                change_ref = ?, pending_confirm = false, updated_seq = ?
            WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
            """;

    private static final String ADVANCE_FULL_SQL = """
            UPDATE directory_publish_metadata SET committed_seq = ?, applied_seq = ?, updated_at = now()
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String ADVANCE_COMMITTED_SQL = """
            UPDATE directory_publish_metadata SET committed_seq = ?, updated_at = now()
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String SET_PREMERGED_SQL = """
            UPDATE directory_generation SET premerged_seq = ?, updated_at = now()
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PgTransactionGate gate;
    private final DirectoryChangeLog changeLog;

    public PgDirectoryIncrementApplier(JdbcTemplate jdbcTemplate, DirectoryChangeLog changeLog) {
        this.jdbcTemplate = jdbcTemplate;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
        this.changeLog = changeLog;
    }

    @Override
    public IncrementResult apply(DirectoryScope scope, IncrementBatch batch) {
        gate.requireActiveTransaction("directory-increment:apply");
        if (batch.isEmpty()) {
            return zeroResult(readMetadata(scope));
        }
        jdbcTemplate.update(BOOTSTRAP_METADATA_SQL, scope.tenantKey(), scope.appScope());
        Watermark before = jdbcTemplate.queryForObject(LOCK_METADATA_SQL,
                (rs, i) -> new Watermark(rs.getLong(1), rs.getLong(2)),
                scope.tenantKey(), scope.appScope());
        List<String> openGen = jdbcTemplate.query(SELECT_OPEN_GEN_SQL,
                (rs, i) -> rs.getString(1), scope.tenantKey(), scope.appScope());
        boolean premerge = !openGen.isEmpty();
        long committedAfter = before.committedSeq() + 1;
        String ulid = Ulids.newUlid();

        List<DirectoryPerson> changedPersons = new ArrayList<>();
        for (DirectoryPerson person : batch.persons()) {
            String json = personJson(person);
            if (adjudicate(CHECK_PERSON_SQL, json, scope, person.openId()) != Verdict.SKIP) {
                writeFact(scope, "PERSON", person.openId(), json, ulid, committedAfter);
                changedPersons.add(person);
            }
        }
        List<DirectoryDepartment> changedDepartments = new ArrayList<>();
        for (DirectoryDepartment department : batch.departments()) {
            String json = departmentJson(department);
            if (adjudicate(CHECK_DEPARTMENT_SQL, json, scope, department.departmentId()) != Verdict.SKIP) {
                writeFact(scope, "DEPARTMENT", department.departmentId(), json, ulid, committedAfter);
                changedDepartments.add(department);
            }
        }
        if (changedPersons.isEmpty() && changedDepartments.isEmpty()) {
            // 表 6-1②：重复投递/内容零差异——零写零推（仅持有锁至提交）
            return zeroResult(before);
        }

        // 水位推进 + 预合并收敛 + 变更日志（同事务；目录域每次水位推进各写一行）
        if (premerge) {
            jdbcTemplate.update(ADVANCE_COMMITTED_SQL, committedAfter, scope.tenantKey(), scope.appScope());
            jdbcTemplate.update(SET_PREMERGED_SQL, committedAfter, openGen.get(0));
        } else {
            jdbcTemplate.update(ADVANCE_FULL_SQL, committedAfter, committedAfter,
                    scope.tenantKey(), scope.appScope());
        }
        changeLog.append(new ChangeLogEntry(ulid, scope, ChangeLogEntry.Kind.INCREMENT_APPLIED,
                null, committedAfter, summaryJson(batch, changedPersons.size(), changedDepartments.size())));

        List<String> eventIds = List.of();
        if (!premerge) {
            List<String> ids = new ArrayList<>();
            if (!changedPersons.isEmpty()) {
                ids.add(changeEventId(scope, committedAfter, DirectoryChangeType.PERSON_CHANGED));
            }
            if (!changedDepartments.isEmpty()) {
                ids.add(changeEventId(scope, committedAfter, DirectoryChangeType.DEPARTMENT_CHANGED));
            }
            eventIds = ids;
        }
        return new IncrementResult(true, premerge, committedAfter,
                premerge ? before.appliedSeq() : committedAfter,
                premerge ? committedAfter : null,
                ulid, eventIds);
    }

    /** 逐对象三态裁决：无行（INSERT）/ 内容变更（UPDATE）/ 跳过（墓碑或内容一致）。 */
    private enum Verdict {
        ABSENT, CHANGE, SKIP
    }

    private Verdict adjudicate(String checkSql, String contentJson, DirectoryScope scope, String externalId) {
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(checkSql);
            ps.setString(1, contentJson);
            ps.setString(2, scope.tenantKey());
            ps.setString(3, scope.appScope());
            ps.setString(4, externalId);
            return ps;
        }, rs -> {
            if (!rs.next()) {
                return Verdict.ABSENT;
            }
            if (rs.getBoolean("tombstone") || rs.getBoolean("unchanged")) {
                return Verdict.SKIP;
            }
            return Verdict.CHANGE;
        });
    }

    private void writeFact(DirectoryScope scope, String objectType, String externalId,
                           String contentJson, String ulid, long updatedSeq) {
        int updated = jdbcTemplate.update(UPDATE_FACT_SQL, contentJson, ulid, updatedSeq,
                scope.tenantKey(), scope.appScope(), objectType, externalId);
        if (updated == 0) {
            jdbcTemplate.update(INSERT_FACT_SQL, scope.tenantKey(), scope.appScope(),
                    objectType, externalId, contentJson, ulid, updatedSeq);
        }
    }

    /** 空批零写读：行不存在按 0/0 返回（从未发布过），无 bootstrap 自举写。 */
    private Watermark readMetadata(DirectoryScope scope) {
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(READ_METADATA_SQL);
            ps.setString(1, scope.tenantKey());
            ps.setString(2, scope.appScope());
            return ps;
        }, rs -> rs.next() ? new Watermark(rs.getLong(1), rs.getLong(2)) : new Watermark(0, 0));
    }

    private static IncrementResult zeroResult(Watermark current) {
        return new IncrementResult(false, false, current.committedSeq(), current.appliedSeq(),
                null, null, List.of());
    }

    private static String changeEventId(DirectoryScope scope, long committedSeq, DirectoryChangeType type) {
        // §9.1：dir-change:{scope}:{committed_seq}:{type}；{scope} 与 T8 eventIdFor
        // 同源（DirectoryScope.toString = tenantKey + "/" + appScope）
        return "dir-change:" + scope + ":" + committedSeq + ":" + type.name();
    }

    private static String summaryJson(IncrementBatch batch, int persons, int departments) {
        return "{\"eventId\":" + jsonString(batch.eventId())
                + ",\"personsChanged\":" + persons
                + ",\"departmentsChanged\":" + departments + "}";
    }

    static String personJson(DirectoryPerson person) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"objectType\":\"PERSON\",\"externalId\":").append(jsonString(person.openId()));
        if (person.name() != null) {
            sb.append(",\"name\":").append(jsonString(person.name()));
        }
        if (person.enName() != null) {
            sb.append(",\"enName\":").append(jsonString(person.enName()));
        }
        sb.append(",\"departmentIds\":[");
        List<String> deptIds = person.departmentIds();
        for (int i = 0; i < deptIds.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonString(deptIds.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    static String departmentJson(DirectoryDepartment department) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"objectType\":\"DEPARTMENT\",\"externalId\":").append(jsonString(department.departmentId()));
        if (department.parentDepartmentId() != null) {
            sb.append(",\"parentDepartmentId\":").append(jsonString(department.parentDepartmentId()));
        }
        if (department.name() != null) {
            sb.append(",\"name\":").append(jsonString(department.name()));
        }
        sb.append('}');
        return sb.toString();
    }

    /** 最小转义（与 PgDirectoryMirrorStore 同式：反斜杠/引号/控制字符），无 JSON 库依赖。 */
    static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private record Watermark(long committedSeq, long appliedSeq) {
    }
}
