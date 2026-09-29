package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 目录同步手动触发组件（W3 设计 v3 §9.4，W3-c）：<b>非 Web 端点</b>——宿主以
 * CLI/JMX/消息/内部 HTTP 自选入口包装本组件；与停用/启用管理端点分列。
 *
 * <p><b>两个入口</b>：</p>
 * <ul>
 *   <li>{@link #runFullSync()}：运维/宿主入口（同调度路径）——SYSTEM_JOB 上下文由
 *       {@link DirectorySyncUseCase} 内部建立（job-key {@code directory-sync}），
 *       不经策略裁决（策略不进核心用例，§9.3）；</li>
 *   <li>{@link #triggerInteractively(Actor)}：交互管理员入口——逐操作裁决
 *       （op={@link IdentityAdminOperation#SYNC_TRIGGER}，无固定目标）；缺
 *       {@link IdentityAdminPolicy} bean（admin.enabled=false 形态）或缺操作者
 *       Actor 均<b>缺省拒绝</b>（§9.4），裁决通过后与运维入口同路径执行。</li>
 * </ul>
 *
 * <p><b>互斥（§9.4）</b>：与定时调度共用存储租约——同 scope 已有持有者时
 * {@code beginGeneration} 抛 {@link DirectoryLeaseHeldException}，本组件转译为
 * {@link ManualSyncResult#inProgress()}（"同步进行中"，非错误）；其余失败
 * （源不可用/契约违反等）原样抛出，由宿主入口决定处置（代已按契约整代废弃）。
 * scope 取自 {@link DirectorySource#scope()}（源拥有 scope 定义，与调度器同源
 * 不缓存）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectorySyncManualTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(DirectorySyncManualTrigger.class);

    private final DirectorySyncUseCase useCase;
    private final DirectorySource directorySource;
    private final ObjectProvider<IdentityAdminPolicy> adminPolicy;

    public DirectorySyncManualTrigger(DirectorySyncUseCase useCase,
                                      DirectorySource directorySource,
                                      ObjectProvider<IdentityAdminPolicy> adminPolicy) {
        this.useCase = useCase;
        this.directorySource = directorySource;
        this.adminPolicy = adminPolicy;
    }

    /** 运维/宿主入口：立即执行一轮全量同步（SYSTEM_JOB 上下文；无策略裁决）。 */
    public ManualSyncResult runFullSync() {
        try {
            DirectoryPublication publication = useCase.runFullSync(directorySource.scope());
            LOG.info("[directory] 手动同步完成：generationId={}, committedSeq {}->{}, factsInserted={}",
                    publication.generationId(), publication.committedSeqBefore(),
                    publication.committedSeqAfter(), publication.factsInserted());
            return ManualSyncResult.completed(publication);
        } catch (DirectoryLeaseHeldException held) {
            LOG.info("[directory] 手动同步未执行（同步进行中：租约已被持有）：{}", held.getMessage());
            return ManualSyncResult.inProgress();
        }
    }

    /**
     * 交互管理员入口：逐操作裁决（SYNC_TRIGGER，无固定目标）后执行。
     * 无 {@link IdentityAdminPolicy} bean 或 actor 为 null 均 缺省拒绝（§9.4）。
     */
    public ManualSyncResult triggerInteractively(Actor actor) {
        IdentityAdminPolicy policy = adminPolicy.getIfAvailable();
        if (policy == null) {
            throw new IdentityAdminDeniedException(IdentityAdminOperation.SYNC_TRIGGER.name(), null,
                    "交互式手动同步触发需要 IdentityAdminPolicy bean（admin.enabled=true 时宿主必须提供）"
                            + "——无策略即拒绝");
        }
        if (actor == null) {
            throw new IdentityAdminDeniedException(IdentityAdminOperation.SYNC_TRIGGER.name(), null,
                    "交互式手动同步触发缺少操作者身份（Actor）——缺省拒绝");
        }
        policy.checkPrincipalAdmin(IdentityAdminOperation.SYNC_TRIGGER, actor, null);
        return runFullSync();
    }

    /** 手动同步结果：outcome（completed / in_progress）+ 发布结果（进行中为 null）。 */
    public record ManualSyncResult(String outcome, DirectoryPublication publication) {

        public static final String OUTCOME_COMPLETED = "completed";
        public static final String OUTCOME_IN_PROGRESS = "in_progress";

        static ManualSyncResult completed(DirectoryPublication publication) {
            return new ManualSyncResult(OUTCOME_COMPLETED, publication);
        }

        static ManualSyncResult inProgress() {
            return new ManualSyncResult(OUTCOME_IN_PROGRESS, null);
        }

        public boolean isInProgress() {
            return OUTCOME_IN_PROGRESS.equals(outcome);
        }

        @Override
        public String toString() {
            return "ManualSyncResult[" + outcome
                    + (publication == null ? "" : ", " + publication) + "]";
        }
    }
}
