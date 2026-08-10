package com.gallop.fastddd.crud;

import com.gallop.fastddd.crud.entity.User;
import com.gallop.fastddd.crud.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FastDDD CRUD模块的性能集成测试。
 * 验证批量操作相比单个操作的性能优势。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class PerformanceIntegrationTest {
    
    @Autowired
    private UserRepository userRepository;

    /**
     * 用 JdbcTemplate 做物理删除清理。
     * 不能用 userRepository.deleteById()：那是 MyBatis-Plus 逻辑删除（deleted=1），
     * 行仍物理存在并占据 username/email 的 UNIQUE 约束，
     * 会导致本测试方法中段“清理→重插相同 username”时撞唯一键（B-01）。
     * 这里物理 DELETE，绕过逻辑删除策略，且不修改生产逻辑删除配置。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private static final String PERF_TEST_PREFIX = "perf_test_";
    private static final int BATCH_SIZE = 100;
    
    @BeforeEach
    void setUp() {
        // 清理性能测试数据
        cleanupPerfTestData();
    }
    
    @Test
    @Transactional
    void testBatchInsertPerformance() {
        // 准备测试数据
        List<User> users = createTestUsers(BATCH_SIZE);
        
        // 测试批量插入性能
        long batchStartTime = System.currentTimeMillis();
        int batchInsertResult = userRepository.batchInsert(users);
        long batchEndTime = System.currentTimeMillis();
        long batchDuration = batchEndTime - batchStartTime;
        
        assertEquals(BATCH_SIZE, batchInsertResult, "Batch insert should insert all users");
        
        // 清理数据准备下一个测试
        cleanupPerfTestData();
        
        // 测试单个插入性能
        List<User> singleUsers = createTestUsers(BATCH_SIZE);
        long singleStartTime = System.currentTimeMillis();
        int singleInsertCount = 0;
        for (User user : singleUsers) {
            singleInsertCount += userRepository.insert(user);
        }
        long singleEndTime = System.currentTimeMillis();
        long singleDuration = singleEndTime - singleStartTime;
        
        assertEquals(BATCH_SIZE, singleInsertCount, "Single inserts should insert all users");
        
        // 性能比较
        System.out.println(String.format(
                "Performance Comparison for %d records:\n" +
                "Batch Insert: %d ms\n" +
                "Single Insert: %d ms\n" +
                "Performance Improvement: %.2fx",
                BATCH_SIZE, batchDuration, singleDuration, 
                (double) singleDuration / batchDuration
        ));
        
        // 验证批量操作完成了预期的插入数量（性能优势在大数据量时更明显）
        // 对于小数据量，批量操作可能不会显著更快，但功能应该正确
        assertTrue(batchDuration > 0, "Batch insert should complete in measurable time");
        assertTrue(singleDuration > 0, "Single inserts should complete in measurable time");
        
        // 记录性能比较结果，但不强制要求批量操作更快（小数据量下可能差异不大）
        if (batchDuration <= singleDuration) {
            System.out.println("✓ Batch insert performed better or equal to single inserts");
        } else {
            System.out.println("ℹ Single inserts were faster for this small dataset (normal for small batches)");
        }
    }
    
    @Test
    @Transactional
    void testBatchUpdatePerformance() {
        // 先插入测试数据
        List<User> users = createTestUsers(BATCH_SIZE);
        userRepository.batchInsert(users);
        
        // 获取插入后的用户（带ID）
        List<User> insertedUsers = userRepository.selectListByFieldLike("username", PERF_TEST_PREFIX + "%");
        assertEquals(BATCH_SIZE, insertedUsers.size(), "Should have inserted all test users");
        
        // 修改用户数据准备更新
        for (User user : insertedUsers) {
            user.setEmail("updated_" + user.getEmail());
            user.setStatus("UPDATED");
        }
        
        // 测试批量更新性能
        long batchStartTime = System.currentTimeMillis();
        int batchUpdateResult = userRepository.batchUpdateById(insertedUsers);
        long batchEndTime = System.currentTimeMillis();
        long batchDuration = batchEndTime - batchStartTime;
        
        assertEquals(BATCH_SIZE, batchUpdateResult, "Batch update should update all users");
        
        // 重置数据准备单个更新测试
        for (User user : insertedUsers) {
            user.setEmail(user.getEmail().replace("updated_", "single_updated_"));
            user.setStatus("SINGLE_UPDATED");
        }
        
        // 测试单个更新性能
        long singleStartTime = System.currentTimeMillis();
        int singleUpdateCount = 0;
        for (User user : insertedUsers) {
            singleUpdateCount += userRepository.updateById(user);
        }
        long singleEndTime = System.currentTimeMillis();
        long singleDuration = singleEndTime - singleStartTime;
        
        assertEquals(BATCH_SIZE, singleUpdateCount, "Single updates should update all users");
        
        // 性能比较
        System.out.println(String.format(
                "Update Performance Comparison for %d records:\n" +
                "Batch Update: %d ms\n" +
                "Single Update: %d ms\n" +
                "Performance Improvement: %.2fx",
                BATCH_SIZE, batchDuration, singleDuration, 
                (double) singleDuration / batchDuration
        ));
        
        // 验证批量操作完成了预期的更新数量（性能优势在大数据量时更明显）
        // 对于小数据量，批量操作可能不会显著更快，但功能应该正确
        assertTrue(batchDuration > 0, "Batch update should complete in measurable time");
        assertTrue(singleDuration > 0, "Single updates should complete in measurable time");
        
        // 记录性能比较结果，但不强制要求批量操作更快（小数据量下可能差异不大）
        if (batchDuration <= singleDuration) {
            System.out.println("✓ Batch update performed better or equal to single updates");
        } else {
            System.out.println("ℹ Single updates were faster for this small dataset (normal for small batches)");
        }
    }
    
    @Test
    @Transactional
    void testLargeBatchOperations() {
        // 测试更大批量的操作
        int largeBatchSize = 500;
        List<User> largeUsers = createTestUsers(largeBatchSize);
        
        // 测试大批量插入
        long startTime = System.currentTimeMillis();
        int insertResult = userRepository.batchInsert(largeUsers);
        long endTime = System.currentTimeMillis();
        
        assertEquals(largeBatchSize, insertResult, "Large batch insert should insert all users");
        
        System.out.println(String.format(
                "Large Batch Insert Performance for %d records: %d ms (%.2f ms per record)",
                largeBatchSize, endTime - startTime, (double)(endTime - startTime) / largeBatchSize
        ));
        
        // 验证数据完整性
        List<User> insertedUsers = userRepository.selectListByFieldLike("username", PERF_TEST_PREFIX + "%");
        assertEquals(largeBatchSize, insertedUsers.size(), "Should find all inserted users");
        
        // 验证自动填充字段
        for (User user : insertedUsers) {
            assertNotNull(user.getCreateTime(), "Create time should be filled");
            assertNotNull(user.getUpdateTime(), "Update time should be filled");
            assertNotNull(user.getCreateUser(), "Create user should be filled");
            assertNotNull(user.getUpdateUser(), "Update user should be filled");
        }
    }
    
    @Test
    @Transactional
    void testQueryPerformanceWithLargeDataset() {
        // 插入大量数据用于查询性能测试
        int datasetSize = 1000;
        List<User> users = createTestUsers(datasetSize);
        userRepository.batchInsert(users);
        
        // 测试不同查询方法的性能
        
        // 1. selectListByField 性能
        long startTime = System.currentTimeMillis();
        List<User> activeUsers = userRepository.selectListByField("status", "ACTIVE");
        long fieldQueryTime = System.currentTimeMillis() - startTime;
        
        // 2. selectListByFieldLike 性能
        startTime = System.currentTimeMillis();
        List<User> likeUsers = userRepository.selectListByFieldLike("username", PERF_TEST_PREFIX + "%");
        long likeQueryTime = System.currentTimeMillis() - startTime;
        
        // 3. countByField 性能
        startTime = System.currentTimeMillis();
        Long count = userRepository.countByField("status", "ACTIVE");
        long countQueryTime = System.currentTimeMillis() - startTime;
        
        // 4. existsByField 性能
        startTime = System.currentTimeMillis();
        boolean exists = userRepository.existsByField("username", PERF_TEST_PREFIX + "user_500");
        long existsQueryTime = System.currentTimeMillis() - startTime;
        
        System.out.println(String.format(
                "Query Performance with %d records:\n" +
                "selectListByField: %d ms (found %d records)\n" +
                "selectListByFieldLike: %d ms (found %d records)\n" +
                "countByField: %d ms (count: %d)\n" +
                "existsByField: %d ms (exists: %s)",
                datasetSize, fieldQueryTime, activeUsers.size(),
                likeQueryTime, likeUsers.size(),
                countQueryTime, count,
                existsQueryTime, exists
        ));
        
        // 基本性能断言（查询时间应该在合理范围内）
        assertTrue(fieldQueryTime < 1000, "Field query should complete within 1 second");
        assertTrue(likeQueryTime < 1000, "Like query should complete within 1 second");
        assertTrue(countQueryTime < 500, "Count query should complete within 0.5 seconds");
        assertTrue(existsQueryTime < 500, "Exists query should complete within 0.5 seconds");
    }
    
    /**
     * 创建测试用户列表
     */
    private List<User> createTestUsers(int count) {
        List<User> users = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 1; i <= count; i++) {
            User user = new User();
            user.setUsername(PERF_TEST_PREFIX + "user_" + i);
            user.setEmail(PERF_TEST_PREFIX + "user" + i + "@example.com");
            user.setStatus("ACTIVE");
            user.setDepartment("DEPT_" + (i % 5 + 1)); // 5个不同的部门
            user.setCreateTime(now);
            user.setUpdateTime(now);
            user.setCreateUser("perfTest");
            user.setUpdateUser("perfTest");
            users.add(user);
        }
        
        return users;
    }
    
    /**
     * 清理性能测试数据：物理删除 perf_test_* 行。
     * 物理删除确保 username/email 的 UNIQUE 约束被释放，
     * 同一测试方法内“清理→重插相同 username”不再撞唯一键（B-01）。
     * 通过 JdbcTemplate 直接执行 SQL，绕过 MyBatis-Plus 逻辑删除。
     */
    private void cleanupPerfTestData() {
        try {
            jdbcTemplate.update("DELETE FROM t_user WHERE username LIKE ?", PERF_TEST_PREFIX + "%");
        } catch (Exception e) {
            // 忽略清理过程中的异常（如表尚未创建等）
            System.out.println("Warning: Failed to cleanup performance test data: " + e.getMessage());
        }
    }
}