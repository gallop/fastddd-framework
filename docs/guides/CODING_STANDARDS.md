# FastDDD Coding Standards

**English** | [简体中文](CODING_STANDARDS.zh-CN.md)

> **Author**: gallop  
> **Created**: 2025-09-01  
> **Version**: 1.0.0

This document defines the unified coding standards and naming conventions for the FastDDD framework project; all developers must follow them strictly.

## 1. Java Coding Standards

### 1.1 Package Naming Conventions

- **Format**: `com.gallop.fastddd.{module}`
- **Rule**: all lowercase, separated by dots
- **Examples**:
  - `com.gallop.fastddd.web`
  - `com.gallop.fastddd.crud`
  - `com.gallop.fastddd.event`

### 1.2 Class Naming Conventions

- **Class names**: PascalCase (upper camel case)
- **Interface names**: PascalCase; may start with `I` or use a descriptive name directly
- **Enum names**: PascalCase
- **Annotation names**: PascalCase

**Examples**:
```java
// 类
public class UserService {}
public class ApiResponse<T> {}

// 接口
public interface UserRepository {}
public interface IUserService {}

// 枚举
public enum UserStatus {}

// 注解
public @interface EnableApiWrapper {}
```

### 1.3 Method and Variable Naming Conventions

- **Method names**: camelCase (lower camel case)
- **Variable names**: camelCase
- **Parameter names**: camelCase

**Examples**:
```java
public class UserService {
    private UserRepository userRepository;
    
    public User findUserById(Long userId) {
        return userRepository.findById(userId);
    }
    
    public List<User> findUsersByStatus(UserStatus userStatus) {
        return userRepository.findByStatus(userStatus);
    }
}
```

### 1.4 Constant Naming Conventions

- **Format**: `UPPER_SNAKE_CASE`
- **Rule**: all uppercase, with words separated by underscores

**Examples**:
```java
public class Constants {
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final int MAX_RETRY_COUNT = 3;
    public static final long DEFAULT_TIMEOUT_MS = 5000L;
}
```

## 2. Database Naming Conventions

### 2.1 Table Naming Conventions

- **Format**: `snake_case`
- **Rule**: all lowercase, with words separated by underscores
- **Prefix**: a module prefix may optionally be added

**Examples**:
- `user_info`
- `order_detail`
- `sys_config`

### 2.2 Column Naming Conventions

- **Format**: `snake_case`
- **Rule**: all lowercase, with words separated by underscores
- **Primary key**: always use `id`
- **Foreign key**: use the `{related_table_name}_id` format
- **Time fields**: use `created_time` and `updated_time`
- **Logical delete**: use `deleted`

**Examples**:
```sql
CREATE TABLE user_info (
    id BIGINT PRIMARY KEY,
    user_name VARCHAR(50),
    email_address VARCHAR(100),
    phone_number VARCHAR(20),
    created_time DATETIME,
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0
);
```

### 2.3 Database-to-Java Mapping Rules

**Core rule**: database `snake_case` maps automatically to Java `camelCase`

| Database Column | Java Property | Description |
|-----------|-----------|------|
| `user_id` | `userId` | User ID |
| `user_name` | `userName` | Username |
| `email_address` | `emailAddress` | Email address |
| `phone_number` | `phoneNumber` | Phone number |
| `created_time` | `createdTime` | Creation time |
| `updated_time` | `updatedTime` | Last update time |

**MyBatis Plus configuration**:
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

## 3. Configuration File Naming Conventions

### 3.1 application.yml Property Naming

- **Format**: `kebab-case`
- **Rule**: all lowercase, with words separated by hyphens

