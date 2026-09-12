# FastDDD Framework Usage Guide

**English** | [简体中文](USAGE_GUIDE.zh-CN.md)

> Enterprise-grade Domain-Driven Design (DDD) development framework - the complete usage manual
>
> For new projects, follow the [New Business System Development Guide](NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md) first; this document serves as a module feature reference.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Framework Overview](#framework-overview)
3. [Core Modules in Depth](#core-modules-in-depth)
4. [Development in Practice](#development-in-practice)
5. [Configuration Reference](#configuration-reference)
6. [Best Practices](#best-practices)
7. [Monitoring and Operations](#monitoring-and-operations)
8. [Multi-Channel Auth](#multi-channel-auth)
9. [Improvement Suggestions](#improvement-suggestions)

---

## Quick Start

### Environment Requirements

| Component | Version Requirement |
|------|----------|
| JDK | 21+ |
| Maven | 3.9+ |
| Spring Boot | 3.5.16 |
| MySQL | 8.0+ (production) |
| Redis | 6.0+ (optional) |
| Docker | 20.10+ (optional) |

### Five-Minute Quick Start

#### 1. Add Dependencies

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.gallop.fastddd</groupId>
            <artifactId>fastddd-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
    <!-- version 由 fastddd-bom 管理 -->
</dependency>
```

#### 2. Configure the Data Source

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: root
    password: password
```

#### 3. Generate Code

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=password \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_
```

#### 4. Run the Application

```bash
mvn spring-boot:run
```

---

## Framework Overview

### DDD Four-Layer Architecture

```
┌─────────────────────────────────────────────────────────┐
│  User Interface Layer (用户接口层)                        │
│  - REST Controllers                                      │
│  - Request/Response DTOs                                 │
│  - API Response Wrapper                                  │
├─────────────────────────────────────────────────────────┤
│  Application Layer (应用层)                               │
│  - Application Services                                  │
│  - Use Case Orchestration                                │
│  - Transaction Management                                │
├─────────────────────────────────────────────────────────┤
│  Domain Layer (领域层)                                    │
│  - Entities / Aggregate Roots                           │
│  - Value Objects                                         │
│  - Domain Events                                         │
│  - Domain Services                                       │
├─────────────────────────────────────────────────────────┤
│  Infrastructure Layer (基础设施层)                        │
│  - Repository Implementations                           │
│  - External Service Clients                             │
│  - Persistence (MyBatis Plus)                           │
└─────────────────────────────────────────────────────────┘
```

### Core Design Principles

- **High standards**: unified code/architecture/documentation standards, 90%+ code consistency
- **High efficiency**: code generation cuts single-table CRUD development from 2 days to 5 minutes
- **High collaboration**: standardized interface conventions, reducing integration time by 50%

---

## Core Modules in Depth

### Module Dependency Diagram

```
Level 1 (基础层):
├── fast-ddd-common       # 基础工具、异常、注解
└── fast-ddd-config       # 配置管理、条件加载

Level 2 (核心层):
├── fast-ddd-model        # 数据模型、PO 基类
├── fast-ddd-crud         # CRUD 操作、MyBatis Plus 增强
├── fast-ddd-web          # Web 层、控制器基类、响应包装

Level 3 (功能层):
├── fast-ddd-event        # 事件驱动架构、领域事件
├── fast-ddd-cache        # 多级缓存
├── fast-ddd-search       # Elasticsearch 集成
└── fast-ddd-monitor      # 监控、SQL 性能跟踪

Level 4 (工具层):
├── fast-ddd-security-starter       # 安全、审计日志
├── fast-ddd-ai-spring-boot-starter # AI 驱动开发工具

Level 5 (生成器层):
├── fastddd-code-generator    # 代码生成引擎
├── fastddd-doc-generator     # 文档生成引擎
└── fastddd-maven-plugin      # Maven 插件

Level 6 (应用层):
└── fastddd-studio-server         # 演示应用
```

### 1. fast-ddd-common (Base Utilities Module)

**Core Features:**

| Component | Description |
|------|------|
| `Result<T>` | Unified response wrapper |
| `PageResult<T>` | Paginated response wrapper |
| `BusinessException` | Base business exception class |
| `@ConditionalOnDev` | Development environment conditional annotation |
| `@ConditionalOnSystemProperty` | System property conditional annotation |
| `JsonUtil` | JSON utility class |
| `DateUtil` | Date utility class |

**Usage Example:**

```java
// 统一响应返回
@GetMapping("/users/{id}")
public Result<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    return Result.success(user);
}

// 业务异常抛出
if (user == null) {
    throw new BusinessException(ErrorCode.USER_NOT_FOUND);
}
```

### 2. fast-ddd-config (Configuration Management Module)

**Core Features:**

- Conditional loading mechanism
- Multi-environment configuration support
- Dynamic configuration refresh

**Configuration Example:**

```yaml
fastddd:
  config:
    environment: dev
    dynamic-refresh: true
```

### 3. fast-ddd-model (Data Model Module)

**Core Base Classes:**

```java
// 基础实体 (领域层)
@Data
public abstract class BaseEntity<ID> implements Serializable {
    private ID id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    private Boolean deleted;
}

// 聚合根 (领域层)
@Data
public abstract class AggregateRoot<ID> extends BaseEntity<ID> {
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected void addDomainEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}

// 基础持久化对象 (数据层)
@Data
@TableName("user_info")
public class UserPO extends BasePO<Long> {
    private String username;
    private String email;
    private String phone;
}
```

### 4. fast-ddd-crud (CRUD Operations Module)

**Core Features:**

| Feature | Description |
|------|------|
| Generic CRUD | Create/read/update/delete operations based on MyBatis Plus |
| Batch operations | Batch insert and batch update |
| Auto-fill | Automatic population of creation and update timestamps |
| Logical delete | Soft-delete support |
| Optimistic lock | Version-number concurrency control |

**Usage Example:**

```java
// Service 层继承 BaseServiceImpl
@Service
public class UserServiceImpl
    extends BaseServiceImpl<User>
    implements UserService {

    @Autowired
    private UserRepository userRepository;

    // CRUD 方法自动继承：
    // - save(T entity)
    // - deleteById(ID id)
    // - getById(ID id)
    // - list()
    // - page(Page<T> page)
}

// 自定义 Repository
public interface UserRepository extends BaseMapper<User> {
    // 自定义查询方法
    List<User> findByUsername(String username);
}
```

### 5. fast-ddd-web (Web Layer Module)

**Core Features:**

| Feature | Description |
|------|------|
| BaseAdminCrudController | Full admin-side CRUD controller base class (added in 1.1) |
| BaseReadOnlyController | Read-only query controller base class (added in 1.1) |
| BaseController | Generic controller base class (@Deprecated, kept for compatibility) |
| Automatic response wrapping | ApiResponse wrapping |
| Unified exception handling | Global exception capture |
| Request logging | Automatic request/response logging |

**Choosing a Controller Base Class:**

| Scenario | Recommended Base Class | Examples |
|------|---------|------|
| Admin CRUD | `BaseAdminCrudController` | Dictionary tables, configuration tables, low-risk resources |
| Read-only queries | `BaseReadOnlyController` | Dropdown options, data display |
| Sensitive business APIs | Do not extend a CRUD base class | Authentication, payments, orders |
| Legacy project compatibility | `BaseController` (deprecated) | Existing projects, not migrating yet |

**Admin CRUD Example:**

```java
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController
    extends BaseAdminCrudController<UserService, User, Long> {

    // 自动继承完整 CRUD 端点：
    // GET    /api/admin/users        - 列表
    // GET    /api/admin/users/{id}   - 详情
    // GET    /api/admin/users/page   - 分页
    // POST   /api/admin/users        - 创建
    // PUT    /api/admin/users/{id}   - 更新
    // DELETE /api/admin/users/{id}   - 删除
    // + 批量操作端点
}
```

**Read-Only Query Example:**

```java
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController
    extends BaseReadOnlyController<DictionaryService, DictionaryPO, Long> {

    // 仅暴露 GET 端点，无写操作风险
    // GET    /api/dictionaries        - 列表
    // GET    /api/dictionaries/{id}   - 详情
    // GET    /api/dictionaries/page   - 分页
}
```

**Legacy Project Compatibility (not recommended for new projects):**

```java
// BaseController 已 @Deprecated(since="1.1")，仍可编译使用
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserService, User, Long> {
}
```

**Enabling Automatic Response Wrapping:**

```java
@SpringBootApplication
@EnableApiResponseWrapper  // 启用响应包装
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 6. fast-ddd-event (Event-Driven Module)

**Core Features:**

| Feature | Description |
|------|------|
| Domain events | Domain event publishing/subscribing |
| Outbox pattern | Transactional event publishing |
| RocketMQ integration | Distributed event messaging |
| Dead-letter queue | Failed event handling |

**Usage Example:**

```java
// 1. 定义领域事件
@Data
public class OrderCreatedEvent extends DomainEvent {
    private final String orderId;
    private final BigDecimal amount;

    public OrderCreatedEvent(String orderId, BigDecimal amount) {
        super(orderId, "Order");
        this.orderId = orderId;
        this.amount = amount;
    }
}

// 2. 发布事件
@Service
public class OrderServiceImpl {

    @Autowired
    private EventPublisher eventPublisher;

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        // 发布领域事件
        eventPublisher.publishEvent(
            new OrderCreatedEvent(order.getId(), order.getAmount())
        );
    }
}

// 3. 处理事件
@Component
public class OrderEventHandler {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 发送通知
        // 更新统计
        // 同步到其他系统
    }
}
```

### 7. fast-ddd-security-starter (Security Module)

**Core Features:**

| Feature | Description |
|------|------|
| JWT authentication | Stateless token authentication |
| Audit logging | Operation log recording |
| Access control | Annotation-based permission checks |
| Security validation | SQL injection and XSS protection |

**Configuration Example:**

```yaml
fastddd:
  security:
    jwt:
      enabled: true
      secret: your-secret-key-at-least-64-bytes-long!!    # HmacJwtSigner 默认使用
      expiration: 1h             # Duration 格式，必须带单位: s/m/h/d
      refresh-expiration: 7d
      token-prefix: "Bearer "
      header-name: "Authorization"
      algorithm: HS512           # 信息性质，框架不读取；RSA 场景可写 RS256
    audit:
      enabled: true
      log-success: true
      log-failure: true
```

> **Signing strategy**: HMAC-SHA512 (a symmetric key) is used by default. If you need RSA/ES256 asymmetric signing (for example, for cross-subsystem SSO), declare a `@Component` that implements the `JwtSigner` interface to override the default. See the "JWT Signing Strategy" section of `FASTDDD_REFERENCE.md` for details.

**Usage Example:**

```java
// JWT Token 生成和使用
@Autowired
private JwtTokenProvider jwtTokenProvider;

// 生成 Token
Authentication auth = new UsernamePasswordAuthenticationToken(
    userDetails, null, authorities
);
String token = jwtTokenProvider.generateAccessToken(auth);

// 权限控制
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/users/{id}")
public Result<Void> deleteUser(@PathVariable Long id) {
    userService.deleteById(id);
    return Result.success();
}
```

### 8. fast-ddd-ai-spring-boot-starter (AI Development Tools)

**Core Features:**

| Feature | Description |
|------|------|
| EasyCode | Database-driven code generation |
| FastDoc | Automatic API documentation generation |

**Configuration Example:**

```yaml
fastddd:
  ai:
    enabled: true
    easy-code:
      enabled: true
      output-dir: ./generated-code
      author: Your Name
      package-name: com.example.generated
      use-lombok: true
      use-swagger: true
    fast-doc:
      enabled: true
      scan-package: com.example.controller
      output-dir: ./docs/api
      project-name: My API
      format: markdown
```

**Usage Example:**

```java
// 代码生成
@Autowired
private EasyCodeService easyCodeService;

Map<String, Object> result = easyCodeService.generateTable("user_info",
    EasyCodeService.DatabaseConfig.create()
        .url("jdbc:mysql://localhost:3306/mydb")
        .username("root")
        .password("password")
);

// 文档生成
@Autowired
private FastDocService fastDocService;

Map<String, Object> result = fastDocService.generate("com.example.controller");
```

### 9. fast-ddd-monitor (Monitoring Module)

**Core Features:**

| Feature | Description |
|------|------|
| Performance monitoring | HTTP request and SQL execution monitoring |
| Prometheus integration | Metrics collection |
| ELK logging | Centralized log management |
| Alerting rules | Custom alerts |

**Configuration Example:**

```yaml
fastddd:
  monitor:
    enabled: true
    slow-query-threshold: 1000  # 慢查询阈值 (ms)
    sql-logging: true
```

---

## Development in Practice

### Scenario 1: Building a User Management Feature

#### 1. Design the Database Table

```sql
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    status TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT DEFAULT 0,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### 2. Generate Code

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=password \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_ \
  -Dcodegen.tables=user
```

#### 3. Customize Business Logic

```java
// 领域服务
@Service
public class UserDomainService {

    public void validateUser(User user) {
        if (isEmailDuplicate(user.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
        }
    }

    public void sendWelcomeEmail(User user) {
        // 发送欢迎邮件
    }
}

// 应用服务
@Service
public class UserServiceImpl extends BaseServiceImpl<User> implements UserService {

    @Autowired
    private UserDomainService userDomainService;

    @Autowired
    private EventPublisher eventPublisher;

    @Transactional
    @Override
    public User save(User user) {
        // 业务验证
        userDomainService.validateUser(user);

        // 保存用户
        super.save(user);

        // 发布事件
        eventPublisher.publishEvent(new UserCreatedEvent(user.getId()));

        return user;
    }
}
```

### Scenario 2: Order Processing Flow

#### 1. Define the Aggregate Root

```java
@Data
@Entity
public class Order extends AggregateRoot<Long> {

    private String orderNo;
    private Long customerId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private List<OrderItem> items;

    public void placeOrder() {
        // 业务规则验证
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_EMPTY);
        }

        // 计算金额
        this.totalAmount = calculateTotal();
        this.status = OrderStatus.PENDING;

        // 添加领域事件
        addDomainEvent(new OrderPlacedEvent(orderNo, totalAmount));
    }

    public void pay(BigDecimal amount) {
        if (this.status != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }

        this.status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(orderNo, amount));
    }

    private BigDecimal calculateTotal() {
        return items.stream()
            .map(OrderItem::getSubTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

#### 2. Implement Event Handling

```java
@Component
public class OrderEventHandler {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private NotificationService notificationService;

    @EventListener
    @Async
    public void handleOrderPlaced(OrderPlacedEvent event) {
        // 扣减库存
        inventoryService.deductInventory(event.getOrderNo());

        // 发送通知
        notificationService.sendOrderConfirmation(event.getOrderNo());
    }

    @EventListener
    @Async
    public void handleOrderPaid(OrderPaidEvent event) {
        // 更新统计
        // 触发发货流程
    }
}
```

---

## Configuration Reference

### Complete Configuration Example

```yaml
server:
  port: 8080

spring:
  application:
    name: fastddd-demo

  datasource:
    url: jdbc:mysql://localhost:3306/fastddd_db?useSSL=false
    username: root
    password: password
    driver-class-name: com.mysql.cj.jdbc.Driver

  redis:
    host: localhost
    port: 6379

# FastDDD 配置
fastddd:
  # 主开关
  enabled: true

  # AI 功能
  ai:
    enabled: true
    easy-code:
      enabled: true
      output-dir: ./generated-code
    fast-doc:
      enabled: true

  # 安全配置
  security:
    jwt:
      enabled: true
      # Required. Inject a random secret of at least 64 bytes at deployment time.
      secret: ${JWT_SECRET}
      expiration: 3600000
    audit:
      enabled: true

  # 监控配置
  monitor:
    enabled: true
    slow-query-threshold: 1000

# MyBatis Plus 配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# 日志配置
logging:
  level:
    com.gallop.fastddd: DEBUG
  config: classpath:logback-elk.xml

# 管理端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Best Practices

### 1. Naming Conventions

| Type | Convention | Examples |
|------|------|------|
| Database tables | snake_case | `user_info`, `order_detail` |
| Java classes | PascalCase | `UserService`, `OrderController` |
| Method names | camelCase, starting with a verb | `getUserById`, `createOrder` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| DTOs | PascalCase + suffix | `UserDTO`, `CreateUserRequest` |

### 2. Layer Responsibilities

```java
// ✅ 正确：各层职责清晰
@Controller
public class UserController {
    // 只负责 HTTP 请求/响应处理
    @PostMapping("/users")
    public Result<User> create(@RequestBody CreateUserRequest request) {
        return Result.success(userService.createUser(request));
    }
}

@Service
public class UserService {
    // 负责用例编排
    public User createUser(CreateUserRequest request) {
        User user = assembler.toDomain(request);
        userDomainService.validate(user);
        return userRepository.save(user);
    }
}

// ✅ 正确：领域逻辑在领域层
@Entity
public class User {
    public void changeEmail(String newEmail) {
        if (!isValidEmail(newEmail)) {
            throw new BusinessException(ErrorCode.INVALID_EMAIL);
        }
        this.email = newEmail;
    }
}

// ❌ 错误：领域逻辑泄露到 Service
@Service
public class UserService {
    public void changeEmail(Long userId, String newEmail) {
        if (newEmail == null || !newEmail.contains("@")) {
            // 验证逻辑应该在领域层
        }
    }
}
```

### 3. Exception Handling

```java
// 使用业务异常
throw new BusinessException(ErrorCode.USER_NOT_FOUND, userId);

// 自定义错误码
public enum ErrorCode {
    USER_NOT_FOUND(10001, "用户不存在"),
    EMAIL_DUPLICATE(10002, "邮箱已被使用"),
    INSUFFICIENT_BALANCE(20001, "余额不足");

    private final int code;
    private final String message;
}
```

### 4. Transaction Management

```java
// 在应用层管理事务
@Service
public class OrderService {

    @Transactional(rollbackFor = Exception.class)
    public void placeOrder(PlaceOrderRequest request) {
        // 1. 创建订单
        Order order = createOrder(request);

        // 2. 扣减库存
        inventoryService.deduct(order.getItems());

        // 3. 创建支付
        paymentService.createPayment(order);

        // 任何异常都会回滚整个事务
    }
}
```

---

## Monitoring and Operations

### Prometheus + Grafana Monitoring

Start the monitoring services:

```bash
docker-compose up -d prometheus grafana alertmanager
```

Access:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### ELK Log Analysis

Start the ELK services:

```bash
docker-compose up -d elasticsearch logstash kibana filebeat
```

Access:
- Kibana: http://localhost:5601

### Key Metrics

| Metric | Description | Alert Threshold |
|------|------|----------|
| http_request_rate | HTTP request rate | > 1000 req/s |
| http_error_rate | HTTP error rate | > 5% |
| http_response_time_p95 | P95 response time | > 500ms |
| jvm_heap_usage | JVM heap usage | > 85% |
| slow_query_count | Slow query count | > 10/min |

---

## Improvement Suggestions

### Current Items Pending Improvement

#### 1. fast-ddd-cache Module

**Current state**: The module exists but the implementation is incomplete.

**Suggestions**:
- Implement multi-level caching (local cache + Redis)
- Add a cache warm-up mechanism
- Implement a cache consistency solution
- Support cache annotations (@Cacheable, @CacheEvict)

```java
// 计划实现的功能
@Cacheable(value = "user", key = "#id")
public User getById(Long id) {
    return userRepository.findById(id);
}

@CacheEvict(value = "user", key = "#id")
public void deleteUser(Long id) {
    userRepository.deleteById(id);
}
```

#### 2. fast-ddd-search Module

**Current state**: The module exists but the Elasticsearch integration is incomplete.

**Suggestions**:
- Complete the Elasticsearch Repository
- Implement a search condition builder
- Add aggregation query support
- Implement a data synchronization mechanism

```java
// 计划实现的功能
@SpringBootTest
public class SearchServiceTest {
    @Test
    public void testFullTextSearch() {
        // 全文搜索测试
    }

    @Test
    public void testAggregationQuery() {
        // 聚合查询测试
    }
}
```

#### 3. Test Coverage

**Current state**: Some modules lack sufficient test coverage.

**Suggestions**:
- Target unit test coverage above 80%
- Add integration tests
- Add end-to-end tests
- Use JMH for performance benchmarking

#### 4. Documentation Improvements

**Current state**: Some modules lack documentation.

**Suggestions**:
- Add a README for each module
- Improve JavaDoc comments
- Add more usage examples
- Create a troubleshooting guide

#### 5. Frontend Platform Integration

**Current state**: The Vue 3 platform exists but the integration is incomplete.

**Suggestions**:
- Implement automatic API integration
- Add a permission management UI
- Visualize code generation
- Add monitoring dashboards

#### 6. DevOps Improvements

**Suggestions**:
- Optimize CI/CD pipelines
- Integrate automated testing
- Automate deployment
- Support blue-green deployment / canary releases

---

## Multi-Channel Auth

### Overview

FastDDD supports two authentication modes — single-channel and multi-channel — selected as needed through the Studio code generator.

| Mode | Applicable Scenarios | Authentication Methods | Project Structure |
|------|---------|---------|---------|
| Single-channel (SINGLE) | Admin consoles, single-channel business systems | Password | Single Spring Boot application |
| Multi-channel (MULTI) | E-commerce (mini-program + admin), SaaS, logistics | Password/SMS/WeChat mini-program | Maven multi-module |

### Multi-Channel Project Structure

```
my-project/                        ← Maven 父工程
├── pom.xml
├── common/                        ← 共享模块
│   ├── common-user/               ← 用户领域 (Entity/Mapper/Service)
│   ├── common-order/              ← 订单领域
│   └── common-shared/             ← 跨领域公共
├── admin-api/                     ← 管理端 (8080)
│   ├── src/.../security/          ← AppIdentityService
│   └── src/.../controller/
├── customer-api/                  ← 客户端 (8081)
│   ├── src/.../security/          ← AppIdentityService
│   └── src/.../controller/
├── docker-compose.yml
└── docker/nginx/default.conf
```

### Studio Configuration Steps

1. **All-in-One Generator** or **My Projects** → select the data source and tables
2. **Security Configuration** → select `多端模式 (MULTI)`
3. Configure channels: enable ADMIN/CUSTOMER/MERCHANT and choose authentication strategies
4. **Table assignment**: assign tables to the corresponding sub-application or to shared modules
5. Generate → download the ZIP

### Token Isolation

Each sub-application uses a different JWT Secret + channel validation:

```yaml
# admin-api/application.yml
fastddd:
  security:
    jwt:
      channel: ADMIN
      secret: admin-secret-change-me
```

```yaml
# customer-api/application.yml
fastddd:
  security:
    jwt:
      channel: CUSTOMER
      secret: customer-secret-change-me
```

The framework's built-in `JwtAuthenticationFilter` verifies that the token's channel matches the application configuration, achieving isolation between channels.

### Authentication Strategies

| Strategy | type | Trigger Condition | Generation Condition |
|------|------|---------|---------|
| Password | `password` | Default when no type is specified | Built into the framework, no generation needed |
| SMS verification code | `sms_code` | Explicitly pass `type=sms_code`, with `phone` + `code` in extra | Built into the framework; the business implements `VerificationCodeSender` |
| Email verification code | `email_code` | Explicitly pass `type=email_code`, with `email` + `code` in extra | Built into the framework; the business implements `VerificationCodeSender` |
| WeChat mini-program | `wechat-mini` | Explicitly pass `type=wechat-mini`, with `wechatCode` in extra | Channel strategy includes `wechat-mini` |

The login endpoint is unified as `POST /api/auth/login`; the framework routes the request to the corresponding strategy based on `type`, and uses `password` when no `type` is passed.

#### Overriding Framework Default Strategies with Custom Ones

The application layer can override a framework default by registering an `AuthStrategy` of the same type via `@Component`. When `AuthStrategyRegistry` detects a conflict, it **keeps the application-defined strategy with priority**, and the built-in framework strategy automatically yields.

```java
@Service
@ConditionalOnProperty(name = "app.auth.dev-mode", havingValue = "true")
public class DevPasswordAuthStrategy implements AuthStrategy {
    @Override
    public String getType() { return "password"; }  // 同 type 覆盖框架 PasswordAuthStrategy

    @Override
    public Authentication authenticate(AuthenticationRequest request) {
        // 自定义认证逻辑
    }
}
```

> The framework determines a strategy's origin by its package name: strategies under the `com.gallop.fastddd.security.strategy` package are framework built-ins; all others are user-defined.

Verification code sending endpoint:

```text
POST /api/auth/code/send
```

```json
{
  "type": "email",
  "target": "user@example.com",
  "purpose": "login"
}
```

### Generated Security Components

Each sub-application is generated with:

- `AppIdentityService.java` — implements `SecurityIdentityService`, wired to the common-user module
- `AppIdentityService.java` also implements `CodeLoginIdentityService`, providing the lookups required for phone-number/email verification-code login
- `WechatMiniAuthStrategy.java` — (optional) WeChat mini-program authentication

### Deployment

```bash
# Docker Compose 一键部署
docker-compose up -d

# Nginx 反向代理: /api/admin → admin-api:8080, /api/customer → customer-api:8081
```

---

## Appendix

### Frequently Asked Questions

**Q: How do I customize exception handling?**

A: Implement a `GlobalExceptionHandler`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getErrorCode(), e.getMessage());
    }
}
```

**Q: How do I implement multi-tenancy?**

A: Use a MyBatis Plus interceptor:

```java
@Component
public class TenantInterceptor implements InnerInterceptor {
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms,
                           Object parameter, RowBounds rowBounds) {
        // 添加租户条件
    }
}
```

**Q: How do I implement distributed transactions?**

A: The framework recommends the Saga pattern or the TCC pattern:

```java
@Saga
public void processOrder(Order order) {
    // Saga 事务编排
}
```

### Technical Support

- Issue Tracker: to be announced along with the public Framework Community repository
- Documentation site: this repository's `docs/README.md` currently serves as the entry point
- Community discussion forum: to be added once the public channel is confirmed

---

**Document version**: 1.1.0
**Last updated**: 2026-04-21
**Maintainers**: FastDDD Team
