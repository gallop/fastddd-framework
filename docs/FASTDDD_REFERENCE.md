# FastDDD Framework Reference for Claude Code

> 本文档为 FastDDD 框架的完整参考，可直接复制到新项目的 CLAUDE.md 中使用。

## 框架概述

FastDDD 是基于 Spring Boot 3.x 的企业级 DDD 开发框架。提供标准化的分层架构、代码生成、事件驱动、多端安全认证等能力。

**Maven 坐标**: `com.gallop.fastddd:fast-ddd-*:1.0.0-SNAPSHOT`
**Java 版本**: 17+
**Spring Boot 版本**: 3.4.x

---

## 模块依赖关系

```
fast-ddd-starter (一键引入核心五模块)
 ├── fast-ddd-common    # 基础工具、领域基类、异常、结果包装
 ├── fast-ddd-config    # 条件注解 (@ConditionalOnDev 等)
 ├── fast-ddd-model     # 数据模型基类 BasePO
 ├── fast-ddd-crud      # CRUD 服务/Repository 增强
 └── fast-ddd-web       # Web 层基类、统一响应、异常处理

fast-ddd-security-starter  # JWT 认证 + CORS + 审计日志 + 多端通道
fast-ddd-application       # CQRS 命令/查询基础设施
fast-ddd-event             # 领域事件 (同步/异步/Outbox/RocketMQ)
fast-ddd-cache             # 两级缓存 (Caffeine L1 + Redis L2)
fast-ddd-search            # 全文搜索 (SQL LIKE/FULLTEXT + Meilisearch 可选)
fast-ddd-monitor           # SQL 性能监控 + Prometheus 指标
fast-ddd-ai-spring-boot-starter       # AI/LLM 提示词模板
fast-ddd-analyzer-spring-boot-starter # 智能表分析 (领域划分)
```

**典型项目 POM 依赖选择**:
- 基础 CRUD 项目: `fast-ddd-starter` + `fast-ddd-security-starter`
- 充血模型项目: 额外引入 `fast-ddd-application` + `fast-ddd-event`
- 完整企业项目: 全部引入

---

## 两套基类体系

FastDDD 提供两套独立的基类体系，字段命名已统一：

| 字段 | BaseEntity (领域层) | BasePO (持久层) | DB 列名 |
|------|-------------------|----------------|---------|
| 创建时间 | `protected LocalDateTime createTime` | `@TableField(fill=INSERT) createTime` | `create_time` |
| 更新时间 | `protected LocalDateTime updateTime` | `@TableField(fill=INSERT_UPDATE) updateTime` | `update_time` |
| 创建人 | `protected String createUser` | `@TableField(fill=INSERT) createUser` | `create_user` |
| 更新人 | `protected String updateUser` | `@TableField(fill=INSERT_UPDATE) updateUser` | `update_user` |
| 逻辑删除 | `protected Integer deleted` | `@TableLogic Integer deleted` | `deleted` |
| 主键 | `protected ID id` (abstract getId/setId) | `protected Serializable id` (内置 @TableId) | `id` |
| 版本号 | `VersionedEntity` 子类提供 | `VersionedPO` 子类提供 | `version` |

**version 字段按需继承**：只有数据库表有 `version` 列的实体才继承 `VersionedPO`/`VersionedEntity`，其余继承 `BasePO`/`BaseEntity`。

**统一收益**: 充血模型 Entity ↔ PO 转换时，公共字段名完全一致，BeanUtils/MapStruct 可直接拷贝。

---

## 核心基类速查

### 1. 贫血模型 — BasePO (fast-ddd-model)

```java
@TableName("t_order")
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
public class Order extends BasePO {

    private String orderNo;
    private Integer status;

    // id 字段已由 BasePO 内置，无需声明 @TableId / getId() / setId()
    // getId() 返回 Serializable，需具体类型时强转: (Long) entity.getId()
}
// BasePO 自动提供: id, createTime, updateTime, createUser, updateUser, deleted
// createTime/updateTime/createUser/updateUser 由框架 AutoFill 自动填充
// 需要乐观锁的实体请继承 VersionedPO（自动包含 version 字段）
```

**关键规则**:
- 继承 `BasePO` 时用 `@Getter/@Setter`，不用 `@Data`（`@Data` 的 `equals/hashCode` 对 JPA/MyBatis 代理对象有副作用）
- `BasePO` 已内置 `id` 字段（`@TableId(type = IdType.ASSIGN_ID)`），子类无需再声明 `id`、`@TableId`、`getId()`、`setId()`
- 不要在子类中重复声明 `createTime`, `updateTime`, `createUser`, `updateUser`, `deleted` — 这些已由 `BasePO` 提供
- ID 策略通过全局配置 `mybatis-plus.global-config.db-config.id-type` 控制，子类可重新声明 `id` 覆盖
- 需要乐观锁的实体继承 `VersionedPO`（自动包含 `version` 字段 + `@Version` 注解），不需要乐观锁的直接继承 `BasePO`
- `BasePO.id` 类型为 `Serializable`（兼容 Long/String 等），框架自动注册 `SerializableTypeHandler`，MyBatis-Plus 结果映射无需额外配置

### 2. 充血模型 — AggregateRoot (fast-ddd-common)

```java
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
public class Order extends AggregateRoot<Long> {
    private String orderNo;
    private Integer status;

    // 必须显式实现 getId()/setId() — 直接访问父类 protected id 字段
    @Override public Long getId() { return id; }
    @Override public void setId(Long id) { this.id = id; }

    // 领域行为
    public void pay() {
        this.status = 1;
        addDomainEvent(new OrderPaidEvent(this.id, this.orderNo));
    }
}
// BaseEntity 自动提供: createTime, updateTime, createUser, updateUser, deleted
// prePersist()/preUpdate() 自动填充时间字段
// 需要乐观锁的领域实体请继承 VersionedEntity（自动包含 version 字段）
```

**充血模型 PO 与 Entity 转换** (infrastructure 层):

