package com.gallop.fastddd.crud.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * BasePO中元数据字段的自动填充处理器。
 * 在插入和更新操作期间自动填充createTime、updateTime、createUser和updateUser字段。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@RequiredArgsConstructor
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    private final CurrentUserProvider currentUserProvider;

    /**
     * Field names for auto-filling.
     */
    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";
    private static final String CREATE_USER = "createUser";
    private static final String UPDATE_USER = "updateUser";

    /**
     * Auto-fill logic for insert operations.
     * Sets createTime, updateTime, createUser, and updateUser.
     * 
     * @param metaObject the meta object containing field information
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("Auto-filling fields for insert operation");
        
        LocalDateTime now = LocalDateTime.now();
        String currentUser = currentUserProvider.getCurrentUserId();
        
        // Fill create and update time
        if (metaObject.hasSetter(CREATE_TIME)) {
            metaObject.setValue(CREATE_TIME, now);
        }
        if (metaObject.hasSetter(UPDATE_TIME)) {
            metaObject.setValue(UPDATE_TIME, now);
        }
        
        // Fill create and update user
        if (metaObject.hasSetter(CREATE_USER)) {
            metaObject.setValue(CREATE_USER, currentUser);
        }
        if (metaObject.hasSetter(UPDATE_USER)) {
            metaObject.setValue(UPDATE_USER, currentUser);
        }
        
        log.debug("Auto-filled fields: createTime={}, updateTime={}, createUser={}, updateUser={}", 
                 now, now, currentUser, currentUser);
    }

    /**
     * Auto-fill logic for update operations.
     * Sets updateTime and updateUser.
     * 
     * @param metaObject the meta object containing field information
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("Auto-filling fields for update operation");
        
        LocalDateTime now = LocalDateTime.now();
        String currentUser = currentUserProvider.getCurrentUserId();
        
        // Fill update time and user
        if (metaObject.hasSetter(UPDATE_TIME)) {
            metaObject.setValue(UPDATE_TIME, now);
        }
        if (metaObject.hasSetter(UPDATE_USER)) {
            metaObject.setValue(UPDATE_USER, currentUser);
        }
        
        log.debug("Auto-filled fields: updateTime={}, updateUser={}", now, currentUser);
    }
}