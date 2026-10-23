package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryChangeType;
import com.gallop.fastddd.identity.core.domain.DirectoryIncrementEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.domain.Ulids;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.util.List;

/**
 * 权威回查提交 PG 参考适配器（W5 设计 §7.1/表 7-1，W5-c 交付）。
 *
 * <p><b>事务模型与步骤链</b>：须在提交事务内调用（首行 {@link PgTransactionGate}
 * 拒绝无事务）；锁 metadata 行 → 路径裁决（P1-2 与增量同构：非终态构建代在场 →
 * 预合并语义，否则空闲全量面）→ CAS 基线核对 → 分支落库 → 水位推进 → 变更日志
 * →（空闲态）事件 id 返回；<b>锁内零远程调用</b>（回查外呼在调用方事务外完成，
 * 本方法只裁决提交）。</p>
 *
 * <p><b>CAS 语义</b>：{@code evidence.expectedRevision()} 与镜像行 local_revision
 * 不一致 → {@code stale=true} 零写零推返回（调用方以新基线重走回查）；无行而
 * 基线非 0（行不可能被删除——判定为基线失真）同样 stale 零写。写入语句均带
 * {@code WHERE local_revision = ?} 双保险（同 scope 写入方经 metadata 行锁串行，
 * 锁内竞态不存在——CAS 谓词为不变量守护而非竞态防线）。</p>
 *
 * <p><b>四分支（表 7-1）</b>：CONTENT_DIFFERS → 内容替换（rev+1、origin=RECHECK、
 * pending_confirm 复位；无行 = 确证存在形态，INSERT rev=1）+ 水位推进 + 日志 +
 * {@code *_CHANGED} 事件；IDENTICAL → 仅复位 pending_confirm（零 H、零日志、
 * 零事件——水位不动）；ABSENT/DEACTIVATED → 墓碑（既有行 rev+1、tombstone=true、
 * origin=RECHECK、pending_confirm 复位——权威确证即裁决终结；无行零写空操作）
 * + 水位推进 + 日志 + {@code *_DEACTIVATED} 事件（§7.4 停用细分含 deleted，单对象
 * ≤1 事件）。DEACTIVATED 的 external_rejection 门禁链写入不在本适配器（W5-e
 * 登录链路集成，与登录同序）。</p>
 *
 * <p><b>change_ref</b>：本事务预铸 ULID（P2-5），同时作为 RECHECK_COMMITTED
 * 日志条目 id；事实行与日志行同 id 引用。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryRecheckCommitter implements DirectoryRecheckCommitter {

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

    private static final String SELECT_OPEN_GEN_SQL = """
            SELECT id FROM directory_generation
            WHERE tenant_id = ? AND app_scope = ?
              AND state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
            ORDER BY created_at
            LIMIT 1
            """;

    private static final String CHECK_FACT_SQL = """
            SELECT local_revision FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
            """;

    private static final String UPDATE_CONTENT_SQL = """
            UPDATE directory_fact
            SET local_revision = local_revision + 1, fact = ?::jsonb, tombstone = false,
                origin = 'RECHECK', change_ref = ?, pending_confirm = false, updated_seq = ?
            WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
              AND local_revision = ?
            """;

    private static final String INSERT_CONTENT_SQL = """
            INSERT INTO directory_fact
                (tenant_id, app_scope, object_type, external_id, local_revision, fact,
                 tombstone, origin, change_ref, pending_confirm, source_generation, updated_seq)
            VALUES (?, ?, ?, ?, 1, ?::jsonb, false, 'RECHECK', ?, false, NULL, ?)
            """;

    private static final String UPDATE_TOMBSTONE_SQL = """
            UPDATE directory_fact
            SET local_revision = local_revision + 1, tombstone = true,
                origin = 'RECHECK', change_ref = ?, pending_confirm = false, updated_seq = ?
            WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
              AND local_revision = ?
            """;

    private static final String RESET_PENDING_SQL = """
            UPDATE directory_fact
            SET pending_confirm = false
            WHERE tenant_id = ? AND app_scope = ? AND object_type = ? AND external_id = ?
              AND local_revision = ? AND pending_confirm
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

    public PgDirectoryRecheckCommitter(JdbcTemplate jdbcTemplate, DirectoryChangeLog changeLog) {
        this.jdbcTemplate = jdbcTemplate;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
        this.changeLog = changeLog;
    }

    @Override
    public RecheckResult commitCas(DirectoryScope scope, RecheckEvidence evidence) {
        gate.requireActiveTransaction("directory-recheck:commitCas");
        validateCarriage(evidence);
        String objectType = evidence.objectType().name();

        jdbcTemplate.update(BOOTSTRAP_METADATA_SQL, scope.tenantKey(), scope.appScope());
        long[] wm = jdbcTemplate.queryForObject(LOCK_METADATA_SQL,
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)},
                scope.tenantKey(), scope.appScope());
        long committedBefore = wm[0];
        long appliedBefore = wm[1];
        List<String> openGen = jdbcTemplate.query(SELECT_OPEN_GEN_SQL,
                (rs, i) -> rs.getString(1), scope.tenantKey(), scope.appScope());
        boolean premerge = !openGen.isEmpty();

        Long currentRevision = jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(CHECK_FACT_SQL);
            ps.setString(1, scope.tenantKey());
            ps.setString(2, scope.appScope());
            ps.setString(3, objectType);
            ps.setString(4, evidence.externalId());
            return ps;
        }, rs -> rs.next() ? rs.getLong(1) : null);
        if (currentRevision != null && currentRevision != evidence.expectedRevision()) {
            return staleResult(committedBefore, appliedBefore);
        }
        if (currentRevision == null && evidence.expectedRevision() != 0) {
            // 行不可能被删除——基线非 0 而行缺席 = 基线失真（如跨 scope 读错行），零写退出
            return staleResult(committedBefore, appliedBefore);
        }

        // ---- IDENTICAL：零 H 分支（仅可能复位 pending，无水位/日志/事件） ----
        if (evidence.conclusion() == RecheckConclusion.IDENTICAL) {
            if (currentRevision != null) {
                jdbcTemplate.update(RESET_PENDING_SQL, scope.tenantKey(), scope.appScope(),
                        objectType, evidence.externalId(), evidence.expectedRevision());
            }
            return new RecheckResult(false, false, committedBefore, appliedBefore,
                    null, null, List.of());
        }

        // ---- ABSENT / DEACTIVATED：墓碑分支（无行 = 零写空操作） ----
        if (evidence.conclusion() == RecheckConclusion.ABSENT
                || evidence.conclusion() == RecheckConclusion.DEACTIVATED) {
            if (currentRevision == null) {
                return new RecheckResult(false, false, committedBefore, appliedBefore,
                        null, null, List.of());
            }
            String ulid = Ulids.newUlid();
            long committedAfter = committedBefore + 1;
            int updated = jdbcTemplate.update(UPDATE_TOMBSTONE_SQL, ulid, committedAfter,
                    scope.tenantKey(), scope.appScope(), objectType, evidence.externalId(),
                    evidence.expectedRevision());
            if (updated == 0) {
                return staleResult(committedBefore, appliedBefore);
            }
            return pushAndReport(scope, premerge, openGen, ulid, committedBefore, appliedBefore,
                    committedAfter, evidence, deactivationType(evidence.objectType()));
        }

        // ---- CONTENT_DIFFERS：内容替换分支（无行 = 确证存在形态 INSERT rev=1） ----
        String contentJson = evidence.objectType() == DirectoryObjectType.PERSON
                ? PgDirectoryIncrementApplier.personJson(evidence.observedPerson())
                : PgDirectoryIncrementApplier.departmentJson(evidence.observedDepartment());
        String ulid = Ulids.newUlid();
        long committedAfter = committedBefore + 1;
        if (currentRevision != null) {
            int updated = jdbcTemplate.update(UPDATE_CONTENT_SQL, contentJson, ulid, committedAfter,
                    scope.tenantKey(), scope.appScope(), objectType, evidence.externalId(),
                    evidence.expectedRevision());
            if (updated == 0) {
                return staleResult(committedBefore, appliedBefore);
            }
        } else {
            jdbcTemplate.update(INSERT_CONTENT_SQL, scope.tenantKey(), scope.appScope(),
                    objectType, evidence.externalId(), contentJson, ulid, committedAfter);
        }
        return pushAndReport(scope, premerge, openGen, ulid, committedBefore, appliedBefore,
                committedAfter, evidence,
                evidence.objectType() == DirectoryObjectType.PERSON
                        ? DirectoryChangeType.PERSON_CHANGED
                        : DirectoryChangeType.DEPARTMENT_CHANGED);
    }

    /** 水位推进（路径感知）+ RECHECK_COMMITTED 日志 +（空闲态）单事件 id。 */
    private RecheckResult pushAndReport(DirectoryScope scope, boolean premerge, List<String> openGen,
                                        String ulid, long committedBefore, long appliedBefore,
                                        long committedAfter, RecheckEvidence evidence,
                                        DirectoryChangeType eventType) {
        if (premerge) {
            jdbcTemplate.update(ADVANCE_COMMITTED_SQL, committedAfter, scope.tenantKey(), scope.appScope());
            jdbcTemplate.update(SET_PREMERGED_SQL, committedAfter, openGen.get(0));
        } else {
            jdbcTemplate.update(ADVANCE_FULL_SQL, committedAfter, committedAfter,
                    scope.tenantKey(), scope.appScope());
        }
        changeLog.append(new ChangeLogEntry(ulid, scope, ChangeLogEntry.Kind.RECHECK_COMMITTED,
                null, committedAfter, summaryJson(evidence)));
        // 增量通知事件（W5-e 通知链）：单对象事件实体随 result 返回，用例层同事务
        // 转发 DirectoryIncrementPublisher（预合并不发；objectExternalIds = 回查目标键）
        List<DirectoryIncrementEvent> events = premerge ? List.of()
                : List.of(new DirectoryIncrementEvent(
                        DirectoryIncrementEvent.eventIdFor(scope, committedAfter, eventType),
                        scope, committedAfter, eventType,
                        List.of(evidence.externalId()), ulid));
        return new RecheckResult(false, premerge, committedAfter,
                premerge ? appliedBefore : committedAfter,
                premerge ? committedAfter : null,
                ulid, events);
    }

    /** 携带约定校验（端口 javadoc：CONTENT_DIFFERS 恰携带一个与 objectType 匹配的观察对象）。 */
    private static void validateCarriage(RecheckEvidence evidence) {
        boolean person = evidence.observedPerson() != null;
        boolean department = evidence.observedDepartment() != null;
        if (evidence.conclusion() == RecheckConclusion.CONTENT_DIFFERS) {
            if (person == department) {
                throw new IllegalArgumentException("CONTENT_DIFFERS 须恰携带一个观察对象（"
                        + "observedPerson=" + person + ", observedDepartment=" + department + "）: " + evidence);
            }
            if (evidence.objectType() == DirectoryObjectType.PERSON
                    && !evidence.externalId().equals(evidence.observedPerson().openId())) {
                throw new IllegalArgumentException("observedPerson.openId 与 externalId 不一致: " + evidence);
            }
            if (evidence.objectType() == DirectoryObjectType.DEPARTMENT
                    && !evidence.externalId().equals(evidence.observedDepartment().departmentId())) {
                throw new IllegalArgumentException("observedDepartment.departmentId 与 externalId 不一致: " + evidence);
            }
        } else if (person || department) {
            throw new IllegalArgumentException(evidence.conclusion() + " 不携带替换内容: " + evidence);
        }
    }

    private static DirectoryChangeType deactivationType(DirectoryObjectType objectType) {
        return objectType == DirectoryObjectType.PERSON
                ? DirectoryChangeType.PERSON_DEACTIVATED
                : DirectoryChangeType.DEPARTMENT_DEACTIVATED;
    }

    private static RecheckResult staleResult(long committed, long applied) {
        return new RecheckResult(true, false, committed, applied, null, null, List.of());
    }

    /** 无 PII 摘要（sourceEventId 为 Inbox 事件 id，非个人数据）。 */
    private static String summaryJson(RecheckEvidence evidence) {
        return "{\"conclusion\":\"" + evidence.conclusion().name()
                + "\",\"objectType\":\"" + evidence.objectType().name()
                + "\",\"sourceEventId\":" + (evidence.sourceEventId() == null
                        ? "null"
                        : PgDirectoryIncrementApplier.jsonString(evidence.sourceEventId()))
                + "}";
    }
}
