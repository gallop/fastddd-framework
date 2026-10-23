package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.VerifiedPrincipal;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityBindingStore;
import com.gallop.fastddd.identity.core.port.IdentityLifecyclePort;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;

import java.util.List;

/**
 * 本地受控停用/启用用例（T4，§9.2）：停用 = 拒绝行记录（W5-e，gate 在场时）
 * + 锁主体 + 条件 UPDATE（status/epoch+1）+ 撤销全部会话，同一事务；已 DISABLED
 * 幂等；不存在 NOT_FOUND（同事务回滚连带拒绝行——同成同败）。
 *
 * <p><b>W5-e 拒绝行接线（§7.5(b) 写入方③，R2-P2-1 锁序钉死）</b>：事务外反查
 * 主体全部绑定五元组（binding 创建后不可变，无竞态面）→ 事务内 <b>先</b>逐键
 * {@code record}（⓪ 五元组键 advisory + 拒绝行，<b>先于任何 binding/principal
 * 语句</b>——与登录路径同序，无 ABBA）→ 主体停用 + 撤会话。record 幂等复活
 * （D4 补位）。{@code rejectionGate} 为 null（旧构造器）= M1 形态零行为变化。</p>
 *
 * <p><b>reactivate 不自动清拒绝行</b>（W5-e 裁决）：拒绝行可能源自权威确证
 * （回查 DEACTIVATED），静默清除削弱可追溯性——逆转受控停用 =
 * {@code reactivate} + 管理端 {@code clearControlled} 两显式操作（各自审计）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class PrincipalLifecycleUseCase {

    private final IdentityLifecyclePort lifecyclePort;
    private final IdentityTransactionRunner runner;
    /** 外部拒绝门禁（W5-e，可空：null = M1 形态，停用不落拒绝行）。 */
    private final ExternalRejectionGate rejectionGate;
    /** 绑定反查（仅 gate 在场时使用；T4 五元组来源）。 */
    private final IdentityBindingStore bindingStore;

    public PrincipalLifecycleUseCase(IdentityLifecyclePort lifecyclePort, IdentityTransactionRunner runner) {
        this(lifecyclePort, runner, null, null);
    }

    public PrincipalLifecycleUseCase(IdentityLifecyclePort lifecyclePort,
                                     IdentityTransactionRunner runner,
                                     ExternalRejectionGate rejectionGate,
                                     IdentityBindingStore bindingStore) {
        if (rejectionGate != null && bindingStore == null) {
            throw new IllegalArgumentException("rejectionGate 在场时 bindingStore 必须非空（T4 五元组反查）");
        }
        this.lifecyclePort = lifecyclePort;
        this.runner = runner;
        this.rejectionGate = rejectionGate;
        this.bindingStore = bindingStore;
    }

    public VerifiedPrincipal deactivate(String principalId) {
        // 事务外反查绑定五元组（binding 不可变——创建后五元组不变，无竞态面）
        List<ExternalIdentity> bindings =
                rejectionGate != null ? bindingStore.findBindings(principalId) : List.of();
        return runner.inTransaction(() -> {
            // ⓪ record 先于主体语句（§7.5(b) 锁序钉死；主体不存在抛 NOT_FOUND 时
            // 同事务回滚连带拒绝行——同成同败）
            if (rejectionGate != null) {
                for (ExternalIdentity identity : bindings) {
                    rejectionGate.record(identity, "controlled-deactivation:" + principalId);
                }
            }
            return lifecyclePort.deactivatePrincipal(principalId);
        });
    }

    public VerifiedPrincipal reactivate(String principalId) {
        return runner.inTransaction(() -> lifecyclePort.reactivatePrincipal(principalId));
    }
}