```java
// PO — 持久化对象，对应数据库表
@TableName("t_order")
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
public class OrderPO extends BasePO {
    // id 已由 BasePO 内置，无需声明
    private String orderNo;
    private Integer status;
}

// EntityMapper — PO ↔ Entity 转换器
// 由于 BaseEntity 和 BasePO 字段名统一，公共字段可直接拷贝
@Component
public class OrderEntityMapper {
    public Order toEntity(OrderPO po) {
        Order order = new Order();
        order.setId(po.getId());
        order.setOrderNo(po.getOrderNo());
        order.setStatus(po.getStatus());
        // 公共字段名一致，可使用 BeanUtils.copyProperties 或逐个设置
        order.setCreateTime(po.getCreateTime());
        order.setUpdateTime(po.getUpdateTime());
        // 如需乐观锁: order.setVersion(po.getVersion()) — PO 需继承 VersionedPO，Entity 需继承 VersionedEntity
        return order;
    }
    public OrderPO toPo(Order entity) { /* 反向映射 */ }
}

// Repository 实现
@Repository
public class OrderRepositoryImpl implements EntityRepository<Order, Long> {
    private final OrderBaseMapper baseMapper;  // extends BaseMapper<OrderPO>
    private final OrderEntityMapper entityMapper;

    @Override
    public Order save(Order entity) {
        OrderPO po = entityMapper.toPo(entity);
        if (po.getId() == null) baseMapper.insert(po);
        else baseMapper.updateById(po);
        return entityMapper.toEntity(po);
    }
}
```

**关键规则**:
- 继承 `AggregateRoot` 时用 `@Getter/@Setter`，不用 `@Data`
- 不要调用 `super.getId()` — 它是抽象方法，无实现，直接访问 `protected id` 字段
- DTO 跨包访问必须用 `private` 字段 + `@Data`

### 3. 数据层 (fast-ddd-crud)

```java
// Mapper — 继承 BaseRepository 获得通用查询方法
// BaseRepository<T> 不要求 T 继承 BasePO，任何实体都能用
public interface OrderMapper extends BaseRepository<Order> {
    // 自动继承: selectOneByField, selectListByField, existsByField, deleteByField 等
}

// 不继承 BasePO 的纯数据表也能直接用 BaseRepository
public interface VaccineScheduleMapper extends BaseRepository<VaccineSchedule> {
    // 便捷查询方法同样可用，无需 BasePO 审计字段
}

// Service 接口 — 框架 IService 要求 T extends BasePO
public interface OrderService extends IService<Order> {
    // 继承: save, removeById, updateById, getById, list, page, getByField 等
}

// Service 实现
@Service
public class OrderServiceImpl extends BaseServiceImpl<OrderMapper, Order> implements OrderService {
    // baseMapper 已自动注入，所有 CRUD 方法继承即可
}
```

**BaseRepository 内置查询方法**（无需手写 SQL，字段名支持 Java 驼峰自动映射）:
- `selectOneByField("username", "admin")` → `Optional<T>`
- `selectListByField("status", 1)` → `List<T>`
- `selectListByFieldLike("nickname", "%张%")` → `List<T>`
- `selectListByFieldIn("status", List.of(1,2,3))` → `List<T>`
- `selectListByFields("status", 1, "department", "IT")` → `List<T>`（AND 条件）
- `existsByField("username", "admin")` → `boolean`
- `countByField("status", 1)` → `long`
- `deleteByField("status", 0)` → `int`
- `batchInsert(List<T>)` → 批量插入
- `batchUpdateById(List<T>)` → 批量更新

> **字段名约定**：`*ByField` 方法的 `field` 参数写 Java 驼峰名（如 `createTime`、`isDefault`），框架自动转为数据库列名（`create_time`、`is_default`）。传入 snake_case 也兼容。

> **泛型约束说明**：`BaseRepository<T>` 对 `T` 无约束（不要求继承 BasePO），便捷查询方法适用于任何实体表。`BaseServiceImpl<M, T>` 和 `IService<T>` 仍要求 `T extends BasePO`，因为 Service 层需要审计字段自动填充。纯数据表（无审计字段）用 Mapper 层即可，不需要走框架 Service 层。

### 4. Web 层 (fast-ddd-web)

#### Controller 基类层次结构

```
AbstractBaseController              ← 基础设施（service、resolveId、resolveEntityClass），无端点
├── BaseReadOnlyController          ← 只读查询（getById、list、page）
│   └── BaseAdminCrudController     ← 完整 CRUD + 8 个 lifecycle hooks
│       └── BaseController          ← @Deprecated 兼容入口
```

#### 后台管理 CRUD（推荐）

```java
@RestController
@RequestMapping("/api/admin/orders")
public class OrderAdminController extends BaseAdminCrudController<OrderService, Order> {
    // 自动继承以下 REST 端点:
    // GET    /{id}                     — 按ID查询
    // GET    /                         — 列表查询
    // GET    /page?current=1&size=10   — 分页查询
    // POST   /                         — 新增单条
    // POST   /batch                    — 批量新增
    // PUT    /{id}                     — 按ID更新
    // PUT    /                         — 条件更新
    // PUT    /batch                    — 批量更新
    // DELETE /{id}                     — 按ID删除
    // DELETE /batch                    — 批量删除

    // 可覆写生命周期 Hook:
    // beforeCreate / afterCreate / beforeUpdate / afterUpdate
    // beforeDelete / afterDelete / beforeCreateBatch / beforeUpdateBatch
}
```

#### 只读查询

```java
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController extends BaseReadOnlyController<DictionaryService, DictionaryPO> {
    // 仅暴露 GET 端点:
    // GET /{id}                       — 按ID查询
    // GET /                           — 列表查询
    // GET /page?current=1&size=10     — 分页查询
}
```

#### 兼容旧项目（不推荐新项目使用）

```java
// BaseController 已 @Deprecated(since="1.1")，仍可编译使用
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserService, User> {
}
```

#### 使用建议

