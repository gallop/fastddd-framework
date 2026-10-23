package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.error.ExternalLoginRejectedException;

/**
 * 外部登录提供者端口（v0.2 §6）：发起外部认证、服务端交换凭证，返回经验证的
 * ExternalIdentity——不返回未校验的客户端声明。
 *
 * <p>协议依据（CW-01 修订，W0-4 §1.1）：飞书实现为 OAuth 2.0 code 流
 * （authorize v1 页 + token/refresh v3 + user_info v1，非 OIDC），W2-2 交付。
 * 本轮无生产实现；测试专用桩只存在于测试源/隔离夹具，不得进入生产自动配置，
 * 不得新增"客户端提交 PrincipalId/ExternalIdentity 即登录"的公开接口。</p>
 *
 * <p>事务责任：全部远程调用在事务外（T2 之前完成）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface ExternalLoginProvider {

    /**
     * 验证外部凭证。
     *
     * @param command 不透明凭证载体
     * @return 经验证的外部身份与可读信息
     * @throws ExternalLoginRejectedException 用户状态/范围类拒绝（非重试类，按端点错误码分类）
     */
    VerifiedExternalLogin verify(ExternalLoginCommand command);
}
