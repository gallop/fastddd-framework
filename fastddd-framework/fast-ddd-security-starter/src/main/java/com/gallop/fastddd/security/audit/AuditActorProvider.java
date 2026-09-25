package com.gallop.fastddd.security.audit;

/**
 * 审计 Actor 提供者（identity 模式桥接缝，v0.2 §5.6 / W2-1 修复轮 W21-04）。
 *
 * <p>由 identity C/Worker 装配（委托统一 {@code ActorResolver}）；存在时
 * {@link AuditLogAspect} 在<b>进入业务前</b>解析执行者——身份缺失抛异常
 * （业务前拒绝，不降级 anonymous），解析结果随 {@link AuditLogEntity} 捕获
 * （含异步落库路径，不延后到异步线程读取）。未装配（A/B/D 与目录单开）时
 * 切面保持旧行为（SecurityContext 名或 anonymous），旧消费者零变化。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface AuditActorProvider {

    /**
     * 解析当前执行者；身份缺失时抛出异常（由切面在业务执行前传播，拒绝业务）。
     */
    AuditActor resolveActor();

    /**
     * 统一执行者快照：{@code actorType} 词表与 identity {@code PrincipalType}
     * 同名（HUMAN / SYSTEM_JOB / SYSTEM），由 identity 桥写入。
     */
    record AuditActor(String tenantId, String principalId, String actorType) {
    }
}
