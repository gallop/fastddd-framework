package com.gallop.fastddd.crud.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gallop.fastddd.crud.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseRepository接口的单元测试。
 * 测试默认方法实现，无需Spring上下文。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@ExtendWith(MockitoExtension.class)
class BaseRepositoryTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void testSelectOneByField() {
        // Given
        User expectedUser = new User();
        expectedUser.setId(1L);
        expectedUser.setUsername("testuser");
        
        when(userRepository.selectOne(any(QueryWrapper.class))).thenReturn(expectedUser);
        when(userRepository.selectOneByField("username", "testuser")).thenCallRealMethod();
        
        // When
        Optional<User> result = userRepository.selectOneByField("username", "testuser");
        
        // Then
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getUsername());
        verify(userRepository).selectOne(any(QueryWrapper.class));
    }

    @Test
    void testSelectListByField() {
        // Given
        User user1 = new User();
        user1.setId(1L);
        user1.setStatus("ACTIVE");
        
        User user2 = new User();
        user2.setId(2L);
        user2.setStatus("ACTIVE");
        
        List<User> expectedUsers = Arrays.asList(user1, user2);
        
        when(userRepository.selectList(any(QueryWrapper.class))).thenReturn(expectedUsers);
        when(userRepository.selectListByField("status", "ACTIVE")).thenCallRealMethod();
        
        // When
        List<User> result = userRepository.selectListByField("status", "ACTIVE");
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(userRepository).selectList(any(QueryWrapper.class));
    }

    @Test
    void testCountByField() {
        // Given
        when(userRepository.selectCount(any(QueryWrapper.class))).thenReturn(5L);
        when(userRepository.countByField("status", "ACTIVE")).thenCallRealMethod();
        
        // When
        Long result = userRepository.countByField("status", "ACTIVE");
        
        // Then
        assertEquals(5L, result);
        verify(userRepository).selectCount(any(QueryWrapper.class));
    }

    @Test
    void testExistsByField() {
        // Given
        when(userRepository.selectCount(any(QueryWrapper.class))).thenReturn(1L);
        when(userRepository.countByField("username", "testuser")).thenCallRealMethod();
        when(userRepository.existsByField("username", "testuser")).thenCallRealMethod();
        
        // When
        boolean result = userRepository.existsByField("username", "testuser");
        
        // Then
        assertTrue(result);
        verify(userRepository).selectCount(any(QueryWrapper.class));
    }

    @Test
    void testExistsByFieldReturnsFalse() {
        // Given
        when(userRepository.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(userRepository.countByField("username", "nonexistent")).thenCallRealMethod();
        when(userRepository.existsByField("username", "nonexistent")).thenCallRealMethod();
        
        // When
        boolean result = userRepository.existsByField("username", "nonexistent");
        
        // Then
        assertFalse(result);
        verify(userRepository).selectCount(any(QueryWrapper.class));
    }

    @Test
    void testDeleteByField() {
        // Given
        when(userRepository.delete(any(QueryWrapper.class))).thenReturn(3);
        when(userRepository.deleteByField("status", "INACTIVE")).thenCallRealMethod();
        
        // When
        int result = userRepository.deleteByField("status", "INACTIVE");
        
        // Then
        assertEquals(3, result);
        verify(userRepository).delete(any(QueryWrapper.class));
    }
}