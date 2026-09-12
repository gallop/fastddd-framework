# FastDDD 编码规范文档

[English](CODING_STANDARDS.md) | **简体中文**

> **作者**: gallop  
> **创建时间**: 2025-09-01  
> **版本**: 1.0.0

本文档定义了 FastDDD 框架项目的统一编码规范和命名约定，所有开发人员必须严格遵守。

## 1. Java 编码规范

### 1.1 包命名规范

- **格式**: `com.gallop.fastddd.{module}`
- **规则**: 全小写，使用点号分隔
- **示例**:
  - `com.gallop.fastddd.web`
  - `com.gallop.fastddd.crud`
  - `com.gallop.fastddd.event`

### 1.2 类命名规范

- **类名**: PascalCase（首字母大写的驼峰命名）
- **接口名**: PascalCase，可以 `I` 开头或直接使用描述性名称
- **枚举名**: PascalCase
- **注解名**: PascalCase

**示例**:
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

### 1.3 方法和变量命名规范

- **方法名**: camelCase（小驼峰命名）
- **变量名**: camelCase
- **参数名**: camelCase

**示例**:
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

### 1.4 常量命名规范

- **格式**: `UPPER_SNAKE_CASE`
- **规则**: 全大写，使用下划线分隔单词

**示例**:
```java
public class Constants {
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final int MAX_RETRY_COUNT = 3;
    public static final long DEFAULT_TIMEOUT_MS = 5000L;
}
```

## 2. 数据库命名规范

### 2.1 表命名规范

- **格式**: `snake_case`
- **规则**: 全小写，使用下划线分隔单词
- **前缀**: 可选择性添加模块前缀

**示例**:
- `user_info`
- `order_detail`
- `sys_config`

### 2.2 字段命名规范

- **格式**: `snake_case`
- **规则**: 全小写，使用下划线分隔单词
- **主键**: 统一使用 `id`
- **外键**: 使用 `{关联表名}_id` 格式
- **时间字段**: 使用 `created_time`、`updated_time`
- **逻辑删除**: 使用 `deleted`

**示例**:
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

### 2.3 数据库到 Java 映射规则

**核心规则**: 数据库 `snake_case` 自动映射到 Java `camelCase`

| 数据库字段 | Java 属性 | 说明 |
|-----------|-----------|------|
| `user_id` | `userId` | 用户ID |
| `user_name` | `userName` | 用户名 |
| `email_address` | `emailAddress` | 邮箱地址 |
| `phone_number` | `phoneNumber` | 手机号码 |
| `created_time` | `createdTime` | 创建时间 |
| `updated_time` | `updatedTime` | 更新时间 |

**MyBatis Plus 配置**:
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

## 3. 配置文件命名规范

### 3.1 application.yml 属性命名

- **格式**: `kebab-case`
- **规则**: 全小写，使用连字符分隔单词

**示例**（消费者显式 opt-in 后，框架聚合 starter 默认已开）:
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

> WEB-002：响应包装与 Jackson 扩展均为 **opt-in（默认关）**。仅引入 `fast-ddd-web` 不会改写消费者
> 既有的 JSON 契约——Long 保持数值、无 `code/data` 信封、`LocalDateTime` 为 Spring Boot 默认表示。
> 框架聚合消费者（studio / examples / archetype 生成项目）在各自的 `application.yml` 显式 opt-in
> 还原信封能力。门控施加在 `SmartResponseAdvice` 与 `FastDddJacksonAutoConfiguration` 的类级别
> `@ConditionalOnProperty(..., matchIfMissing = false)`，属性是唯一开关（`@EnableApiResponseWrapper`
> 已降级为 `@Deprecated` 源兼容标记，不再具备 opt-in 语义）。

### 3.2 自定义配置属性

响应包装/Jackson 扩展不提供独立的排除路径配置；不希望被包装的端点直接返回
`ResponseEntity<ApiResponse<T>>`、`Result`、`void` 或 Reactive 类型（`Flux`/`Mono`），
`SmartResponseAdvice` 与 OpenAPI customizer 经共享谓词 `ResponseWrapPredicate` 同源识别并跳过。

## 4. 基础设施命名规范

### 4.1 RocketMQ 命名规范

**Topic 命名**:
- **格式**: `{project}_{module}_{business}_{event}_topic`
- **示例**: `fastddd_order_payment_success_topic`

**Consumer Group 命名**:
- **格式**: `{project}_{module}_{consumer}_group`
- **示例**: `fastddd_order_notification_group`

### 4.2 Elasticsearch 命名规范

**索引命名**:
- **格式**: `{project}_{module}_{data}_{version}`
- **示例**: `fastddd_search_product_v1`

**别名命名**:
- **格式**: `{project}_{module}_{data}`
- **示例**: `fastddd_search_product`

### 4.3 Redis 命名规范

**Key 命名**:
- **格式**: `{project}:{module}:{entity}:{id}`
- **示例**: 
  - `fastddd:user:profile:12345`
  - `fastddd:cache:product:67890`
  - `fastddd:session:token:abc123`

**缓存过期时间**:
- **短期缓存**: 5-30分钟
- **中期缓存**: 1-6小时  
- **长期缓存**: 1-7天

## 5. 代码注释规范

### 5.1 类注释

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

### 5.2 方法注释

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

## 6. 异常处理规范

### 6.1 异常类命名

- **业务异常**: `{Business}Exception`
- **系统异常**: `{System}Exception`

**示例**:
```java
public class UserNotFoundException extends BusinessException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

### 6.2 异常码规范

- **成功**: 200
- **客户端错误**: 400-499
- **服务端错误**: 500-599
- **业务错误**: 1000-9999

## 7. 测试代码规范

### 7.1 测试类命名

- **单元测试**: `{ClassName}Test`
- **集成测试**: `{ClassName}IntegrationTest`

### 7.2 测试方法命名

- **格式**: `should{ExpectedBehavior}_when{StateUnderTest}`
- **示例**: `shouldReturnUser_whenValidIdProvided()`

## 8. 版本控制规范

### 8.1 分支命名

- **功能分支**: `feature/{feature-name}`
- **修复分支**: `bugfix/{bug-description}`
- **发布分支**: `release/{version}`

### 8.2 提交信息格式

```
{type}({scope}): {subject}

{body}

{footer}
```

**类型**:
- `feat`: 新功能
- `fix`: 修复bug
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建过程或辅助工具的变动

## 9. 强制执行

本规范通过以下工具自动化执行：

1. **Checkstyle**: 代码风格检查
2. **Maven**: 构建时验证
3. **IDE 配置**: 开发时实时检查

**验证命令**:
```bash
# 检查代码规范
mvn validate -P checkstyle

# 完整构建验证
mvn clean install
```

---

> 本文档是 FastDDD 框架的核心规范，所有开发人员必须严格遵守。如有疑问或建议，请联系架构团队。