| 场景 | 推荐基类 | 说明 |
|------|---------|------|
| 后台管理 CRUD | `BaseAdminCrudController` | 完整增删改查，路径应放在 `/api/admin/**` |
| 只读字典/查询 | `BaseReadOnlyController` | 仅 GET 方法，无写操作风险 |
| 敏感业务 API | 不继承任何 CRUD 基类 | 显式声明每个端点，避免隐式暴露 |
| 旧项目兼容 | `BaseController`（已废弃） | 保持编译通过，建议逐步迁移 |

**统一响应格式** (`ApiResponse<T>`):
```json
{ "code": 200, "message": "操作成功", "data": {...}, "timestamp": 1704067200000 }
```

**ID 路径变量自动类型转换**：`GET /{id}`、`PUT /{id}`、`DELETE /{id}` 端点的路径变量已自动处理类型转换。框架根据实体的 MyBatis-Plus `TableInfo.idType` 判断 ID 类型：

| `id-type` 配置 | URL 路径 `/{id}` | 控制器收到的类型 |
|----------------|-------------------|-----------------|
| `assign_id`（雪花算法，推荐） | `/api/users/18956483271045` | `Long` |
| `auto`（数据库自增） | `/api/users/123` | `Long` |
| `assign_uuid` | `/api/orders/a1b2c3d4-...` | `String` |

业务项目**无需任何额外配置**，ID 转换由 `AbstractBaseController.resolveId()` 自动完成。覆写端点时参数改为 `String idStr` 并调用 `resolveId(idStr)` 即可。

启用自动包装: 在 `Application` 类上加 `@EnableApiResponseWrapper`

### 5. CQRS 充血模型 (fast-ddd-application)

```java
// 命令
public class CreateOrderCommand extends Command {
    @NotBlank private String customerId;
    private List<OrderItemDto> items;
}

// 命令处理器
@Component
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {
    @Override
    public OrderDto handle(CreateOrderCommand cmd) {
        Order order = Order.create(cmd.getCustomerId(), cmd.getItems());
        orderRepository.saveAndPublishEvents(order);
        return OrderDto.from(order);
    }
}

// 应用服务 — 使用 @ApplicationService 语义化标注
@ApplicationService(aggregate = Order.class, description = "订单应用服务")
public class OrderApplicationService { ... }

// 命令控制器
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {
    @Override protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return createOrderCommandHandler;
    }
}

// 查询控制器 (读侧)
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController extends QueryController<GetOrderByIdQuery, OrderDto> {
    @Override protected QueryHandler<GetOrderByIdQuery, OrderDto> getQueryHandler() {
        return getOrderByIdQueryHandler;
    }
}
```

---

## 安全模块 (fast-ddd-security-starter)

### 快速集成指南

#### 1. 引入依赖

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-security-starter</artifactId>
</dependency>
```

#### 2. 启动类

```java
@SpringBootApplication(scanBasePackages = "com.example.app")
@MapperScan("com.example.app")    // 必须加！BaseRepository 底层是 BaseMapper，不会自动扫描
@EnableApiResponseWrapper
public class Application { ... }
```

#### 3. YAML 配置

```yaml
fastddd:
  security:
    jwt:
      enabled: true
      secret: your-secret-key-at-least-64-bytes-long!!
      expiration: 24h          # Duration 类型，必须带单位: s/m/h/d
      refresh-expiration: 7d
      channel: DEFAULT         # 多端通道: DEFAULT/ADMIN/CUSTOMER/MERCHANT
      strict-channel: false    # 严格模式下 DEFAULT 令牌也会被拒绝
    cors:
      enabled: true
      allowed-origins:
        - http://localhost:3000
      allowed-methods:         # 必须用 YAML list 格式，不能用逗号分隔字符串
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      max-age: 3600s           # Duration 类型，必须带单位
    http:
      permit-all-paths:        # 精确放行，不要用 /api/auth/** 通配符
        - /api/auth/login
        - /api/auth/code/send
        - /api/auth/refresh
        - /actuator/health
    code:
      enabled: true
      length: 6
      ttl: 5m                  # 验证码有效期
      max-attempts: 5          # 单个验证码最大校验次数
      resend-interval: 60s     # 同一目标重复发送间隔
      dev-mode: false          # true 时注册 Noop sender，仅打印验证码日志
