package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * 飞书登录适配自动配置（W2-2）：{@code fastddd.identity.enabled=true} 且
 * {@code fastddd.identity.login.mode=feishu-web} 时装配真实
 * {@link ExternalLoginProvider}/{@link ExternalAuthorizePort}。
 *
 * <p><b>装配范围</b>：只在 C 模式装配——目录单开（mode=none）/Worker 不要求飞书
 * 凭据，本模块在 classpath 也不产生 Bean（identity.enabled=false 时整体退让）。
 * 必填配置缺失在 Bean 构造期启动失败（缺哪一项列出哪一项），不留"能启动但登录
 * 必败"形态。</p>
 *
 * <p><b>顺序</b>：先于 {@code IdentityAutoConfiguration} 注册——其登录用例与
 * {@code IdentityModeValidator} 终检以 @ConditionalOnBean/Bean 清单引用
 * ExternalLoginProvider，需要本类 Bean 定义先行（与 reference-pg 同模式）。</p>
 *
 * <p>宿主使用其他 IdP 时提供自己的 ExternalLoginProvider/ExternalAuthorizePort Bean，
 * 本类以 @ConditionalOnMissingBean 退让；测试桩仅存在于测试夹具（不进生产装配）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(beforeName = "com.gallop.fastddd.identity.spring.IdentityAutoConfiguration")
@Conditional(FeishuIdentityAutoConfiguration.FeishuLoginConditions.class)
@EnableConfigurationProperties(FeishuLoginProperties.class)
public class FeishuIdentityAutoConfiguration {

    /** 真实飞书适配器（provider + authorize 双端口实现）。 */
    @Bean
    @ConditionalOnMissingBean(ExternalLoginProvider.class)
    FeishuExternalLoginProvider feishuExternalLoginProvider(FeishuLoginProperties properties) {
        return new FeishuExternalLoginProvider(properties);
    }

    /**
     * 登录链路授权发起端口（与 provider 同实例；显式声明便于按类型注入与终检）。
     *
     * <p>W22-RV-07：参数用 {@code ObjectProvider} 而非具体类型——宿主自备
     * {@code ExternalLoginProvider} 时 feishu provider Bean 因
     * {@code @ConditionalOnMissingBean} 退让不创建，而本 Bean 条件（无 authorize
     * port）仍成立；具体类型注入会以 UnsatisfiedDependencyException 崩溃且报错
     * 不指向根因。改为显式判定缺失并给出可操作指引（fail-closed 但可诊断）。</p>
     */
    @Bean
    @ConditionalOnMissingBean(ExternalAuthorizePort.class)
    ExternalAuthorizePort feishuAuthorizePort(
            org.springframework.beans.factory.ObjectProvider<FeishuExternalLoginProvider> provider) {
        FeishuExternalLoginProvider feishuProvider = provider.getIfAvailable();
        if (feishuProvider == null) {
            throw new IllegalStateException("检测到宿主自定义 ExternalLoginProvider（fast-ddd-identity-feishu "
                    + "的提供者 Bean 已按 @ConditionalOnMissingBean 退让），但宿主未提供 ExternalAuthorizePort——"
                    + "浏览器登录闭环需要授权发起端口。请一并提供 ExternalAuthorizePort Bean，"
                    + "或宿主不打算用飞书授权页时移除 fast-ddd-identity-feishu 依赖");
        }
        return feishuProvider;
    }

    /** enabled=true AND login.mode=feishu-web（目录单开/Worker 不装配飞书凭据要求）。 */
    static class FeishuLoginConditions extends AllNestedConditions {

        public FeishuLoginConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
        static class IdentityEnabled {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
        static class LoginModeFeishuWeb {
        }
    }
}
