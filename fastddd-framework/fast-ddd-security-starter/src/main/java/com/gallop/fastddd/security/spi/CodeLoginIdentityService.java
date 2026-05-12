package com.gallop.fastddd.security.spi;

/**
 * 验证码登录身份服务 SPI。
 *
 * <p>业务项目需要按手机号或邮箱登录时可以实现此接口；未实现时框架会回退到
 * {@link SecurityIdentityService#loadUserByUsername(String)}。</p>
 *
 * @author gallop
 */
public interface CodeLoginIdentityService extends SecurityIdentityService {

    default SecurityUserDetails loadUserByPhone(String phone) {
        return loadUserByUsername(phone);
    }

    default SecurityUserDetails loadUserByEmail(String email) {
        return loadUserByUsername(email);
    }
}