```

**配置注意点**:
- 前缀是 `fastddd:`，不是 `fast-ddd:`
- 所有 Duration 字段必须带单位后缀 (`s`/`m`/`h`/`d`)
- `permit-all-paths` 不要写 `/api/auth/**`，否则 `/api/auth/userinfo` 也会被跳过 JWT 认证，导致 principal 为 `anonymousUser`
- 验证码发送接口 `/api/auth/code/send` 必须允许未登录访问；生产环境必须提供真实的 `VerificationCodeSender`

#### 4. 实体类

```java
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BasePO {
    // 不要重新声明 id 字段！BasePO 已有 protected Serializable id
    // 框架通过 SerializableTypeHandler 自动处理 Long ↔ Serializable 转换

    private String username;
    private String passwordHash;
    private String nickname;
    private String role;
    private Integer status;
}
```

#### 5. 实现 SPI（两个 @Service）

安全模块使用 `@AutoConfiguration`（deferred import），业务项目只需 `@Service` 声明即可自动替换框架默认实现，无需 `@Import`。

```java
// 1. 身份服务 — 替换 DefaultSecurityIdentityService
@Service
@RequiredArgsConstructor
public class AppIdentityService implements SecurityIdentityService {

    private final UserMapper userMapper;

    @Override
    public SecurityUserDetails loadUserByUsername(String username) {
        return userMapper.selectOneByField("username", username)   // 字段名用 Java 驼峰
                .map(user -> SecurityUserDetails.builder()
                        .userId(String.valueOf(user.getId()))      // userId 会写入 JWT claims
                        .username(user.getUsername())
                        .password(user.getPasswordHash())
                        .enabled(user.getStatus() != null && user.getStatus() == 1)
                        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                        .build())
                .orElse(null);
    }

    @Override
    public SecurityUserInfo getUserInfo(String userId) {
        User user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) return null;
        return SecurityUserInfo.builder()
                .id(String.valueOf(user.getId()))
                .username(user.getUsername())
                .realName(user.getNickname())
                .enabled(user.getStatus() != null && user.getStatus() == 1)
                .status(user.getStatus())
                .build();
    }
}

// 2. 权限服务 — 替换 DefaultSecurityPermissionService
@Service
@RequiredArgsConstructor
public class AppPermissionService implements SecurityPermissionService {

    private final UserMapper userMapper;

    @Override
    public List<String> getPermissionsByUserId(String userId) {
        return List.of("*");
    }

    @Override
    public List<String> getRolesByUserId(String userId) {
        User user = userMapper.selectById(Long.valueOf(userId));
        return user != null ? List.of(user.getRole()) : List.of();
    }
}
```

**关键**：`SecurityUserDetails.builder()` 必须设置 `userId`，框架会将其写入 JWT claims。后续请求中 `JwtAuthenticationFilter` 自动从 Token 提取 userId 作为 principal，`/api/auth/userinfo` 无需再通过 username 回查。

如需支持短信验证码或邮箱验证码登录，实现 `CodeLoginIdentityService`，提供手机号/邮箱到用户的查询能力:

```java
@Service
@RequiredArgsConstructor
public class AppIdentityService implements SecurityIdentityService, CodeLoginIdentityService {
    private final UserMapper userMapper;

    @Override
    public SecurityUserDetails loadUserByPhone(String phone) {
        return userMapper.selectOneByField("phone", phone)
                .map(this::toUserDetails)
                .orElse(null);
    }

    @Override
    public SecurityUserDetails loadUserByEmail(String email) {
        return userMapper.selectOneByField("email", email)
                .map(this::toUserDetails)
                .orElse(null);
    }

    private SecurityUserDetails toUserDetails(User user) {
        return SecurityUserDetails.builder()
                .userId(String.valueOf(user.getId()))
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .enabled(user.getStatus() != null && user.getStatus() == 1)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
                .build();
    }
}
```

验证码发送通道通过 `VerificationCodeSender` 接入，框架不绑定具体短信/邮件服务商:

```java
@Component
public class EmailVerificationCodeSender implements VerificationCodeSender {
    @Override
    public VerificationCodeType getType() {
        return VerificationCodeType.EMAIL;
    }

    @Override
    public void send(String target, String code, VerificationCodePurpose purpose) {
        // 调用邮件服务商发送验证码
    }
}
```

#### 6. 自动获得的能力

完成上述配置后，框架自动提供:

| 能力 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录，返回 accessToken + refreshToken + 用户信息 + 权限 |
| `POST /api/auth/code/send` | 发送短信/邮箱验证码，默认未登录可访问 |
| `POST /api/auth/logout` | 登出（清除 SecurityContext） |
| `POST /api/auth/refresh` | Token 续期 |
| `GET /api/auth/userinfo` | 当前用户信息（需 Bearer Token，优先用 JWT 中的 userId） |
| `createUser/updateUser` | CRUD 审计字段自动填充当前登录用户名 |
| `SecurityFilterChain` | JWT 验证 + CORS + 路径白名单 |

#### 7. 在业务代码中获取当前用户

```java
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;

// 方式一：从 Authentication principal 获取（推荐，零 DB 查询）
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
if (auth.getPrincipal() instanceof SecurityUserDetails details) {
    String userId = details.getUserId();       // 来自 JWT claims
    String username = details.getUsername();
}

// 方式二：仅获取用户名
String username = auth.getName();

// 方式三：审计字段自动填充 — 直接使用 CurrentUserProvider
@Autowired private CurrentUserProvider currentUserProvider;
String currentUserId = currentUserProvider.getCurrentUserId();
```

### 认证策略扩展

框架内置三种登录策略:

| type | 说明 | 请求参数 |
|------|------|----------|
| `password` | 账号密码登录，未指定 `type` 时默认使用 | `username` + `password` |
| `sms_code` | 手机短信验证码登录 | `extra.phone` + `extra.code` |
| `email_code` | 邮箱验证码登录 | `extra.email` + `extra.code` |

发送验证码:

```http
POST /api/auth/code/send
Content-Type: application/json

{
  "type": "email",
  "target": "user@example.com",
  "purpose": "login"
}
```

邮箱验证码登录:

```http
POST /api/auth/login
Content-Type: application/json

{
  "type": "email_code",
  "extra": {
    "email": "user@example.com",
    "code": "123456"
  }
}
```

短信验证码登录:

```http
POST /api/auth/login
Content-Type: application/json

{
  "type": "sms_code",
  "extra": {
    "phone": "13800138000",
    "code": "123456"
  }
}
```

验证码登录注意:
- `AuthenticationRequest.type` 是认证策略的显式路由字段，不再通过 `extra.smsCode` 等字段猜测策略
- 验证码会哈希存储，校验成功后立即消费，失败次数超过 `max-attempts` 会删除
- 默认 `InMemoryVerificationCodeStore` 适合单体/开发环境；分布式生产环境建议自定义 `VerificationCodeStore` 或接入 Redis
- 如需 IP、设备、每日次数等防刷策略，实现 `VerificationCodeRateLimiter`
- `fastddd.security.code.dev-mode=true` 会注册 Noop sender，仅用于开发环境日志查看验证码

自定义认证策略仍可通过 `AuthStrategy` 扩展:

```java
// 自动注册到 AuthStrategyRegistry
@Component
public class CustomAuthStrategy implements AuthStrategy {
    @Override public String getType() { return "custom"; }
    @Override public Authentication authenticate(AuthenticationRequest request) { ... }
}
```

> **策略覆盖**: 如果自定义策略的 `getType()` 与框架内置策略相同（如 `password`），`AuthStrategyRegistry` 会自动优先保留应用层策略，框架内置策略退让。无需额外配置。

### JWT 签名策略（JwtSigner）

框架通过 `JwtSigner` 策略接口将签名/验签算法与 `JwtTokenProvider` 解耦。默认使用 HMAC-SHA512 对称签名，业务层可通过 `@Component` 覆盖为 RSA/EC 等非对称签名。

```java
public interface JwtSigner {
    String sign(JwtBuilder builder);                          // 签名并序列化
    Claims parse(String token) throws JwtException;           // 验签并解析 Claims
}
```

| 实现 | 算法 | 适用场景 | 注册方式 |
|------|------|----------|----------|
| `HmacJwtSigner`（框架内置） | HMAC-SHA512 | 单体应用，默认 | `@ConditionalOnMissingBean` 自动注册 |
| 自定义实现 | RS256 / ES256 等 | 跨子系统 SSO | `@Component` 声明即可覆盖 |

**默认 HMAC 路径**（零配置）：

```yaml
fastddd:
  security:
    jwt:
      secret: your-secret-key-at-least-64-bytes-long!!   # HmacJwtSigner 自动使用
```

**RSA 路径**（覆盖默认）：

```java
@Component
public class RsaJwtSigner implements JwtSigner {
    private final KeyPair keyPair;

    public RsaJwtSigner(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    @Override
    public String sign(JwtBuilder builder) {
        return builder.signWith(keyPair.getPrivate(), SignatureAlgorithm.RS256).compact();
    }

    @Override
    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(keyPair.getPublic())
                .build().parseClaimsJws(token).getBody();
    }
}
```

声明 `@Component` 后，`@ConditionalOnMissingBean(JwtSigner.class)` 会跳过框架的 `HmacJwtSigner`，`JwtTokenProvider` 自动使用自定义签名器。子类也可通过 `protected signToken(JwtBuilder)` 和 `protected getClaimsFromToken(String)` 扩展点定制签名流程。

### 审计日志

框架提供 `@AuditLog` 注解，用于记录操作审计日志（自动脱敏 password/secret/token 字段）:

```java
@PostMapping("/login")
@AuditLog(value = "用户登录", type = AuditLog.OperationType.LOGIN, async = true)
public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) { ... }

// @AuditLog 参数:
// value     — 操作描述
// type      — 操作类型: LOGIN, LOGOUT, CREATE, UPDATE, DELETE, OTHER
// logParams — 是否记录请求参数 (默认 true)
// logResult — 是否记录返回结果 (默认 false)
// async     — 是否异步保存审计日志 (默认 true)
```

---

## 事件驱动 (fast-ddd-event)

```java
// 发布事件
@Autowired private EventPublisher eventPublisher;

// 同步发布
eventPublisher.publishEvent(new OrderPaidEvent(orderId, amount));

// 异步发布
eventPublisher.publishEventAsync(new OrderPaidEvent(orderId, amount));

// 批量发布
eventPublisher.publishEvents(events);

// 监听事件 — 使用框架 @DomainEventListener (元标注 Spring @EventListener)
@Component
public class OrderEventHandler {
    @DomainEventListener(condition = "#event.amount > 1000")
    public void handleOrderPaid(OrderPaidEvent event) { ... }
}
```

### Outbox 事务性事件

创建 `outbox_events` 表并注册 `OutboxEventRepository` Bean 即可启用。
MySQL 和 PostgreSQL 的完整 DDL 见 `fast-ddd-event/src/main/resources/sql/` 目录，以下为 PostgreSQL 版本：

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    aggregate_id VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    event_data TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    max_retry_count INT NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP NULL,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbox_status_created ON outbox_events(status, created_at);
CREATE INDEX idx_outbox_event_type ON outbox_events(event_type);
CREATE INDEX idx_outbox_aggregate_id ON outbox_events(aggregate_id);
CREATE INDEX idx_outbox_aggregate_type_id ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_published_at ON outbox_events(published_at);
CREATE INDEX idx_outbox_next_retry_at ON outbox_events(next_retry_at);
CREATE INDEX idx_outbox_retry_status ON outbox_events(retry_count, status);

ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_status
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'CANCELLED'));
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_retry_count
    CHECK (retry_count >= 0 AND retry_count <= max_retry_count);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_max_retry_count
    CHECK (max_retry_count >= 0 AND max_retry_count <= 10);
ALTER TABLE outbox_events ADD CONSTRAINT chk_outbox_version
    CHECK (version >= 0);
```

启用后，`eventPublisher.publishEvent()` 会在事务内先将事件持久化到 outbox 表，后台定时器负责实际投递。

### Outbox 事件消费注意事项

1. **不要将 `event_data` 反序列化为 `DomainEvent` 子类** — `DomainEvent` 无无参构造器（有意设计，保证不可变），Jackson 反序列化会报 `InvalidDefinitionException`
2. **推荐模式**: 定义内部 record 只提取需要的字段，完整数据从聚合根加载
   ```java
   record EventPayload(String orderId) {}
   String orderId = objectMapper.readValue(event.getEventData(), EventPayload.class).orderId();
   Order order = orderRepository.findByOrderId(orderId).orElseThrow(...);
   ```
3. 事件是**触发器**（通知"发生了什么"），不作为数据载体。详见 [SPI 参考手册](./guides/SPI_REFERENCE.md) 第 4 节

---

## 缓存模块 (fast-ddd-cache)

两级缓存：Caffeine (L1 本地) + Redis (L2 分布式)，零配置开箱即用。

### 引入依赖

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-cache</artifactId>
</dependency>
```

无 Redis 配置 → 自动降级为纯本地缓存。不引入此依赖 → 缓存模块不加载。

### 使用方式一：Spring Cache 注解（推荐）

```java
@Service
public class UserService {

    @Cacheable(cacheNames = "users", key = "#id")
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    @CachePut(cacheNames = "users", key = "#user.id")
    public User update(User user) {
        userMapper.updateById(user);
        return user;
    }

    @CacheEvict(cacheNames = "users", key = "#id")
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }
}
```

**类型安全**：框架在 Redis 中存储 `{"__type":"className","__value":{...}}`，`@Cacheable` 返回的对象类型与原始方法一致，不会出现 `LinkedHashMap`。

### 使用方式二：编程 API (CacheService)

```java
@Autowired
private CacheService cacheService;

// getOrLoad — 带防击穿的自动加载
User user = cacheService.getOrLoad("users", "user:123", User.class,
    () -> userMapper.selectById(123));

// 批量操作
Map<String, User> users = cacheService.multiGet("users",
    List.of("user:1", "user:2"), User.class);

// 清除
cacheService.evict("users", "user:123");
cacheService.clear("users");
```

两种方式共享同一缓存实例，操作同一份数据。

### 配置

```yaml
fastddd:
  cache:
    enabled: true                    # 总开关
    allow-null-values: true          # 缓存 null 防穿透
    local:
      enabled: true
      max-size: 10000
      expire-after-write: 5m
    redis:
      enabled: true
      key-prefix: "fastddd:"
      expire-after-write: 30m
      sync-topic: "fastddd:cache:sync"
    caches:                          # 按 cache name 覆盖
      users:
        local:
          max-size: 5000
          expire-after-write: 2m
        redis:
          expire-after-write: 10m
```

### 运行场景

| 场景 | 条件 | 行为 |
|------|------|------|
| 完整两级 | 有 Redis 配置 | L1 → L2 → loader，Pub/Sub 同步 L1 失效 |
| 纯本地 | 无 Redis | 仅 Caffeine，适合单体应用 |
| 禁用 | `enabled=false` | 不加载任何 Bean |

详细用法参见 [CACHE_GUIDE.md](guides/CACHE_GUIDE.md)。

---

## 搜索模块 (fast-ddd-search)

轻量级全文搜索，SQL 默认零配置，Meilisearch 可选切换，业务代码不变。

### 依赖引入

```xml
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-search</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 核心接口

```java
// 非泛型 SPI，注入无歧义
@Autowired
private SearchService searchService;

SearchResult<Product> result = searchService.search(query, Product.class);
```

### SearchQuery 构建器

```java
SearchQuery query = SearchQuery.builder()
    .keyword("手机")                              // 关键词
    .field("name")                                // 搜索字段 (Java 属性名，自动转下划线)
    .field("description")
    .filter("status", SearchQuery.Filter.eq(1))   // 精确过滤
    .filter("price", SearchQuery.Filter.gte(100)) // 范围过滤
    .sort(SearchQuery.Sort.desc("createTime"))    // 排序
    .pageNum(1)
    .pageSize(10)
    .highlight(true)                              // 高亮 (仅 Meilisearch)
    .build();
```

### Filter 操作符

| 方法 | 说明 | 示例 SQL |
|------|------|---------|
| `eq(val)` | 精确匹配 | `status = 1` |
| `ne(val)` | 不等于 | `status != 0` |
| `gt/gte/lt/lte(val)` | 范围 | `price >= 100` |
| `in(list)` / `notIn(list)` | 集合 | `category_id IN (1,2,3)` |
| `isNull()` / `notNull()` | 空值 | `email IS NOT NULL` |
| `like(pattern)` | 模糊匹配 | `name LIKE '%张%'` |

### 后端切换 (仅改配置)

```yaml
# 默认 SQL 后端 — 零配置
fastddd:
  search:
    enabled: true        # 默认 true
    backend: sql         # 默认 sql

# 切换 Meilisearch
fastddd:
  search:
    backend: meilisearch
    meilisearch:
      host: "http://localhost:7700"
      api-key: "${MEILISEARCH_API_KEY}"
```

### 运行场景

| 场景 | 条件 | 行为 |
|------|------|------|
| SQL LIKE | `backend=sql` (默认) | QueryWrapper LIKE 查询，零额外依赖 |
| SQL FULLTEXT | `backend=sql` + MySQL | 自动 `MATCH...AGAINST` (需 FULLTEXT 索引) |
| Meilisearch | `backend=meilisearch` + SDK 在 classpath | 全文搜索 + 高亮 |
| 禁用 | `enabled=false` | 不加载任何 Bean |

详细用法参见 [SEARCH_GUIDE.md](guides/SEARCH_GUIDE.md)。

---

## 代码生成

### CLI 方式 (Maven Plugin)

```bash
# 单表代码生成
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-SNAPSHOT:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=123456 \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_

# 项目初始化
mvn com.gallop.fastddd:fastddd-maven-plugin:1.0.0-SNAPSHOT:init-project \
  -Dinit.groupId=com.example \
  -Dinit.artifactId=my-app \
  -Dinit.modelType=RICH \
  -Dinit.databaseType=mysql
```

### Studio 方式 (可视化)

访问 `http://localhost:8082/fastddd-studio`，提供:
- **项目初始化**: 可视化选择模块、数据库类型
- **表设计**: 在线设计表结构 + DDL 执行
- **代码生成**: 选表生成，支持贫血/充血两种模型
- **AI 设计**: 粘贴 PRD 自动生成表结构设计
- **多端项目生成**: 多模块 Maven 项目 + 安全组件 + Docker 部署配置

### 生成模型选择

| 场景 | 模型 | 生成文件数 | 适用 |
|------|------|-----------|------|
| 简单 CRUD 表 | ANEMIC | 5 个 (Entity/Mapper/Service/ServiceImpl/Controller) | 配置表、字典表 |
| 核心业务实体 | RICH | 11+ 个 (AggregateRoot/PO/Repository/CQRS/Event) | 订单、用户、商品 |

---

## 包结构规范

### 贫血模型
```
com.example.{module}
├── entity/          # extends BasePO
├── mapper/          # extends BaseRepository<T>
├── service/         # extends IService<T>
│   └── impl/        # extends BaseServiceImpl<M, T>
└── controller/      # extends BaseAdminCrudController<S, T> 或 BaseReadOnlyController<S, T>
```

### 充血模型
```
com.example.{aggregate}
├── domain/
│   ├── {Aggregate}.java           # extends AggregateRoot<ID>
│   ├── {ValueObject}.java
│   └── {Aggregate}CreatedEvent.java
├── application/
│   ├── command/                    # extends Command
│   ├── query/                      # extends Query
│   └── handler/                    # CommandHandler / QueryHandler
├── infrastructure/
│   └── persistence/
│       ├── {Aggregate}PO.java      # extends BasePO (数据库映射)
│       ├── {Aggregate}BaseMapper.java  # extends BaseMapper<PO>
│       ├── {Aggregate}EntityMapper.java  # PO ↔ Entity 转换
│       ├── {Aggregate}Repository.java     # 接口
│       └── {Aggregate}RepositoryImpl.java # 实现
└── interfaces/
    ├── controller/
    │   ├── {Aggregate}CommandController.java  # extends CommandController
    │   └── {Aggregate}QueryController.java    # extends QueryController
    └── dto/
```

---

## 数据库规范

### 表命名
- `snake_case`，可选 `t_` 或 `sys_` 前缀
- 示例: `user_info`, `order_detail`, `sys_config`

### 标准字段（框架自动填充）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | 主键 |
| `create_time` | DATETIME | 创建时间（自动填充） |
| `update_time` | DATETIME | 更新时间（自动填充） |
| `create_user` | VARCHAR(64) | 创建人（自动填充） |
| `update_user` | VARCHAR(64) | 更新人（自动填充） |
| `version` | INT DEFAULT 0 | 乐观锁版本号（**可选**，仅 VersionedPO 子类需要） |
| `deleted` | TINYINT DEFAULT 0 | 逻辑删除标志 |

### MyBatis Plus 配置
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: assign_id            # 雪花算法（推荐），不配则默认 AUTO
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

> `BasePO.id` 类型为 `Serializable`，框架在 `FastDddCrudAutoConfiguration` 中自动注册 `SerializableTypeHandler`，将 JDBC 返回的 Long/String 等类型自动适配为 Serializable，无需手动配置 TypeHandler。

---

## 配置属性汇总

| 前缀 | 模块 | 说明 |
|------|------|------|
| `fastddd.security.jwt` | security | JWT 配置 (secret, expiration, channel) |
| `fastddd.security.cors` | security | CORS 配置 (origins, methods, headers) |
| `fastddd.security.http` | security | HTTP 安全 (permit-all-paths) |
| `fastddd.cache` | cache | 两级缓存 (L1 Caffeine + L2 Redis, per-cache 配置) |
| `fastddd.search` | search | 全文搜索 (backend: sql/meilisearch, SQL 全文策略, Meilisearch 连接配置, 按索引定制) |
| `fastddd.jackson` | web | JSON 序列化 (日期格式, Long→String) |
| `fastddd.web.knife4j` | web | Knife4j/Swagger 文档 |
| `fastddd.monitor.sql` | monitor | SQL 监控 (慢查询阈值, Prometheus) |
| `fastddd.ai` | ai | AI 模块开关 |
| `fastddd.analyzer` | analyzer | 智能表分析 (策略, 权重) |

**Duration 字段**: 所有 `Duration` 类型配置必须带单位后缀 (`s`/`m`/`h`/`d`)，如 `8h`, `3600s`, `7d`。

---

## 异常处理

框架自动注册 `GlobalExceptionHandler`，统一处理:

| 异常 | HTTP 状态码 | 说明 |
|------|-----------|------|
| `BusinessException` | 400 | 业务异常，客户端可处理 |
| `SystemException` | 500 | 系统异常，需排查 |
| `CommandValidationException` | 400 | 命令校验失败（含字段级错误详情） |
| `MethodArgumentNotValidException` | 400 | 参数校验失败 |
| `AuthenticationException` | 401 | 认证失败 |
| 其他 `Exception` | 500 | 兜底处理 |

业务中抛出异常:
```java
Assert.notNull(user, "用户不存在");                    // 自动抛 BusinessException
throw new BusinessException("余额不足");                // errorCode=400
throw new BusinessException(1001, "库存不足");          // 自定义 errorCode
throw new SystemException("支付网关超时");               // code=500

// 下游获取错误码: exception.getErrorCode() / exception.getErrorMessage()
```

> **提示**: `BusinessException` 已内置 `errorCode`（Integer）和 `errorMessage`（String）两个字段。推荐为不同业务领域规划错误码范围（如 1xxx 订单、2xxx 支付、3xxx 交付），下游通过 `getErrorCode()` 做结构化处理。详见 [SPI 参考手册](./guides/SPI_REFERENCE.md) 第 3 节。

---

## 多端项目生成

Studio 支持一键生成多端 Maven 多模块项目:

```
generated-project/
├── pom.xml                    # 父 POM
├── common/
│   ├── common-user/           # 用户领域 (Entity/Mapper/Service)
│   ├── common-order/          # 订单领域
│   ├── common-shared/         # 共享代码
│   └── pom.xml
├── admin-api/                 # 管理后台 (port 8080)
├── customer-api/              # 客户端 (port 8081, 可含 SMS/微信认证)
├── merchant-api/              # 商户端 (port 8082)
├── docker-compose.yml         # 容器编排 (MySQL/PostgreSQL/H2 条件生成)
└── docker/nginx/default.conf  # 反向代理配置
```

每个子应用自动生成:
- Security SPI 实现 (`AppIdentityService`，多端模板会实现 `CodeLoginIdentityService`)
- 短信/邮箱验证码策略由 `fast-ddd-security-starter` 内置，业务项目按需实现 `VerificationCodeSender`
- 按需生成微信认证策略 (`WechatMiniAuthStrategy`)
- 独立 JWT Secret + Channel 隔离
- Dockerfile + nginx 配置

---

## 国际化 i18n (fast-ddd-common)

框架内置国际化支持，所有 API 响应消息、错误提示均已 i18n 化。默认中文，支持英文切换。

### 基本用法

```java
import com.gallop.fastddd.common.i18n.Messages;

// 获取消息（自动根据当前 Locale 选择语言）
String msg = Messages.get("web.crud.create.success");         // → "创建成功" (zh) / "Created successfully" (en)

// 带参数的消息
String msg = Messages.get("web.crud.create.error", "主键冲突"); // → "创建失败: 主键冲突"
String msg = Messages.get("web.crud.batch-create.detail", 5);  // → "成功创建 5 条记录"
```

### Locale 切换

框架自动配置 `LocaleResolver`，按以下优先级解析语言:

1. **URL 参数**: `?lang=en` → 英文, `?lang=zh` → 中文
2. **Accept-Language Header**: 浏览器/客户端自动携带
3. **默认**: `zh_CN` (简体中文)

示例: `GET /api/users/1?lang=en` → 所有响应消息变为英文

### 消息码约定

格式: `{module}.{category}.{operation}.{status}`

| 前缀 | 范围 | 示例 |
|------|------|------|
| `common.*` | 通用 | `common.success` |
| `web.crud.*` | BaseController 端点 | `web.crud.create.success`, `web.crud.query.not-found` |
| `web.command.*` | CommandController | `web.command.success`, `web.command.failed` |
| `web.query.*` | QueryController | `web.query.success`, `web.query.not-found` |
| `web.error.*` | GlobalExceptionHandler | `web.error.param-validation`, `web.error.internal` |
| `web.doc.*` | FastDoc 文档生成 | `web.doc.generate.failed` |
| `security.auth.*` | 认证端点 | `security.auth.login.success`, `security.auth.user-not-found` |
| `security.access.*` | 访问控制 | `security.access.denied`, `security.access.unauthorized` |

### 业务项目自定义消息

在业务项目的 `src/main/resources/` 下创建 `messages.properties` (中文) 和 `messages_en.properties` (英文)，同名 key 会覆盖框架默认值:

```properties
# messages.properties — 覆盖框架默认消息
web.crud.create.success=用户新增成功
web.crud.query.not-found=找不到该用户

# 也可以添加业务消息
biz.order.paid=订单已支付
biz.order.cancelled=订单已取消
```

```properties
# messages_en.properties
web.crud.create.success=User created successfully
biz.order.paid=Order has been paid
biz.order.cancelled=Order has been cancelled
```

业务代码中使用:

```java
throw new BusinessException(Messages.get("biz.order.cancelled"));
return ApiResponse.success(data, Messages.get("biz.order.paid"));
```

### 自动配置

框架通过 `I18nAutoConfiguration` 自动注册:
- `MessageSource` — 加载 `messages` 资源包 (`@ConditionalOnMissingBean`，可覆盖)
- `I18nWebConfiguration` — `LocaleResolver` + `LocaleChangeInterceptor` (fast-ddd-web 模块)

---

## 常见踩坑点

| 场景 | 正确做法 | 错误做法 |
|------|---------|---------|
| 继承 BasePO | `@Getter @Setter`，不声明 id | `@Data` + 重复声明 `private Long id` |
| 继承 AggregateRoot | `@Getter @Setter` + 显式实现 getId/setId | `@Data`（不处理父类抽象方法） |
| BasePO getId 返回值 | `(Long) entity.getId()` 强转 | 期望自动拆箱为 Long |
| DTO 跨包字段 | `private` + `@Data` | 默认包级访问（跨包不可见） |
| Duration 配置 | `8h`, `3600s`, `7d` | 纯数字 `28800`（缺少单位） |
| CORS allowed-methods | YAML list 格式 | 逗号分隔字符串 |
| BasePO 子类 | 不重复声明 id/createTime/updateTime/deleted | 重复声明导致类型冲突 |
| 乐观锁 | 需要时继承 VersionedPO / VersionedEntity | 所有实体都加 version（表中无 version 列会报错） |
| Security SPI 实现 | 用 `@Service` 即可，框架自动检测 | 加 `@Import` 或 `@Component` |
| SecurityUserDetails | builder 必须设 userId（写入 JWT） | 不设 userId → /userinfo 无法直接查询 |
| permit-all-paths | 精确放行 `/api/auth/login`、`/api/auth/code/send` | 写 `/api/auth/**`（userinfo 被跳过认证） |
| @MapperScan | 启动类必须加 | 缺失 → BaseRepository 不注册 |
| *ByField 字段名 | 传 Java 驼峰 `"createTime"` | 传数据库列名 `"create_time"`（也兼容，但不推荐） |
| i18n 业务消息 | 在业务项目 resources 下放 messages.properties | 修改框架 JAR 内的 properties |

---

## 常用工具类

| 类 | 用途 |
|----|------|
| `Assert` | 参数校验，失败抛 `BusinessException` |
| `Messages` | i18n 国际化消息获取 (`Messages.get(code, args...)`) |
| `CacheService` | 两级缓存编程 API (get/put/evict/getOrLoad/multiGet) |
| `JsonUtils` | JSON 序列化/反序列化 |
| `NamingUtils` | 命名转换 (camelCase ↔ snakeCase ↔ PascalCase) |
| `Result<T>` | 通用结果包装（**已废弃**，请使用 `ApiResponse<T>`） |
| `ApiResponse<T>` | 统一响应包装（推荐），位于 `com.gallop.fastddd.web.response`，配合 `@EnableApiResponseWrapper` 自动包装 Controller 返回值 |
| `PageResult<T>` | 分页结果包装 |
| `SearchService` | 全文搜索 SPI (SQL/Meilisearch 双后端) |
| `SearchQuery` | 搜索查询构建器 (关键词+字段+过滤+排序+分页) |
| `SearchResult<T>` | 搜索结果 (分页+高亮+聚合+后端标识) |
| `DomainModelType` | 模型类型枚举 (ANEMIC/RICH) |
| `CurrentUserProvider` | 当前用户 ID 提供者 SPI（默认返回 "system"） |
