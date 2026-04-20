package com.gallop.fastddd.security.spi;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认身份服务实现（占位）
 *
 * <p>当项目中未提供 SecurityIdentityService Bean 时使用。
 * 会抛出 UnsupportedOperationException 引导用户配置。</p>
 *
 * @author gallop
 * @since 2026-04-19
 */
@Slf4j
public class DefaultSecurityIdentityService implements SecurityIdentityService {

    @Override
    public SecurityUserDetails loadUserByUsername(String username) {
        throw new UnsupportedOperationException(
                "请配置 SecurityIdentityService Bean 或启用 RBAC 模块（security 模块 + 模板生成）");
    }

    @Override
    public SecurityUserInfo getUserInfo(Long userId) {
        throw new UnsupportedOperationException(
                "请配置 SecurityIdentityService Bean 或启用 RBAC 模块（security 模块 + 模板生成）");
    }
}
