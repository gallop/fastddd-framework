package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EffectiveSnapshot;
import com.gallop.fastddd.identity.core.domain.EffectiveSnapshot.EffectiveDepartment;
import com.gallop.fastddd.identity.core.domain.EffectiveSnapshot.EffectivePerson;
import com.gallop.fastddd.identity.core.port.DirectoryEffectiveView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 有效视图 PG 参考适配器（W5 设计 §8.1 四要点 a–d，W5-c 交付——M2 首个生产实现）。
 *
 * <p><b>读取协议（七语句同事务）</b>：调用方事务在场则汇入；否则以
 * {@link TransactionTemplate} 自开只读短事务（READ_COMMITTED 语句快照下，
 * 事实写入与水位推进同事务提交，阈值过滤 {@code updated_seq <= applied_seq}
 * 保证不读到"已提交但不可见"的未生效事实——要点 a）。语句顺序固定：
 * ① 拒绝行（要点 c：仅 {@code external_id_type='open_id'} 且未清除，排除对应
 * PERSON 对象——O-1 同批紧邻先于事实读取）→ ② metadata（active_generation +
 * applied_seq，同事务首读）→ ③④⑤ 活动代 raw 部门/人员/关系 → ⑥ 阈值内事实
 * （含墓碑标记）→ ⑦ 阈值内事实人员 departmentIds（jsonb 服务端展开）。</p>
 *
 * <p><b>叠加规则（要点 b/d）</b>：对象成员 = raw 键 ∪ 阈值内事实键（union 兜底：
 * FULL_SCAN 事实对象不在 raw 镜像亦纳入）；阈值内墓碑 → 对象整体排除（raw 侧
 * 同样剔除）；阈值内事实 origin ∈ {RECHECK, INCREMENT} → <b>事实胜出</b>
 * （内容替换：displayName/parentDepartmentId/name 取事实值，人员 departmentIds
 * 以事实集为准——事实为该对象最新权威内容）；其余 → raw 胜出。人员
 * departmentIds：事实胜出者取事实集（⑦），raw 人员取关系镜像（⑤）。要点 c
 * 拒绝排除在叠加后施加（仅 PERSON）。</p>
 *
 * <p><b>形态约定</b>：从未发布（无 metadata 行或 active_generation 空）→
 * activeGenerationId 空串、双清单空（与 readObservation 同式，不冒充）。</p>
 *
 * <p><b>要点 c 结构化日志（§8.1(c)，R5-P3-1 承接）</b>：拒绝排除面实际生效
 * （本快照存在被排除的 open_id 型未清除拒绝行）时输出一条 INFO 结构化日志
 * （scope/activeGeneration/appliedSeq/excludedOpenIds/excludedCount）——静默
 * 排除 = 消费侧不可观测的人员失踪；排除零生效不输出（无噪声）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PgDirectoryEffectiveView implements DirectoryEffectiveView {

    private static final Logger log = LoggerFactory.getLogger(PgDirectoryEffectiveView.class);

    /** 要点 c（O-1：同批紧邻、先于事实读取）——仅 open_id 型且未清除。 */
    private static final String REJECTED_OPEN_IDS_SQL = """
            SELECT external_id FROM external_rejection
            WHERE tenant_id = ? AND app_scope = ? AND external_id_type = 'open_id' AND cleared_at IS NULL
            """;

    private static final String METADATA_SQL = """
            SELECT active_generation, applied_seq FROM directory_publish_metadata
            WHERE tenant_id = ? AND app_scope = ?
            """;

    private static final String RAW_DEPARTMENTS_SQL = """
            SELECT department_id, parent_department_id, name
            FROM directory_department_mirror WHERE generation_id = ?
            ORDER BY department_id
            """;

    private static final String RAW_PERSONS_SQL = """
            SELECT open_id, name, en_name
            FROM directory_person_mirror WHERE generation_id = ?
            ORDER BY open_id
            """;

    private static final String RAW_RELATIONS_SQL = """
            SELECT open_id, department_id
            FROM directory_relation_mirror WHERE generation_id = ?
            """;

    /** 阈值内事实（要点 a 阈值 + 墓碑标记；显示字段 SQL 侧 jsonb 提取，Java 零解析）。 */
    private static final String FACTS_SQL = """
            SELECT object_type, external_id, tombstone, origin,
                   fact->>'name' AS name, fact->>'enName' AS en_name, fact->>'parentDepartmentId' AS parent_id
            FROM directory_fact
            WHERE tenant_id = ? AND app_scope = ? AND updated_seq <= ?
            """;

    private static final String FACT_DEPT_IDS_SQL = """
            SELECT f.external_id, d.dept_id
            FROM directory_fact f, jsonb_array_elements_text(f.fact->'departmentIds') AS d(dept_id)
            WHERE f.tenant_id = ? AND f.app_scope = ? AND f.object_type = 'PERSON'
              AND f.updated_seq <= ? AND f.tombstone = false
              AND f.origin IN ('RECHECK', 'INCREMENT')
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readTx;

    public PgDirectoryEffectiveView(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.readTx = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                        jdbcTemplate.getDataSource()));
        this.readTx.setReadOnly(true);
    }

    @Override
    public EffectiveSnapshot readEffective(DirectoryScope scope) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return doRead(scope);
        }
        return readTx.execute(status -> doRead(scope));
    }

    private EffectiveSnapshot doRead(DirectoryScope scope) {
        // ① 拒绝清单（要点 c）——首位语句
        Set<String> rejected = new HashSet<>(jdbcTemplate.queryForList(REJECTED_OPEN_IDS_SQL,
                String.class, scope.tenantKey(), scope.appScope()));

        // ② metadata：活动代 + applied_seq（同事务首读，要点 a）
        String[] meta = jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(METADATA_SQL);
            ps.setString(1, scope.tenantKey());
            ps.setString(2, scope.appScope());
            return ps;
        }, rs -> rs.next() ? new String[]{rs.getString(1), String.valueOf(rs.getLong(2))} : null);
        if (meta == null || meta[0] == null || meta[0].isEmpty()) {
            return new EffectiveSnapshot(scope, "", meta == null ? 0L : Long.parseLong(meta[1]),
                    List.of(), List.of());
        }
        String generationId = meta[0];
        long appliedSeq = Long.parseLong(meta[1]);

        // ③④⑤ 活动代 raw 三流
        Map<String, EffectiveDepartment> departments = new TreeMap<>();
        jdbcTemplate.query(RAW_DEPARTMENTS_SQL, rs -> {
            departments.put(rs.getString(1), new EffectiveDepartment(
                    rs.getString(1), rs.getString(2), rs.getString(3)));
        }, generationId);
        Map<String, String[]> rawPersons = new TreeMap<>(); // open_id -> [name, enName]
        jdbcTemplate.query(RAW_PERSONS_SQL, rs -> {
            rawPersons.put(rs.getString(1), new String[]{rs.getString(2), rs.getString(3)});
        }, generationId);
        Map<String, List<String>> rawRelations = new HashMap<>();
        jdbcTemplate.query(RAW_RELATIONS_SQL, rs -> {
            rawRelations.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, generationId);

        // ⑥ 阈值内事实：墓碑 → 排除（raw 侧同剔）；RECHECK/INCREMENT → 事实胜出
        Map<String, EffectiveDepartment> deptOverlay = new HashMap<>();
        Map<String, String[]> factPersonOverlay = new HashMap<>(); // open_id -> [name, enName]
        List<String> factOnlyPersons = new ArrayList<>();
        List<String> factOnlyDepartments = new ArrayList<>();
        jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(FACTS_SQL);
            ps.setString(1, scope.tenantKey());
            ps.setString(2, scope.appScope());
            ps.setLong(3, appliedSeq);
            return ps;
        }, rs -> {
            String objectType = rs.getString("object_type");
            String externalId = rs.getString("external_id");
            boolean tombstone = rs.getBoolean("tombstone");
            String origin = rs.getString("origin");
            boolean factWins = !tombstone
                    && ("RECHECK".equals(origin) || "INCREMENT".equals(origin));
            if ("PERSON".equals(objectType)) {
                if (tombstone) {
                    rawPersons.remove(externalId); // 阈值内墓碑：对象整体排除（要点 d）
                    return;
                }
                if (factWins) {
                    factPersonOverlay.put(externalId, new String[]{
                            rs.getString("name"), rs.getString("en_name")});
                } else if (!rawPersons.containsKey(externalId)) {
                    factOnlyPersons.add(externalId); // union 兜底：FULL_SCAN 事实对象
                }
            } else if ("DEPARTMENT".equals(objectType)) {
                if (tombstone) {
                    departments.remove(externalId);
                    return;
                }
                if (factWins) {
                    deptOverlay.put(externalId, new EffectiveDepartment(
                            externalId, rs.getString("parent_id"), rs.getString("name")));
                } else if (!departments.containsKey(externalId)) {
                    factOnlyDepartments.add(externalId);
                }
            }
            // 其余 object_type 不在有效视图面，无操作
        });

        // ⑦ 阈值内事实人员 departmentIds（事实胜出者的事实集）
        Map<String, List<String>> factDeptIds = new HashMap<>();
        jdbcTemplate.query(con -> {
            var ps = con.prepareStatement(FACT_DEPT_IDS_SQL);
            ps.setString(1, scope.tenantKey());
            ps.setString(2, scope.appScope());
            ps.setLong(3, appliedSeq);
            return ps;
        }, rs -> {
            factDeptIds.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
        });

        // 叠加 + 要点 c 拒绝排除（仅 PERSON）+ 排序输出
        departments.putAll(deptOverlay);
        for (String id : factOnlyDepartments) {
            departments.putIfAbsent(id, new EffectiveDepartment(id, null, null));
        }
        List<EffectiveDepartment> effectiveDepartments = List.copyOf(departments.values());

        List<EffectivePerson> effectivePersons = new ArrayList<>();
        List<String> excludedByRejection = rejected.isEmpty() ? List.of() : new ArrayList<>();
        TreeMap<String, String[]> merged = new TreeMap<>(rawPersons);
        for (var entry : factPersonOverlay.entrySet()) {
            merged.put(entry.getKey(), entry.getValue()); // 事实胜出：替换显示内容
        }
        for (String id : factOnlyPersons) {
            merged.putIfAbsent(id, new String[]{null, null});
        }
        for (var entry : merged.entrySet()) {
            String openId = entry.getKey();
            if (rejected.contains(openId)) {
                ((List<String>) excludedByRejection).add(openId); // 要点 c：open_id 型未清除拒绝 → 排除（记观测面）
                continue;
            }
            String displayName = entry.getValue()[0] != null ? entry.getValue()[0] : entry.getValue()[1];
            List<String> deptIds = factPersonOverlay.containsKey(openId)
                    ? factDeptIds.getOrDefault(openId, List.of())
                    : rawRelations.getOrDefault(openId, List.of());
            effectivePersons.add(new EffectivePerson(openId, displayName, deptIds));
        }
        if (!excludedByRejection.isEmpty()) {
            // §8.1(c)/R5-P3-1：拒绝排除生效面结构化日志——静默排除不可观测
            log.info("[directory-effective-view] rejection-exclusion scope={} activeGeneration={} "
                            + "appliedSeq={} excludedCount={} excludedOpenIds={}",
                    scope, generationId, appliedSeq, excludedByRejection.size(), excludedByRejection);
        }
        return new EffectiveSnapshot(scope, generationId, appliedSeq,
                effectiveDepartments, List.copyOf(effectivePersons));
    }
}
