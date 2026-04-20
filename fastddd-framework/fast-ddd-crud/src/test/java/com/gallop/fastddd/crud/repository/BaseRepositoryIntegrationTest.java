package com.gallop.fastddd.crud.repository;

import com.gallop.fastddd.crud.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BaseRepository功能的集成测试。
 * 测试BaseRepository接口提供的通用CRUD方法。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootTest(classes = com.gallop.fastddd.crud.TestApplication.class)
@ActiveProfiles("test")
@Transactional
class BaseRepositoryIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;
    
    private User testUser1;
    private User testUser2;
    private User testUser3;
    
    @BeforeEach
    void setUp() {
        // Clean up any existing data
        userRepository.delete(null);
        
        // Create test users
        testUser1 = new User();
        testUser1.setUsername("john_doe");
        testUser1.setEmail("john@example.com");
        testUser1.setStatus("ACTIVE");
        testUser1.setDepartment("IT");
        userRepository.insert(testUser1);
        
        testUser2 = new User();
        testUser2.setUsername("jane_smith");
        testUser2.setEmail("jane@example.com");
        testUser2.setStatus("ACTIVE");
        testUser2.setDepartment("HR");
        userRepository.insert(testUser2);
        
        testUser3 = new User();
        testUser3.setUsername("bob_wilson");
        testUser3.setEmail("bob@example.com");
        testUser3.setStatus("INACTIVE");
        testUser3.setDepartment("IT");
        userRepository.insert(testUser3);
    }
    
    @Test
    void testSelectOneByField() {
        // Test finding user by username
        Optional<User> foundUser = userRepository.selectOneByField("username", "john_doe");
        assertTrue(foundUser.isPresent());
        assertEquals("john_doe", foundUser.get().getUsername());
        assertEquals("john@example.com", foundUser.get().getEmail());
        
        // Test finding non-existent user
        Optional<User> notFound = userRepository.selectOneByField("username", "non_existent");
        assertFalse(notFound.isPresent());
    }
    
    @Test
    void testSelectListByField() {
        // Test finding users by status
        List<User> activeUsers = userRepository.selectListByField("status", "ACTIVE");
        assertEquals(2, activeUsers.size());
        assertTrue(activeUsers.stream().allMatch(user -> "ACTIVE".equals(user.getStatus())));
        
        // Test finding users by department
        List<User> itUsers = userRepository.selectListByField("department", "IT");
        assertEquals(2, itUsers.size());
        assertTrue(itUsers.stream().allMatch(user -> "IT".equals(user.getDepartment())));
    }
    
    @Test
    void testSelectListByFields() {
        // Test finding users by status and department
        List<User> activeItUsers = userRepository.selectListByFields("status", "ACTIVE", "department", "IT");
        assertEquals(1, activeItUsers.size());
        assertEquals("john_doe", activeItUsers.get(0).getUsername());
        
        // Test finding users with no matches
        List<User> inactiveHrUsers = userRepository.selectListByFields("status", "INACTIVE", "department", "HR");
        assertEquals(0, inactiveHrUsers.size());
    }
    
    @Test
    void testSelectListByFieldLike() {
        // Test finding users by username pattern
        List<User> usersWithJo = userRepository.selectListByFieldLike("username", "%jo%");
        assertEquals(1, usersWithJo.size());
        assertEquals("john_doe", usersWithJo.get(0).getUsername());
        
        // Test finding users by email pattern
        List<User> exampleUsers = userRepository.selectListByFieldLike("email", "%@example.com");
        assertEquals(3, exampleUsers.size());
    }
    
    @Test
    void testSelectListByFieldIn() {
        // Test finding users by username list
        List<String> usernames = Arrays.asList("john_doe", "jane_smith", "non_existent");
        List<User> foundUsers = userRepository.selectListByFieldIn("username", usernames);
        assertEquals(2, foundUsers.size());
        
        // Test with empty list
        List<User> emptyResult = userRepository.selectListByFieldIn("username", Arrays.asList());
        assertEquals(0, emptyResult.size());
        
        // Test with null list
        List<User> nullResult = userRepository.selectListByFieldIn("username", null);
        assertEquals(0, nullResult.size());
    }
    
    @Test
    void testCountByField() {
        // Test counting users by status
        Long activeCount = userRepository.countByField("status", "ACTIVE");
        assertEquals(2L, activeCount);
        
        Long inactiveCount = userRepository.countByField("status", "INACTIVE");
        assertEquals(1L, inactiveCount);
        
        // Test counting users by department
        Long itCount = userRepository.countByField("department", "IT");
        assertEquals(2L, itCount);
    }
    
    @Test
    void testExistsByField() {
        // Test checking existence by username
        assertTrue(userRepository.existsByField("username", "john_doe"));
        assertFalse(userRepository.existsByField("username", "non_existent"));
        
        // Test checking existence by email
        assertTrue(userRepository.existsByField("email", "jane@example.com"));
        assertFalse(userRepository.existsByField("email", "non_existent@example.com"));
    }
    
    @Test
    void testDeleteByField() {
        // Test deleting users by status
        int deletedCount = userRepository.deleteByField("status", "INACTIVE");
        assertEquals(1, deletedCount);
        
        // Verify deletion
        Long remainingCount = userRepository.countByField("status", "INACTIVE");
        assertEquals(0L, remainingCount);
        
        // Verify other users are still there
        Long activeCount = userRepository.countByField("status", "ACTIVE");
        assertEquals(2L, activeCount);
    }
    
    @Test
    void testUserRepositoryCustomMethods() {
        // Test UserRepository specific methods that use BaseRepository methods
        Optional<User> userByUsername = userRepository.findByUsername("john_doe");
        assertTrue(userByUsername.isPresent());
        
        Optional<User> userByEmail = userRepository.findByEmail("jane@example.com");
        assertTrue(userByEmail.isPresent());
        
        List<User> activeUsers = userRepository.findByStatus("ACTIVE");
        assertEquals(2, activeUsers.size());
        
        List<User> itUsers = userRepository.findByDepartment("IT");
        assertEquals(2, itUsers.size());
        
        List<User> activeItUsers = userRepository.findByStatusAndDepartment("ACTIVE", "IT");
        assertEquals(1, activeItUsers.size());
        
        List<User> searchResults = userRepository.searchByUsernamePattern("%john%");
        assertEquals(1, searchResults.size());
        
        assertTrue(userRepository.existsByUsername("john_doe"));
        assertFalse(userRepository.existsByUsername("non_existent"));
        
        Long statusCount = userRepository.countByStatus("ACTIVE");
        assertEquals(2L, statusCount);
    }
}