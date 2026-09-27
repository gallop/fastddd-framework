package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 目录全量同步编排用例（W3 设计 §2/§5.2，W3-a）：
 * begin → 分页写批（每页续租）→ completePaging → validate → publish；
 * 任何失败按契约整代 abandon。
 *
 * <p><b>编排约束（W3 设计 §5.2/§6.5）</b>：</p>
 * <ul>
 *   <li><b>不含任何 SQL/HTTP</b>——一切持久化经 {@link DirectoryMirrorStore}
 *       （每方法一个短事务，写端口 MANDATORY）、一切远程经 {@link DirectorySource}
 *       （全部在事务外）。本用例不开启、也不包裹任何事务——<b>全量同步无长事务</b>；</li>
 *   <li><b>不重试分页</b>：瞬时故障的有界重试责任在源适配器（重试责任不叠加）；
 *       源抛出 = 已耗尽，本用例整代废弃后结束，下轮调度重新开始；</li>
 *   <li><b>获取租约失败不废弃他人的代</b>：beginGeneration 抛出（如
 *       {@link DirectoryLeaseHeldException}）时无 handle、无自家代，直接传播，
 *       不调用 abandon；</li>
 *   <li><b>清理失败不掩盖原始异常</b>：失败路径 try-abandon，abandon 自身异常
 *       {@code addSuppressed} 后原样重抛原始异常（也不影响新持有者的代——
 *       被接管后 abandon 会被 token 校验拒绝，属预期）；</li>
 *   <li><b>分页护栏</b>：每页校验 {@link DirectoryPage} 一致性契约
 *       （hasMore ⇒ 游标非空且前进，否则 {@link DirectoryContractViolationException}
 *       整代废弃，防无限页）；累计页数不超过 maxPagesPerRun（同样契约违反处理）；</li>
 *   <li><b>系统任务身份显式</b>：全程运行于 SYSTEM_JOB 上下文
 *       （principalId = {@code system-job:directory-sync}）——审计与下游 Actor
 *       解析取到系统作业身份，<b>不把缺失员工身份静默降级</b>；</li>
 *   <li><b>M1 无 publisher</b>：本用例不依赖 {@code IdentityChangePublisher}
 *       （端口冻结，装配零要求——见 {@code DirectoryMirrorStore.publish} javadoc）。</li>
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
    private final int pageSize;
    private final int maxPagesPerRun;

    public DirectorySyncUseCase(DirectorySource source, DirectoryMirrorStore store,
                                int pageSize, int maxPagesPerRun) {
        this.source = Objects.requireNonNull(source, "source");
        this.store = Objects.requireNonNull(store, "store");
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize 必须为正数（收到：" + pageSize + "）");
        }
        if (maxPagesPerRun <= 0) {
            throw new IllegalArgumentException("maxPagesPerRun 必须为正数（收到：" + maxPagesPerRun + "）");
        }
        this.pageSize = pageSize;
        this.maxPagesPerRun = maxPagesPerRun;
    }

    /**
     * 执行一次全量同步（同步阻塞；调度/手动入口各自决定执行线程）。
     *
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
        // begin 失败（LeaseHeld 等）：无 handle、无自家代——直接传播，不 abandon
        GenerationHandle handle = store.beginGeneration(scope);
        try {
            List<DirectoryDepartment> departments = syncDepartments(handle);
            syncPersons(handle, departments);
            store.completePaging(handle);
            store.validate(handle);
            return store.publish(handle);
        } catch (Throwable failure) {
            // 清理失败不掩盖原始异常；被接管后 abandon 被拒（保护新持有者）也走 suppressed
            try {
                store.abandon(handle, reasonOf(failure));
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /** 部门流：分页读 + 逐页写批 + 页间续租；返回本代部门全集（供人员流遍历）。 */
    private List<DirectoryDepartment> syncDepartments(GenerationHandle handle) {
        List<DirectoryDepartment> all = new ArrayList<>();
        PageCounter pages = new PageCounter();
        String cursor = null;
        while (true) {
            DirectoryPage<DirectoryDepartment> page =
                    source.departments(new DirectoryPageQuery(cursor, pageSize));
            requireWellFormed(page, cursor, "departments");
            pages.count();
            all.addAll(page.items());
            store.writeBatch(handle, DirectoryBatch.ofDepartments(page.items()));
            if (!page.hasMore()) {
                return all;
            }
            store.renewLease(handle);
            cursor = page.nextCursor();
        }
    }

    /** 人员流：按部门遍历，每个部门内分页读 + 逐页写批 + 页间续租。 */
    private void syncPersons(GenerationHandle handle, List<DirectoryDepartment> departments) {
        PageCounter pages = new PageCounter();
        for (DirectoryDepartment department : departments) {
            String cursor = null;
            while (true) {
                DirectoryPage<DirectoryPerson> page = source.persons(
                        department.departmentId(), new DirectoryPageQuery(cursor, pageSize));
                requireWellFormed(page, cursor, "persons[" + department.departmentId() + "]");
                pages.count();
                store.writeBatch(handle, DirectoryBatch.ofPersons(page.items()));
                if (!page.hasMore()) {
                    break;
                }
                store.renewLease(handle);
                cursor = page.nextCursor();
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

    /** 页数护栏：部门流与人员流合计不超过 maxPagesPerRun。 */
    private final class PageCounter {
        private int counted;

        void count() {
            counted++;
            if (counted > maxPagesPerRun) {
                throw new DirectoryContractViolationException("max-pages-exceeded",
                        "本次同步页数超过护栏 maxPagesPerRun=" + maxPagesPerRun
                                + "——疑似无限分页，整代废弃");
            }
        }
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
