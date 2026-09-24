package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 经登录验证后框架内唯一的主体表达（v0.2 §5.1）：principalId 为内部稳定编号，
 * loginName/displayName 为可读形式；authEpoch 随停用递增（§7）。
 *
 * @author gallop
 * @since 1.1.0
 */
public final class VerifiedPrincipal {

    private final String tenantId;
    private final PrincipalId principalId;
    private final String loginName;
    private final String displayName;
    private final PrincipalType principalType;
    private final PrincipalStatus status;
    private final long authEpoch;

    public VerifiedPrincipal(String tenantId, PrincipalId principalId, String loginName, String displayName,
                             PrincipalType principalType, PrincipalStatus status, long authEpoch) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.principalId = Objects.requireNonNull(principalId, "principalId");
        this.loginName = loginName;
        this.displayName = displayName;
        this.principalType = Objects.requireNonNull(principalType, "principalType");
        this.status = Objects.requireNonNull(status, "status");
        this.authEpoch = authEpoch;
    }

    public String tenantId() {
        return tenantId;
    }

    public PrincipalId principalId() {
        return principalId;
    }

    public String loginName() {
        return loginName;
    }

    public String displayName() {
        return displayName;
    }

    public PrincipalType principalType() {
        return principalType;
    }

    public PrincipalStatus status() {
        return status;
    }

    public long authEpoch() {
        return authEpoch;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VerifiedPrincipal && principalId.equals(((VerifiedPrincipal) o).principalId);
    }

    @Override
    public int hashCode() {
        return principalId.hashCode();
    }

    @Override
    public String toString() {
        return "VerifiedPrincipal{" + principalId + ", type=" + principalType + ", status=" + status
                + ", epoch=" + authEpoch + "}";
    }
}
