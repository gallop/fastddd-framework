package com.gallop.fastddd.agentaccess.kit;

import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.permit.DefaultExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.permit.LocalPermitIssuer;
import com.gallop.fastddd.agentaccess.spi.CallerAuthenticationException;
import com.gallop.fastddd.agentaccess.spi.CallerIdentityBridge;
import com.gallop.fastddd.agentaccess.spi.ExecutionPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.PermitIssuer;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.PermitBackedVerifiedCaller;
import com.gallop.fastddd.agentaccess.spi.VerifiedCaller;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentAccessAssemblyValidator 首版装配门禁测试。
 *
 * <p>说明：Boot 的 {@code @ConditionalOnMissingBean} 等 meta 注解不在 kit 依赖内
 * （kit 只依赖 spring-context）；其检出路径与直接 {@code @Conditional} 相同
 * （findAnnotationOnBean 的 @Conditional 查找覆盖 meta 注解），故以
 * {@code @Conditional} 代表整个家族验证。</p>
 */
class AgentAccessAssemblyValidatorTest {

    private static final OffsetDateTime AUTH_TIME = OffsetDateTime.parse("2026-08-16T03:00:00Z");

    /** 合法人工 Caller（桥接返回值样板，仅用于装配测试）。 */
    static CallerContext humanCaller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice", SubjectType.HUMAN,
                "subject-alice", ActorType.HUMAN,
                "workload-host-1", "acme", "dev",
                null, AUTH_TIME, null);
    }

    /** 显式完整装配：必需 Bean + 唯一性 Bean + 校验器 → 启动成功。 */
    @Configuration
    static class FullAssembly {
        @Bean
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        CallerIdentityBridge callerIdentityBridge() {
            return () -> { throw new CallerAuthenticationException("宿主未提供已验证上下文"); };
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    /** 缺 CallerIdentityBridge → 启动 fail-fast 并指名缺失类型。 */
    @Configuration
    static class MissingBridge {
        @Bean
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    /** 双 PermitIssuer 实现 → 启动 fail-fast（受控入口禁止第二实现）。 */
    @Configuration
    static class DuplicateIssuer {
        @Bean
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        CallerIdentityBridge callerIdentityBridge() {
            return () -> humanCaller();
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        PermitIssuer rogueSecondIssuer() {
            return permit -> null;
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    /** 忺需 Bean 以 @Conditional 家族声明（条件为真，Bean 存在）→ 启动 fail-fast 并指名。 */
    @Configuration
    static class ConditionalDeclaration {
        @Bean
        @Conditional(AlwaysMatchCondition.class)
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        CallerIdentityBridge callerIdentityBridge() {
            return () -> humanCaller();
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    /** 忺需 Bean 以 @Primary 声明 → 启动 fail-fast 并指名。 */
    @Configuration
    static class PrimaryDeclaration {
        @Bean
        @Primary
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        CallerIdentityBridge callerIdentityBridge() {
            return () -> humanCaller();
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    @Test
    void fullExplicitAssemblyStarts() {
        try (var ctx = new AnnotationConfigApplicationContext(FullAssembly.class)) {
            assertThat(ctx.getBean(VerifiedCaller.class)).isInstanceOf(PermitBackedVerifiedCaller.class);
            assertThat(ctx.getBean(CallerIdentityBridge.class)).isNotNull();
            assertThat(ctx.getBean(ExecutionPermitVerifier.class))
                    .isInstanceOf(DefaultExecutionPermitVerifier.class);
            assertThat(ctx.getBeansOfType(PermitIssuer.class)).hasSize(1);
        }
    }

    @Test
    void duplicatePermitIssuerFailsFast() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(DuplicateIssuer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(PermitIssuer.class.getName())
                .hasMessageContaining("禁止宿主自造第二实现");
    }

    @Test
    void missingRequiredBeanFailsFastNamingType() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(MissingBridge.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CallerIdentityBridge.class.getName())
                .hasMessageContaining("必需 Bean 缺失");
    }

    @Test
    void conditionalDeclarationFailsFastNamingBean() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(ConditionalDeclaration.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verifiedCaller")
                .hasMessageContaining("@Conditional");
    }

    @Test
    void primaryDeclarationFailsFastNamingBean() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(PrimaryDeclaration.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verifiedCaller")
                .hasMessageContaining("@Primary");
    }

    @Test
    void emptyRequiredListIsRejectedAtConstruction() {
        var bf = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        assertThatThrownBy(() -> new AgentAccessAssemblyValidator(bf, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不得为空");
    }

    @Test
    void shrinkingBelowDefaultRequiredTypesIsRejectedAtConstruction() {
        var bf = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        // 非空但移除 CallerIdentityBridge（收窄）→ 构造失败
        assertThatThrownBy(() -> new AgentAccessAssemblyValidator(bf,
                        java.util.List.of(VerifiedCaller.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只允许在默认清单上扩充")
                .hasMessageContaining(CallerIdentityBridge.class.getName());
    }

    @Test
    void expandingBeyondDefaultRequiredTypesIsAccepted() {
        var bf = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        // 默认清单 + 追加类型（扩充）→ 构造成功（后续任务按此模式扩充清单）
        var expanded = new java.util.ArrayList<>(AgentAccessAssemblyValidator.DEFAULT_REQUIRED_BEAN_TYPES);
        expanded.add(String.class);
        new AgentAccessAssemblyValidator(bf, expanded);
    }

    /** 外围 @Configuration 类携带 @Conditional（条件真、Bean 实际注册）→ 启动 fail-fast。 */
    @org.springframework.context.annotation.Configuration
    @Conditional(AlwaysMatchCondition.class)
    static class ConditionalOuterConfig {
        @Bean
        VerifiedCaller verifiedCaller() {
            return new PermitBackedVerifiedCaller();
        }

        @Bean
        CallerIdentityBridge callerIdentityBridge() {
            return () -> humanCaller();
        }

        @Bean
        ExecutionPermitVerifier executionPermitVerifier() {
            return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                    java.time.Clock.systemUTC(),
                    java.util.Set.of("issuer-fastagent-kernel"),
                    "audience-fastddd-ar",
                    java.time.Duration.ofSeconds(30),
                    false));
        }

        @Bean
        PermitIssuer permitIssuer() {
            return new LocalPermitIssuer();
        }

        @Bean
        AgentAccessAssemblyValidator agentAccessAssemblyValidator(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
            return new AgentAccessAssemblyValidator(bf);
        }
    }

    @Test
    void conditionalOuterConfigurationFailsFast() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(ConditionalOuterConfig.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("verifiedCaller")
                .hasMessageContaining("@Conditional");
    }
}
