package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.port.IdentityBindingStore;
import com.gallop.fastddd.identity.core.port.IdentityLifecyclePort;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LocalSessionPort;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.LogoutUseCase;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.spring.actor.SecurityContextActorResolver;
import com.gallop.fastddd.identity.spring.security.CModeConditions;
import com.gallop.fastddd.identity.spring.tx.SpringIdentityTransactionRunner;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * identity 主体装配（v0.2 §4.5）：{@code fastddd.identity.enabled=true} 时提供
 * 统一 Actor 解析、事务 runner 桥接、员工身份 provider（C/Worker）与登录/登出/
 * 生命周期用例（C 模式，要求宿主/引用适配器提供端口 Bean）。
 *
 * <p><b>自动配置顺序（§4.5 显式声明，不靠 @CoM 猜顺序）</b>：本类先于
 * {@code FastDddCrudAutoConfiguration} 与 {@code FastDddSecurityAutoConfiguration}
 * 注册——identity 的 C/Worker provider 和 Actor 适配优先于 crud 默认 provider 与
 * security CRUD 桥接；A/B/D 非 C 保留原退让行为（本类整体退让于 identity 关闭）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(beforeName = {
        "com.gallop.fastddd.crud.FastDddCrudAutoConfiguration",
        "com.gallop.fastddd.security.FastDddSecurityAutoConfiguration"})
@ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(FastDddIdentityProperties.class)
public class IdentityAutoConfiguration {

    /** 统一 Actor 解析（§5.6）：SYSTEM_JOB 栈优先，其次 C 会话 Authentication。 */
    @Bean
    @ConditionalOnMissingBean(ActorResolver.class)
    SecurityContextActorResolver identityActorResolver() {
        return new SecurityContextActorResolver();
    }

    /**
     * T2/T3/T4 Spring 桥接：按名绑定业务 DataSource（多源须指名，不猜 @Primary/单候选）。
     */
    @Bean
    @ConditionalOnMissingBean(IdentityTransactionRunner.class)
    SpringIdentityTransactionRunner springIdentityTransactionRunner(
            ConfigurableListableBeanFactory beanFactory, FastDddIdentityProperties properties) {
        return new SpringIdentityTransactionRunner(
                com.gallop.fastddd.identity.spring.tx.IdentityDataSourceResolver
                        .requireByName(beanFactory, properties.getTransaction().getDataSourceBean()));
    }

    /** 默认准入策略：放行（状态门禁由用例框架级保证），宿主可替换收紧。 */
    @Bean
    @ConditionalOnMissingBean(AdmissionPolicy.class)
    AdmissionPolicy permitActiveAdmissionPolicy() {
        return principal -> {
        };
    }

    /** 登录用例（C 模式）：凭证验证在事务外；缺真实 ExternalLoginProvider 由终检拒绝启动。 */
    @Bean
    @Conditional(CModeConditions.class)
    @ConditionalOnBean({ExternalLoginProvider.class, IdentityBindingStore.class,
            LocalSessionPort.class, IdentityTransactionRunner.class})
    @ConditionalOnMissingBean
    LocalLoginUseCase localLoginUseCase(ExternalLoginProvider externalLoginProvider,
                                        IdentityBindingStore bindingStore,
                                        AdmissionPolicy admissionPolicy,
                                        LocalSessionPort localSessionPort,
                                        IdentityTransactionRunner runner,
                                        FastDddIdentityProperties properties) {
        return new LocalLoginUseCase(externalLoginProvider, bindingStore, admissionPolicy,
                localSessionPort, runner, properties.getLogin().getSession().getTtl());
    }

    /** 登出用例（T3 撤单，幂等）。 */
    @Bean
    @Conditional(CModeConditions.class)
    @ConditionalOnBean({LocalSessionPort.class, IdentityTransactionRunner.class})
    @ConditionalOnMissingBean
    LogoutUseCase logoutUseCase(LocalSessionPort localSessionPort, IdentityTransactionRunner runner) {
        return new LogoutUseCase(localSessionPort, runner);
    }

    /** 主体生命周期用例（T4 受控停用/启用）。 */
    @Bean
    @Conditional(CModeConditions.class)
    @ConditionalOnBean({IdentityLifecyclePort.class, IdentityTransactionRunner.class})
    @ConditionalOnMissingBean
    PrincipalLifecycleUseCase principalLifecycleUseCase(IdentityLifecyclePort lifecyclePort,
                                                        IdentityTransactionRunner runner) {
        return new PrincipalLifecycleUseCase(lifecyclePort, runner);
    }

    /** C/Worker 最终 Bean/路由检查（§4.5：防默认用户名认证与旧路由旁路）。 */
    @Bean
    IdentityModeValidator identityModeValidator(ConfigurableApplicationContext applicationContext,
                                                FastDddIdentityProperties properties) {
        return new IdentityModeValidator(applicationContext, properties);
    }

    /**
     * identity 自持认证管理器（W1 注意点 7 的 W2 决策落地）：C/Worker 模式下
     * security-starter 的 authenticationManager 随 jwt=false 退让，本 Bean 以 bean
     * 定义存在性使 Boot {@code UserDetailsServiceAutoConfiguration} 退让（杜绝默认
     * user 旁路）；会话认证不经它，调用即显式拒绝。仅 C/Worker 装配——目录单开
     * （A 形态宿主安全）不注册，避免与 security-starter 无条件 authenticationManager 冲突。
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityProviderConditions.class)
    static class IdentityAuthenticationManagerConfiguration {

        @Bean
        @ConditionalOnMissingBean(org.springframework.security.authentication.AuthenticationManager.class)
        org.springframework.security.authentication.AuthenticationManager identityAuthenticationManager() {
            return authentication -> {
                throw new org.springframework.security.authentication.AuthenticationServiceException(
                        "identity C/Worker 模式不提供用户名/密码认证管理器：会话认证由"
                                + " IdentitySessionAuthenticationFilter 每请求权威复验完成");
            };
        }
    }

    /**
     * CRUD CurrentUserProvider 桥（§4.5）：identity 员工身份 provider——C/Worker 装配，
     * String 协议不变返回 principalId；身份缺失拒绝（不降 system）。目录单开 web 不装。
     * 嵌套类 + 字符串条件，与 security-starter CrudBridgeConfiguration 同模式。
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityProviderConditions.class)
    @ConditionalOnClass(name = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
    static class IdentityCrudBridgeConfiguration {

        @Bean
        @ConditionalOnMissingBean(type = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
        com.gallop.fastddd.identity.spring.bridge.IdentityCurrentUserProvider identityCurrentUserProvider(
                ActorResolver actorResolver) {
            return new com.gallop.fastddd.identity.spring.bridge.IdentityCurrentUserProvider(actorResolver);
        }
    }
}
