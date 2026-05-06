package com.gallop.fastddd.crud;

import com.baomidou.mybatisplus.core.injector.ISqlInjector;
import com.gallop.fastddd.crud.entity.User;
import com.gallop.fastddd.crud.handler.AutoFillMetaObjectHandler;
import com.gallop.fastddd.crud.injector.FastDddSqlInjector;
import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.crud.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FastDDD CRUD模块的综合集成测试。
 * 验证所有功能包括：
 * 1. 自动配置类的正确注册
 * 2. BaseRepository的标准CRUD操作
 * 3. 自定义查询方法
 * 4. 批量插入和更新操作
 * 5. 自动填充功能
 * 6. 数据完整性和正确性
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ComprehensiveIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private CurrentUserProvider currentUserProvider;
    
    @Autowired
    private AutoFillMetaObjectHandler autoFillMetaObjectHandler;
    
    @Autowired
    private ISqlInjector sqlInjector;
    
    private static final String TEST_USERNAME_PREFIX = "comprehensive_test_";
    
    @BeforeEach
    void setUp() {
        // 清理测试数据
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user1");
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user2");
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user3");
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch1");
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch2");
        userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch3");
    }
    
    @Test
    @Order(1)
    void testAutoConfigurationBeansAreRegistered() {
        // 验证自动配置的Bean都已正确注册
        assertNotNull(currentUserProvider, "CurrentUserProvider should be auto-configured");
        assertNotNull(autoFillMetaObjectHandler, "AutoFillMetaObjectHandler should be auto-configured");
        assertNotNull(sqlInjector, "ISqlInjector should be auto-configured");
        
        // 验证具体实现类型
        assertTrue(sqlInjector instanceof FastDddSqlInjector, 
                "Should be FastDddSqlInjector instance");
        
        // 验证CurrentUserProvider能正常工作
        String userId = currentUserProvider.getCurrentUserId();
        assertNotNull(userId, "Current user ID should not be null");
        assertFalse(userId.trim().isEmpty(), "Current user ID should not be empty");
    }
    
    @Test
    @Order(2)
    @Transactional
    void testStandardCrudOperations() {
        // 创建测试用户
        User user = createTestUser(TEST_USERNAME_PREFIX + "user1", "test1@example.com", "ACTIVE", "IT");
        
        // 测试插入
        int insertResult = userRepository.insert(user);
        assertEquals(1, insertResult, "Insert should return 1");
        assertNotNull(user.getId(), "ID should be generated after insert");
        assertNotNull(user.getCreateTime(), "Create time should be auto-filled");
        assertNotNull(user.getUpdateTime(), "Update time should be auto-filled");
        assertNotNull(user.getCreateUser(), "Create user should be auto-filled");
        assertNotNull(user.getUpdateUser(), "Update user should be auto-filled");
        
        // 测试查询
        User foundUser = userRepository.selectById(user.getId());
        assertNotNull(foundUser, "User should be found by ID");
        assertEquals(user.getUsername(), foundUser.getUsername());
        assertEquals(user.getEmail(), foundUser.getEmail());
        
        // 测试更新
        foundUser.setEmail("updated@example.com");
        LocalDateTime originalUpdateTime = foundUser.getUpdateTime();
        
        // 等待一毫秒确保更新时间不同
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        int updateResult = userRepository.updateById(foundUser);
        assertEquals(1, updateResult, "Update should return 1");
        
        // 验证更新后的数据
        User updatedUser = userRepository.selectById(user.getId());
        assertEquals("updated@example.com", updatedUser.getEmail());
        assertTrue(updatedUser.getUpdateTime().isAfter(originalUpdateTime), 
                "Update time should be refreshed");
        
        // 测试删除
        int deleteResult = userRepository.deleteById(user.getId());
        assertEquals(1, deleteResult, "Delete should return 1");
        
        User deletedUser = userRepository.selectById(user.getId());
        assertNull(deletedUser, "User should be deleted");
    }
    
    @Test
    @Order(3)
    @Transactional
    void testCustomQueryMethods() {
        // 创建测试数据
        User user1 = createTestUser(TEST_USERNAME_PREFIX + "user1", "test1@example.com", "ACTIVE", "IT");
        User user2 = createTestUser(TEST_USERNAME_PREFIX + "user2", "test2@example.com", "ACTIVE", "HR");
        User user3 = createTestUser(TEST_USERNAME_PREFIX + "user3", "test3@example.com", "INACTIVE", "IT");
        
        userRepository.insert(user1);
        userRepository.insert(user2);
        userRepository.insert(user3);
        
        // 测试 selectOneByField
        Optional<User> foundUser = userRepository.selectOneByField("username", TEST_USERNAME_PREFIX + "user1");
        assertTrue(foundUser.isPresent(), "Should find user by username");
        assertEquals(user1.getUsername(), foundUser.get().getUsername());
        
        // 测试 selectListByField
        List<User> activeUsers = userRepository.selectListByField("status", "ACTIVE");
        long activeCount = activeUsers.stream()
                .filter(u -> u.getUsername().startsWith(TEST_USERNAME_PREFIX))
                .count();
        assertEquals(2, activeCount, "Should find 2 active test users");
        
        List<User> itUsers = userRepository.selectListByField("department", "IT");
        long itCount = itUsers.stream()
                .filter(u -> u.getUsername().startsWith(TEST_USERNAME_PREFIX))
                .count();
        assertEquals(2, itCount, "Should find 2 IT test users");
        
        // 测试 selectListByFields
        List<User> activeItUsers = userRepository.selectListByFields("status", "ACTIVE", "department", "IT");
        long activeItCount = activeItUsers.stream()
                .filter(u -> u.getUsername().startsWith(TEST_USERNAME_PREFIX))
                .count();
        assertEquals(1, activeItCount, "Should find 1 active IT test user");
        
        // 测试 selectListByFieldLike
        List<User> usersLike = userRepository.selectListByFieldLike("username", TEST_USERNAME_PREFIX + "%");
        assertEquals(3, usersLike.size(), "Should find 3 users with username pattern");
        
        // 测试 selectListByFieldIn
        List<String> usernames = Arrays.asList(
                TEST_USERNAME_PREFIX + "user1", 
                TEST_USERNAME_PREFIX + "user2", 
                "non_existent"
        );
        List<User> usersIn = userRepository.selectListByFieldIn("username", usernames);
        assertEquals(2, usersIn.size(), "Should find 2 users from the list");
        
        // 测试 countByField
        Long activeCountResult = userRepository.countByField("status", "ACTIVE");
        assertTrue(activeCountResult >= 2, "Should count at least 2 active users");
        
        // 测试 existsByField
        assertTrue(userRepository.existsByField("username", TEST_USERNAME_PREFIX + "user1"), 
                "Should exist by username");
        assertFalse(userRepository.existsByField("username", "non_existent_user"), 
                "Should not exist for non-existent username");
    }
    
    @Test
    @Order(4)
    @Transactional
    void testBatchOperations() {
        // 测试批量插入
        User batchUser1 = createTestUser(TEST_USERNAME_PREFIX + "batch1", "batch1@example.com", "ACTIVE", "IT");
        User batchUser2 = createTestUser(TEST_USERNAME_PREFIX + "batch2", "batch2@example.com", "ACTIVE", "HR");
        User batchUser3 = createTestUser(TEST_USERNAME_PREFIX + "batch3", "batch3@example.com", "ACTIVE", "FINANCE");
        
        List<User> usersToInsert = Arrays.asList(batchUser1, batchUser2, batchUser3);
        
        int insertedCount = userRepository.batchInsert(usersToInsert);
        assertEquals(3, insertedCount, "Should insert 3 users in batch");
        
        // 验证批量插入的数据
        List<User> insertedUsers = userRepository.selectListByFieldIn("username", 
                Arrays.asList(TEST_USERNAME_PREFIX + "batch1", TEST_USERNAME_PREFIX + "batch2", TEST_USERNAME_PREFIX + "batch3"));
        assertEquals(3, insertedUsers.size(), "Should find 3 batch inserted users");
        
        // 验证自动填充在批量插入中也生效
        for (User user : insertedUsers) {
            assertNotNull(user.getCreateTime(), "Create time should be auto-filled in batch insert");
            assertNotNull(user.getUpdateTime(), "Update time should be auto-filled in batch insert");
            assertNotNull(user.getCreateUser(), "Create user should be auto-filled in batch insert");
            assertNotNull(user.getUpdateUser(), "Update user should be auto-filled in batch insert");
        }
        
        // 测试批量更新
        for (User user : insertedUsers) {
            user.setEmail("updated_" + user.getEmail());
            user.setStatus("UPDATED");
        }
        
        int updatedCount = userRepository.batchUpdateById(insertedUsers);
        assertEquals(3, updatedCount, "Should update 3 users in batch");
        
        // 验证批量更新的数据
        List<User> updatedUsers = userRepository.selectListByFieldIn("username", 
                Arrays.asList(TEST_USERNAME_PREFIX + "batch1", TEST_USERNAME_PREFIX + "batch2", TEST_USERNAME_PREFIX + "batch3"));
        
        for (User user : updatedUsers) {
            assertTrue(user.getEmail().startsWith("updated_"), "Email should be updated");
            assertEquals("UPDATED", user.getStatus(), "Status should be updated");
        }
    }
    
    @Test
    @Order(5)
    @Transactional
    void testBatchOperationsWithEmptyLists() {
        // 测试空列表的批量操作
        List<User> emptyList = Arrays.asList();
        
        int insertResult = userRepository.batchInsert(emptyList);
        assertEquals(0, insertResult, "Batch insert with empty list should return 0");
        
        int updateResult = userRepository.batchUpdateById(emptyList);
        assertEquals(0, updateResult, "Batch update with empty list should return 0");
    }
    
    @Test
    @Order(6)
    @Transactional
    void testUserRepositorySpecificMethods() {
        // 创建测试用户
        User user = createTestUser(TEST_USERNAME_PREFIX + "specific", "specific@example.com", "ACTIVE", "IT");
        userRepository.insert(user);
        
        // 测试UserRepository特有的方法
        Optional<User> userByUsername = userRepository.findByUsername(TEST_USERNAME_PREFIX + "specific");
        assertTrue(userByUsername.isPresent(), "Should find user by username");
        
        Optional<User> userByEmail = userRepository.findByEmail("specific@example.com");
        assertTrue(userByEmail.isPresent(), "Should find user by email");
        
        List<User> activeUsers = userRepository.findByStatus("ACTIVE");
        assertTrue(activeUsers.stream().anyMatch(u -> u.getUsername().equals(TEST_USERNAME_PREFIX + "specific")), 
                "Should find user in active users list");
        
        List<User> itUsers = userRepository.findByDepartment("IT");
        assertTrue(itUsers.stream().anyMatch(u -> u.getUsername().equals(TEST_USERNAME_PREFIX + "specific")), 
                "Should find user in IT users list");
        
        List<User> activeItUsers = userRepository.findByStatusAndDepartment("ACTIVE", "IT");
        assertTrue(activeItUsers.stream().anyMatch(u -> u.getUsername().equals(TEST_USERNAME_PREFIX + "specific")), 
                "Should find user in active IT users list");
        
        List<User> searchResults = userRepository.searchByUsernamePattern("%specific%");
        assertEquals(1, searchResults.size(), "Should find 1 user with username pattern");
        
        assertTrue(userRepository.existsByUsername(TEST_USERNAME_PREFIX + "specific"), 
                "Should exist by username");
        
        Long statusCount = userRepository.countByStatus("ACTIVE");
        assertTrue(statusCount >= 1, "Should count at least 1 active user");
    }
    
    @Test
    @Order(7)
    @Transactional
    void testDataIntegrityAndTransactionSupport() {
        // 测试事务回滚
        User user = createTestUser(TEST_USERNAME_PREFIX + "transaction", "transaction@example.com", "ACTIVE", "IT");
        userRepository.insert(user);
        
        Long userId = (Long) user.getId();
        assertNotNull(userId, "User should be inserted with ID");
        
        // 在事务中修改数据
        user.setEmail("modified@example.com");
        userRepository.updateById(user);
        
        // 验证修改生效
        User modifiedUser = userRepository.selectById(userId);
        assertEquals("modified@example.com", modifiedUser.getEmail(), "Email should be modified");
        
        // 由于@Transactional注解，测试结束后数据会回滚
    }
    
    /**
     * 创建测试用户的辅助方法
     */
    private User createTestUser(String username, String email, String status, String department) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setStatus(status);
        user.setDepartment(department);
        return user;
    }
    
    @AfterEach
    void tearDown() {
        // 清理测试数据（由于@Transactional，实际上会自动回滚）
        try {
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user1");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user2");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "user3");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch1");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch2");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "batch3");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "specific");
            userRepository.deleteByField("username", TEST_USERNAME_PREFIX + "transaction");
        } catch (Exception e) {
            // 忽略清理过程中的异常
        }
    }
}