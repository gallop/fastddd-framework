package com.gallop.fastddd.identity.core.directory;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryGeneration;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryInvalidGenerationStateException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseLostException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * W3-a 编排测试用内存镜像存储（<b>测试替身，非生产实现</b>）：
 * 按 W3 设计 v3 §4/§5.2/§6 实现 M1 语义——代状态机、租约联合校验、
 * 追加式事实层（规则 A–E）、H_before/H_after 水位（空差异不推进）、
 * pending_confirm 待核对与范围级登记（可查询承载）。
 *
 * <p><b>不证明</b>：PG 行锁/部分唯一索引/条件更新原子性/fencing 时序——
 * 这些属 W3-c 真实存储验收（复评钉住事项 ⑤）。本类只验证编排与协议语义。</p>
 *
 * <p>可注入：时钟（租约过期）、各操作失败钩子（回滚/抑制语义测试）。</p>
 */
public final class InMemoryDirectoryMirrorStore implements DirectoryMirrorStore {

    /** 测试种子用事实记录形态（可预置 tombstone——M1 生产无写入方，矩阵 15 同法）。 */
    public static final class SeedFact {
        public final ProtectedFactView.ObjectType objectType;
        public final String externalId;
        public final long localRevision;
        public final boolean tombstone;

        public SeedFact(ProtectedFactView.ObjectType objectType, String externalId,
                        long localRevision, boolean tombstone) {
            this.objectType = objectType;
            this.externalId = externalId;
            this.localRevision = localRevision;
            this.tombstone = tombstone;
        }
    }

    private static final class Fact {
        long localRevision = 1;
        boolean tombstone;
        ProtectedFactView.Origin origin = ProtectedFactView.Origin.FULL_SCAN;
        String changeRef;
        boolean pendingConfirm;
        String sourceGeneration;
        long updatedSeq;

        ProtectedFactView view(ProtectedFactView.ObjectType type, String id) {
            return new ProtectedFactView(type, id, localRevision, tombstone, origin,
                    changeRef, pendingConfirm, sourceGeneration, updatedSeq);
        }
    }

    private static final class Gen {
        final String id;
        final DirectoryScope scope;
        DirectoryGeneration.State state = DirectoryGeneration.State.BUILDING;
        final List<DirectoryDepartment> departments = new ArrayList<>();
        final Map<String, DirectoryPerson> personsByOpenId = new LinkedHashMap<>();
        String abandonReason;
        ValidationReport report;
        DirectoryPublication publication;

        Gen(String id, DirectoryScope scope) {
            this.id = id;
            this.scope = scope;
        }

        boolean isTerminal() {
            return state == DirectoryGeneration.State.PUBLISHED
                    || state == DirectoryGeneration.State.ABANDONED;
        }
    }

    private static final class Meta {
        String activeGenerationId;
        long committedSeq;
        long appliedSeq;
        String leaseToken;
        Instant leaseExpiresAt;
    }

    private static final class RawSnapshot {
        final Set<String> deptIds;
        final Set<String> personIds;

        RawSnapshot(Set<String> deptIds, Set<String> personIds) {
            this.deptIds = deptIds;
            this.personIds = personIds;
        }
    }

    private final Map<DirectoryScope, Meta> metadata = new LinkedHashMap<>();
    private final Map<DirectoryScope, Map<String, Gen>> generations = new LinkedHashMap<>();
    private final Map<DirectoryScope, Map<String, Fact>> facts = new LinkedHashMap<>();
    private final Map<DirectoryScope, RawSnapshot> publishedRaw = new LinkedHashMap<>();
    private final Map<DirectoryScope, List<ObservedSnapshot.ScopeSuspicion>> suspicions = new LinkedHashMap<>();
    private final AtomicLong idSeq = new AtomicLong();

    private final Duration leaseTtl;
    private Supplier<Instant> clock = Instant::now;

    /** 失败注入钩子（发布回滚/清理抑制语义测试）；抛出即模拟对应事务中途失败。 */
    public Runnable publishFailureHook;
    public Consumer<GenerationHandle> writeBatchFailureHook;
    public Runnable validateFailureHook;
    public Runnable abandonFailureHook;

