package com.gallop.fastddd.crud.provider;

/**
 * {@link CurrentUserProvider}的默认实现。
 * 当没有提供自定义实现时返回默认值'system'。
 *
 * 注意：此类不使用 @Component，由 {@link com.gallop.fastddd.crud.FastDddCrudAutoConfiguration}
 * 通过 @Bean + @ConditionalOnMissingBean + @Primary 注册，确保自定义实现可以正确替换。
 *
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
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