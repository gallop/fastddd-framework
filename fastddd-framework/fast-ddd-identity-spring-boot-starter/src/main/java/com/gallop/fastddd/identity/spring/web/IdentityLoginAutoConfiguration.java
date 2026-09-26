package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.port.LoginStateStore;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.core.usecase.LogoutUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * identity 浏览器登录端点装配（W2-2）：C 模式（enabled + login.mode=feishu-web）
 * + servlet web + Spring MVC + Jackson 且授权发起/状态存储端口齐备时，注册
 * {@link IdentityLoginService} 与 {@link IdentityLoginController}。
 *
 * <p><b>端口齐备条件（不破坏既有 C 场景）</b>：{@link ExternalAuthorizePort} 与
 * {@link LoginStateStore} 由真实适配器模块提供（fast-ddd-identity-feishu +
 * reference-pg，或宿主自实现）；未提供时登录端点不注册（既有 C 场景——宿主自有
 * 登录入口/仅会话复验——行为不变），且 {@code IdentityModeValidator} 在存在
 * 授权端口而缺状态存储时启动失败（防"以为有登录闭环实际 404"）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(afterName = "com.gallop.fastddd.identity.spring.IdentityAutoConfiguration")
@ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {"org.springframework.web.servlet.DispatcherServlet",
        "com.fasterxml.jackson.databind.ObjectMapper"})
@EnableConfigurationProperties(FastDddIdentityProperties.class)
public class IdentityLoginAutoConfiguration {

    /** C 模式 + 端口齐备才注册浏览器登录链。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
    @ConditionalOnBean({ExternalAuthorizePort.class, LoginStateStore.class,
            LocalLoginUseCase.class, LogoutUseCase.class, IdentityTransactionRunner.class})
    static class IdentityLoginEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentityLoginService identityLoginService(ExternalAuthorizePort authorizePort,
                                                  LoginStateStore loginStateStore,
                                                  LocalLoginUseCase loginUseCase,
                                                  LogoutUseCase logoutUseCase,
                                                  IdentityTransactionRunner runner,
                                                  FastDddIdentityProperties properties) {
            return new IdentityLoginService(authorizePort, loginStateStore, loginUseCase,
                    logoutUseCase, runner, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        IdentityLoginController identityLoginController(IdentityLoginService loginService,
                                                        FastDddIdentityProperties properties) {
            return new IdentityLoginController(loginService, properties);
        }
    }
}