    public InMemoryDirectoryMirrorStore(Duration leaseTtl) {
        this.leaseTtl = leaseTtl;
    }

    public void setClock(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /** 预置受保护事实（测试种子；生产 M1 无 tombstone 写入方，矩阵 15 同法验证不被清除）。 */
    public void seedFact(DirectoryScope scope, SeedFact seed) {
        Fact f = new Fact();
        f.localRevision = seed.localRevision;
        f.tombstone = seed.tombstone;
        f.origin = seed.tombstone
                ? ProtectedFactView.Origin.RECHECK : ProtectedFactView.Origin.FULL_SCAN;
        f.sourceGeneration = "seeded";
        facts.computeIfAbsent(scope, s -> new LinkedHashMap<>())
                .put(key(seed.objectType, seed.externalId), f);
    }

    public DirectoryGeneration.State stateOf(DirectoryScope scope, String generationId) {
        Gen gen = generations.getOrDefault(scope, Map.of()).get(generationId);
        return gen == null ? null : gen.state;
    }

    public String abandonReasonOf(DirectoryScope scope, String generationId) {
        Gen gen = generations.getOrDefault(scope, Map.of()).get(generationId);
        return gen == null ? null : gen.abandonReason;
    }

    public long committedSeq(DirectoryScope scope) {
        Meta meta = metadata.get(scope);
        return meta == null ? 0 : meta.committedSeq;
    }

    /** 测试观测面：该 scope 的全部代 id（按创建序）。 */
    public List<String> generationIds(DirectoryScope scope) {
        return List.copyOf(generations.getOrDefault(scope, Map.of()).keySet());
    }

    public long appliedSeq(DirectoryScope scope) {
        Meta meta = metadata.get(scope);
        return meta == null ? 0 : meta.appliedSeq;
    }

    public String activeGenerationId(DirectoryScope scope) {
        Meta meta = metadata.get(scope);
        return meta == null ? null : meta.activeGenerationId;
    }

    /** 模拟另一实例接管（测试迟到操作语义）：废弃当前非终态代并换新持有者 token。 */
    public void simulateTakeover(DirectoryScope scope) {
        Meta meta = metadata.computeIfAbsent(scope, s -> new Meta());
        for (Gen gen : generations.computeIfAbsent(scope, s -> new LinkedHashMap<>()).values()) {
            if (!gen.isTerminal()) {
                gen.state = DirectoryGeneration.State.ABANDONED;
                gen.abandonReason = "lease-takeover";
            }
        }
        meta.leaseToken = "taken-over-token";
        meta.leaseExpiresAt = clock.get().plus(leaseTtl);
    }

    @Override
    public GenerationHandle beginGeneration(DirectoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        Meta meta = metadata.computeIfAbsent(scope, s -> new Meta());
        Map<String, Gen> gens = generations.computeIfAbsent(scope, s -> new LinkedHashMap<>());
        Instant now = clock.get();
        for (Gen gen : gens.values()) {
            if (gen.isTerminal()) {
                continue;
            }
            boolean leaseValid = meta.leaseToken != null && meta.leaseExpiresAt != null
                    && meta.leaseExpiresAt.isAfter(now);
            if (leaseValid) {
                throw new DirectoryLeaseHeldException("同步进行中（持有代 " + gen.id + "）");
            }
            // 接管：任意非终态 + 租约失效 → ABANDONED('lease-takeover') + 新代，同事务
            gen.state = DirectoryGeneration.State.ABANDONED;
            gen.abandonReason = "lease-takeover";
        }
        Gen gen = new Gen("G" + idSeq.incrementAndGet(), scope);
        gens.put(gen.id, gen);
        meta.leaseToken = "T" + idSeq.incrementAndGet();
        meta.leaseExpiresAt = now.plus(leaseTtl);
        return new GenerationHandle(scope, gen.id, meta.leaseToken);
    }

    @Override
    public Lease renewLease(GenerationHandle handle) {
        Gen gen = requireGeneration(handle);
        jointCheck(handle, gen, DirectoryGeneration.State.BUILDING,
                DirectoryGeneration.State.VALIDATING);
        Meta meta = metadata.get(handle.scope());
        meta.leaseExpiresAt = clock.get().plus(leaseTtl);
        return new Lease(meta.leaseToken, meta.leaseExpiresAt);
    }

    @Override
    public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
        Gen gen = requireGeneration(handle);
        jointCheck(handle, gen, DirectoryGeneration.State.BUILDING);
        if (writeBatchFailureHook != null) {
            writeBatchFailureHook.accept(handle);
        }
        gen.departments.addAll(batch.departments());
        for (DirectoryPerson person : batch.persons()) {
            gen.personsByOpenId.putIfAbsent(person.openId(), person);
        }
    }

