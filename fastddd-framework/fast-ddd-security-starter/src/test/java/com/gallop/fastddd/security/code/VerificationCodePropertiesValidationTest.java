package com.gallop.fastddd.security.code;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VerificationCodeProperties} 绑定期校验单测：证明 {@code @Validated} 在属性绑定时实际生效，
 * 非法 length/maxAttempts 启动期明确失败。
 *
 * <p>对应验收 CFG-001-step4：声明式约束在绑定期生效。
 *
 * @author gallop
 */
class VerificationCodePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableCodeCfg.class);

    @Configuration
    @EnableConfigurationProperties(VerificationCodeProperties.class)
    static class EnableCodeCfg {
    }

    @Test
    void shouldBind_whenLengthAndAttemptsValid() {
        runner.withPropertyValues(
                        "fastddd.security.code.length=6",
                        "fastddd.security.code.max-attempts=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    VerificationCodeProperties props = context.getBean(VerificationCodeProperties.class);
                    assertThat(props.getLength()).isEqualTo(6);
                    assertThat(props.getMaxAttempts()).isEqualTo(2);
                });
    }

    @Test
    void shouldBind_whenDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            VerificationCodeProperties props = context.getBean(VerificationCodeProperties.class);
            assertThat(props.getLength()).isEqualTo(6);
            assertThat(props.getMaxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void shouldFailFast_whenLengthTooShort() {
        runner.withPropertyValues("fastddd.security.code.length=3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailFast_whenLengthTooLong() {
        runner.withPropertyValues("fastddd.security.code.length=11")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailFast_whenMaxAttemptsZero() {
        runner.withPropertyValues("fastddd.security.code.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
