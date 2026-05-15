package com.gallop.fastddd.security.spi;

import java.util.List;

/**
 * 权限服务 SPI：查询角色和权限
 *
 * <p>实现此接口以接入自定义 RBAC 表。默认实现会抛出异常提示配置。</p>
 *
 * @author gallop
 * @since 2026-04-19
 */
public interface SecurityPermissionService {

    /**
     * 根据用户ID查询权限码列表
     *
     * @param userId 用户ID
     * @return 权限码列表（如 system:user:view）
     */
    List<String> getPermissionsByUserId(String userId);

    /**
     * 根据用户ID查询角色标识列表
     *
     * @param userId 用户ID
     * @return 角色标识列表（如 ADMIN, USER）
     */
    List<String> getRolesByUserId(String userId);
}
