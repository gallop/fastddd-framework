package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryGenerationActivatedEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.Ulids;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 目录全量同步编排用例（W3 设计 §2/§5.2，W3-a；W3-a 补修 W3A-01/03/05）：
 * begin → 分页写批（按扫描进度续租）→ completePaging → validate → publish；
 * 任何失败按契约整代 abandon。
 *
 * <p><b>编排约束（W3 设计 §5.2/§6.5）</b>：</p>
 * <ul>
 *   <li><b>不含任何 SQL/HTTP</b>——一切持久化经 {@link DirectoryMirrorStore}、
 *       一切远程经 {@link DirectorySource}。<b>事务所有者（W3A-01）</b>：每个
 *       存储方法（begin/writeBatch/renewLease/completePaging/validate/publish/
 *       abandon）经 {@link IdentityTransactionRunner} 各自独立短事务执行
 *       （READ_COMMITTED，写端口 MANDATORY 由此满足）；源调用<b>全部在事务外</b>；
 *       本用例不给整个同步加事务——<b>全量同步无长事务</b>。顶层入口若已有宿主
 *       事务，首个存储步骤（begin，先于任何源访问/写入）即被 runner 契约
 *       （入口已有活动事务拒绝）拒绝，不把分页与远程调用包进宿主事务；</li>
 *   <li><b>不重试分页</b>：瞬时故障的有界重试责任在源适配器（重试责任不叠加）；
 *       源抛出 = 已耗尽，本用例整代废弃后结束，下轮调度重新开始；</li>
 *   <li><b>获取租约失败不废弃他人的代</b>：beginGeneration 抛出（如
 *       {@link DirectoryLeaseHeldException}）时无 handle、无自家代，直接传播，
 *       不调用 abandon；</li>
 *   <li><b>清理失败不掩盖原始异常</b>：失败路径 try-abandon（abandon 同样走
 *       独立短事务），abandon 自身异常 {@code addSuppressed} 后原样重抛原始
 *       异常（也不影响新持有者的代——被接管后 abandon 会被 token 校验拒绝，
 *       属预期）；</li>
 *   <li><b>分页护栏</b>：每页校验 {@link DirectoryPage} 一致性契约
 *       （hasMore ⇒ 游标非空且前进，否则 {@link DirectoryContractViolationException}
 *       整代废弃，防无限页）；<b>页数预算整轮共享（W3A-05）</b>——部门流与
 *       人员流合计不超过 maxPagesPerRun，且在<b>发出下一次源调用前</b>检查/
 *       消耗（跨流、跨部门共管；超限不再外呼、不 publish，直接整代废弃）；</li>
 *   <li><b>续租按扫描进度（W3A-03）</b>：不按单流 hasMore——除全局首次外的
 *       每次源调用前、以及末页转 completePaging/validate/publish 的阶段边界前
 *       各续租一次，覆盖部门流结束、切换部门、单页部门链等漏续租路径（每次
 *       请求短于租约、累计超过 TTL 的扫描仍可完成）；过期、被接管后的联合
 *       校验仍严格拒绝——续租不是静默延期；</li>
 *   <li><b>系统任务身份显式</b>：全程运行于 SYSTEM_JOB 上下文
 *       （principalId = {@code system-job:directory-sync}）——审计与下游 Actor
 *       解析取到系统作业身份，<b>不把缺失员工身份静默降级</b>；</li>
 *   <li><b>T8 组合（W5-b）</b>：publish 与代激活日志条目、代激活事件<b>同一短事务</b>
 *       ——{@code store.publish} 后 {@code DirectoryChangeLog.append}
 *       （GENERATION_ACTIVATED，预铸 ULID——P2-5）+ {@code IdentityChangePublisher}
 *       发布（changeLogRef 指向载体条目）；{@code hasGenerationActivation} 命中则
 *       no-op 跳过 append 与事件（B-5）。changeLog/publisher 均为 null 的 M1 形态
 *       零行为变化——不 append 不发事件（装配零要求，见
 *       {@code DirectoryMirrorStore.publish} javadoc）。</li>
 * </ul>
 *
 * <p>内存中保留本代部门全集用于人员流遍历（M1 规模假设下的最小可用取舍；
 * 流式检查点续扫属 W3-c 存储实现关注点——失败即整代废弃，无需续跑游标）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectorySyncUseCase {

    /** 目录同步系统作业 job-key（principalId = {@code system-job:directory-sync}）。 */
    public static final String JOB_KEY = "directory-sync";

    /** abandon_reason 稳定值（存储列 VARCHAR(32)）。 */
    static final String REASON_SOURCE_UNAVAILABLE = "source-unavailable";
    static final String REASON_SOURCE_FORBIDDEN = "source-forbidden";
    static final String REASON_CONTRACT_VIOLATION = "contract-violation";
    static final String REASON_LEASE_EXPIRED = "lease-expired";
    static final String REASON_UNEXPECTED = "unexpected";

    private static final String SYSTEM_TENANT = "system";

    private final DirectorySource source;
    private final DirectoryMirrorStore store;
    private final IdentityTransactionRunner txRunner;
    private final int pageSize;
    private final int maxPagesPerRun;
    private final DirectoryChangeLog changeLog;
    private final IdentityChangePublisher publisher;

    /** M1 形态（W5-b 保留）：无变更日志、无通知——不 append 不发事件，行为与 W3 等价。 */
    public DirectorySyncUseCase(DirectorySource source, DirectoryMirrorStore store,
                                IdentityTransactionRunner txRunner,
                                int pageSize, int maxPagesPerRun) {
        this(source, store, txRunner, pageSize, maxPagesPerRun, null, null);
    }

    /**
     * W5-b T8 组合形态：publish 与代激活日志条目、代激活事件同事务
     * （任一失败整体回滚——先库后信）。
     *
     * @throws IllegalArgumentException publisher 非 null 而 changeLog 为 null
     *         ——事件 changeLogRef 契约（须指向不可变载体条目）无法满足
     */
    public DirectorySyncUseCase(DirectorySource source, DirectoryMirrorStore store,
                                IdentityTransactionRunner txRunner,
                                int pageSize, int maxPagesPerRun,
                                DirectoryChangeLog changeLog, IdentityChangePublisher publisher) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
        this.txRunner = Objects.requireNonNull(txRunner, "txRunner");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须为正数（收到：" + pageSize + "）");
        }
        if (maxPagesPerRun <= 0) {
            throw new IllegalArgumentException("maxPagesPerRun 必须为正数（收到：" + maxPagesPerRun + "）");
        }
        this.pageSize = pageSize;
        this.maxPagesPerRun = maxPagesPerRun;
        if (publisher != null && changeLog == null) {
            throw new IllegalArgumentException(
                    "publisher 非 null 时 changeLog 不能为 null（事件 changeLogRef 须指向不可变载体条目）");
        }
        this.changeLog = changeLog;
        this.publisher = publisher;
    }

    /**
     * 执行一次全量同步（同步阻塞；调度/手动入口各自决定执行线程）。
     *
     * @throws com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException
     *         宿主已有活动事务（首个存储步骤即拒绝，先于任何源访问/写入）
     * @throws DirectoryLeaseHeldException 同步进行中（他方持有有效租约；无废弃动作）
     * @throws DirectorySourceForbiddenException 源权限不足（fail-fast 整代废弃）
     * @throws DirectorySourceUnavailableException 源瞬时故障耗尽（整代废弃，下轮重试）
     * @throws DirectoryContractViolationException 分页/校验契约违反（整代废弃）
     */
    public DirectoryPublication runFullSync(DirectoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        Actor systemJob = new Actor(SYSTEM_TENANT, PrincipalId.forSystemJob(JOB_KEY),
                PrincipalType.SYSTEM_JOB);
        return SystemJobPrincipalContext.run(systemJob, () -> doRun(scope));
    }

    private DirectoryPublication doRun(DirectoryScope scope) {
        // begin 失败（LeaseHeld 等）：无 handle、无自家代——直接传播，不 abandon。
        // begin 经 runner 短事务执行：宿主已有事务在此即被拒绝（W3A-01）
        GenerationHandle handle = txGet(() -> store.beginGeneration(scope));
        try {
            ScanProgress progress = new ScanProgress(handle);
            List<DirectoryDepartment> departments = syncDepartments(handle, progress);
            syncPersons(handle, progress, departments);
            progress.beforeFinalization();
            txRun(() -> store.completePaging(handle));
            txRun(() -> store.validate(handle));
            return publishWithChangeLog(handle);
        } catch (Throwable failure) {
            // 清理失败不掩盖原始异常；被接管后 abandon 被拒（保护新持有者）也走 suppressed；
            // abandon 自身也在独立短事务中执行（W3A-01）
            try {
                txRun(() -> store.abandon(handle, reasonOf(failure)));
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** 部门流：分页读 + 逐页写批；返回本代部门全集（供人员流遍历）。 */
    private List<DirectoryDepartment> syncDepartments(GenerationHandle handle,
                                                      ScanProgress progress) {
        List<DirectoryDepartment> all = new ArrayList<>();
        String cursor = null;
        while (true) {
            progress.beforeSourceCall();
            DirectoryPage<DirectoryDepartment> page =
                    source.departments(new DirectoryPageQuery(cursor, pageSize));
            requireWellFormed(page, cursor, "departments");
            all.addAll(page.items());
            txRun(() -> store.writeBatch(handle, DirectoryBatch.ofDepartments(page.items())));
            if (!page.hasMore()) {
                return all;
            }
            cursor = page.nextCursor();
        }
    }

    /** 人员流：按部门遍历，每个部门内分页读 + 逐页写批。 */
    private void syncPersons(GenerationHandle handle, ScanProgress progress,
                             List<DirectoryDepartment> departments) {
        for (DirectoryDepartment department : departments) {
            String cursor = null;
            while (true) {
                progress.beforeSourceCall();
                DirectoryPage<DirectoryPerson> page = source.persons(
                        department.departmentId(), new DirectoryPageQuery(cursor, pageSize));
                requireWellFormed(page, cursor, "persons[" + department.departmentId() + "]");
                txRun(() -> store.writeBatch(handle, DirectoryBatch.ofPersons(page.items())));
                if (!page.hasMore()) {
                    break;
                }
                cursor = page.nextCursor();
            }
        }
    }

    /**
     * 整轮扫描进度状态（W3A-03/W3A-05）：
     * ① 页数预算整轮共享，在发出每一次源调用<b>前</b>检查/消耗（跨流、跨部门
     *    共管；超限在下次外呼前拒绝，不先发出超限请求）；② 续租按扫描进度而
     *    非单流 hasMore：除全局首次外的每次源调用前、以及末页转 completePaging
     *    的阶段边界前续租，覆盖部门流结束、切换部门、单页部门链等漏续租路径。
     *    过期/被接管仍由存储联合校验严格拒绝——续租不是静默延期。
     */
    private final class ScanProgress {
        private final GenerationHandle handle;
        private int consumedPages;
        private boolean firstSourceCall = true;

        ScanProgress(GenerationHandle handle) {
            this.handle = handle;
        }

        /** 发出源调用前：先检查共享预算（超限不外呼），再按进度续租（全局首次除外）。 */
        void beforeSourceCall() {
            consumedPages++;
            if (consumedPages > maxPagesPerRun) {
                throw new DirectoryContractViolationException("max-pages-exceeded",
                        "本次同步页数超过护栏 maxPagesPerRun=" + maxPagesPerRun
                                + "——疑似无限分页，整代废弃");
            }
            if (firstSourceCall) {
                firstSourceCall = false;
                return;
            }
            txRun(() -> store.renewLease(handle));
        }

        /** 末页 → completePaging/validate/publish 的阶段边界：不外呼，同样按进度续租。 */
        void beforeFinalization() {
            if (!firstSourceCall) {
                txRun(() -> store.renewLease(handle));
            }
        }
    }

    /** 分页一致性契约校验（W3 设计 §6.5：游标不前进/无限页 = 契约违反，不重试）。 */
    private <T> void requireWellFormed(DirectoryPage<T> page, String enteredCursor, String stream) {
        if (page == null || page.items() == null) {
            throw new DirectoryContractViolationException("null-page",
                    "目录源返回空页对象（流：" + stream + "）——契约违反，整代废弃");
        }
        if (page.hasMore()) {
            String next = page.nextCursor();
            if (next == null || next.isBlank()) {
                throw new DirectoryContractViolationException("has-more-without-cursor",
                        "目录页 hasMore=true 但无 nextCursor（流：" + stream + "）——契约违反，整代废弃");
            }
            if (next.equals(enteredCursor)) {
                throw new DirectoryContractViolationException("cursor-not-advancing",
                        "目录页游标未前进（流：" + stream + "，cursor=" + next + "）——契约违反，整代废弃");
            }
        }
    }

    /**
     * T8 组合（W5 设计 §5.2，W5-b）：publish → 变更日志 append → 代激活事件，
     * 三者同一短事务（任一失败整体回滚）。changeLog 为 null = M1 形态仅 publish。
     * no-op 判据 = {@code hasGenerationActivation}（同 scope 同代
     * GENERATION_ACTIVATED 行已存在 → 跳过 append 与事件——B-5；
     * factsInserted=0 非判据，A-9 空差异首发布亦为 0）。包私有以供同包测试直驱
     * （runFullSync 每轮新开代，no-op 重发布路径不可由公开入口触达）。
     */
    DirectoryPublication publishWithChangeLog(GenerationHandle handle) {
        if (changeLog == null) {
            return txGet(() -> store.publish(handle));
        }
        return txGet(() -> {
            // P2-5：预铸条目 id——append 载体行与事件 changeLogRef 共用该值。
            String entryId = Ulids.newUlid();
            DirectoryPublication publication = store.publish(handle);
            if (changeLog.hasGenerationActivation(handle.scope(), handle.generationId())) {
                return publication;
            }
            changeLog.append(new ChangeLogEntry(entryId, handle.scope(),
                    ChangeLogEntry.Kind.GENERATION_ACTIVATED, handle.generationId(),
                    publication.committedSeqAfter(), summaryOf(publication)));
            if (publisher != null) {
                publisher.publishGenerationActivated(new DirectoryGenerationActivatedEvent(
                        DirectoryGenerationActivatedEvent.eventIdFor(handle.scope(), handle.generationId()),
                        handle.scope(), handle.generationId(),
                        publication.committedSeqAfter(), entryId));
            }
            return publication;
        });
    }

    /** 紧凑 JSON 摘要（无 PII；core 零依赖手工拼装——全数值字段，无需转义）。 */
    private static String summaryOf(DirectoryPublication publication) {
        return "{\"kind\":\"GENERATION_ACTIVATED\""
                + ",\"committedSeqBefore\":" + publication.committedSeqBefore()
                + ",\"committedSeqAfter\":" + publication.committedSeqAfter()
                + ",\"appliedSeq\":" + publication.appliedSeq()
                + ",\"factsInserted\":" + publication.factsInserted() + "}";
    }

    /** 存储步骤短事务执行（W3A-01）：每个存储方法经 runner 各自独立短事务，远程调用永不进入。 */
    private <T> T txGet(Supplier<T> work) {
        return txRunner.inTransaction(new IdentityTransactionRunner.IdentityTransactionCallback<T>() {
            @Override
            public T doInTransaction() {
                return work.get();
            }
        });
    }

    /** 同 {@link #txGet}（无返回值形态；两名区分以避开泛型 SAM 重载推断歧义）。 */
    private void txRun(Runnable work) {
        txRunner.inTransaction(new IdentityTransactionRunner.IdentityTransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                work.run();
                return null;
            }
        });
    }

    /** abandon_reason 稳定分类（≤32 字符；不含异常消息避免 PII/超长）。 */
    private static String reasonOf(Throwable failure) {
        if (failure instanceof DirectorySourceUnavailableException) {
            return REASON_SOURCE_UNAVAILABLE;
        }
        if (failure instanceof DirectorySourceForbiddenException) {
            return REASON_SOURCE_FORBIDDEN;
        }
        if (failure instanceof DirectoryContractViolationException) {
            return REASON_CONTRACT_VIOLATION;
        }
        if (failure instanceof com.gallop.fastddd.identity.core.error.DirectoryLeaseExpiredException) {
            return REASON_LEASE_EXPIRED;
        }
        return REASON_UNEXPECTED;
    }
}
