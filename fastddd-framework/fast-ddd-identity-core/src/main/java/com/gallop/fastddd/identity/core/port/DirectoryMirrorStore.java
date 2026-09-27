package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;

import java.util.List;

/**
 * 目录镜像存储端口（W3 设计 §2/§4/§5/§6；PG 实现 = W3-c）：代状态机、
 * 租约联合校验、观察快照与受保护事实层的全部持久化语义。
 *
 * <p><b>事务责任</b>：每个方法 = 一个短事务（经 {@code IdentityTransactionRunner}
 * 加入，写方法 MANDATORY）；handle 的联合校验与写入同事务原子。全部远程调用
 * 在事务外（本端口零远程）。</p>
 *
 * <p><b>联合校验（每个写方法，§6.1）</b>：
 * {@code metadata.lease_token == handle.leaseToken ∧ lease_expires_at > now() ∧ 代状态前置}
 * ——失败分别抛 {@code DirectoryLeaseLostException} /
 * {@code DirectoryLeaseExpiredException} / {@code DirectoryInvalidGenerationStateException}，
 * 拒绝时零写入。唯一例外：{@link #abandon} 仅要求 token 匹配（不要求未过期；
 * 被接管后拒绝，保护新持有者）。</p>
 *
 * <p><b>M2 能力分离（复评钉住事项 ①）</b>：有效视图 {@code readEffective} 是
 * <b>独立接口</b> {@link DirectoryEffectiveView}（M2 冻结签名），不在本端口上——
 * M1 装配既不要求其实现，本端口也不存在返回空集合冒充有效视图的默认方法。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryMirrorStore {

    /**
     * 获取代（acquire/takeover，T6a 短事务）：无有效租约时建 BUILDING 代 + 新 token；
     * 存在有效租约 → {@code DirectoryLeaseHeldException}（拒绝时零写入，
     * <b>不废弃他人的代</b>）；存在失效非终态代（任意非终态）→ 旧代置
     * ABANDONED('lease-takeover') + 新代插入，同一事务。
     */
    GenerationHandle beginGeneration(DirectoryScope scope);

    /**
     * 续租（T6b 页间短事务）：token 匹配 ∧ 未过期 → 延长 expires_at（token 不变）；
     * 已过期 → {@code DirectoryLeaseExpiredException}（不静默延期，须重新 acquire 走接管）。
     * 前置状态：BUILDING/VALIDATING。
     */
    Lease renewLease(GenerationHandle handle);

    /**
     * 写批（T6b）：联合校验（含 state=BUILDING ∧ 对应流未完成）通过后
     * INSERT 镜像批（携带 generation_id，仅新代可见）+ 检查点回写。
     * VALIDATED 后追加批次一律拒绝（矩阵 12）。拒绝时零行落库。
     */
    void writeBatch(GenerationHandle handle, DirectoryBatch batch);

    /**
     * 分页完成（三流 has_more=false）：BUILDING → VALIDATING，冻结 observed_scope。
     */
    void completePaging(GenerationHandle handle);

    /**
     * 校验（只读校验 + 置 VALIDATED）：三流分页均正常结束；任职关系引用的
     * 部门/人员均在本代内；范围外对象未混入；批次计数与检查点一致。
     * 任何一条不满足 → 抛 {@code DirectoryContractViolationException}（不置
     * VALIDATED；调用方走整代废弃）。通过后返回报告，此后禁止追加批次。
     */
    ValidationReport validate(GenerationHandle handle);

    /**
     * 发布（T7，唯一发布点，幂等）：VALIDATED 前置；已 PUBLISHED 且同代 id
     * → 幂等 no-op 成功（返回同值结果）。内容：联合校验 → H_before 读取 →
     * 事实同步（M1 = 仅追加新增对象键；已有键零修改；观察缺失且范围证据完整
     * → 仅置 pending_confirm）→ 有 INSERT 则 committed_seq = H_before+1 →
     * 切活动代指针、applied_seq = H_after、state→PUBLISHED、清租约。
     * 任一步失败全部回滚（零残留）。M1 无 T8 子步骤、无 publisher 调用。
     */
    DirectoryPublication publish(GenerationHandle handle);

    /**
     * 废弃（任意非终态，仅 token 匹配——不要求未过期）：置 ABANDONED(reason)、
     * 清租约；保留行排障。被接管后拒绝（token 不匹配 →
     * {@code DirectoryLeaseLostException}）。
     */
    void abandon(GenerationHandle handle, String reason);

    /**
     * 读观察快照（M1 唯一目录读接口，§6.6）：单事务读发布元数据
     * （active_generation + 水位快照）→ 按该 generation_id 读 raw + observed_scope。
     * <b>使用禁令：不得作授权/准入/身份状态依据；不得把缺失解释为删除。</b>
     * 从未发布过 → 返回空 activeGenerationId 的快照（或由适配器抛
     * UnsupportedOperationException 语义前须与调用方约定；M1 参考实现返回空值形态）。
     */
    ObservedSnapshot readObservation(DirectoryScope scope);

    /**
     * 读受保护事实层（只读，供差异报表/待核对清单）：对象键账本 + 受保护
     * 状态位视图。只进管理视图（经 §9.3 授权），不进授权路径。
     * 排序不作契约（适配器自定）；分页由 W3-c 参考实现决定（M1 全量返回可接受）。
     */
    List<ProtectedFactView> readProtectedFacts(DirectoryScope scope);
}
