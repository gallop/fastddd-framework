package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryEventInboxAdmin;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.usecase.PrincipalLifecycleUseCase;
import com.gallop.fastddd.identity.spring.FastDddIdentityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * identity 本地生命周期管理端点装配（W3 设计 v3 §9.2–9.3，W3-c）：
 * C 模式（enabled + login.mode=feishu-web + {@code fastddd.identity.admin.enabled=true}）
 * + servlet web + Spring MVC + Jackson 且端口齐备时，注册
 * {@link IdentityAdminController}。
 *
 * <p><b>端口齐备条件</b>：{@link PrincipalLifecycleUseCase}（C 模式用例，经
 * {@code IdentityAutoConfiguration} 装配——先于本类注册）、{@link IdentityAdminPolicy}
 * 与 {@link IdentityAuditWriter}（宿主/引用适配器提供；admin.enabled=true 而缺任一
 * 由 {@code IdentityModeValidator} 终检使启动失败，fail-closed——审计行是成功契约
 * 一部分，对齐 W21-01 先例）、{@link ActorResolver}（identity 上下文兜底 Bean）。
 * 缺 {@code PrincipalLifecycleUseCase}（无 IdentityLifecyclePort 形态）时端点不注册，
 * 与登录端点的端口齐备语义一致。</p>
 *
 * <p><b>双链等效（§9.3）</b>：框架不向任何链添加 admin permitAll——默认链下两端点
 * 维持 anyRequest().authenticated()；宿主链下控制器级认证在场检查 + 逐操作策略
 * 裁决是两层链无关防线，CSRF 归宿主责任项。</p>
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
public class IdentityAdminAutoConfiguration {

    /** C 模式 + 管理开关 + 端口齐备才注册管理控制器（admin.enabled 缺省 false）。 */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityAdminAutoConfiguration.AdminEndpointConditions.class)
    @ConditionalOnBean({PrincipalLifecycleUseCase.class, IdentityAdminPolicy.class,
            IdentityAuditWriter.class, ActorResolver.class})
    static class IdentityAdminEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentityAdminController identityAdminController(PrincipalLifecycleUseCase lifecycleUseCase,
                                                       IdentityAdminPolicy adminPolicy,
                                                       IdentityAuditWriter auditWriter,
                                                       ActorResolver actorResolver) {
            return new IdentityAdminController(lifecycleUseCase, adminPolicy, auditWriter, actorResolver);
        }
    }

    /** login.mode=feishu-web ∧ admin.enabled=true（缺一即不装配管理端点；两开关独立，§9.2）。 */
    static final class AdminEndpointConditions extends AllNestedConditions {

        AdminEndpointConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
        static class CMode {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.admin", name = "enabled", havingValue = "true")
        static class AdminEnabled {
        }
    }

    /**
     * W5-d：目录增量事件管理端点（FAILED 显式重放）——C 模式 + admin.enabled +
     * increment.enabled 且四端口齐备（收件箱管理端口 + 策略/审计/Actor——
     * {@link DirectoryEventInboxAdmin} 由 reference-pg 提供）才注册；四态责任序
     * 同 {@link IdentityAdminController}。审计/策略端口缺任一同样由
     * {@code IdentityModeValidator} 终检 fail-closed（admin.enabled 侧既有检查覆盖）。
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(IdentityAdminAutoConfiguration.EventsAdminEndpointConditions.class)
    @ConditionalOnBean({DirectoryEventInboxAdmin.class, IdentityAdminPolicy.class,
            IdentityAuditWriter.class, ActorResolver.class})
    static class IdentityEventsAdminEndpointConfiguration {

        @Bean
        @ConditionalOnMissingBean
        IdentityDirectoryEventsAdminController identityDirectoryEventsAdminController(
                DirectoryEventInboxAdmin inboxAdmin,
                IdentityAdminPolicy adminPolicy,
                IdentityAuditWriter auditWriter,
                ActorResolver actorResolver) {
            return new IdentityDirectoryEventsAdminController(inboxAdmin, adminPolicy,
                    auditWriter, actorResolver);
        }
    }

    /** login.mode=feishu-web ∧ admin.enabled ∧ increment.enabled（W5-d，三者缺一即不装配）。 */
    static final class EventsAdminEndpointConditions extends AllNestedConditions {

        EventsAdminEndpointConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.login", name = "mode", havingValue = "feishu-web")
        static class CMode {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.admin", name = "enabled", havingValue = "true")
        static class AdminEnabled {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.directory.increment", name = "enabled",
                havingValue = "true")
        static class IncrementEnabled {
        }
    }
}
