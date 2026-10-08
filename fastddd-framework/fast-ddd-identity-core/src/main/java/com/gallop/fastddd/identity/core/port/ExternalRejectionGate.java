package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.RejectionRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * 外部拒绝门禁端口（v0.2 §8.2 / W5 设计 §3.3/§7.5，W5-b 签名冻结）：
 * external_rejection 表（V1 已交付 uk 五元组）的读写面——登录链路对
 * 明确停用外部身份的拒绝行写入与生效判定。
 *
 * <h2>advisory 锁契约（实现侧 W5-c 落地，签名层冻结）</h2>
 *
 * <p><b>⓪ 序锁</b>：所有写入路径（{@link #lockActive}/{@link #record}/
 * {@link #clearControlled}/{@link #clearByAuthoritativeRestoration}）的
 * <b>第一把锁</b> = {@code pg_advisory_xact_lock(ADVISORY_LOCK_CLASS,
 * hashtext(normalizedLockKey(identity)))}——双参 {@code _xact_} 事务作用域
 * （非 session 级）键级序列化：<b>行不存在亦持锁至事务结束、无行不插空行</b>；
 * {@link #lockActive} 锁内 {@code SELECT ... FOR UPDATE} 仅为读改写竞态防护，
 * 非串行化依赖。线性化语义 = INSERT principal 时点不存在"已提交但不可见"的
 * 拒绝行（READ_COMMITTED 语句快照可见性 + 未提交写入方被 advisory 锁阻塞，
 * 两向封死）。{@code hashtext} 碰撞仅致跨键过度串行化，不损正确性
 * （键空间为库级全局——类常量选取已避开既有占用；hashtext 系 PG 内部函数，
 * 跨大版本保持 int4 语义）。</p>
 *
 * <p><b>锁序（防死锁全域无环）</b>：登录签发事务 ⓪ advisory 锁 → 拒绝行 →
 * binding/principal → session（T2/T4 行为集成归 W5-e；T4 的
 * {@link #record} 语句位置钉死<b>先于</b>任何 binding/principal 语句）；
 * 回查链路 = metadata → advisory。{@link #findActive} 为<b>无锁普通读</b>，
 * 消费面 = readEffective 剔除（§8.1(c)）与非门禁性读取（诊断/报表等）——
 * 不得复用 {@link #lockActive}（防读路径持锁阻塞写方）；<b>登录复验在
 * {@link #lockActive} 事务内承载、非 findActive 消费者</b>（R3-P3-3）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface ExternalRejectionGate {

    /** advisory 锁类键（ASCII "FDDD" = 1,178,879,044；库级全局键空间）。 */
    int ADVISORY_LOCK_CLASS = 0x46444444;

    /**
     * 无锁普通读活跃拒绝行（消费面 = readEffective 剔除〔§8.1(c)〕与非门禁性
     * 读取〔诊断/报表等〕；不得复用 {@link #lockActive}——防读路径持锁阻塞
     * 写方。<b>登录复验在 {@link #lockActive} 事务内承载、非本方法消费者</b>
     * ——R3-P3-3）。
     */
    Optional<RejectionRecord> findActive(ExternalIdentity identity);

    /**
     * ⓪ 序锁定读活跃拒绝行：先取键级 advisory 事务锁（见类文档），锁内
     * FOR UPDATE 读行——无行返回 empty 且<b>不插空行</b>，锁仍持至事务结束。
     */
    Optional<RejectionRecord> lockActive(ExternalIdentity identity);

    /**
     * 写入拒绝行（幂等：重复写入落回既有行，不抛唯一冲突）。写入方三支
     * （登录链路上游明确停用 / 回查链路确证停用 / T4 本地受控停用——第三支
     * 随 W5-e T2 门禁集成同轮交付）；T4 调用面语句位置先于任何
     * binding/principal 语句（与登录同序，消除 ABBA 死锁面）。
     */
    RejectionRecord record(ExternalIdentity identity, String reason);

    /**
     * 受控解除（人工/管理端）：置 cleared_at；操作者与理由入审计日志不入表
     * （V1 无对应列）。列表重现不解除——仅本方法与
     * {@link #clearByAuthoritativeRestoration} 两支清除。
     *
     * @return 清除行数（0 = 无活跃行，1 = 已解除）
     */
    int clearControlled(ExternalIdentity identity, String operator, String reason);

    /**
     * 权威恢复清除（P-8 条件分支）：回查确证对象恢复（IDENTICAL/
     * CONTENT_DIFFERS 结论）时由回查提交事务调用，evidenceRef 指向
     * RECHECK_COMMITTED 载体条目。
     *
     * @return 清除行数（0 = 无活跃行，1 = 已解除）
     */
    int clearByAuthoritativeRestoration(ExternalIdentity identity, String evidenceRef);

    /**
     * 规范化锁键（R3-P3-2 冻结公式）：五元组按
     * {@code provider + '\n' + tenantId + '\n' + appScope + '\n' + externalIdType + '\n' + externalId}
     * 精确拼接——无大小写折叠、无裁剪，与 external_rejection uk 列值逐字节一致域。
     * 静态方法（域内公式可测），不构成实例签名面。
     */
    static String normalizedLockKey(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        return identity.provider() + '\n' + identity.tenantId() + '\n' + identity.appScope()
                + '\n' + identity.externalIdType() + '\n' + identity.externalId();
    }
}
