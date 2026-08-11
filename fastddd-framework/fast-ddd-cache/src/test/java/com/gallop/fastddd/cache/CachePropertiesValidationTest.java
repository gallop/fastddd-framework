package com.gallop.fastddd.cache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CacheProperties} 绑定期校验单测：证明 {@code @Validated} 在属性绑定时实际生效，
 * 非法值（maxSize &lt; 1）启动期明确失败。
 *
 * <p>对应验收 CFG-001-step4：声明式约束在绑定期生效。
 *
 * @author gallop
 */
class CachePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableCacheCfg.class);

    @Configuration
    @EnableConfigurationProperties(CacheProperties.class)
    static class EnableCacheCfg {
    }

    @Test
    void shouldBind_whenMaxSizeValid() {
        runner.withPropertyValues("fastddd.cache.local.max-size=1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CacheProperties props = context.getBean(CacheProperties.class);
                    assertThat(props.getLocal().getMaxSize()).isEqualTo(1L);
                });
    }

    @Test
    void shouldBind_whenMaxSizeDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CacheProperties.class).getLocal().getMaxSize()).isEqualTo(10000L);
        });
    }

    @Test
    void shouldFailFast_whenMaxSizeZero() {
        runner.withPropertyValues("fastddd.cache.local.max-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(mentionsSizeField(context.getStartupFailure()))
                            .as("失败信息应指向 maxSize/max-size 约束违反，实际：%s",
                                    context.getStartupFailure().getMessage())
                            .isTrue();
                });
    }

    @Test
    void shouldFailFast_whenMaxSizeNegative() {
        runner.withPropertyValues("fastddd.cache.local.max-size=-5")
                .run(context -> assertThat(context).hasFailed());
    }

    /** 沿因果链查找指向 maxSize 字段的失败描述（字段名与语言无关，避免依赖 Hibernate 的本地化文案）。 */
    private static boolean mentionsSizeField(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && (msg.contains("maxSize") || msg.contains("max-size") || msg.contains("max.size"))) {
                return true;
            }
        }
        return false;
    }
}
