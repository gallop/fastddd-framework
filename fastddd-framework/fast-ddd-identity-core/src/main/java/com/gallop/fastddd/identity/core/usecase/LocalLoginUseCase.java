package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.PrincipalCreationSpec;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.IdentityConcurrencyException;
import com.gallop.fastddd.identity.core.error.RetryableTransactionConflictException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.port.ExternalRejectionGate;
import com.gallop.fastddd.identity.core.port.IdentityBindingStore;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;

import java.time.Duration;

/**
 * 本地登录用例（T2，v0.2 §8.3）：
 *
 * <ol>
 *   <li>外部登录提供者验证凭证（事务外，远程调用不进本地签发事务）；</li>
 *   <li>W5-e ⓪①②（gate 在场时，§7.5(b) 锁序最前）：五元组键级 advisory
 *       （{@code pg_advisory_xact_lock(基类常量, hashtext(五元组规范化串))}，
 *       行不存在亦持锁）→ 锁内读活跃拒绝行 → <b>命中即拒绝</b>（不创建主体、
 *       不签发会话，fail-closed）——「停用/离职人员登录被拒」的框架级门禁，
 *       先于绑定/主体语句（与 T4/回查写入方同序，无 ABBA）；</li>
 *   <li>T2 内：锁定/创建主体 + 绑定（T1 为其子步骤）→ 准入复验 → INSERT 会话（epoch 快照）；</li>
 *   <li>绑定唯一键竞争/死锁：完整回滚后**新开事务**回读赢家，默认 ≤3 次；重试不重新兑换凭证
 *       （重走⓪①②——并发输家重试时拒绝行可见性由 advisory 键级序列化承载）。</li>
 * </ol>
 *
 * <p>{@code rejectionGate} 为 null（旧构造器）= M1 形态零行为变化（无门禁链，
 * 首次创建任何主体）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class LocalLoginUseCase {

    /** §8.3a：默认重试上限。 */
    public static final int DEFAULT_RETRY_LIMIT = 3;

    private final ExternalLoginProvider externalLoginProvider;
    private final IdentityBindingStore bindingStore;
    private final AdmissionPolicy admissionPolicy;
    private final LocalSessionPort sessionPort;
    private final IdentityTransactionRunner runner;
    private final Duration sessionTtl;
    private final int retryLimit;
    /** 外部拒绝门禁（W5-e，可空：null = M1 形态，跳过⓪①②）。 */
    private final ExternalRejectionGate rejectionGate;

    public LocalLoginUseCase(ExternalLoginProvider externalLoginProvider,
                             IdentityBindingStore bindingStore,
                             AdmissionPolicy admissionPolicy,
                             LocalSessionPort sessionPort,
                             IdentityTransactionRunner runner,
                             Duration sessionTtl) {
        this(externalLoginProvider, bindingStore, admissionPolicy, sessionPort, runner, sessionTtl,
                DEFAULT_RETRY_LIMIT);
    }

    public LocalLoginUseCase(ExternalLoginProvider externalLoginProvider,
                             IdentityBindingStore bindingStore,
                             AdmissionPolicy admissionPolicy,
                             LocalSessionPort sessionPort,
                             IdentityTransactionRunner runner,
                             Duration sessionTtl,
                             int retryLimit) {
        this(externalLoginProvider, bindingStore, admissionPolicy, sessionPort, runner, sessionTtl,
                retryLimit, null);
    }

    public LocalLoginUseCase(ExternalLoginProvider externalLoginProvider,
                             IdentityBindingStore bindingStore,
                             AdmissionPolicy admissionPolicy,
                             LocalSessionPort sessionPort,
                             IdentityTransactionRunner runner,
                             Duration sessionTtl,
                             int retryLimit,
                             ExternalRejectionGate rejectionGate) {
        this.externalLoginProvider = externalLoginProvider;
        this.bindingStore = bindingStore;
        this.admissionPolicy = admissionPolicy;
        this.sessionPort = sessionPort;
        this.runner = runner;
        this.sessionTtl = sessionTtl;
        this.retryLimit = retryLimit;
        this.rejectionGate = rejectionGate;
    }

    /** 执行登录：凭证验证（事务外）→ 统一签发事务 T2。 */
    public LoginResult login(ExternalLoginCommand command) {
        VerifiedExternalLogin external = externalLoginProvider.verify(command);

        RetryableTransactionConflictException lastConflict = null;
        for (int attempt = 1; attempt <= retryLimit; attempt++) {
            try {
                return runner.inTransaction(() -> issueInTransaction(external));
            } catch (RetryableTransactionConflictException conflict) {
                // 完整回滚已由 runner 保证；此处仅新事务重试（不重新兑换凭证）
                lastConflict = conflict;
            }
        }
        throw new IdentityConcurrencyException(
                "登录签发在 " + retryLimit + " 次事务重试后仍未收敛（绑定竞争/死锁）", lastConflict);
    }

    private LoginResult issueInTransaction(VerifiedExternalLogin external) {
        // W5-e ⓪①②：五元组键级 advisory + 锁内读——先于一切 binding/principal 语句
        // （§7.5(b) 锁序；命中活跃拒绝行 = 拒绝登录，不创建主体不签发会话）
        if (rejectionGate != null && rejectionGate.lockActive(external.identity()).isPresent()) {
            throw new com.gallop.fastddd.identity.core.error.AdmissionDeniedException(
                    "外部身份处于活跃拒绝期（上游确证停用/T4 受控停用），拒绝登录: "
                            + external.identity().externalId());
        }
        var principal = bindingStore.findOrCreatePrincipalWithBinding(
                external.identity(),
                new PrincipalCreationSpec(external.loginName(), external.displayName()));
        // 框架级状态门禁（不依赖宿主策略）：DISABLED 主体不签发——即使宿主策略放行
        if (principal.status() != com.gallop.fastddd.identity.core.domain.PrincipalStatus.ACTIVE) {
            throw new com.gallop.fastddd.identity.core.error.AdmissionDeniedException(
                    "主体已停用，拒绝签发：" + principal.principalId());
        }
        admissionPolicy.checkAdmission(principal);
        var session = sessionPort.issueSession(principal, sessionTtl);
        return new LoginResult(principal, session);
    }
}
