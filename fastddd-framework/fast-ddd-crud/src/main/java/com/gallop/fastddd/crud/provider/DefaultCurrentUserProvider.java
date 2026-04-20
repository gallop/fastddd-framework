package com.gallop.fastddd.crud.provider;

import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider}的默认实现。
 * 当没有提供自定义实现时返回默认值'system'。
 * 
 * 此实现可以通过在应用程序上下文中提供自定义的
 * {@link CurrentUserProvider} bean来覆盖。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Component
public class DefaultCurrentUserProvider implements CurrentUserProvider {

    /**
     * Default user identifier used when no authenticated user is available.
     */
    private static final String DEFAULT_USER = "system";

    /**
     * Returns the default user identifier 'system'.
     * 
     * @return the default user ID 'system'
     */
    @Override
    public String getCurrentUserId() {
        return DEFAULT_USER;
    }
}