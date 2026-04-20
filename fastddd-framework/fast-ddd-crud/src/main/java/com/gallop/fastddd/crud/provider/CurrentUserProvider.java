package com.gallop.fastddd.crud.provider;

/**
 * 提供当前用户标识符的函数式接口。
 * 用于在插入和更新操作期间填充BasePO中的'createUser'和'updateUser'字段。
 * 
 * 应用程序可以提供此接口的自定义实现作为Spring bean
 * 来覆盖默认行为（例如，与Spring Security集成以获取认证用户）。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@FunctionalInterface
public interface CurrentUserProvider {

    /**
     * Get the current user's identifier.
     * 
     * @return the current user's ID, or a default value if no user is authenticated
     */
    String getCurrentUserId();
}