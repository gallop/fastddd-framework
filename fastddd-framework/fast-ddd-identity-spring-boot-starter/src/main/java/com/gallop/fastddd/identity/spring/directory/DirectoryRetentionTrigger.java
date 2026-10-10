package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.domain.RetentionReport;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.port.DirectoryRetentionExecutor;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 手动目录事实回收触发组件（W5 设计 §8.3 M-26 管理入口先例；W5-f 设计 §3.4，
 * f-1）：照 {@link DirectorySyncManualTrigger} 两入口分列——运维入口无策略裁决
 * （宿主定时任务/CLI 直接调用），交互入口逐操作裁决
 * （{@code RETENTION_RUN}，无固定目标 {@code null} 形态）+ 缺 policy/缺
 * {@link Actor} 双缺省拒绝。
 *
 * <p>{@link RetentionPolicy} 构造期注入（starter 装配自 §10 键族缺省
 * 7d/不限期）；preview 与 execute 同判据（{@link DirectoryRetentionExecutor}
 * 契约）。自动周期清理零交付——本组件是显式触发唯一入口（W5 设计 §8.3 口径）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class DirectoryRetentionTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryRetentionTrigger.class);

    private final DirectoryRetentionExecutor executor;
    private final RetentionPolicy policy;
    private final ObjectProvider<IdentityAdminPolicy> adminPolicy;

    public DirectoryRetentionTrigger(DirectoryRetentionExecutor executor,
                                     RetentionPolicy policy,
                                     ObjectProvider<IdentityAdminPolicy> adminPolicy) {
        this.executor = executor;
        this.policy = policy;
        this.adminPolicy = adminPolicy;
    }

    /**
     * 运维/宿主入口（无策略裁决——沿 {@code runFullSync()} 先例，SYSTEM_JOB
     * 语义）：preview=true 只读预估，false 分面执行。
     */
    public RetentionReport runRetention(DirectoryScope scope, boolean preview) {
        RetentionReport report = preview ? executor.preview(scope, policy) : executor.execute(scope, policy);
        LOG.info("[directory-retention] 手动回收{}完成：scope={}/{}，镜像 person/dept/relation={}/{}/{}，"
                        + "inboxPurged={}，changeLogPurged={}，changeLogSkippedByWatermark={}",
                preview ? "（preview）" : "", scope.tenantKey(), scope.appScope(),
                report.mirrorPersonRows(), report.mirrorDepartmentRows(), report.mirrorRelationRows(),
                report.inboxRowsPurged(), report.changeLogRowsPurged(), report.changeLogSkippedByWatermark());
        return report;
    }

    /**
     * 交互管理员入口：逐操作裁决（RETENTION_RUN 无固定目标）后执行；缺
     * {@code IdentityAdminPolicy} bean 或缺 {@link Actor} 双缺省拒绝
     * （deny-by-default——同 {@code triggerInteractively} 先例）。
     */
    public RetentionReport triggerInteractively(Actor actor, DirectoryScope scope, boolean preview) {
        IdentityAdminPolicy policy = adminPolicy.getIfAvailable();
        if (policy == null) {
            throw new IdentityAdminDeniedException("RETENTION_RUN", null,
                    "目录回收交互式触发需要 IdentityAdminPolicy bean——无策略即拒绝");
        }
        if (actor == null) {
            throw new IdentityAdminDeniedException("RETENTION_RUN", null,
                    "目录回收交互式触发缺少操作者身份（Actor）——缺省拒绝");
        }
        policy.checkPrincipalAdmin(
                com.gallop.fastddd.identity.core.domain.IdentityAdminOperation.RETENTION_RUN, actor, null);
        return runRetention(scope, preview);
    }
}
