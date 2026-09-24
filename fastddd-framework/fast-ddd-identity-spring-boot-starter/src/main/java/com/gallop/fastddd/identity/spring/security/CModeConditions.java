package com.gallop.fastddd.identity.spring.security;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * C 模式 Bean 级组合条件：{@code fastddd.identity.enabled=true} AND
 * {@code fastddd.identity.login.mode=feishu-web}（v0.2 §4.3 条 1）。
 *
 * <p>REGISTER_BEAN 阶段（仅用于 @Bean 方法；外层配置类已按 enabled 门控）。
 * 属性级冲突（双开关显式关闭等）已由 {@code IdentityModeGuard} 前置拦截。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public class CModeConditions extends AllNestedConditions {

    public CModeConditions() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
    static class IdentityEnabled {
    }

    @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
    static class LoginModeFeishuWeb {
    }
}
