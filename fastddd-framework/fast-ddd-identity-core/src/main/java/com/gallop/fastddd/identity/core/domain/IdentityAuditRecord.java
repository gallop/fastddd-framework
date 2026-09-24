package com.gallop.fastddd.identity.core.domain;

import java.time.Instant;

/**
 * 带 Actor 的身份审计记录（v0.2 §5.6）：由两种显式 SQL writer 之一写入
 * （仅旧列 / 旧列 + actor 列），随宿主审计策略（T5）。
 *
 * @author gallop
 * @since 1.1.0
 */
public record IdentityAuditRecord(String tenantId, PrincipalId actorPrincipalId, PrincipalType actorType,
                                  String operation, String detail, boolean success, String errorMessage,
                                  Instant occurredAt) {
}