    @Override
    public void completePaging(GenerationHandle handle) {
        Gen gen = requireGeneration(handle);
        jointCheck(handle, gen, DirectoryGeneration.State.BUILDING);
        gen.state = DirectoryGeneration.State.VALIDATING;
    }

    @Override
    public ValidationReport validate(GenerationHandle handle) {
        Gen gen = requireGeneration(handle);
        jointCheck(handle, gen, DirectoryGeneration.State.VALIDATING);
        List<String> checks = new ArrayList<>();
        checks.add("paging-complete");
        Set<String> deptIds = new HashSet<>();
        for (DirectoryDepartment dept : gen.departments) {
            deptIds.add(dept.departmentId());
        }
        for (DirectoryPerson person : gen.personsByOpenId.values()) {
            for (String deptId : person.departmentIds()) {
                if (!deptIds.contains(deptId)) {
                    throw new DirectoryContractViolationException("dangling-relation",
                            "人员 " + person.openId() + " 引用本代外部门 " + deptId);
                }
            }
        }
        checks.add("relations-resolved");
        int relations = gen.personsByOpenId.values().stream()
                .mapToInt(p -> p.departmentIds().size()).sum();
        checks.add("counts-consistent");
        ObservedSnapshot.ObservedScope observedScope = new ObservedSnapshot.ObservedScope(
                gen.departments.stream().filter(d -> d.parentDepartmentId() == null)
                        .map(DirectoryDepartment::departmentId).toList(),
                List.copyOf(deptIds), true, true);
        if (validateFailureHook != null) {
            validateFailureHook.run();
        }
        gen.state = DirectoryGeneration.State.VALIDATED;
        gen.report = new ValidationReport(gen.id, observedScope,
                gen.departments.size(), gen.personsByOpenId.size(), relations, checks);
        return gen.report;
    }

