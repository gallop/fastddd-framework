package com.gallop.fastddd.crud;

import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.gallop.fastddd.crud.handler.AutoFillMetaObjectHandler;
import com.gallop.fastddd.crud.injector.FastDddSqlInjector;
import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.crud.provider.DefaultCurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FastDddCrudAutoConfiguration的测试类。
 * 验证自动配置类是否正确注册了所有必要的Bean。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootTest
@ActiveProfiles("test")
class FastDddCrudAutoConfigurationTest {

    @Autowired
    private CurrentUserProvider currentUserProvider;
    
    @Autowired
    private AutoFillMetaObjectHandler autoFillMetaObjectHandler;
    
    @Autowired
    private ISqlInjector sqlInjector;
    
    @Test
    void testCurrentUserProviderBeanIsRegistered() {
        assertNotNull(currentUserProvider, "CurrentUserProvider bean should be registered");
        assertTrue(currentUserProvider instanceof DefaultCurrentUserProvider, 
                "Should be DefaultCurrentUserProvider instance");
        assertEquals("system", currentUserProvider.getCurrentUserId(), 
                "Default user should be 'system'");
    }
    
    @Test
    void testAutoFillMetaObjectHandlerBeanIsRegistered() {
        assertNotNull(autoFillMetaObjectHandler, "AutoFillMetaObjectHandler bean should be registered");
        assertTrue(autoFillMetaObjectHandler instanceof AutoFillMetaObjectHandler, 
                "Should be AutoFillMetaObjectHandler instance");
    }
    
    @Test
    void testSqlInjectorBeanIsRegistered() {
        assertNotNull(sqlInjector, "ISqlInjector bean should be registered");
        assertTrue(sqlInjector instanceof FastDddSqlInjector, 
                "Should be FastDddSqlInjector instance");
    }
    
    @Test
    void testAutoConfigurationIntegration() {
        // 验证所有组件都能正常协作
        assertNotNull(currentUserProvider, "CurrentUserProvider should be available");
        assertNotNull(autoFillMetaObjectHandler, "AutoFillMetaObjectHandler should be available");
        assertNotNull(sqlInjector, "ISqlInjector should be available");
        
        // 验证CurrentUserProvider能正常工作
        String userId = currentUserProvider.getCurrentUserId();
        assertNotNull(userId, "User ID should not be null");
        assertFalse(userId.trim().isEmpty(), "User ID should not be empty");
    }
}