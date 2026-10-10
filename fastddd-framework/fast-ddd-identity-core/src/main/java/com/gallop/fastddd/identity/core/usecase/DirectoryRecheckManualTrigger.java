package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryIncrementEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.ExternalIdTypes;
import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.LocalFactView;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckEvidence;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryIncrementPublisher;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 手动 pending_confirm 对账回查编排用例（W5 设计 §7.5 对账面；W5-f 设计 §5
 * M-17，W5-f f-2）：枚举 {@link DirectoryMirrorStore#readProtectedFacts} 中
 * {@code pendingConfirm} 事实，逐键执行「本地基线 → 权威单查 → 结论派生 →
 * 单短事务 CAS 提交」——与 worker 回查同构（{@link DirectoryEventWorkerUseCase}
 * §7.3 推演表/锁序/门禁与通知接线），触发源为管理端而非事件信封。
 *
 * <p><b>仅 pending_confirm 面（M-17/F3 口径）</b>：不提供任意键强制回查——
 * 非 pending 行不在枚举面，权威与本地已一致时本用例无事可做。逐键串行：
 * 单键事务边界外完成权威查询（{@link DirectoryRecheckQuery} 契约——远程调用
 * 不进事务），提交经 {@link IdentityTransactionRunner} 独立短事务；跨实例
 * 并发由 {@code commitCas} 乐观基线（stale = 零写零接线）兜底（D7）。</p>
 *
 * <p><b>端口面（五枚，均既有——W5-f 设计 §5 记「四端口」漏计
 * {@link DirectoryFactMatcher}：结论派生 IDENTICAL/CONTENT_DIFFERS 分界
 * 必须结构级等值，f-4 差异登记）</b>：镜像枚举/事实读取/权威单查/内容匹配/
 * CAS 提交器。W5-e 门禁与通知接线同构引入（均可空，缺席各自退化为 M1
 * 行为）：{@code ExternalRejectionGate}——DEACTIVATED→record / 权威恢复→
 * clear（PERSON-only，与 commitCas 同事务）；{@code DirectoryIncrementPublisher}
 * ——增量通知机械转发。{@code sourceEventId=null}（{@link RecheckEvidence}
 * javadoc 明载管理端手动触发形态），门禁 reason 以 {@code manual} 标记区分
 * 事件驱动写入。异常照常上抛 fail-closed——设计结论面无错误态，失败键阻断
 * 后续键、重跑幂等收敛（已复位 pending 的键不再枚举）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryRecheckManualTrigger {

    private final DirectoryMirrorStore mirrorStore;
    private final DirectoryFactReader factReader;
    private final DirectoryRecheckQuery recheckQuery;
    private final DirectoryFactMatcher factMatcher;
    private final DirectoryRecheckCommitter committer;
    private final IdentityTransactionRunner runner;
    private final ExternalRejectionGate rejectionGate;
    private final DirectoryIncrementPublisher incrementPublisher;
    private final String rejectionProvider;

    /**
     * @param rejectionGate       可空（缺席 = M1 形态无门禁接线）
     * @param incrementPublisher 可空（缺席 = 零通知转发）
     * @param rejectionProvider   拒绝行 provider 值；gate 在场而空 = 构造拒绝
     *                            （worker 第二道防线同款——第一道在装配终检）
     */
    public DirectoryRecheckManualTrigger(DirectoryMirrorStore mirrorStore,
                                         DirectoryFactReader factReader,
                                         DirectoryRecheckQuery recheckQuery,
                                         DirectoryFactMatcher factMatcher,
                                         DirectoryRecheckCommitter committer,
                                         IdentityTransactionRunner runner,
                                         ExternalRejectionGate rejectionGate,
                                         DirectoryIncrementPublisher incrementPublisher,
                                         String rejectionProvider) {
        if (rejectionGate != null && (rejectionProvider == null || rejectionProvider.isBlank())) {
            throw new IllegalArgumentException(
                    "directory-recheck-manual: ExternalRejectionGate 在场而 rejection-provider 空——拒绝行五元组无法构造");
        }
        this.mirrorStore = mirrorStore;
        this.factReader = factReader;
        this.recheckQuery = recheckQuery;
        this.factMatcher = factMatcher;
        this.committer = committer;
        this.runner = runner;
        this.rejectionGate = rejectionGate;
        this.incrementPublisher = incrementPublisher;
        this.rejectionProvider = rejectionProvider;
    }

    /**
     * 执行一轮 pending_confirm 对账回查：枚举 → 逐键回查提交，返回逐键结论
     * 报告。零 pending 键 = 空报告（幂等重跑收敛形态）。core 用例零日志
     * （worker 同款纪律——完成日志由调用侧承载，starter 控制器/宿主入口负责）。
     */
    public ManualRecheckReport runPendingRecheck(DirectoryScope scope) {
        List<ManualRecheckOutcome> outcomes = new ArrayList<>();
        for (ProtectedFactView view : mirrorStore.readProtectedFacts(scope)) {
            if (!view.pendingConfirm()) {
                continue; // 仅 pending_confirm 面（M-17/F3）
            }
            // 键型映射：镜像视图枚举与域枚举同名分立（ProtectedFactView 冻结面）
            DirectoryObjectType objectType = DirectoryObjectType.valueOf(view.objectType().name());
            outcomes.add(recheckOne(scope, objectType, view.externalId()));
        }
        return new ManualRecheckReport(scope, List.copyOf(outcomes));
    }

    /**
     * 单键回查（与 worker {@code recheckAndCommit} 同构，触发源不同）：
     * ①② 事务外本地基线 + 权威单查 → 结论派生 → ③ 单短事务 CAS 提交 +
     * W5-e 门禁/通知接线（stale = 零写零接线）。
     */
    private ManualRecheckOutcome recheckOne(DirectoryScope scope, DirectoryObjectType objectType,
                                            String externalId) {
        Optional<LocalFactView> local = factReader.readFact(scope, objectType, externalId);
        Optional<ObservedDirectoryObject> observed = recheckQuery.fetchObserved(objectType, externalId);
        RecheckConclusion conclusion = deriveConclusion(local, observed);
        // 观察内容仅 CONTENT_DIFFERS 携带（RecheckEvidence 契约：写面需要权威新值）
        ObservedDirectoryObject present = observed.orElse(null);
        DirectoryPerson person = conclusion == RecheckConclusion.CONTENT_DIFFERS && present != null
                ? present.person() : null;
        DirectoryDepartment department = conclusion == RecheckConclusion.CONTENT_DIFFERS && present != null
                ? present.department() : null;
        RecheckEvidence evidence = new RecheckEvidence(objectType, externalId, conclusion,
                local.map(LocalFactView::localRevision).orElse(0L), person, department, null);
        // ——锁序（R3-P3-1，worker 同款）：committer 先锁 metadata 行 → gate 写
        // 后取五元组 advisory（升序，与登录路径 advisory→binding 无环）；通知
        // 转发纯机械（预合并/stale/IDENTICAL 的 events 已空，零枚即零转发）
        RecheckResult result = runner.inTransaction(() -> {
            RecheckResult committed = committer.commitCas(scope, evidence);
            if (rejectionGate != null && !committed.stale()
                    && objectType == DirectoryObjectType.PERSON) {
                if (conclusion == RecheckConclusion.DEACTIVATED) {
                    rejectionGate.record(rejectionIdentity(scope, externalId),
                            "authority-recheck-deactivated:manual");
                } else if (conclusion == RecheckConclusion.IDENTICAL
                        || conclusion == RecheckConclusion.CONTENT_DIFFERS) {
                    rejectionGate.clearByAuthoritativeRestoration(
                            rejectionIdentity(scope, externalId),
                            "authority-recheck-restored:manual");
                }
            }
            if (incrementPublisher != null) {
                for (DirectoryIncrementEvent event : committed.publishedEvents()) {
                    incrementPublisher.publishDirectoryChanged(event);
                }
            }
            return committed;
        });
        return new ManualRecheckOutcome(objectType, externalId, conclusion, result.stale(),
                result.changeLogRef());
    }

    /**
     * 拒绝行五元组（provider 来自装配注入；人员键 = open_id 域——worker
     * {@code rejectionIdentity} 同款冻结键域与跨域缺口注记，W5-e D 差异登记）。
     */
    private ExternalIdentity rejectionIdentity(DirectoryScope scope, String openId) {
        return new ExternalIdentity(rejectionProvider, scope.tenantKey(), scope.appScope(),
                ExternalIdTypes.OPEN_ID, openId);
    }

    /**
     * 结论派生（§7.3 推演表，与 worker 逐分支同构）：ABSENT（权威确证不存在）
     * &gt; DEACTIVATED（权威在场且停用）&gt; CONTENT_DIFFERS（无本地行/本地墓碑
     * 复活/内容不等）&gt; IDENTICAL（结构等值——零 H，仅可能复位 pending）。
     */
    private RecheckConclusion deriveConclusion(Optional<LocalFactView> local,
                                               Optional<ObservedDirectoryObject> observed) {
        if (observed.isEmpty()) {
            return RecheckConclusion.ABSENT;
        }
        ObservedDirectoryObject present = observed.get();
        if (present.deactivated()) {
            return RecheckConclusion.DEACTIVATED;
        }
        if (local.isEmpty()) {
            // 无本地行 + 权威在场：确证存在形态 INSERT（W5-c committer rev=1 路径）
            return RecheckConclusion.CONTENT_DIFFERS;
        }
        LocalFactView baseline = local.get();
        if (baseline.tombstone()) {
            // 墓碑 + 权威在场：复活 = 内容替换（IDENTICAL 只复位 pending，不复活）
            return RecheckConclusion.CONTENT_DIFFERS;
        }
        boolean identical = present.person() != null
                ? factMatcher.matches(baseline.factJson(), present.person())
                : factMatcher.matches(baseline.factJson(), present.department());
        return identical ? RecheckConclusion.IDENTICAL : RecheckConclusion.CONTENT_DIFFERS;
    }

    /** 单键结论（M-17 管理报告面：键/类型/结论/stale/change_log 引用）。 */
    public record ManualRecheckOutcome(DirectoryObjectType objectType, String externalId,
                                       RecheckConclusion conclusion, boolean stale,
                                       String changeLogRef) {
    }

    /** 一轮对账回查报告（scope + 逐键结论；空 outcomes = 零 pending 幂等形态）。 */
    public record ManualRecheckReport(DirectoryScope scope,
                                      List<ManualRecheckOutcome> outcomes) {

        public ManualRecheckReport {
            outcomes = List.copyOf(outcomes);
        }
    }
}
