package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证策略注册中心
 * Spring 自动注入所有 AuthStrategy 实现，应用层只需 @Component 标注策略类即可自动注册
 * <p>
 * 当存在同 type 的策略冲突时，优先保留非框架包（非 com.gallop.fastddd.security.strategy）的实现，
 * 确保用户自定义策略能覆盖框架默认策略。
 *
 * @author gallop
 * @date 2026-04-21
 */
@Slf4j
public class AuthStrategyRegistry {

    private static final String FRAMEWORK_STRATEGY_PACKAGE = "com.gallop.fastddd.security.strategy";

    private final Map<String, AuthStrategy> strategyMap;

    public AuthStrategyRegistry(List<AuthStrategy> strategies) {
        this.strategyMap = (strategies != null ? strategies : Collections.<AuthStrategy>emptyList())
                .stream()
                .collect(Collectors.toMap(
                        AuthStrategy::getType,
                        Function.identity(),
                        (existing, replacement) -> resolveDuplicate(existing, replacement)
                ));
    }

    private AuthStrategy resolveDuplicate(AuthStrategy existing, AuthStrategy replacement) {
        boolean existingIsFramework = isFrameworkStrategy(existing);
        boolean replacementIsFramework = isFrameworkStrategy(replacement);

        // User bean always wins over framework bean
        if (existingIsFramework && !replacementIsFramework) {
            log.info("AuthStrategy type '{}': framework {} replaced by user-defined {}",
                    existing.getType(),
                    existing.getClass().getSimpleName(),
                    replacement.getClass().getSimpleName());
            return replacement;
        }
        if (!existingIsFramework && replacementIsFramework) {
            log.info("AuthStrategy type '{}': user-defined {} kept, framework {} ignored",
                    existing.getType(),
                    existing.getClass().getSimpleName(),
                    replacement.getClass().getSimpleName());
            return existing;
        }

        // Both user or both framework — last one wins (original behavior)
        log.warn("Duplicate AuthStrategy type '{}': {} overridden by {}",
                existing.getType(),
                existing.getClass().getSimpleName(),
                replacement.getClass().getSimpleName());
        return replacement;
    }

    private boolean isFrameworkStrategy(AuthStrategy strategy) {
        return strategy.getClass().getPackageName().startsWith(FRAMEWORK_STRATEGY_PACKAGE);
    }

    public AuthStrategy getStrategy(String type) {
        return strategyMap.get(type);
    }

    public List<AuthStrategy> getStrategiesForChannel(AuthChannel channel) {
        return strategyMap.values().stream()
                .filter(s -> s.supports(channel))
                .collect(Collectors.toList());
    }
}
