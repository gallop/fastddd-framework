package com.gallop.fastddd.identity.core.domain;

import java.util.Objects;

/**
 * 经外部登录提供者验证的外部身份（v0.2 §6 ExternalLoginProvider 返回内容）。
 *
 * <p>作用域唯一键五元组（provider, tenantId, appScope, externalIdType, externalId）
 * 与 external_binding 的 UNIQUE 约束一致——并发首次登录由该约束裁决。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ExternalIdentity {

    private final String provider;
    private final String tenantId;
    private final String appScope;
    private final String externalIdType;
    private final String externalId;

    public ExternalIdentity(String provider, String tenantId, String appScope,
                            String externalIdType, String externalId) {
        this.provider = requireNonBlank(provider, "provider");
        this.tenantId = requireNonBlank(tenantId, "tenantId");
        this.appScope = requireNonBlank(appScope, "appScope");
        this.externalIdType = requireNonBlank(externalIdType, "externalIdType");
        this.externalId = requireNonBlank(externalId, "externalId");
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ExternalIdentity." + name + " 不能为空");
        }
        return value;
    }

    public String provider() {
        return provider;
    }

    public String tenantId() {
        return tenantId;
    }

    public String appScope() {
        return appScope;
    }

    public String externalIdType() {
        return externalIdType;
    }

    public String externalId() {
        return externalId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExternalIdentity that)) {
            return false;
        }
        return provider.equals(that.provider) && tenantId.equals(that.tenantId) && appScope.equals(that.appScope)
                && externalIdType.equals(that.externalIdType) && externalId.equals(that.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, tenantId, appScope, externalIdType, externalId);
    }

    @Override
    public String toString() {
        return "ExternalIdentity{" + provider + '/' + tenantId + '/' + appScope
                + '/' + externalIdType + ':' + externalId + '}';
    }
}
