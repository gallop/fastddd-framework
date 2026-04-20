package com.gallop.fastddd.crud.injector;

import com.gallop.fastddd.crud.entity.User;
import com.gallop.fastddd.crud.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FastDddSqlInjector功能的测试类。
 * 测试自定义批量插入和批量更新方法。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FastDddSqlInjectorTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testBatchInsert() {
        // Prepare test data
        User user1 = new User();
        user1.setUsername("batchUser1");
        user1.setEmail("batch1@example.com");
        user1.setStatus("ACTIVE");
        user1.setDepartment("IT");
        user1.setCreateTime(LocalDateTime.now());
        user1.setUpdateTime(LocalDateTime.now());
        user1.setCreateUser("testUser");
        user1.setUpdateUser("testUser");

        User user2 = new User();
        user2.setUsername("batchUser2");
        user2.setEmail("batch2@example.com");
        user2.setStatus("ACTIVE");
        user2.setDepartment("HR");
        user2.setCreateTime(LocalDateTime.now());
        user2.setUpdateTime(LocalDateTime.now());
        user2.setCreateUser("testUser");
        user2.setUpdateUser("testUser");

        User user3 = new User();
        user3.setUsername("batchUser3");
        user3.setEmail("batch3@example.com");
        user3.setStatus("ACTIVE");
        user3.setDepartment("FINANCE");
        user3.setCreateTime(LocalDateTime.now());
        user3.setUpdateTime(LocalDateTime.now());
        user3.setCreateUser("testUser");
        user3.setUpdateUser("testUser");

        List<User> users = Arrays.asList(user1, user2, user3);

        // Execute batch insert
        int insertedCount = userRepository.batchInsert(users);

        // Verify results
        assertEquals(3, insertedCount, "Should insert 3 users");
        
        // Verify users were actually inserted
        List<User> insertedUsers = userRepository.selectListByFieldIn("username", 
            Arrays.asList("batchUser1", "batchUser2", "batchUser3"));
        assertEquals(3, insertedUsers.size(), "Should find 3 inserted users");
    }

    @Test
    void testBatchUpdateById() {
        // First, insert some test data
        User user1 = new User();
        user1.setUsername("updateUser1");
        user1.setEmail("update1@example.com");
        user1.setStatus("ACTIVE");
        user1.setDepartment("IT");
        user1.setCreateTime(LocalDateTime.now());
        user1.setUpdateTime(LocalDateTime.now());
        user1.setCreateUser("testUser");
        user1.setUpdateUser("testUser");
        userRepository.insert(user1);

        User user2 = new User();
        user2.setUsername("updateUser2");
        user2.setEmail("update2@example.com");
        user2.setStatus("ACTIVE");
        user2.setDepartment("HR");
        user2.setCreateTime(LocalDateTime.now());
        user2.setUpdateTime(LocalDateTime.now());
        user2.setCreateUser("testUser");
        user2.setUpdateUser("testUser");
        userRepository.insert(user2);

        // Modify the users for batch update
        user1.setEmail("updated1@example.com");
        user1.setUpdateTime(LocalDateTime.now());
        user1.setUpdateUser("updatedBy");
        
        user2.setEmail("updated2@example.com");
        user2.setUpdateTime(LocalDateTime.now());
        user2.setUpdateUser("updatedBy");

        List<User> usersToUpdate = Arrays.asList(user1, user2);

        // Execute batch update
        int updatedCount = userRepository.batchUpdateById(usersToUpdate);

        // Verify results
        assertEquals(2, updatedCount, "Should update 2 users");
        
        // Verify users were actually updated
        User updatedUser1 = userRepository.selectById(user1.getId());
        User updatedUser2 = userRepository.selectById(user2.getId());
        
        assertNotNull(updatedUser1);
        assertNotNull(updatedUser2);
        assertEquals("updated1@example.com", updatedUser1.getEmail());
        assertEquals("updated2@example.com", updatedUser2.getEmail());
        assertEquals("system", updatedUser1.getUpdateUser());
        assertEquals("system", updatedUser2.getUpdateUser());
    }

    @Test
    void testBatchInsertWithEmptyList() {
        // Test with empty list
        List<User> emptyList = Arrays.asList();
        
        // This should not throw an exception and should return 0
        int insertedCount = userRepository.batchInsert(emptyList);
        assertEquals(0, insertedCount, "Should insert 0 users for empty list");
    }

    @Test
    void testBatchUpdateByIdWithEmptyList() {
        // Test with empty list
        List<User> emptyList = Arrays.asList();
        
        // This should not throw an exception and should return 0
        int updatedCount = userRepository.batchUpdateById(emptyList);
        assertEquals(0, updatedCount, "Should update 0 users for empty list");
    }
}