package com.gallop.fastddd.crud.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link DefaultCurrentUserProvider}的单元测试。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
class DefaultCurrentUserProviderTest {

    @Test
    void testGetCurrentUserId() {
        // Given
        DefaultCurrentUserProvider provider = new DefaultCurrentUserProvider();
        
        // When
        String userId = provider.getCurrentUserId();
        
        // Then
        assertNotNull(userId, "User ID should not be null");
        assertEquals("system", userId, "Default user ID should be 'system'");
    }
    
    @Test
    void testGetCurrentUserIdConsistency() {
        // Given
        DefaultCurrentUserProvider provider = new DefaultCurrentUserProvider();
        
        // When
        String userId1 = provider.getCurrentUserId();
        String userId2 = provider.getCurrentUserId();
        
        // Then
        assertEquals(userId1, userId2, "Multiple calls should return the same value");
    }
}