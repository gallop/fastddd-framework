package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryGeneration.State;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot.ObservedScope;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot.ScopeSuspicion;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryInvalidGenerationStateException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.pg.support.PgTransactionGate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 目录镜像存储 PG 参考适配器（W3 设计 v3 §5/§6，W3-c 交付）。
 *
 * <p><b>事务模型</b>：本适配器不开事务——每个端口方法假定调用方（编排经
 * {@code IdentityTransactionRunner}）已开启一个 READ_COMMITTED 短事务，写方法
 * 首行经 {@link PgTransactionGate} 拒绝无事务调用（MANDATORY；abandon 的例外
 * 仅是"不要求未过期"，不是免事务）。锁序固定（§5.2）：
 * {@code directory_publish_metadata} 行（SELECT FOR UPDATE，第一把锁）→
 * {@code directory_generation} 行 → 镜像/fact 行；T7 单事务零 HTTP，
 * 任一步失败全回滚（代保持 VALIDATED、水位回退、零残留）。</p>
 *
 * <p><b>metadata 首行自举</b>：设计原文"捕获 DuplicateKeyException 后重查"在
 * PG 不可行——语句级约束冲突使整个事务进入 aborted 态，后续语句全失败；本实现
 * 以 {@code INSERT ... ON CONFLICT DO NOTHING} 汇合后重 SELECT FOR UPDATE，
 * 语义等价且无异常路径（W3-c 执行记录登记的实现偏差）。</p>
 *
 * <p><b>JSON 策略</b>：Java 侧只做 JSON 发射（固定形态 + 最小转义），读取解析
 * 全部推给 PG 服务端 jsonb 操作符——reference-pg 不引入 JSON 库。</p>
 *
 * <p><b>标识生成</b>：代 id = ULID 26 字符（Crockford base32：48bit 毫秒时间戳
 * 10 字符 + 80bit SecureRandom 16 字符）；租约 token = 32 字节 SecureRandom →
 * 64 hex（≥128bit）；配置指纹 = sha256("tenantKey\nappScope") → 64 hex。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryMirrorStore implements DirectoryMirrorStore {

    /** M1 租约持有者标识（调度与手动触发共用租约互斥，§9.4）。 */
    static final String LEASE_HOLDER = "directory-sync";

    /** 建代接管时废弃旧代使用的稳定原因码（与内存替身一致）。 */
    static final String TAKEOVER_REASON = "lease-takeover";

    private static final char[] CROCKFORD_32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final String BOOTSTRAP_METADATA_SQL = """
            INSERT INTO directory_publish_metadata (tenant_id, app_scope, committed_seq, applied_seq, updated_at)
            VALUES (?, ?, 0, 0, ?)
            ON CONFLICT (tenant_id, app_scope) DO NOTHING
            """;

    private static final String LOCK_METADATA_SQL = """
            SELECT active_generation, committed_seq, applied_seq, lease_token, lease_expires_at
            FROM directory_publish_metadata
            WHERE tenant_id = ? AND app_scope = ?
            FOR UPDATE
            """;

    private static final String SELECT_OPEN_GENERATION_SQL = """
            SELECT id, state, start_seq, scope_fingerprint, observed_scope, validation_report,
                   pub_committed_before, pub_committed_after, pub_applied_seq, pub_facts_inserted
            FROM directory_generation
            WHERE tenant_id = ? AND app_scope = ?
              AND state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
            ORDER BY created_at
            LIMIT 1
            """;

    private static final String SELECT_GENERATION_BY_ID_SQL = """
            SELECT id, tenant_id, app_scope, state, start_seq, scope_fingerprint, observed_scope,
                   validation_report, pub_committed_before, pub_committed_after, pub_applied_seq, pub_facts_inserted
            FROM directory_generation
            WHERE id = ?
            """;

    private static final String ABANDON_OPEN_GENERATION_SQL = """
            UPDATE directory_generation
            SET state = 'ABANDONED', abandon_reason = ?, lease_token = NULL, lease_expires_at = NULL, updated_at = ?
            WHERE id = ? AND state IN ('BUILDING', 'VALIDATING', 'VALIDATED')
            """;

    private static final String INSERT_GENERATION_SQL = """
            INSERT INTO directory_generation
                (id, tenant_id, app_scope, state, start_seq, scope_fingerprint,
                 lease_token, lease_expires_at, created_at, updated_at)
            VALUES (?, ?, ?, 'BUILDING', ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE_METADATA_LEASE_SQL = """
            UPDATE directory_publish_metadata
            SET lease_token = ?, lease_expires_at = ?, lease_holder = ?, updated_at = ?
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String RENEW_METADATA_LEASE_SQL = """
            UPDATE directory_publish_metadata
            SET lease_expires_at = ?, updated_at = ?
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String CLEAR_METADATA_LEASE_SQL = """
            UPDATE directory_publish_metadata
            SET lease_token = NULL, lease_expires_at = NULL, lease_holder = NULL, updated_at = ?
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String INSERT_DEPARTMENT_SQL = """
            INSERT INTO directory_department_mirror (generation_id, department_id, parent_department_id, name)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (generation_id, department_id) DO NOTHING
            """;

    private static final String INSERT_PERSON_SQL = """
            INSERT INTO directory_person_mirror (generation_id, open_id, name, en_name, department_ids)
            VALUES (?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (generation_id, open_id) DO NOTHING
            """;

    private static final String INSERT_RELATION_SQL = """
            INSERT INTO directory_relation_mirror (generation_id, open_id, department_id)
            VALUES (?, ?, ?)
            ON CONFLICT (generation_id, open_id, department_id) DO NOTHING
            """;

    private static final String BUMP_PAGES_DONE_SQL = """
            UPDATE directory_generation
            SET pages_done = pages_done + 1, updated_at = ?
            WHERE id = ?
            """;

    private static final String SELECT_ROOT_DEPARTMENTS_SQL = """
            SELECT department_id FROM directory_department_mirror
            WHERE generation_id = ? AND parent_department_id IS NULL
            ORDER BY department_id
            """;

    private static final String SELECT_ALL_DEPARTMENTS_SQL = """
            SELECT department_id FROM directory_department_mirror
            WHERE generation_id = ?
            ORDER BY department_id
            """;

    private static final String COMPLETE_PAGING_SQL = """
            UPDATE directory_generation
            SET state = 'VALIDATING', observed_scope = ?::jsonb, updated_at = ?
            WHERE id = ? AND state = 'BUILDING'
            """;

    private static final String DANGLING_RELATION_SQL = """
            SELECT r.open_id, r.department_id
            FROM directory_relation_mirror r
            WHERE r.generation_id = ?
              AND NOT EXISTS (SELECT 1 FROM directory_department_mirror d
                              WHERE d.generation_id = ? AND d.department_id = r.department_id)
            LIMIT 1
            """;

    private static final String COUNT_MISMATCH_PERSONS_SQL = """
            SELECT count(*) FROM directory_person_mirror p
            WHERE p.generation_id = ?
              AND jsonb_array_length(p.department_ids) <>
                  (SELECT count(*) FROM directory_relation_mirror r
                   WHERE r.generation_id = ? AND r.open_id = p.open_id)
            """;

    private static final String MIRROR_COUNTS_SQL = """
            SELECT (SELECT count(*) FROM directory_department_mirror WHERE generation_id = ?) AS departments,
                   (SELECT count(*) FROM directory_person_mirror WHERE generation_id = ?) AS persons,
                   (SELECT count(*) FROM directory_relation_mirror WHERE generation_id = ?) AS relations
            """;

    private static final String MARK_VALIDATED_SQL = """
            UPDATE directory_generation
            SET state = 'VALIDATED', validation_report = ?::jsonb, updated_at = ?
            WHERE id = ? AND state = 'VALIDATING'
            """;

    private static final String OBSERVED_ROOTS_SQL = """
            SELECT jsonb_array_elements_text(observed_scope->'rootDepartmentIds') FROM directory_generation WHERE id = ?
            """;

    private static final String OBSERVED_DEPARTMENTS_SQL = """
            SELECT jsonb_array_elements_text(observed_scope->'departmentIds') FROM directory_generation WHERE id = ?
            """;

    private static final String CURRENT_DEPARTMENT_IDS_SQL = """
            SELECT department_id FROM directory_department_mirror WHERE generation_id = ?
            """;

    private static final String CURRENT_PERSONS_SQL = """
            SELECT open_id, name, en_name FROM directory_person_mirror WHERE generation_id = ?
            """;

    private static final String CURRENT_DEPARTMENTS_SQL = """
            SELECT department_id, parent_department_id FROM directory_department_mirror WHERE generation_id = ?
            """;

    private static final String SCOPE_FACT_KEYS_SQL = """
            SELECT object_type, external_id, tombstone FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String INSERT_FACT_SQL = """
            INSERT INTO directory_fact
                (tenant_id, app_scope, object_type, external_id, local_revision, fact,
                 tombstone, origin, change_ref, pending_confirm, source_generation, updated_seq)
            VALUES (?, ?, ?, ?, 1, ?::jsonb, false, 'FULL_SCAN', NULL, false, ?, ?)
            """;

    private static final String CLEAR_PENDING_FOR_OBSERVED_SQL = """
            UPDATE directory_fact f
            SET pending_confirm = false
            FROM directory_person_mirror p
            WHERE f.tenant_id = ? AND f.app_scope = ? AND f.object_type = 'PERSON'
              AND f.external_id = p.open_id AND p.generation_id = ?
              AND f.tombstone = false AND f.pending_confirm
            """;

    private static final String MARK_MISSING_PERSONS_PENDING_SQL = """
            UPDATE directory_fact f
            SET pending_confirm = true
            WHERE f.tenant_id = ? AND f.app_scope = ? AND f.object_type = 'PERSON'
              AND f.tombstone = false
              AND NOT EXISTS (SELECT 1 FROM directory_person_mirror cur
                              WHERE cur.generation_id = ? AND cur.open_id = f.external_id)
            """;

    private static final String MARK_GENERATION_PUBLISHED_SQL = """
            UPDATE directory_generation
            SET state = 'PUBLISHED', pub_committed_before = ?, pub_committed_after = ?,
                pub_applied_seq = ?, pub_facts_inserted = ?, updated_at = ?
            WHERE id = ? AND state = 'VALIDATED'
            """;

    private static final String SWITCH_ACTIVE_GENERATION_SQL = """
            UPDATE directory_publish_metadata
            SET committed_seq = ?, applied_seq = ?, active_generation = ?,
                lease_token = NULL, lease_expires_at = NULL, lease_holder = NULL,
                scope_suspicion = ?::jsonb, updated_at = ?
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String ABANDON_GENERATION_SQL = """
            UPDATE directory_generation
            SET state = 'ABANDONED', abandon_reason = ?, lease_token = NULL, lease_expires_at = NULL, updated_at = ?
            WHERE id = ?
            """;

    private static final String READ_METADATA_SQL = """
            SELECT active_generation, committed_seq, applied_seq FROM directory_publish_metadata
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String ACTIVE_MIRROR_COUNTS_SQL = """
            SELECT (SELECT count(*) FROM directory_department_mirror WHERE generation_id = ?) AS departments,
                   (SELECT count(*) FROM directory_person_mirror WHERE generation_id = ?) AS persons
            """;

    private static final String SCOPE_SUSPICIONS_SQL = """
            SELECT e->>'type' AS type, e->>'detail' AS detail
            FROM directory_publish_metadata m, jsonb_array_elements(m.scope_suspicion) e
            WHERE m.tenant_id = ? AND m.app_scope = ?
            """;

    private static final String READ_FACTS_SQL = """
            SELECT object_type, external_id, local_revision, tombstone, origin,
                   change_ref, pending_confirm, source_generation, updated_seq
            FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ?
            ORDER BY object_type, external_id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PgLocalSessionStore.Clock clock;
    private final Duration leaseTtl;
    private final PgTransactionGate gate;
    private final SecureRandom random = new SecureRandom();

    public PgDirectoryMirrorStore(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Instant::now, Duration.ofMinutes(10));
    }

    public PgDirectoryMirrorStore(JdbcTemplate jdbcTemplate, PgLocalSessionStore.Clock clock, Duration leaseTtl) {
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl 必须为正时长（收到：" + leaseTtl + "）");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.leaseTtl = leaseTtl;
        this.gate = new PgTransactionGate(jdbcTemplate.getDataSource());
    }

    // ------------------------------------------------------------- T6a 建代

    @Override
    public GenerationHandle beginGeneration(DirectoryScope scope) {
        gate.requireActiveTransaction("beginGeneration(directory)");
        MetaRow meta = lockMetadata(scope);
        Instant now = clock.now();
        // ② 非终态代 ∧ 租约有效 → LeaseHeld 零写入（保护进行中的持有者，不废弃他人代）
        List<GenRow> open = queryOpenGeneration(scope);
        if (!open.isEmpty()) {
            GenRow current = open.get(0);
            boolean leaseValid = meta.leaseToken() != null && meta.leaseExpiresAt() != null
                    && meta.leaseExpiresAt().isAfter(now);
            if (leaseValid) {
                throw new DirectoryLeaseHeldException("同步进行中：scope " + scope
                        + " 存在持有代 " + current.id() + "（租约未过期，拒绝并发建代）");
            }
            // ③ 失效非终态代（任意非终态，§6.1 接管覆盖全部）→ 同事务置 ABANDONED 后接管
            jdbcTemplate.update(ABANDON_OPEN_GENERATION_SQL, TAKEOVER_REASON, Timestamp.from(now), current.id());
        }
        // ④ 新 token ≥128bit + INSERT 新代（BUILDING、start_seq=①、指纹、租约快照）+ ⑤ 写租约权威值
        String id = newUlid(now);
        String token = newLeaseToken();
        Instant expiresAt = now.plus(leaseTtl);
        jdbcTemplate.update(INSERT_GENERATION_SQL, id, scope.tenantKey(), scope.appScope(),
                meta.committedSeq(), scopeFingerprint(scope), token, Timestamp.from(expiresAt),
                Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update(UPDATE_METADATA_LEASE_SQL, token, Timestamp.from(expiresAt), LEASE_HOLDER,
                Timestamp.from(now), scope.tenantKey(), scope.appScope());
        return new GenerationHandle(scope, id, token);
    }

    @Override
    public Lease renewLease(GenerationHandle handle) {
        gate.requireActiveTransaction("renewLease(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        jointCheck(handle, meta, gen, State.BUILDING, State.VALIDATING);
        Instant now = clock.now();
        Instant expiresAt = now.plus(leaseTtl);
        // 只延 expires_at（权威值），token 不轮换、代快照列不动（§5.2 T6b 页间续租）
        jdbcTemplate.update(RENEW_METADATA_LEASE_SQL, Timestamp.from(expiresAt), Timestamp.from(now),
                handle.scope().tenantKey(), handle.scope().appScope());
        return new Lease(handle.leaseToken(), expiresAt);
    }

    // ------------------------------------------------------------- T6b 写批

    @Override
    public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
        gate.requireActiveTransaction("writeBatch(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        jointCheck(handle, meta, gen, State.BUILDING);
        if (!batch.isEmpty()) {
            if (!batch.departments().isEmpty()) {
                List<Object[]> deptRows = new ArrayList<>();
                for (var d : batch.departments()) {
                    deptRows.add(new Object[]{gen.id(), d.departmentId(), d.parentDepartmentId(), d.name()});
                }
                jdbcTemplate.batchUpdate(INSERT_DEPARTMENT_SQL, deptRows);
            }
            if (!batch.persons().isEmpty()) {
                List<Object[]> personRows = new ArrayList<>();
                List<Object[]> relationRows = new ArrayList<>();
                for (var p : batch.persons()) {
                    personRows.add(new Object[]{gen.id(), p.openId(), p.name(), p.enName(),
                            jsonArray(p.departmentIds())});
                    for (String deptId : p.departmentIds()) {
                        // 源侧多部门关系透传不去重（W3-b 决策 12 归 W3-c 承接）；
                        // 本表主键 ON CONFLICT DO NOTHING 承接去重（同人多页/同批重复归一）
                        relationRows.add(new Object[]{gen.id(), p.openId(), deptId});
                    }
                }
                jdbcTemplate.batchUpdate(INSERT_PERSON_SQL, personRows);
                if (!relationRows.isEmpty()) {
                    jdbcTemplate.batchUpdate(INSERT_RELATION_SQL, relationRows);
                }
            }
        }
        jdbcTemplate.update(BUMP_PAGES_DONE_SQL, Timestamp.from(now()), gen.id());
    }

    @Override
    public void completePaging(GenerationHandle handle) {
        gate.requireActiveTransaction("completePaging(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        jointCheck(handle, meta, gen, State.BUILDING);
        List<String> roots = jdbcTemplate.queryForList(SELECT_ROOT_DEPARTMENTS_SQL, String.class, gen.id());
        List<String> departments = jdbcTemplate.queryForList(SELECT_ALL_DEPARTMENTS_SQL, String.class, gen.id());
        jdbcTemplate.update(COMPLETE_PAGING_SQL, observedScopeJson(roots, departments),
                Timestamp.from(now()), gen.id());
    }

    @Override
    public ValidationReport validate(GenerationHandle handle) {
        gate.requireActiveTransaction("validate(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        jointCheck(handle, meta, gen, State.VALIDATING);
        if (gen.observedScope() == null) {
            throw new DirectoryContractViolationException("paging-incomplete",
                    "代 " + gen.id() + " 缺少 observed_scope（completePaging 未冻结范围证据）");
        }
        // relations-resolved：任职关系引用的部门必须在本代内（消息含 openId+deptId）
        jdbcTemplate.query(DANGLING_RELATION_SQL, rs -> {
            if (rs.next()) {
                throw new DirectoryContractViolationException("dangling-relation",
                        "代 " + gen.id() + " 人员 " + rs.getString(1) + " 引用本代外部门 " + rs.getString(2));
            }
            return null;
        }, gen.id(), gen.id());
        // counts-consistent：人员镜像 department_ids 数与关系行数一致
        Long mismatches = jdbcTemplate.queryForObject(COUNT_MISMATCH_PERSONS_SQL, Long.class, gen.id(), gen.id());
        if (mismatches != null && mismatches > 0) {
            throw new DirectoryContractViolationException("counts-inconsistent",
                    "代 " + gen.id() + " 有 " + mismatches + " 人员的部门集与关系流计数不一致");
        }
        Long[] counts = jdbcTemplate.queryForObject(MIRROR_COUNTS_SQL, (rs, i) -> new Long[]{
                rs.getLong(1), rs.getLong(2), rs.getLong(3)}, gen.id(), gen.id(), gen.id());
        long departmentCount = counts[0];
        long personCount = counts[1];
        long relationCount = counts[2];
        jdbcTemplate.update(MARK_VALIDATED_SQL,
                validationReportJson(departmentCount, personCount, relationCount), Timestamp.from(now()), gen.id());
        List<String> roots = jdbcTemplate.queryForList(OBSERVED_ROOTS_SQL, String.class, gen.id());
        List<String> departments = jdbcTemplate.queryForList(OBSERVED_DEPARTMENTS_SQL, String.class, gen.id());
        return new ValidationReport(gen.id(), new ObservedScope(roots, departments, true, true),
                (int) departmentCount, (int) personCount, (int) relationCount,
                List.of("paging-complete", "relations-resolved", "counts-consistent"));
    }

    // ------------------------------------------------------------- T7 发布

    @Override
    public DirectoryPublication publish(GenerationHandle handle) {
        gate.requireActiveTransaction("publish(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        // ② 已 PUBLISHED 同代 → 幂等 no-op：以 pub_* 四列重建与首次发布全等的结果
        //（先于联合校验——token 过期不影响已完成的发布语义；四列任一缺失 = 数据被外部改动）
        if (gen.state() == State.PUBLISHED) {
            if (gen.pubCommittedBefore() == null || gen.pubCommittedAfter() == null
                    || gen.pubAppliedSeq() == null || gen.pubFactsInserted() == null) {
                throw new DirectoryContractViolationException("publish-result-missing",
                        "代 " + gen.id() + " 已 PUBLISHED 但发布结果列缺失（数据被外部改动？）");
            }
            return new DirectoryPublication(gen.id(), gen.pubCommittedBefore(), gen.pubCommittedAfter(),
                    gen.pubAppliedSeq(), gen.pubFactsInserted().intValue());
        }
        jointCheck(handle, meta, gen, State.VALIDATED);
        if (gen.validationReport() == null) {
            throw new DirectoryContractViolationException("validation-report-missing",
                    "代 " + gen.id() + " 状态 VALIDATED 但缺 validation_report");
        }

        DirectoryScope scope = handle.scope();
        // H_before = 进入发布事务时的 committed_seq（metadata 行锁下读得）
        long committedBefore = meta.committedSeq();
        String previousGeneration = meta.activeGeneration();

        // 本代观察侧数据（不可变代内读取，READ_COMMITTED 下安全）
        Set<String> currentDepartmentIds = new HashSet<>(
                jdbcTemplate.queryForList(CURRENT_DEPARTMENT_IDS_SQL, String.class, gen.id()));
        List<PersonSnap> currentPersons = jdbcTemplate.query(CURRENT_PERSONS_SQL, (rs, i) ->
                new PersonSnap(rs.getString(1), rs.getString(2), rs.getString(3)), gen.id());
        // 根部门 parent=NULL：Map.entry 拒绝 null 值，行回调直接填 HashMap（W3-c 测试轮发现）
        Map<String, String> currentDepartmentParents = new HashMap<>();
        jdbcTemplate.query(CURRENT_DEPARTMENTS_SQL, rs -> {
            currentDepartmentParents.put(rs.getString(1), rs.getString(2));
        }, gen.id());
        Set<String> currentPersonIds = new HashSet<>();
        for (PersonSnap p : currentPersons) {
            currentPersonIds.add(p.openId());
        }

        // 事实侧现有键 + 墓碑标记（规则 A/B/E 与墓碑对峙的判定输入；M1 全量载入可接受）
        Set<String> existingFactKeys = new HashSet<>();
        Set<String> tombstoneKeys = new HashSet<>();
        for (var row : jdbcTemplate.query(SCOPE_FACT_KEYS_SQL, (rs, i) ->
                Map.entry(rs.getString(1) + ":" + rs.getString(2), rs.getBoolean(3)),
                scope.tenantKey(), scope.appScope())) {
            existingFactKeys.add(row.getKey());
            if (row.getValue()) {
                tombstoneKeys.add(row.getKey());
            }
        }

        // M1 规则 A：仅追加首见对象键（已有键零修改——规则 B/C/D/E 均不动既有行主体）
        List<Object[]> newFactRows = new ArrayList<>();
        for (PersonSnap p : currentPersons) {
            if (!existingFactKeys.contains("PERSON:" + p.openId())) {
                newFactRows.add(new Object[]{scope.tenantKey(), scope.appScope(), "PERSON", p.openId(),
                        personFactJson(p, gen.id()), gen.id(), 0L});
            }
        }
        for (var e : currentDepartmentParents.entrySet()) {
            if (!existingFactKeys.contains("DEPARTMENT:" + e.getKey())) {
                newFactRows.add(new Object[]{scope.tenantKey(), scope.appScope(), "DEPARTMENT", e.getKey(),
                        departmentFactJson(e.getKey(), e.getValue(), gen.id()), gen.id(), 0L});
            }
        }
        int factsInserted = newFactRows.size();
        long committedAfter = factsInserted > 0 ? committedBefore + 1 : committedBefore;

        // §6.4 范围完整性四条件：(i) 指纹重算即此判定；(iv) 本代 ⊇ 上一发布代部门集即下述
        // shrunk 判定；(ii) 人员归属部门 ∈ 本代 observed 被 (iv) 蕴含（上一代
        // relations-resolved ⇒ 人员部门 ⊆ 上一代部门集 ⊆ 本代部门集）；(iii) VALIDATED
        // 前置蕴含三流分页完整——故 (ii)/(iii) 无需单独实现（W3-c 执行记录登记推论）
        boolean fingerprintMismatch = !scopeFingerprint(scope).equals(gen.scopeFingerprint());
        boolean scopeShrunk = false;
        int shrunkMissing = 0;
        if (previousGeneration != null) {
            Set<String> previousDepartmentIds = new HashSet<>(jdbcTemplate.queryForList(
                    CURRENT_DEPARTMENT_IDS_SQL, String.class, previousGeneration));
            previousDepartmentIds.removeAll(currentDepartmentIds);
            shrunkMissing = previousDepartmentIds.size();
            scopeShrunk = shrunkMissing > 0;
        }

        List<Suspicion> suspicions = new ArrayList<>();
        if (fingerprintMismatch) {
            suspicions.add(new Suspicion("scope-fingerprint-mismatch",
                    "配置范围指纹与代指纹不一致（begin 后配置漂移）；本代零人员级待核对标记"));
        }
        if (scopeShrunk) {
            suspicions.add(new Suspicion("department-scope-shrunk",
                    "本代部门集小于上一发布代（缺失 " + shrunkMissing + " 个）；本代零人员级待核对标记，不产生墓碑"));
        }
        // 矩阵 15：墓碑对峙——墓碑对象在本代再次观察到 → 零修改不复活 + 范围级登记
        //（PG 层新增怀疑类型；内存替身未实现，W3-c 执行记录登记）
        int confrontations = 0;
        for (String id : currentPersonIds) {
            if (tombstoneKeys.contains("PERSON:" + id)) {
                confrontations++;
            }
        }
        for (String id : currentDepartmentParents.keySet()) {
            if (tombstoneKeys.contains("DEPARTMENT:" + id)) {
                confrontations++;
            }
        }
        if (confrontations > 0) {
            suspicions.add(new Suspicion("tombstone-confrontation",
                    "墓碑对象在本代再次观察到 " + confrontations + " 个——零修改不复活（M2 回查裁决），待人工核对"));
        }

        // 规则 E：观察复位——本代观察到的人员（非墓碑）清除待核对（再次观察到 =
        // 存在的直接证据；墓碑人员不在此清除，交 M2 回查）
        jdbcTemplate.update(CLEAR_PENDING_FOR_OBSERVED_SQL, scope.tenantKey(), scope.appScope(), gen.id());
        // 规则 B：观察缺失置人员级待核对——仅在范围证据完整（§6.4 四条件成立，
        // 且已有上一发布代）时执行；任一不成立 → 零人员级标记（行不动），范围级
        // 原因已在 suspicions 承载。候选集 = 事实侧（PERSON ∧ 非墓碑 ∧ 不在当前代
        // 镜像）——不 join 上一发布代镜像：缩容代会漏掉从未进入其镜像的缺失人员，
        // 恢复代后缺口永久化（W3C-R1）；新观察人员无事实行，天然不在候选集
        boolean scopeEvidenceComplete = !fingerprintMismatch && !scopeShrunk && previousGeneration != null;
        if (scopeEvidenceComplete) {
            jdbcTemplate.update(MARK_MISSING_PERSONS_PENDING_SQL,
                    scope.tenantKey(), scope.appScope(), gen.id());
        }

        // 规则 A 落库（updated_seq = H_after，与水位切换同事务提交）
        if (!newFactRows.isEmpty()) {
            for (Object[] row : newFactRows) {
                row[6] = committedAfter;
            }
            jdbcTemplate.batchUpdate(INSERT_FACT_SQL, newFactRows);
        }

        // ⑤ 代置 PUBLISHED（幂等结果四列持久化）+ 切 active_generation、水位推进、
        // 清租约、怀疑清单整体替换（空 = '[]'）
        jdbcTemplate.update(MARK_GENERATION_PUBLISHED_SQL, committedBefore, committedAfter,
                committedAfter, (long) factsInserted, Timestamp.from(now()), gen.id());
        jdbcTemplate.update(SWITCH_ACTIVE_GENERATION_SQL, committedAfter, committedAfter, gen.id(),
                suspicionsJson(suspicions), Timestamp.from(now()), scope.tenantKey(), scope.appScope());
        return new DirectoryPublication(gen.id(), committedBefore, committedAfter, committedAfter, factsInserted);
    }

    // ------------------------------------------------------------- 废弃

    @Override
    public void abandon(GenerationHandle handle, String reason) {
        gate.requireActiveTransaction("abandon(directory)");
        MetaRow meta = lockMetadata(handle.scope());
        GenRow gen = requireGeneration(handle);
        // token 先查（LeaseLost）：代已不属我（被接管）时拒绝废弃，保护新持有者；
        // 不校验有效期——清理自己的过期代是被允许的（端口契约唯一例外）
        if (meta.leaseToken() == null || !meta.leaseToken().equals(handle.leaseToken())) {
            throw new DirectoryLeaseLostException("代 " + handle.generationId()
                    + " 租约 token 不匹配（已被接管？）——拒绝废弃他人持有的代");
        }
        if (gen.state() == State.PUBLISHED || gen.state() == State.ABANDONED) {
            throw new DirectoryInvalidGenerationStateException(null, gen.state(),
                    "终态代不可废弃：" + gen.id() + " 已 " + gen.state());
        }
        String truncated = reason == null ? "unspecified"
                : (reason.length() > 32 ? reason.substring(0, 32) : reason);
        jdbcTemplate.update(ABANDON_GENERATION_SQL, truncated, Timestamp.from(now()), gen.id());
        jdbcTemplate.update(CLEAR_METADATA_LEASE_SQL, Timestamp.from(now()),
                handle.scope().tenantKey(), handle.scope().appScope());
    }

    // ------------------------------------------------------------- 读契约

    @Override
    public ObservedSnapshot readObservation(DirectoryScope scope) {
        // 单事务读（调用方事务）：READ_COMMITTED 下按固定 generation_id 读不可变代
        // 成立（§6.6）。读方法无 MANDATORY 门禁。
        MetaLite meta;
        try {
            meta = jdbcTemplate.queryForObject(READ_METADATA_SQL, (rs, i) -> new MetaLite(
                    rs.getString(1), rs.getLong(2), rs.getLong(3)), scope.tenantKey(), scope.appScope());
        } catch (EmptyResultDataAccessException e) {
            meta = null;
        }
        List<ScopeSuspicion> suspicions = readSuspicions(scope);
        if (meta == null || meta.activeGeneration() == null || meta.activeGeneration().isEmpty()) {
            // 从未发布过：空观察快照形态（activeGenerationId 空串，§6.6 端口契约）
            long committed = meta == null ? 0L : meta.committedSeq();
            long applied = meta == null ? 0L : meta.appliedSeq();
            return new ObservedSnapshot("", committed, applied,
                    new ObservedScope(List.of(), List.of(), false, false), 0, 0, suspicions);
        }
        String generationId = meta.activeGeneration();
        List<String> roots = jdbcTemplate.queryForList(OBSERVED_ROOTS_SQL, String.class, generationId);
        List<String> departments = jdbcTemplate.queryForList(OBSERVED_DEPARTMENTS_SQL, String.class, generationId);
        Long[] counts = jdbcTemplate.queryForObject(ACTIVE_MIRROR_COUNTS_SQL, (rs, i) -> new Long[]{
                rs.getLong(1), rs.getLong(2)}, generationId, generationId);
        return new ObservedSnapshot(generationId, meta.committedSeq(), meta.appliedSeq(),
                new ObservedScope(roots, departments, true, true),
                counts[0].intValue(), counts[1].intValue(), suspicions);
    }

    @Override
    public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
        return jdbcTemplate.query(READ_FACTS_SQL, (rs, i) -> new ProtectedFactView(
                ProtectedFactView.ObjectType.valueOf(rs.getString(1)),
                rs.getString(2),
                rs.getLong(3),
                rs.getBoolean(4),
                ProtectedFactView.Origin.valueOf(rs.getString(5)),
                rs.getString(6),
                rs.getBoolean(7),
                rs.getString(8),
                rs.getLong(9)), scope.tenantKey(), scope.appScope());
    }

    // ------------------------------------------------------------- 内部设施

    /**
     * T6a ①：第一把锁——行不存在则同事务自举（committed_seq=0，ON CONFLICT 汇合
     * 并发首插，见类 javadoc 实现偏差说明），随后 SELECT FOR UPDATE 取权威值。
     */
    private MetaRow lockMetadata(DirectoryScope scope) {
        jdbcTemplate.update(BOOTSTRAP_METADATA_SQL, scope.tenantKey(), scope.appScope(),
                Timestamp.from(now()));
        return jdbcTemplate.queryForObject(LOCK_METADATA_SQL, (rs, i) -> new MetaRow(
                rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()),
                scope.tenantKey(), scope.appScope());
    }

    private List<GenRow> queryOpenGeneration(DirectoryScope scope) {
        return jdbcTemplate.query(SELECT_OPEN_GENERATION_SQL, PgDirectoryMirrorStore::mapGenRow,
                scope.tenantKey(), scope.appScope());
    }

    private GenRow requireGeneration(GenerationHandle handle) {
        List<GenRow> rows = jdbcTemplate.query(SELECT_GENERATION_BY_ID_SQL,
                (rs, i) -> new GenRow(rs.getString(1), rs.getString(2), rs.getString(3),
                        State.valueOf(rs.getString(4)), rs.getLong(5), rs.getString(6),
                        rs.getString(7), rs.getString(8), nullableLong(rs, 9), nullableLong(rs, 10),
                        nullableLong(rs, 11), nullableLong(rs, 12)),
                handle.generationId());
        if (rows.isEmpty()) {
            throw new DirectoryLeaseLostException("代不存在：" + handle.generationId());
        }
        GenRow gen = rows.get(0);
        if (!gen.tenantId().equals(handle.scope().tenantKey())
                || !gen.appScope().equals(handle.scope().appScope())) {
            throw new DirectoryLeaseLostException("代 " + gen.id() + " 不属于 scope " + handle.scope());
        }
        return gen;
    }

    /** 联合校验（顺序 = token → 有效期 → 状态；token/有效期以 metadata 权威值为准，等于 now 即过期）。 */
    private void jointCheck(GenerationHandle handle, MetaRow meta, GenRow gen, State... allowedStates) {
        if (meta.leaseToken() == null || !meta.leaseToken().equals(handle.leaseToken())) {
            throw new DirectoryLeaseLostException("代 " + handle.generationId()
                    + " 联合校验失败：租约 token 不匹配（已被接管或已释放）");
        }
        if (meta.leaseExpiresAt() == null || !meta.leaseExpiresAt().isAfter(now())) {
            throw new DirectoryLeaseExpiredException("代 " + handle.generationId()
                    + " 联合校验失败：租约已过期（" + meta.leaseExpiresAt() + "）");
        }
        for (State allowed : allowedStates) {
            if (gen.state() == allowed) {
                return;
            }
        }
        throw new DirectoryInvalidGenerationStateException(
                allowedStates.length == 1 ? allowedStates[0] : null, gen.state(),
                "代 " + gen.id() + " 状态前置不满足：需要 " + List.of(allowedStates) + "，实际 " + gen.state());
    }

    private List<ScopeSuspicion> readSuspicions(DirectoryScope scope) {
        // scope_suspicion 为 NULL 时 jsonb_array_elements 返回零行——从未登记即空清单
        return jdbcTemplate.query(SCOPE_SUSPICIONS_SQL, (rs, i) -> new ScopeSuspicion(
                rs.getString(1), rs.getString(2)), scope.tenantKey(), scope.appScope());
    }

    private Instant now() {
        return clock.now();
    }

    private static Long nullableLong(java.sql.ResultSet rs, int column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static GenRow mapGenRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new GenRow(rs.getString(1), null, null, State.valueOf(rs.getString(2)),
                rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6),
                nullableLong(rs, 7), nullableLong(rs, 8), nullableLong(rs, 9), nullableLong(rs, 10));
    }

    // ------------------------------------------------------------- 标识与 JSON 发射

    private String newUlid(Instant at) {
        long timestamp = at.toEpochMilli(); // 48bit 毫秒时间戳 → 10 字符（低位在前填充）
        byte[] randomness = new byte[10];
        random.nextBytes(randomness);
        BigInteger value = new BigInteger(1, randomness);
        char[] chars = new char[26];
        for (int i = 9; i >= 0; i--) {
            chars[i] = CROCKFORD_32[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
        for (int i = 25; i >= 10; i--) {
            chars[i] = CROCKFORD_32[value.and(BigInteger.valueOf(0x1F)).intValue()];
            value = value.shiftRight(5);
        }
        return new String(chars);
    }

    private String newLeaseToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return toHex(bytes);
    }

    static String scopeFingerprint(DirectoryScope scope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest((scope.tenantKey() + "\n" + scope.appScope())
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256（不可能：标准算法）", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static String observedScopeJson(List<String> roots, List<String> departments) {
        return "{\"rootDepartmentIds\":" + jsonArray(roots)
                + ",\"departmentIds\":" + jsonArray(departments)
                + ",\"departmentPagingComplete\":true,\"personPagingComplete\":true}";
    }

    private static String validationReportJson(long departments, long persons, long relations) {
        return "{\"checks\":[\"paging-complete\",\"relations-resolved\",\"counts-consistent\"]"
                + ",\"departmentCount\":" + departments
                + ",\"personCount\":" + persons
                + ",\"relationCount\":" + relations + "}";
    }

    private static String personFactJson(PersonSnap person, String generationId) {
        StringBuilder json = new StringBuilder("{\"objectType\":\"PERSON\",\"externalId\":");
        jsonString(json, person.openId());
        if (person.name() != null) {
            json.append(",\"name\":");
            jsonString(json, person.name());
        }
        if (person.enName() != null) {
            json.append(",\"enName\":");
            jsonString(json, person.enName());
        }
        json.append(",\"firstSeenGeneration\":");
        jsonString(json, generationId);
        return json.append('}').toString();
    }

    private static String departmentFactJson(String departmentId, String parentDepartmentId, String generationId) {
        StringBuilder json = new StringBuilder("{\"objectType\":\"DEPARTMENT\",\"externalId\":");
        jsonString(json, departmentId);
        if (parentDepartmentId != null) {
            json.append(",\"parentDepartmentId\":");
            jsonString(json, parentDepartmentId);
        }
        json.append(",\"firstSeenGeneration\":");
        jsonString(json, generationId);
        return json.append('}').toString();
    }

    private static String suspicionsJson(List<Suspicion> suspicions) {
        if (suspicions.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < suspicions.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"type\":");
            jsonString(json, suspicions.get(i).type());
            json.append(",\"detail\":");
            jsonString(json, suspicions.get(i).detail());
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static String jsonArray(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            jsonString(json, values.get(i));
        }
        return json.append(']').toString();
    }

    /** 最小转义：双引号、反斜杠、控制字符（读取解析全部由 PG jsonb 承担）。 */
    private static void jsonString(StringBuilder target, String value) {
        target.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                default -> {
                    if (c < 0x20) {
                        target.append(String.format("\\u%04x", (int) c));
                    } else {
                        target.append(c);
                    }
                }
            }
        }
        target.append('"');
    }

    // ------------------------------------------------------------- 行记录

    private record MetaRow(String activeGeneration, long committedSeq, long appliedSeq,
                           String leaseToken, Instant leaseExpiresAt) {
    }

    private record MetaLite(String activeGeneration, long committedSeq, long appliedSeq) {
    }

    private record GenRow(String id, String tenantId, String appScope, State state, long startSeq,
                          String scopeFingerprint, String observedScope, String validationReport,
                          Long pubCommittedBefore, Long pubCommittedAfter,
                          Long pubAppliedSeq, Long pubFactsInserted) {
    }

    private record PersonSnap(String openId, String name, String enName) {
    }

    private record Suspicion(String type, String detail) {
    }
}
