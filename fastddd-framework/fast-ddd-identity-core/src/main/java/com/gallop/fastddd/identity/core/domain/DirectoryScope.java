package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 目录同步范围（W3 设计 §1：单企业 = 单 tenant_key + 单 app_scope；W0-2 §1）。
 *
 * <p>同一进程同一时刻只存在一个同步范围（M1 冻结）；租约、代、水位、事实层
 * 全部以本值为作用域键。{@code tenantKey} 对应存储列 {@code tenant_id}，
 * {@code appScope} 对应 {@code app_scope}（五元组绑定的应用范围维度）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryScope {

    private final String tenantKey;
    private final String appScope;

    public DirectoryScope(String tenantKey, String appScope) {
        this.tenantKey = Objects.requireNonNull(tenantKey, "tenantKey");
        this.appScope = Objects.requireNonNull(appScope, "appScope");
    }

    public String tenantKey() {
        return tenantKey;
    }

    public String appScope() {
        return appScope;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DirectoryScope
                && tenantKey.equals(((DirectoryScope) o).tenantKey)
                && appScope.equals(((DirectoryScope) o).appScope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantKey, appScope);
    }

    @Override
    public String toString() {
        return tenantKey + "/" + appScope;
    }
}
