package com.gallop.fastddd.agentaccess.spi;

import java.util.Set;

/**
 * 数据范围义务（合成规则：求交——两侧允许的数据范围同时满足；交集为空即
 * 合成后无可允许范围，按 fail-closed 转 DENY）。
 */
public record DataScopeObligation(Set<String> allowedDataScopes) implements Obligation {

    public DataScopeObligation {
        if (allowedDataScopes == null || allowedDataScopes.isEmpty()) {
            throw new IllegalArgumentException("allowedDataScopes 必选且不得为空");
        }
        allowedDataScopes = Set.copyOf(allowedDataScopes);
    }

    @Override
    public String type() {
        return "DATA_SCOPE";
    }
}
