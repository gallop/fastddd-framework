package com.gallop.fastddd.security.spi;

/**
 * 身份服务 SPI：加载用户
 *
 * <p>实现此接口以接入自定义用户表。默认实现会抛出异常提示配置。</p>
 *
 * @author gallop
 * @since 2026-04-19
 */
public interface SecurityIdentityService {

    /**
     * 根据用户名加载用户身份信息
     *
     * @param username 用户名
     * @return 用户身份，不存在返回 null
     */
    SecurityUserDetails loadUserByUsername(String username);

    /**
     * 根据用户ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户信息，不存在返回 null
     */
    SecurityUserInfo getUserInfo(Long userId);
}
