package com.gallop.fastddd.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动期拒绝已废弃的旧配置根 {@code fast-ddd.*}（带连字符）。
 *
 * <p>FastDDD 配置根统一为 {@code fastddd.*}（无连字符）。旧根 {@code fast-ddd.*}
 * 与 Java 属性类的前缀不匹配，会被 Spring 静默忽略，导致"配置看似生效实则未生效"
 * （例如消费者误以为 {@code fast-ddd.event.outbox.enabled=true} 启用了 Outbox，实际被忽略）。
 *
 * <p>本 {@link EnvironmentPostProcessor} 在应用配置加载之后（{@link Ordered#LOWEST_PRECEDENCE}）
 * 扫描全部可枚举属性源，发现任何 {@code fast-ddd.*} 键即抛 {@link IllegalStateException}，
 * 明确失败并给出迁移指引。
 *
 * <p>对应验收 CFG-001②：fast-ddd 旧根出现时明确失败。
 *
 * <p>注意：前缀含点（{@code fast-ddd.}），不会误匹配 {@code fast-ddd-common} 等
 * Maven artifactId（它们不会作为属性键出现）。
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class FastDddLegacyRootValidator implements EnvironmentPostProcessor {

    /** 已废弃的旧配置根前缀（带连字符 + 点）。 */
    static final String LEGACY_ROOT = "fast-ddd.";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> legacyKeys = new ArrayList<>();
        environment.getPropertySources().forEach(propertySource -> {
            if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith(LEGACY_ROOT)) {
                        legacyKeys.add(name);
                    }
                }
            }
        });
        if (!legacyKeys.isEmpty()) {
            throw new IllegalStateException(
                    "FastDDD 检测到已废弃的配置根 '" + LEGACY_ROOT + "*'（带连字符），"
                            + "这些键将被忽略。正确配置根为 'fastddd.*'（无连字符）。"
                            + "请迁移以下键：" + legacyKeys + "。"
                            + "示例：fast-ddd.security.jwt.secret → fastddd.security.jwt.secret");
        }
    }
}
