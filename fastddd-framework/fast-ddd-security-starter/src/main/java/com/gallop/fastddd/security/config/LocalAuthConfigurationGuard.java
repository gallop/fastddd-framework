package com.gallop.fastddd.security.config;

import org.springframework.core.env.Environment;

/**
 * W1 本地认证族配置冲突守卫（评审 R1 / 身份集成方案 §4.5）。
 *
 * <p>{@code fastddd.security.local-auth.enabled} 显式关闭框架本地认证族后，
 * 若 {@code fastddd.security.code.dev-mode} 仍显式开启该族的开发用 Noop 验证码
 * sender，属于自相矛盾的配置：dev-mode 验证码设施只服务于本地认证族。该组合
 * 在启动期失败并指名两个属性，避免"以为开了开发验证码、实际整族已关闭"的静默行为。</p>
 *
 * <p>守卫为 eager singleton，构造期即校验，冲突抛 {@link IllegalStateException}
 * 使上下文启动失败。其余身份组合校验（C 模式双开关、host-owned 声明、Worker 断言）
 * 依赖 identity-starter 的 {@code fastddd.identity.*} 属性，随 W2 落地，
 * 不在 security-starter 预实现。</p>
 *
 * @author gallop
 * @since 1.1.0（W1 安全装配解耦）
 */
public final class LocalAuthConfigurationGuard {

    static final String LOCAL_AUTH_ENABLED_PROPERTY = "fastddd.security.local-auth.enabled";
    static final String CODE_DEV_MODE_PROPERTY = "fastddd.security.code.dev-mode";

    private final Environment environment;

    public LocalAuthConfigurationGuard(Environment environment) {
        this.environment = environment;
        validate();
    }

    private void validate() {
        if (isExplicitly(LOCAL_AUTH_ENABLED_PROPERTY, "false") && isExplicitly(CODE_DEV_MODE_PROPERTY, "true")) {
            throw new IllegalStateException(String.format(
                    "FastDDD security 配置冲突：%s=false 已显式关闭框架本地认证族（登录/验证码控制器、"
                            + "认证策略、默认用户名认证与验证码设施），但 %s=true 仍显式开启该族的开发用"
                            + "Noop 验证码 sender，二者矛盾。修复：删除 %2$s=true，或将 %1$s 恢复为 true。"
                            + "宿主自用验证码服务不受本开关影响，请自行装配。",
                    LOCAL_AUTH_ENABLED_PROPERTY, CODE_DEV_MODE_PROPERTY));
        }
    }

    /** 与 @ConditionalOnProperty 的取值比较语义一致：忽略大小写的精确匹配，仅认显式配置值。 */
    private boolean isExplicitly(String property, String expected) {
        return environment.containsProperty(property)
                && expected.equalsIgnoreCase(environment.getProperty(property));
    }
}
