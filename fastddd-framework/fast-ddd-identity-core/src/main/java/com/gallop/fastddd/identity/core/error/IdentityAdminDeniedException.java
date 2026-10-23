package com.gallop.fastddd.identity.core.error;

/**
 * 管理操作授权被拒（W3 设计 §9.3，W3-R1）：{@code IdentityAdminPolicy}
 * 逐操作裁决拒绝（403 语义）。拒绝时操作不执行、principal/epoch/会话零变化；
 * 调用方须落结构化安全日志（字段白名单，不含镜像明细）。
 *
 * @author gallop
 * @since 1.1.0
 */
public class IdentityAdminDeniedException extends RuntimeException {

    private final String operation;
    private final String targetPrincipalId;

    public IdentityAdminDeniedException(String operation, String targetPrincipalId, String message) {
        super(message);
        this.operation = operation;
        this.targetPrincipalId = targetPrincipalId;
    }

    /** 被拒操作（{@link com.gallop.fastddd.identity.core.domain.IdentityAdminOperation} 名）。 */
    public String operation() {
        return operation;
    }

    /** 操作目标主体（可 null：SYNC_TRIGGER 等无固定目标的操作）。 */
    public String targetPrincipalId() {
        return targetPrincipalId;
    }
}