**Example** (after the consumer explicitly opts in; the framework's aggregate starter enables it by default):
```yaml
spring:
  application:
    name: fastddd-demo
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver

fastddd:
  web:
    response-wrapper:
      enabled: true   # opt-in：默认关，显式开启才把控制器返回值包成 ApiResponse 信封
    jackson:
      enabled: true   # opt-in：默认关，显式开启才施加 Long→String / 日期格式 / 枚举→name 扩展
```

> WEB-002: both the response wrapper and the Jackson extensions are **opt-in (off by default)**. Merely pulling in `fast-ddd-web` does not rewrite the consumer's existing JSON contract — Long stays numeric, there is no `code/data` envelope, and `LocalDateTime` keeps the Spring Boot default representation. Framework aggregate consumers (studio / examples / archetype-generated projects) explicitly opt in within their own `application.yml` to restore the envelope behavior. The gate is applied at the class level of `SmartResponseAdvice` and `FastDddJacksonAutoConfiguration` via `@ConditionalOnProperty(..., matchIfMissing = false)`; the property is the only switch (`@EnableApiResponseWrapper` has been downgraded to a `@Deprecated` source-compatibility marker and no longer carries opt-in semantics).

### 3.2 Custom Configuration Properties

The response wrapper / Jackson extensions provide no separate exclusion-path configuration; endpoints that should not be wrapped simply return `ResponseEntity<ApiResponse<T>>`, `Result`, `void`, or Reactive types (`Flux`/`Mono`) — `SmartResponseAdvice` and the OpenAPI customizer recognize and skip them through the shared predicate `ResponseWrapPredicate` from the same source.

## 4. Infrastructure Naming Conventions

### 4.1 RocketMQ Naming Conventions

**Topic naming**:
- **Format**: `{project}_{module}_{business}_{event}_topic`
- **Example**: `fastddd_order_payment_success_topic`

**Consumer Group naming**:
- **Format**: `{project}_{module}_{consumer}_group`
- **Example**: `fastddd_order_notification_group`

### 4.2 Elasticsearch Naming Conventions

**Index naming**:
- **Format**: `{project}_{module}_{data}_{version}`
- **Example**: `fastddd_search_product_v1`

**Alias naming**:
- **Format**: `{project}_{module}_{data}`
- **Example**: `fastddd_search_product`

### 4.3 Redis Naming Conventions

**Key naming**:
- **Format**: `{project}:{module}:{entity}:{id}`
- **Examples**: 
  - `fastddd:user:profile:12345`
  - `fastddd:cache:product:67890`
  - `fastddd:session:token:abc123`

**Cache expiration**:
- **Short-term cache**: 5-30 minutes
- **Medium-term cache**: 1-6 hours  
- **Long-term cache**: 1-7 days

## 5. Code Comment Conventions

### 5.1 Class Comments

```java
/**
 * 用户服务实现类
 * 提供用户相关的业务逻辑处理
 * 
 * @author gallop
 * @since 2025-09-01
 * @version 1.0.0
 */
public class UserServiceImpl implements UserService {
    // 实现代码
}
```

### 5.2 Method Comments

```java
/**
 * 根据用户ID查询用户信息
 * 
 * @param userId 用户ID，不能为空
 * @return 用户信息，如果不存在返回null
 * @throws IllegalArgumentException 当userId为空时抛出
 */
public User findUserById(Long userId) {
    // 实现代码
}
```

## 6. Exception Handling Conventions

### 6.1 Exception Class Naming

- **Business exceptions**: `{Business}Exception`
- **System exceptions**: `{System}Exception`

**Examples**:
```java
public class UserNotFoundException extends BusinessException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

### 6.2 Error Code Conventions

- **Success**: 200
- **Client errors**: 400-499
- **Server errors**: 500-599
- **Business errors**: 1000-9999

## 7. Test Code Conventions

### 7.1 Test Class Naming

- **Unit tests**: `{ClassName}Test`
- **Integration tests**: `{ClassName}IntegrationTest`

### 7.2 Test Method Naming

- **Format**: `should{ExpectedBehavior}_when{StateUnderTest}`
- **Example**: `shouldReturnUser_whenValidIdProvided()`

## 8. Version Control Conventions

### 8.1 Branch Naming

- **Feature branches**: `feature/{feature-name}`
- **Bugfix branches**: `bugfix/{bug-description}`
- **Release branches**: `release/{version}`

### 8.2 Commit Message Format

```
{type}({scope}): {subject}

{body}

{footer}
```

**Types**:
- `feat`: new feature
- `fix`: bug fix
- `docs`: documentation updates
- `style`: code formatting changes
- `refactor`: refactoring
- `test`: test-related changes
- `chore`: changes to the build process or auxiliary tools

## 9. Enforcement

These standards are enforced automatically through the following tools:

1. **Checkstyle**: code style checks
2. **Maven**: build-time validation
3. **IDE configuration**: real-time checks during development

**Verification commands**:
```bash
# 检查代码规范
mvn validate -P checkstyle

# 完整构建验证
mvn clean install
```

---

> This document is the core standard of the FastDDD framework; all developers must follow it strictly. For questions or suggestions, contact the architecture team.
