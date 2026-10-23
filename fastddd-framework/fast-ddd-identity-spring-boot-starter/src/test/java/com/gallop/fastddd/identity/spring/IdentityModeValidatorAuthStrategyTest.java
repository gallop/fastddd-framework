package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W2-1 修复轮 W21-05：真实 {@code com.gallop.fastddd.security.strategy.AuthStrategy}
 * Bean 的负向装配终检——此前类型检查引用不存在的 {@code security.channel} 包，
 * ClassNotFoundException 被当作"组件未引入"吞掉，残留/自定义策略静默漏检
 * （local-auth=false 只关闭框架原装 Bean，不能证明残留策略也被拒绝）。
 *
 * <p>C、C+host-owned、Worker 三形态注入实际 AuthStrategy 均须启动失败并报告
 * 来源 Bean；无策略时正常启动（见既有各形态装配测试）。</p>
 */
class IdentityModeValidatorAuthStrategyTest {

    /** 真实接口类型的桩策略（security-starter 测试 classpath）。 */
    @Configuration(proxyBeanMethods = false)
    static class ResidualStrategyApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProvider();
        }

        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        @Bean
        com.gallop.fastddd.security.strategy.AuthStrategy residualAuthStrategy() {
            return new com.gallop.fastddd.security.strategy.AuthStrategy() {
                @Override
                public String getType() {
                    return "residual";
                }

                @Override
                public org.springframework.security.core.Authentication authenticate(
                        com.gallop.fastddd.security.strategy.AuthenticationRequest request) {
                    throw new UnsupportedOperationException("stub");
                }
            };
        }
    }

    @Test
    void cModeWithRealAuthStrategyBeanFailsStartupReportingBeanName() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(ResidualStrategyApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("residualAuthStrategy"));
    }

    @Test
    void hostOwnedCModeWithRealAuthStrategyBeanFailsStartupReportingBeanName() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(ResidualStrategyApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.login.mode=feishu-web",
                        "fastddd.identity.security.host-owned=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        // host-owned 未提供宿主链另有链终检违例；此处锁定策略 Bean 来源被报告
                        .getFailure().hasMessageContaining("residualAuthStrategy"));
    }

    @Test
    void workerWithRealAuthStrategyBeanFailsStartupReportingBeanName() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        IdentityModeGuardAutoConfiguration.class,
                        IdentityAutoConfiguration.class,
                        FastDddIdentityPgAutoConfiguration.class))
                .withUserConfiguration(ResidualStrategyApp.class)
                .withPropertyValues(
                        "fastddd.identity.enabled=true",
                        "fastddd.identity.worker=true",
                        "fastddd.security.jwt.enabled=false",
                        "fastddd.security.local-auth.enabled=false")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessageContaining("residualAuthStrategy"));
    }
}