    @Override
    public DirectoryPublication publish(GenerationHandle handle) {
        Gen gen = requireGeneration(handle);
        Meta meta = metadata.computeIfAbsent(handle.scope(), s -> new Meta());
        // 幂等：已 PUBLISHED 同代 → no-op 成功（租约已在首次发布时清理，不再校验）
        if (gen.state == DirectoryGeneration.State.PUBLISHED) {
            return gen.publication;
        }
        jointCheck(handle, gen, DirectoryGeneration.State.VALIDATED);

        long hBefore = meta.committedSeq;
        Map<String, Fact> scopeFacts = facts.computeIfAbsent(handle.scope(), s -> new LinkedHashMap<>());
        RawSnapshot prev = publishedRaw.get(handle.scope());

        // ④′ M1 = 仅追加新增（规则 A）；已有键零修改（规则 B/C/D/E）
        Set<String> observedPersonIds = new HashSet<>();
        for (DirectoryPerson person : gen.personsByOpenId.values()) {
            observedPersonIds.add(person.openId());
        }
        Set<String> observedDeptIds = new HashSet<>();
        for (DirectoryDepartment dept : gen.departments) {
            observedDeptIds.add(dept.departmentId());
        }
        List<Map.Entry<String, Fact>> newFacts = new ArrayList<>();
        for (String openId : observedPersonIds) {
            String k = key(ProtectedFactView.ObjectType.PERSON, openId);
            if (!scopeFacts.containsKey(k)) {
                Fact f = new Fact();
                f.sourceGeneration = gen.id;
                f.updatedSeq = hBefore + 1;
                newFacts.add(Map.entry(k, f));
            }
        }
        for (String deptId : observedDeptIds) {
            String k = key(ProtectedFactView.ObjectType.DEPARTMENT, deptId);
            if (!scopeFacts.containsKey(k)) {
                Fact f = new Fact();
                f.sourceGeneration = gen.id;
                f.updatedSeq = hBefore + 1;
                newFacts.add(Map.entry(k, f));
            }
        }
        // 规则 B：观察缺失 → 范围证据完整时仅置 pending_confirm（不动其余字段、不推水位）；
        // 范围存疑（scope-suspect）→ 人员行完全不动，登记范围级待核对（可查询承载）
        boolean scopeEvidenceComplete = scopeEvidenceComplete(prev, gen);
        List<String> missingToConfirm = new ArrayList<>();
        if (scopeEvidenceComplete && prev != null) {
            for (String prevOpenId : prev.personIds) {
                if (!observedPersonIds.contains(prevOpenId)) {
                    Fact f = scopeFacts.get(key(ProtectedFactView.ObjectType.PERSON, prevOpenId));
                    if (f != null && !f.tombstone) {
                        missingToConfirm.add(key(ProtectedFactView.ObjectType.PERSON, prevOpenId));
                    }
                }
            }
        }
        List<ObservedSnapshot.ScopeSuspicion> suspicionList = new ArrayList<>();
        if (prev != null && !observedDeptIds.containsAll(prev.deptIds)) {
            suspicionList.add(new ObservedSnapshot.ScopeSuspicion("department-scope-shrunk",
                    "本代部门集小于上一发布代（缺失 " + countMissing(prev.deptIds, observedDeptIds) + " 个）"));
        }

        // 水位（V2-03 冻结口径）：有 INSERT → H_after = H_before+1；否则 H_after = H_before
        int inserted = newFacts.size();
        long hAfter = inserted > 0 ? hBefore + 1 : hBefore;
        DirectoryPublication publication = new DirectoryPublication(
                gen.id, hBefore, hAfter, hAfter, inserted);

        if (publishFailureHook != null) {
            // 模拟 T7 中途失败：全部变更延迟到钩子之后 → 抛出即全部回滚零残留
            publishFailureHook.run();
        }

        for (Map.Entry<String, Fact> entry : newFacts) {
            scopeFacts.put(entry.getKey(), entry.getValue());
        }
        // §6.4 复位：再次观察到活跃（非墓碑）人员 → 清 pending_confirm——复位不推水位、不动其余字段
        for (String openId : observedPersonIds) {
            Fact f = scopeFacts.get(key(ProtectedFactView.ObjectType.PERSON, openId));
            if (f != null && !f.tombstone) {
                f.pendingConfirm = false;
            }
        }
        for (String k : missingToConfirm) {
            scopeFacts.get(k).pendingConfirm = true;
        }
        suspicions.put(handle.scope(), suspicionList);
        meta.committedSeq = hAfter;
        meta.appliedSeq = hAfter;
        meta.activeGenerationId = gen.id;
        meta.leaseToken = null;
        meta.leaseExpiresAt = null;
        gen.state = DirectoryGeneration.State.PUBLISHED;
        gen.publication = publication;
        publishedRaw.put(handle.scope(), new RawSnapshot(observedDeptIds, observedPersonIds));
        return publication;
    }

    private boolean scopeEvidenceComplete(RawSnapshot prev, Gen gen) {
        if (prev == null) {
            return true;
        }
        Set<String> deptIds = new HashSet<>();
        for (DirectoryDepartment dept : gen.departments) {
            deptIds.add(dept.departmentId());
        }
        return deptIds.containsAll(prev.deptIds);
    }

