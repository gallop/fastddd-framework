package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 统一 Actor 值对象（v0.2 §5.6）：CRUD 自动填充与身份模式审计共用的操作者表达。
 *
 * @author gallop
 * @since 1.1.0
 */
public final class Actor {

    private final String tenantId;
    private final PrincipalId principalId;
    private final PrincipalType principalType;

    public Actor(String tenantId, PrincipalId principalId, PrincipalType principalType) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.principalId = Objects.requireNonNull(principalId, "principalId");
        this.principalType = Objects.requireNonNull(principalType, "principalType");
    }

    public String tenantId() {
        return tenantId;
    }

    public PrincipalId principalId() {
        return principalId;
    }

    public PrincipalType principalType() {
        return principalType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Actor && principalId.equals(((Actor) o).principalId);
    }

    @Override
    public int hashCode() {
        return principalId.hashCode();
    }

    @Override
    public String toString() {
        return "Actor{" + principalId + ", type=" + principalType + '}';
    }
}
