package com.gallop.fastddd.security.spi;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class DefaultSecurityPermissionService implements SecurityPermissionService {

    @Override
    public List<String> getPermissionsByUserId(Long userId) {
        throw new UnsupportedOperationException(
                "请配置 SecurityPermissionService Bean 或启用 RBAC 模块（security 模块 + 模板生成）");
    }

    @Override
    public List<String> getRolesByUserId(Long userId) {
        throw new UnsupportedOperationException(
                "请配置 SecurityPermissionService Bean 或启用 RBAC 模块（security 模块 + 模板生成）");
    }
}