    private static int countMissing(Set<String> prev, Set<String> current) {
        int n = 0;
        for (String id : prev) {
            if (!current.contains(id)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void abandon(GenerationHandle handle, String reason) {
        Gen gen = requireGeneration(handle);
        Meta meta = metadata.get(handle.scope());
        if (meta == null || !Objects.equals(meta.leaseToken, handle.leaseToken())) {
            throw new DirectoryLeaseLostException("token 不匹配（被接管）——拒绝废弃，保护新持有者");
        }
        if (gen.isTerminal()) {
            throw new DirectoryInvalidGenerationStateException(null, gen.state,
                    "终态代不可废弃（" + gen.id + "，state=" + gen.state + "）");
        }
        if (abandonFailureHook != null) {
            abandonFailureHook.run();
        }
        gen.state = DirectoryGeneration.State.ABANDONED;
        gen.abandonReason = reason;
        meta.leaseToken = null;
        meta.leaseExpiresAt = null;
    }

    @Override
    public ObservedSnapshot readObservation(DirectoryScope scope) {
        Meta meta = metadata.computeIfAbsent(scope, s -> new Meta());
        RawSnapshot raw = publishedRaw.get(scope);
        List<ObservedSnapshot.ScopeSuspicion> list = suspicions.getOrDefault(scope, List.of());
        return new ObservedSnapshot(
                meta.activeGenerationId == null ? "" : meta.activeGenerationId,
                meta.committedSeq, meta.appliedSeq,
                raw == null
                        ? new ObservedSnapshot.ObservedScope(List.of(), List.of(), false, false)
                        : new ObservedSnapshot.ObservedScope(List.of(), List.copyOf(raw.deptIds),
                                true, true),
                raw == null ? 0 : raw.deptIds.size(),
                raw == null ? 0 : raw.personIds.size(),
                list);
    }

    @Override
    public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
        Map<String, Fact> scopeFacts = facts.getOrDefault(scope, Map.of());
        List<ProtectedFactView> views = new ArrayList<>();
        for (Map.Entry<String, Fact> entry : scopeFacts.entrySet()) {
            String k = entry.getKey();
            ProtectedFactView.ObjectType type = k.startsWith("P:")
                    ? ProtectedFactView.ObjectType.PERSON : ProtectedFactView.ObjectType.DEPARTMENT;
            views.add(entry.getValue().view(type, k.substring(2)));
        }
        return views;
    }

    private Gen requireGeneration(GenerationHandle handle) {
        Gen gen = generations.getOrDefault(handle.scope(), Map.of()).get(handle.generationId());
        if (gen == null) {
            throw new DirectoryLeaseLostException("代不存在：" + handle.generationId());
        }
        return gen;
    }

    /** 联合校验（§6.1）：token 匹配 ∧ 未过期 ∧ 状态前置（多前置任一匹配即可）。 */
    private void jointCheck(GenerationHandle handle, Gen gen, DirectoryGeneration.State... allowed) {
        Meta meta = metadata.get(handle.scope());
        if (meta == null || !Objects.equals(meta.leaseToken, handle.leaseToken())) {
            throw new DirectoryLeaseLostException("token 不匹配（handle=" + handle.generationId() + "）");
        }
        if (meta.leaseExpiresAt == null || !meta.leaseExpiresAt.isAfter(clock.get())) {
            throw new DirectoryLeaseExpiredException(
                    "租约已过期（handle=" + handle.generationId() + "）——不静默延期，须重新 acquire");
        }
        boolean stateOk = false;
        for (DirectoryGeneration.State state : allowed) {
            if (gen.state == state) {
                stateOk = true;
                break;
            }
        }
        if (!stateOk) {
            throw new DirectoryInvalidGenerationStateException(allowed[0], gen.state,
                    "代状态前置不满足（要求 " + List.of(allowed) + "，实际 " + gen.state + "）");
        }
    }

    private static String key(ProtectedFactView.ObjectType type, String externalId) {
        return (type == ProtectedFactView.ObjectType.PERSON ? "P:" : "D:") + externalId;
    }
}
