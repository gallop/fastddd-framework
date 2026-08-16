package com.gallop.fastddd.agentaccess.contract;

import java.time.OffsetDateTime;

/**
 * 目标侧已验证的调用责任链（Contract §5.1 权威字段表）。
 *
 * <p><b>表示已验证责任链，不是请求体自报身份。</b>受保护字段（tenantId、environment、
 * subjectId、actorId、workloadId、authenticationTime 等）只允许来自已验证许可或安全上下文；
 * 受治理的构造入口在 {@code .spi}（VerifiedCaller），payload 不得直接自报构造本对象参与授权。</p>
 *
 * <p>委托以 {@code delegatedBy} 最小引用承载（不携带完整委托图）；完整责任链投影属于审计
 * 事件模型（FDD-AA-13/14），本类型不引入 ActorChain wire 结构。</p>
 *
 * <p>紧致校验 fail-fast：必选字段缺失、未知 schemaVersion、可选字段提供但为空白均拒绝。
 * {@code SYSTEM_JOB} 是显式可审计的工作负载类型（不变量 4），本类型不提供任何匿名兜底
 * principal 表示（不变量 3）。</p>
 *
 * @param schemaVersion      wire 结构版本，首版 {@code "0.1"}（不得使用契约文档标识）
 * @param subjectId          最终承担业务责任的人、组织或服务主体 ID
 * @param subjectType        主体类型
 * @param actorId            代表 Subject 行动的逻辑主体 ID；Agent 场景为稳定 Agent ID
 * @param actorType          行动主体类型
 * @param workloadId         实际发送调用的 Egress/服务实例的稳定身份引用
 * @param tenantId           权威租户作用域
 * @param environment        隔离环境（dev、test、staging、prod 等；非封闭枚举，紧致非空校验）
 * @param delegatedBy        可选：最小委托人引用；不携带完整委托图
 * @param authenticationTime Workload/主体认证时间
 * @param identityEvidenceRef 可选：身份系统证据引用或摘要，不是 access token
 */
public record CallerContext(
        String schemaVersion,
        String subjectId,
        SubjectType subjectType,
        String actorId,
        ActorType actorType,
        String workloadId,
        String tenantId,
        String environment,
        String delegatedBy,
        OffsetDateTime authenticationTime,
        String identityEvidenceRef) {

    public CallerContext {
        requireSchemaVersion(schemaVersion);
        requireNonBlank("subjectId", subjectId);
        requireNonNull("subjectType", subjectType);
        requireNonBlank("actorId", actorId);
        requireNonNull("actorType", actorType);
        requireNonBlank("workloadId", workloadId);
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("environment", environment);
        if (delegatedBy != null && delegatedBy.isBlank()) {
            throw new IllegalArgumentException("delegatedBy 可选但提供时不得为空白");
        }
        requireNonNull("authenticationTime", authenticationTime);
        if (identityEvidenceRef != null && identityEvidenceRef.isBlank()) {
            throw new IllegalArgumentException("identityEvidenceRef 可选但提供时不得为空白");
        }
    }

    private static void requireSchemaVersion(String schemaVersion) {
        if (!AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException(
                    "schemaVersion 不受支持：期望 " + AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION
                            + "，实际 " + schemaVersion);
        }
    }

    private static void requireNonNull(String field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 必选");
        }
    }

    private static void requireNonBlank(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 必选且不得为空白");
        }
    }
}
