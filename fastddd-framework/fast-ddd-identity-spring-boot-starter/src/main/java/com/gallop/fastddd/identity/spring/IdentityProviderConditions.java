package com.gallop.fastddd.identity.spring;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 员工身份 provider 装配条件（v0.2 §4.5）：C 模式（login.mode=feishu-web）
 * **或** identity Worker——两种形态装配 {@code IdentityCurrentUserProvider}；
 * 目录单开的 web 进程不装（沿用宿主安全与员工 provider）。
 *
 * @author gallop
 * @since 1.1.0
 */
class IdentityProviderConditions extends AnyNestedCondition {

    IdentityProviderConditions() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
    static class LoginModeFeishuWeb {
    }

    @ConditionalOnProperty(prefix = "fastddd.identity", name = "worker", havingValue = "true")
    static class WorkerEnabled {
    }
}
