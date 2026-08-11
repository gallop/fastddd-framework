# FastDDD 框架使用指南

> 企业级领域驱动设计 (DDD) 开发框架 - 完整使用手册

---

## 目录

1. [快速开始](#快速开始)
2. [框架概述](#框架概述)
3. [核心模块详解](#核心模块详解)
4. [开发实战指南](#开发实战指南)
5. [配置参考](#配置参考)
6. [最佳实践](#最佳实践)
7. [监控与运维](#监控与运维)
8. [多端认证](#多端认证multi-channel-auth)
9. [改进建议](#改进建议)

---

## 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.8+ |
| Spring Boot | 3.2.0+ |
| MySQL | 8.0+ (生产环境) |
| Redis | 6.0+ (可选) |
| Docker | 20.10+ (可选) |

### 五分钟快速上手

#### 1. 添加依赖

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
    <version>1.0.0-jdk21-SNAPSHOT</version>
</dependency>
```

#### 2. 配置数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/your_db
    username: root
    password: password
```

#### 3. 生成代码

```bash
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-SNAPSHOT:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=password \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_
```

#### 4. 运行应用

```bash
mvn spring-boot:run
```

---

## 框架概述

### DDD 四层架构

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

### 核心设计原则

- **高标准**: 统一代码/架构/文档标准，90%+ 代码一致性
- **高效率**: 代码生成将单表 CRUD 开发从 2 天缩短到 5 分钟
- **高协作**: 标准化接口规范，集成时间减少 50%

---

## 核心模块详解

### 模块依赖关系图

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

### 1. fast-ddd-common (基础工具模块)

**核心功能：**

| 组件 | 说明 |
|------|------|
| `Result<T>` | 统一响应封装 |
| `PageResult<T>` | 分页响应封装 |
| `BusinessException` | 业务异常基类 |
| `@ConditionalOnDev` | 开发环境条件注解 |
| `@ConditionalOnSystemProperty` | 系统属性条件注解 |
| `JsonUtil` | JSON 工具类 |
| `DateUtil` | 日期工具类 |

**使用示例：**

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

### 2. fast-ddd-config (配置管理模块)

**核心功能：**

- 条件加载机制
- 多环境配置支持
- 动态配置刷新

**配置示例：**

```yaml
fastddd:
  config:
    environment: dev
    dynamic-refresh: true
```

### 3. fast-ddd-model (数据模型模块)

**核心基类：**

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
public class UserPO extends BasePO {
    private String username;
    private String email;
    private String phone;
}
```

### 4. fast-ddd-crud (CRUD 操作模块)

**核心功能：**

| 功能 | 说明 |
|------|------|
| 通用 CRUD | 基于 MyBatis Plus 的增删改查 |
| 批量操作 | 批量插入、批量更新 |
| 自动填充 | 创建时间、更新时间自动填充 |
| 逻辑删除 | 软删除支持 |
| 乐观锁 | 版本号并发控制 |

**使用示例：**

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

### 5. fast-ddd-web (Web 层模块)

**核心功能：**

| 功能 | 说明 |
|------|------|
| BaseAdminCrudController | 管理端完整 CRUD 控制器基类（1.1 新增） |
| BaseReadOnlyController | 只读查询控制器基类（1.1 新增） |
| BaseController | 通用控制器基类（已 @Deprecated，保留兼容） |
| 自动响应包装 | ApiResponse 包装 |
| 统一异常处理 | 全局异常捕获 |
| 请求日志 | 自动请求/响应日志 |

**Controller 基类选择：**

| 场景 | 推荐基类 | 示例 |
|------|---------|------|
| 后台管理 CRUD | `BaseAdminCrudController` | 字典表、配置表、低风险资源 |
| 只读查询 | `BaseReadOnlyController` | 下拉选项、数据展示 |
| 敏感业务 API | 不继承 CRUD 基类 | 认证、支付、订单 |
| 旧项目兼容 | `BaseController`（已废弃） | 已有项目，暂不迁移 |

**管理端 CRUD 示例：**

```java
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController
    extends BaseAdminCrudController<UserService, User> {

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

**只读查询示例：**

```java
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController
    extends BaseReadOnlyController<DictionaryService, DictionaryPO> {

    // 仅暴露 GET 端点，无写操作风险
    // GET    /api/dictionaries        - 列表
    // GET    /api/dictionaries/{id}   - 详情
    // GET    /api/dictionaries/page   - 分页
}
```

**兼容旧项目（不推荐新项目使用）：**

```java
// BaseController 已 @Deprecated(since="1.1")，仍可编译使用
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserService, User> {
}
```

**启用自动响应包装：**

```java
@SpringBootApplication
@EnableApiResponseWrapper  // 启用响应包装
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 6. fast-ddd-event (事件驱动模块)

**核心功能：**

| 功能 | 说明 |
|------|------|
| 领域事件 | 领域事件发布/订阅 |
| Outbox 模式 | 事务性事件发布 |
| RocketMQ 集成 | 分布式事件消息 |
| 死信队列 | 失败事件处理 |

**使用示例：**

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

### 7. fast-ddd-security-starter (安全模块)

**核心功能：**

| 功能 | 说明 |
|------|------|
| JWT 认证 | 无状态 Token 认证 |
| 审计日志 | 操作日志记录 |
| 权限控制 | 基于注解的权限验证 |
| 安全验证 | SQL 注入、XSS 防护 |

**配置示例：**

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

> **签名策略**：默认使用 HMAC-SHA512（对称密钥）。如需 RSA/ES256 非对称签名（如跨子系统 SSO），声明 `@Component` 实现 `JwtSigner` 接口即可覆盖默认，详见 `FASTDDD_REFERENCE.md` 的"JWT 签名策略"章节。

**使用示例：**

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

### 8. fast-ddd-ai-spring-boot-starter (AI 开发工具)

**核心功能：**

| 功能 | 说明 |
|------|------|
| EasyCode | 数据库驱动代码生成 |
| FastDoc | 自动 API 文档生成 |

**配置示例：**

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

**使用示例：**

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

### 9. fast-ddd-monitor (监控模块)

**核心功能：**

| 功能 | 说明 |
|------|------|
| 性能监控 | HTTP 请求、SQL 执行监控 |
| Prometheus 集成 | 指标采集 |
| ELK 日志 | 集中式日志管理 |
| 告警规则 | 自定义告警 |

**配置示例：**

```yaml
fastddd:
  monitor:
    enabled: true
    slow-query-threshold: 1000  # 慢查询阈值 (ms)
    sql-logging: true
```

---

## 开发实战指南

### 场景一：创建用户管理功能

#### 1. 设计数据库表

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

#### 2. 生成代码

```bash
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-jdk21-SNAPSHOT:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=password \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_ \
  -Dcodegen.tables=user
```

#### 3. 自定义业务逻辑

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

### 场景二：订单处理流程

#### 1. 定义聚合根

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

#### 2. 实现事件处理

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

## 配置参考

### 完整配置示例

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

## 最佳实践

### 1. 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 数据库表 | snake_case | `user_info`, `order_detail` |
| Java 类 | PascalCase | `UserService`, `OrderController` |
| 方法名 | camelCase, 动词开头 | `getUserById`, `createOrder` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| DTO | PascalCase + 后缀 | `UserDTO`, `CreateUserRequest` |

### 2. 分层职责

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

### 3. 异常处理

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

### 4. 事务管理

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

## 监控与运维

### Prometheus + Grafana 监控

启动监控服务：

```bash
docker-compose up -d prometheus grafana alertmanager
```

访问：
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### ELK 日志分析

启动 ELK 服务：

```bash
docker-compose up -d elasticsearch logstash kibana filebeat
```

访问：
- Kibana: http://localhost:5601

### 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| http_request_rate | HTTP 请求速率 | > 1000 req/s |
| http_error_rate | HTTP 错误率 | > 5% |
| http_response_time_p95 | P95 响应时间 | > 500ms |
| jvm_heap_usage | JVM 堆使用率 | > 85% |
| slow_query_count | 慢查询数量 | > 10/min |

---

## 改进建议

### 当前待完善项

#### 1. fast-ddd-cache 模块

**现状**：模块存在但实现不完整

**建议**：
- 实现多级缓存（本地缓存 + Redis）
- 添加缓存预热机制
- 实现缓存一致性方案
- 支持缓存注解 (@Cacheable, @CacheEvict)

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

#### 2. fast-ddd-search 模块

**现状**：模块存在但 Elasticsearch 集成不完整

**建议**：
- 完善 Elasticsearch Repository
- 实现搜索条件构建器
- 添加聚合查询支持
- 实现数据同步机制

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

#### 3. 测试覆盖率

**现状**：部分模块测试覆盖不足

**建议**：
- 单元测试覆盖率目标 > 80%
- 添加集成测试
- 添加端到端测试
- 使用 JMH 进行性能基准测试

#### 4. 文档完善

**现状**：部分模块缺少文档

**建议**：
- 为每个模块添加 README
- 完善 JavaDoc 注释
- 添加更多使用示例
- 创建故障排查指南

#### 5. 前端平台集成

**现状**：Vue 3 平台存在但集成不完整

**建议**：
- 实现 API 自动对接
- 添加权限管理界面
- 实现代码生成可视化
- 添加监控仪表板

#### 6. DevOps 完善

**建议**：
- CI/CD 流水线优化
- 自动化测试集成
- 自动化部署
- 蓝绿部署/金丝雀发布支持

---

## 多端认证（Multi-Channel Auth）

### 概述

FastDDD 支持单端和多端两种认证模式，通过 Studio 代码生成器按需选择。

| 模式 | 适用场景 | 认证方式 | 项目结构 |
|------|---------|---------|---------|
| 单端 (SINGLE) | 管理后台、单端业务系统 | 密码 | 单 Spring Boot 应用 |
| 多端 (MULTI) | 电商(小程序+管理)、SaaS、物流 | 密码/短信/微信小程序 | Maven 多模块 |

### 多端项目结构

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

### Studio 配置步骤

1. **一体化生成器** 或 **我的项目** → 选择数据源和表
2. **Security 配置** → 选择 `多端模式 (MULTI)`
3. 配置通道: 启用 ADMIN/CUSTOMER/MERCHANT，选择认证策略
4. **表分配**: 将表分配到对应的子应用或共享
5. 生成 → 下载 ZIP

### Token 隔离

每个子应用使用不同的 JWT Secret + channel 校验:

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

框架内置 `JwtAuthenticationFilter` 会校验 Token 的 channel 与应用配置是否匹配，实现端间隔离。

### 认证策略

| 策略 | type | 触发条件 | 生成条件 |
|------|------|---------|---------|
| 密码 | `password` | 未指定 type 时默认 | 框架内置，无需生成 |
| 短信验证码 | `sms_code` | 显式传入 `type=sms_code`，extra 中含 `phone` + `code` | 框架内置，业务实现 `VerificationCodeSender` |
| 邮箱验证码 | `email_code` | 显式传入 `type=email_code`，extra 中含 `email` + `code` | 框架内置，业务实现 `VerificationCodeSender` |
| 微信小程序 | `wechat-mini` | 显式传入 `type=wechat-mini`，extra 中含 `wechatCode` | 通道策略含 `wechat-mini` |

登录接口统一为 `POST /api/auth/login`，框架根据请求中的 `type` 路由到对应策略；未传 `type` 时使用 `password`。

#### 自定义策略覆盖框架默认策略

应用层可通过 `@Component` 注册同 type 的 `AuthStrategy` 来覆盖框架默认实现。`AuthStrategyRegistry` 在检测到冲突时，**优先保留应用层定义的策略**，框架内置策略自动退让。

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

> 框架通过包名判断策略来源：`com.gallop.fastddd.security.strategy` 包下的为框架内置，其余为用户自定义。

验证码发送接口:

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

### 生成的 Security 组件

每个子应用会生成:

- `AppIdentityService.java` — 实现 `SecurityIdentityService`，连接 common-user 模块
- `AppIdentityService.java` 同时实现 `CodeLoginIdentityService`，提供手机号/邮箱验证码登录所需查询
- `WechatMiniAuthStrategy.java` — (可选) 微信小程序认证

### 部署

```bash
# Docker Compose 一键部署
docker-compose up -d

# Nginx 反向代理: /api/admin → admin-api:8080, /api/customer → customer-api:8081
```

---

## 附录

### 常见问题

**Q: 如何自定义异常处理？**

A: 实现 `GlobalExceptionHandler`：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getErrorCode(), e.getMessage());
    }
}
```

**Q: 如何实现多租户？**

A: 使用 MyBatis Plus 拦截器：

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

**Q: 如何实现分布式事务？**

A: 框架建议使用 Saga 模式或 TCC 模式：

```java
@Saga
public void processOrder(Order order) {
    // Saga 事务编排
}
```

### 技术支持

- GitHub Issues: [提交问题](https://github.com/your-repo/issues)
- 文档站点: [在线文档](https://docs.fastddd.com)
- 社区论坛: [讨论区](https://community.fastddd.com)

---

**文档版本**: 1.1.0
**最后更新**: 2026-04-21
**维护者**: FastDDD Team
