package com.gallop.fastddd.crud.repository;

import com.gallop.fastddd.crud.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 扩展BaseRepository的用户仓储接口。
 * 演示BaseRepository的使用以及额外的自定义方法。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Mapper
public interface UserRepository extends BaseRepository<User> {
    
    /**
     * Find user by username.
     * Uses the inherited selectOneByField method from BaseRepository.
     * 
     * @param username the username to search for
     * @return Optional containing the user if found
     */
    default Optional<User> findByUsername(String username) {
        return selectOneByField("username", username);
    }
    
    /**
     * Find user by email.
     * Uses the inherited selectOneByField method from BaseRepository.
     * 
     * @param email the email to search for
     * @return Optional containing the user if found
     */
    default Optional<User> findByEmail(String email) {
        return selectOneByField("email", email);
    }
    
    /**
     * Find users by status.
     * Uses the inherited selectListByField method from BaseRepository.
     * 
     * @param status the status to filter by
     * @return List of users with the specified status
     */
    default List<User> findByStatus(String status) {
        return selectListByField("status", status);
    }
    
    /**
     * Find users by department.
     * Uses the inherited selectListByField method from BaseRepository.
     * 
     * @param department the department to filter by
     * @return List of users in the specified department
     */
    default List<User> findByDepartment(String department) {
        return selectListByField("department", department);
    }
    
    /**
     * Find users by status and department.
     * Uses the inherited selectListByFields method from BaseRepository.
     * 
     * @param status the status to filter by
     * @param department the department to filter by
     * @return List of users matching both criteria
     */
    default List<User> findByStatusAndDepartment(String status, String department) {
        return selectListByFields("status", status, "department", department);
    }
    
    /**
     * Search users by username pattern.
     * Uses the inherited selectListByFieldLike method from BaseRepository.
     * 
     * @param usernamePattern the username pattern to search for
     * @return List of users with usernames matching the pattern
     */
    default List<User> searchByUsernamePattern(String usernamePattern) {
        return selectListByFieldLike("username", usernamePattern);
    }
    
    /**
     * Check if username exists.
     * Uses the inherited existsByField method from BaseRepository.
     * 
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    default boolean existsByUsername(String username) {
        return existsByField("username", username);
    }
    
    /**
     * Count users by status.
     * Uses the inherited countByField method from BaseRepository.
     * 
     * @param status the status to count by
     * @return count of users with the specified status
     */
    default Long countByStatus(String status) {
        return countByField("status", status);
    }
}