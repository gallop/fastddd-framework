package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.channel.AuthChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证策略注册中心
 * Spring 自动注入所有 AuthStrategy 实现，应用层只需 @Component 标注策略类即可自动注册
 *
 * @author gallop
 * @date 2026-04-21
 */
@Slf4j
@Component
public class AuthStrategyRegistry {

    private final Map<String, AuthStrategy> strategyMap;

    public AuthStrategyRegistry(@Autowired(required = false) List<AuthStrategy> strategies) {
        this.strategyMap = (strategies != null ? strategies : Collections.<AuthStrategy>emptyList())
                .stream()
                .collect(Collectors.toMap(
                        AuthStrategy::getType,
                        Function.identity(),
                        (existing, replacement) -> {
                            log.warn("Duplicate AuthStrategy type '{}': {} overridden by {}",
                                    existing.getType(),
                                    existing.getClass().getSimpleName(),
                                    replacement.getClass().getSimpleName());
                            return replacement;
                        }
                ));
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
