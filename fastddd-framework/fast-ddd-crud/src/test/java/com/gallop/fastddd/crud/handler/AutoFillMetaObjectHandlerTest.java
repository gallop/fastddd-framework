package com.gallop.fastddd.crud.handler;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 自动填充元对象处理器测试
 *
 * @author gallop
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AutoFillMetaObjectHandlerTest {

    private static final String TEST_USER = "testUser";

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private MetaObject metaObject;

    private AutoFillMetaObjectHandler handler;

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(TEST_USER);
        handler = new AutoFillMetaObjectHandler(currentUserProvider);
    }

    @Test
    void testInsertFill() {
        // Given
        when(metaObject.hasSetter("createTime")).thenReturn(true);
        when(metaObject.hasSetter("updateTime")).thenReturn(true);
        when(metaObject.hasSetter("createUser")).thenReturn(true);
        when(metaObject.hasSetter("updateUser")).thenReturn(true);
        
        // When
        handler.insertFill(metaObject);
        
        // Then
        verify(currentUserProvider, times(1)).getCurrentUserId();
        verify(metaObject).setValue(eq("createTime"), any(LocalDateTime.class));
        verify(metaObject).setValue(eq("updateTime"), any(LocalDateTime.class));
        verify(metaObject).setValue(eq("createUser"), eq(TEST_USER));
        verify(metaObject).setValue(eq("updateUser"), eq(TEST_USER));
    }

    @Test
    void testUpdateFill() {
        // Given
        when(metaObject.hasSetter("updateTime")).thenReturn(true);
        when(metaObject.hasSetter("updateUser")).thenReturn(true);
        
        // When
        handler.updateFill(metaObject);
        
        // Then
        verify(currentUserProvider, times(1)).getCurrentUserId();
        verify(metaObject).setValue(eq("updateTime"), any(LocalDateTime.class));
        verify(metaObject).setValue(eq("updateUser"), eq(TEST_USER));
    }

    @Test
    void testInsertFillWithNullProvider() {
        // Given
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
        when(metaObject.hasSetter(anyString())).thenReturn(true);
        
        // When
        handler.insertFill(metaObject);
        
        // Then
        verify(metaObject).setValue(eq("createUser"), isNull());
        verify(metaObject).setValue(eq("updateUser"), isNull());
    }

    @Test
    void testUpdateFillWithNullProvider() {
        // Given
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
        when(metaObject.hasSetter(anyString())).thenReturn(true);
        
        // When
        handler.updateFill(metaObject);
        
        // Then
        verify(metaObject).setValue(eq("updateUser"), isNull());
    }
}