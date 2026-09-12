# FastDDD Framework Reference

**English** | [简体中文](FASTDDD_REFERENCE.zh-CN.md)

> This document is FastDDD's **on-demand API and design reference**. For a new business system, do not load the
> whole document up front: read the [New Business System Development Guide](guides/NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md) first,
> and search this document when you hit a concrete API, SPI, or configuration question.

## Framework Overview

FastDDD is an enterprise-grade DDD development framework built on Spring Boot 3.x. It provides a standardized layered architecture, code generation, event-driven capabilities, multi-channel security authentication, and more.

**Current coordinates**: `io.github.gallop.fastddd:fastddd-bom:1.0.0` (internal v1.0, released 2026-09-10, tag `v1.0.0`; business projects
manage versions uniformly by importing [`fastddd-bom`](UPGRADE_GUIDE.md#2-upgrade-fastddd-dependencies-import-fastddd-bom))
**Java version**: 21+
**Spring Boot version**: 3.5.16

> For the version baseline and compatibility lines see the [Compatibility Matrix](COMPATIBILITY_MATRIX.md); for upgrade steps see the [Upgrade Guide](UPGRADE_GUIDE.md).

```xml
<!-- 业务项目统一版本入口；所有 fast-ddd-* 依赖均不再写 version -->
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
```

---

## Module Dependencies

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

**Typical POM dependency selection**:
- Basic CRUD project: `fast-ddd-starter` + `fast-ddd-security-starter`
- Rich-model project: additionally add `fast-ddd-application` + `fast-ddd-event`
- Full enterprise project: include all of them

---

## Two Base-Class Systems

FastDDD provides two independent base-class systems with unified field naming:

| Field | BaseEntity (domain layer) | BasePO (persistence layer) | DB column |
|------|-------------------|----------------|---------|
| Creation time | `protected LocalDateTime createTime` | `@TableField(fill=INSERT) createTime` | `create_time` |
| Update time | `protected LocalDateTime updateTime` | `@TableField(fill=INSERT_UPDATE) updateTime` | `update_time` |
| Created by | `protected String createUser` | `@TableField(fill=INSERT) createUser` | `create_user` |
| Updated by | `protected String updateUser` | `@TableField(fill=INSERT_UPDATE) updateUser` | `update_user` |
| Logical delete | `protected Integer deleted` | `@TableLogic Integer deleted` | `deleted` |
| Primary key | `protected ID id` (abstract getId/setId) | `protected ID id` (`ID extends Serializable`, defaults to `Long`) | `id` |
| Version | provided by `VersionedEntity` subclasses | provided by `VersionedPO` subclasses | `version` |

**Inherit the `version` field only when needed**: only entities whose database table has a `version` column extend `VersionedPO`/`VersionedEntity`; all others extend `BasePO`/`BaseEntity`.

**Unified benefit**: when converting between Entity and PO in the rich model, the common field names match exactly, so BeanUtils/MapStruct can copy them directly.

---

## Core Base Classes Quick Reference

### 1. Anemic model — BasePO (fast-ddd-model)

```java
@TableName("t_order")
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
public class Order extends BasePO<Long> {

    private String orderNo;
    private Integer status;

    // id 字段已由 BasePO 内置（强类型 Long），无需声明 @TableId / getId() / setId()
    // getId() 直接返回 Long，无需强转: Long id = entity.getId();
}
// BasePO 自动提供: id(Long), createTime, updateTime, createUser, updateUser, deleted
// createTime/updateTime/createUser/updateUser 由框架 AutoFill 自动填充
// 需要乐观锁的实体请继承 VersionedPO（自动包含 version 字段）
```

**Key rules**:
- Use `@Getter/@Setter` when extending `BasePO`, not `@Data` (`@Data`'s `equals/hashCode` has side effects on JPA/MyBatis proxy objects)
- `BasePO<ID>` already ships a strongly typed `ID id` field (**without** a field-level `@TableId`); subclasses never need to declare `id`, `@TableId`, `getId()`, or `setId()` again
- Do not re-declare `createTime`, `updateTime`, `createUser`, `updateUser`, or `deleted` in subclasses — they are already provided by `AuditedPO` (the parent class of `BasePO`)
- The ID strategy is decided solely by the global configuration `mybatis-plus.global-config.db-config.id-type` (default `assign_id` snowflake); a subclass that needs a different strategy re-declares `id` itself with `@TableId(type=...)`
- Entities that need an optimistic lock extend `VersionedPO` (automatically includes the `version` field + the `@Version` annotation); those that do not extend `BasePO` directly
- Use `BasePO<Long>` for `BIGINT` primary keys; for UUID/String primary keys use a matching `ID` type and declare `@TableId(type=...)` in the subclass when needed. MyBatis-Plus handles reads and writes by the actual type — no global `SerializableTypeHandler` required

### 2. Rich model — AggregateRoot (fast-ddd-common)

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

**Rich-model PO ↔ Entity conversion** (infrastructure layer):

```java
// PO — 持久化对象，对应数据库表
@TableName("t_order")
@Getter @Setter
@EqualsAndHashCode(callSuper = true)
public class OrderPO extends BasePO<Long> {
    // id 已由 BasePO<Long> 内置（强类型 Long，ID-003/004），无需声明
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

**Key rules**:
- Use `@Getter/@Setter` when extending `AggregateRoot`, not `@Data`
- Do not call `super.getId()` — it is an abstract method with no implementation; access the `protected id` field directly
- DTOs accessed across packages must use `private` fields + `@Data`

### 3. Data layer (fast-ddd-crud)

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

// Service 接口 — 实体与 ID 类型必须一起传递
public interface OrderService extends IService<Order, Long> {
    // 继承: save, removeById, updateById, getById, list, page, getByField 等
}

// Service 实现
@Service
public class OrderServiceImpl extends BaseServiceImpl<OrderMapper, Order, Long> implements OrderService {
    // baseMapper 已自动注入，所有 CRUD 方法继承即可
}
```

**Built-in BaseRepository query methods** (no handwritten SQL; Java camelCase field names are mapped automatically):
- `selectOneByField("username", "admin")` → `Optional<T>`
- `selectListByField("status", 1)` → `List<T>`
- `selectListByFieldLike("nickname", "%张%")` → `List<T>`
- `selectListByFieldIn("status", List.of(1,2,3))` → `List<T>`
- `selectListByFields("status", 1, "department", "IT")` → `List<T>` (AND conditions)
- `existsByField("username", "admin")` → `boolean`
- `countByField("status", 1)` → `long`
- `deleteByField("status", 0)` → `int`
- `batchInsert(List<T>)` → batch insert
- `batchUpdateById(List<T>)` → batch update

> **Field-name convention**: the `field` parameter of `*ByField` methods takes the Java camelCase name (e.g. `createTime`, `isDefault`); the framework converts it to the database column name (`create_time`, `is_default`) automatically. Passing snake_case also works.

> **Generic constraints**: `BaseRepository<T>` puts no constraint on `T` (extending BasePO is not required), so the convenience query methods work for any entity table. `BaseServiceImpl<M, T, ID>` and `IService<T, ID>` require `T extends BasePO<ID>` and `ID extends Serializable`; the entity, the service, and the controller must use the same ID type. Pure data tables (without audit fields) can use the Mapper layer alone.

### 4. Web layer (fast-ddd-web)

#### Controller base class hierarchy

```
AbstractBaseController              ← 基础设施（service、resolveId、resolveEntityClass），无端点
├── BaseReadOnlyController          ← 只读查询（getById、list、page）
│   └── BaseAdminCrudController     ← 完整 CRUD + 8 个 lifecycle hooks
│       └── BaseController          ← @Deprecated 兼容入口
```

#### Admin CRUD (recommended)

```java
@RestController
@RequestMapping("/api/admin/orders")
public class OrderAdminController extends BaseAdminCrudController<OrderService, Order, Long> {
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

    // ⚠️ 安全: 空体子类继承的 7 个写端点（POST/PUT/DELETE）无方法级权限守卫，
    // 完全依赖子类的类级注解（如 @RequiresPermission）统管。
    // 若需按钮级细粒度控制，必须覆写写方法并加方法级权限注解。
    // 详见下方「继承写端点的安全模型」。
}
```

#### Read-only queries

```java
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController extends BaseReadOnlyController<DictionaryService, DictionaryPO, Long> {
    // 仅暴露 GET 端点:
    // GET /{id}                       — 按ID查询
    // GET /                           — 列表查询
    // GET /page?current=1&size=10     — 分页查询
}
```

#### Legacy project compatibility (not recommended for new projects)

```java
// BaseController 已 @Deprecated(since="1.1")，仍可编译使用
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseController<UserService, User, Long> {
}
```

#### Usage recommendations

| Scenario | Recommended base class | Notes |
|------|---------|------|
| Admin CRUD | `BaseAdminCrudController` | Full CRUD; paths should live under `/api/admin/**`. **The 7 inherited write endpoints carry no method-level guard and are governed by the subclass's class-level annotation** |
| Read-only dictionaries/queries | `BaseReadOnlyController` | GET methods only, no write-operation risk |
| Sensitive business APIs | Do not extend any CRUD base class | Declare every endpoint explicitly to avoid implicit exposure |
| Legacy project compatibility | `BaseController` (deprecated) | Keeps compilation working; gradual migration recommended |

#### ⚠️ Security model of inherited write endpoints

The **7 write endpoints (POST/PUT/DELETE) that an empty subclass inherits from `BaseAdminCrudController` carry no method-level permission guard**; they rely entirely on the subclass's class-level annotation. This means:

- Any role admitted through the class-level annotation can perform **all** inherited write operations (create/update/delete are all permitted)
- **Button-level fine-grained permissions cannot be implemented on an empty subclass** (e.g. "allow create but not delete")

**When to override the write endpoints**:
1. Button-level fine-grained permissions are needed (e.g. the `sys_billing_rule` table needs create/update/delete permissions distinguished)
2. Write operations need `@AuditLog` audit trails
3. Permissions need tightening — the class-level annotation admits writes too broadly, so guard each method independently

**Override example** (same pattern as DishController/FoodSafetyController):

```java
@RestController
@RequestMapping("/api/admin/sys/billing-rules")
@RequiresPermission(value = {"system:view", "fee:billing-rule"}, logical = RequiresPermission.Logical.OR)
public class SysBillingRuleController extends BaseAdminCrudController<SysBillingRuleService, SysBillingRule, Long> {

    @Override
    @RequiresPermission(value = {"fee:manage", "fee:billing-rule:create"}, logical = RequiresPermission.Logical.OR)
    @AuditLog(value = "新建计费规则", type = AuditLog.OperationType.INSERT)
    public ResponseEntity<ApiResponse<SysBillingRule>> create(SysBillingRule entity) {
        return super.create(entity);
    }

    // 同理覆写 createBatch / update / updateById / updateBatch / delete / deleteBatch
    // update/updateById 可共用同一操作码（皆为 UI「编辑」按钮）
    // 读端点（getById/list/page）不覆写，保持继承
}
```

**Default behavior when not overridden**: the 7 inherited write endpoints are guarded only by the class-level annotation. If any entry in the subclass's class-level annotation `value` array matches the current user's permission codes, all write operations are permitted.

> 💡 **Recommended practice**: `BaseAdminCrudController` subclasses for sensitive business modules (fees, contracts, accounts, etc.) should override the write endpoints with method-level annotations from Day 1. Pure dictionary/configuration CRUD (e.g. `meal:dish-tag`) can start with an empty body and tighten later as needed.

**Unified response format** (`ApiResponse<T>`):
```json
{ "code": 200, "message": "操作成功", "data": {...}, "timestamp": 1704067200000 }
```

**Automatic type conversion for the ID path variable**: the path variables of the `GET /{id}`, `PUT /{id}`, and `DELETE /{id}` endpoints are type-converted automatically. The framework determines the ID type from the entity's MyBatis-Plus `TableInfo.idType`:

| `id-type` configuration | URL path `/{id}` | Type received by the controller |
|----------------|-------------------|-----------------|
| `assign_id` (snowflake algorithm, recommended) | `/api/users/18956483271045` | `Long` |
| `auto` (database auto-increment) | `/api/users/123` | `Long` |
| `assign_uuid` | `/api/orders/a1b2c3d4-...` | `String` |

Business projects need **no extra configuration at all**; ID conversion is performed automatically by `AbstractBaseController.resolveId()`. When overriding an endpoint, simply change the parameter to `String idStr` and call `resolveId(idStr)`.

Enabling automatic wrapping: **as of WEB-002 it is opt-in (off by default)**. Both the response wrapper and the Jackson extensions (Long→String / date format / enum→name) are gated at the class level by `@ConditionalOnProperty(..., matchIfMissing = false)`; **the property is the only switch**:

```yaml
fastddd:
  web:
    response-wrapper:
      enabled: true   # 开启 ApiResponse 信封包装（默认关）
  jackson:
    enabled: true     # 开启 Long/日期/枚举 Jackson 扩展（默认关）
```

- **Off by default**: merely adding `fast-ddd-web` does not rewrite a consumer's existing JSON contract (Long as a number, no envelope, dates in Spring Boot's default representation).
- **Framework aggregate consumers** (studio / examples / archetype-generated projects) explicitly opt in via their own `application.yml` to restore the envelope capability.
- `@EnableApiResponseWrapper` is **`@Deprecated(since="1.0.0-jdk21")`** and has been downgraded to a source-compatibility marker with no opt-in semantics; new projects uniformly use the property switch.
- **OpenAPI contract consistency**: when `fastddd.web.response-wrapper.enabled=true`, `ApiResponseOpenApiCustomizer` wraps the 2xx response schema of POJO endpoints into an envelope, deciding by the same source as the runtime `SmartResponseAdvice` (`ResponseWrapPredicate`), so the springdoc documentation (raw signatures) and the runtime (envelope) never diverge.

### 5. CQRS rich model (fast-ddd-application)

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

## Security Module (fast-ddd-security-starter)

### Quick Integration Guide

#### 1. Add the dependencies

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-security-starter</artifactId>
</dependency>
```

#### 2. Application class

```java
@SpringBootApplication(scanBasePackages = "com.example.app")
@MapperScan("com.example.app")    // 必须加！BaseRepository 底层是 BaseMapper，不会自动扫描
public class Application { ... }
```

> For how to enable the response wrapper see "Unified response format" in the section above — opt in via `fastddd.web.response-wrapper.enabled=true`; do not use `@EnableApiResponseWrapper` anymore (already `@Deprecated`, source-compatible but semantics-free).

#### 3. YAML configuration

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

**Configuration notes**:
- The prefix is `fastddd:`, not `fast-ddd:`
- All Duration fields must carry a unit suffix (`s`/`m`/`h`/`d`)
- Do not write `/api/auth/**` in `permit-all-paths`; otherwise `/api/auth/userinfo` is also skipped for JWT authentication and the principal becomes `anonymousUser`
- The verification-code endpoint `/api/auth/code/send` must be reachable without login; in production you must provide a real `VerificationCodeSender`

#### 4. Entity class

```java
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@TableName("t_user")
public class User extends BasePO<Long> {
    // 不要重新声明 id 字段！BasePO<Long> 已有强类型 Long id（ID-003/004）
    // 主键策略由全局 mybatis-plus.global-config.db-config.id-type 决定（默认 assign_id）

    private String username;
    private String passwordHash;
    private String nickname;
    private String role;
    private Integer status;
}
```

#### 5. Implement the SPIs (two @Service beans)

The security module uses `@AutoConfiguration` (deferred import); in a business project a plain `@Service` declaration automatically replaces the framework default implementation — no `@Import` required.

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

**Key point**: `SecurityUserDetails.builder()` must set `userId`; the framework writes it into the JWT claims. On subsequent requests `JwtAuthenticationFilter` automatically extracts the userId from the token as the principal, so `/api/auth/userinfo` no longer needs a username round-trip lookup.

To support SMS-code or email-code login, implement `CodeLoginIdentityService` to provide phone-number/email-to-user lookup:

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

Verification-code delivery channels plug in through `VerificationCodeSender`; the framework binds to no specific SMS/email vendor:

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

#### 6. Capabilities you get automatically

After the configuration above, the framework automatically provides:

| Capability | Description |
|------|------|
| `POST /api/auth/login` | Login; returns accessToken + refreshToken + user info + permissions |
| `POST /api/auth/code/send` | Send an SMS/email verification code; accessible without login by default |
| `POST /api/auth/logout` | Logout (clears the SecurityContext) |
| `POST /api/auth/refresh` | Token renewal |
| `GET /api/auth/userinfo` | Current user info (requires a Bearer token; prefers the userId inside the JWT) |
| `createUser/updateUser` | CRUD audit fields auto-filled with the current logged-in username |
| `SecurityFilterChain` | JWT verification + CORS + path allowlist |

#### 7. Get the current user in business code

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

### Authentication strategy extensions

The framework ships three built-in login strategies:

| type | Description | Request parameters |
|------|------|----------|
| `password` | Username/password login; the default when no `type` is specified | `username` + `password` |
| `sms_code` | SMS verification-code login | `extra.phone` + `extra.code` |
| `email_code` | Email verification-code login | `extra.email` + `extra.code` |

Send a verification code:

```http
POST /api/auth/code/send
Content-Type: application/json

{
  "type": "email",
  "target": "user@example.com",
  "purpose": "login"
}
```

Email verification-code login:

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

SMS verification-code login:

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

Notes on verification-code login:
- `AuthenticationRequest.type` is the explicit routing field for authentication strategies; the strategy is no longer guessed from fields like `extra.smsCode`
- Verification codes are stored hashed, consumed immediately after successful verification, and deleted once failed attempts exceed `max-attempts`
- The default `InMemoryVerificationCodeStore` suits monoliths/development; for distributed production, it is recommended to define a custom `VerificationCodeStore` or back it with Redis
- For anti-abuse policies such as IP, device, or daily-send limits, implement `VerificationCodeRateLimiter`
- `fastddd.security.code.dev-mode=true` registers a Noop sender, intended only for viewing codes in logs during development

Custom authentication strategies can still be added via `AuthStrategy`:

```java
// 自动注册到 AuthStrategyRegistry
@Component
public class CustomAuthStrategy implements AuthStrategy {
    @Override public String getType() { return "custom"; }
    @Override public Authentication authenticate(AuthenticationRequest request) { ... }
}
```

> **Strategy override**: if a custom strategy's `getType()` collides with a built-in strategy (e.g. `password`), `AuthStrategyRegistry` automatically keeps the application-level strategy and the built-in one steps aside. No extra configuration needed.

### JWT Signing Strategy (JwtSigner)

The framework decouples the signing/verification algorithm from `JwtTokenProvider` through the `JwtSigner` strategy interface. It defaults to HMAC-SHA512 symmetric signing; the business layer can override it with RSA/EC or other asymmetric signing via `@Component`.

```java
public interface JwtSigner {
    String sign(JwtBuilder builder);                          // 签名并序列化
    Claims parse(String token) throws JwtException;           // 验签并解析 Claims
}
```

| Implementation | Algorithm | Use case | Registration |
|------|------|----------|----------|
| `HmacJwtSigner` (built into the framework) | HMAC-SHA512 | Monolithic applications; the default | Auto-registered via `@ConditionalOnMissingBean` |
| Custom implementation | RS256 / ES256 etc. | Cross-subsystem SSO | Just declare `@Component` to override |

**Default HMAC path** (zero configuration):

```yaml
fastddd:
  security:
    jwt:
      secret: your-secret-key-at-least-64-bytes-long!!   # HmacJwtSigner 自动使用
```

**RSA path** (overrides the default):

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

Once `@Component` is declared, `@ConditionalOnMissingBean(JwtSigner.class)` skips the framework's `HmacJwtSigner` and `JwtTokenProvider` automatically uses the custom signer. Subclasses can also customize the signing flow through the `protected signToken(JwtBuilder)` and `protected getClaimsFromToken(String)` extension points.

### Audit Logging

The framework provides the `@AuditLog` annotation for recording operation audit logs (password/secret/token fields are masked automatically):

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

## Event-Driven (fast-ddd-event)

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

### Dependency shape (after ECO-006C6 pool neutralization)

`fast-ddd-event` passes along the four-piece MyBatis-Plus runtime set (`mybatis-plus` + `mybatis-spring` +
MyBatis-Plus auto-configuration + `spring-jdbc`) but **passes along no connection pool** (pool-neutral):

```xml
<!-- local 模式（默认）：仅此一条，无库、无数据源配置、无排除即可启动 -->
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-event</artifactId>
</dependency>

<!-- outbox 模式：消费者显式选池（二选一，Boot BOM 受管）+ 自备 outbox_events DDL -->
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-event</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>  <!-- 或 druid-spring-boot-3-starter -->
</dependency>
```

Non-web outbox consumers must additionally provide their own `ObjectMapper` (recommend `findAndRegisterModules()`) and explicitly depend on
`jackson-datatype-jsr310` (`DomainEvent.createdAt` is an `Instant`); web consumers get it automatically via starter-json and need to do nothing.

### Outbox transactional events

Create the `outbox_events` table and register an `OutboxEventRepository` bean to enable it.
Full DDL for MySQL and PostgreSQL is in the `fast-ddd-event/src/main/resources/sql/` directory; below is the PostgreSQL version:

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

Once enabled, `eventPublisher.publishEvent()` persists the event to the outbox table inside the transaction first, and a background scheduler handles actual delivery.

### Notes on consuming Outbox events

1. **Do not deserialize `event_data` into a `DomainEvent` subclass** — `DomainEvent` has no no-arg constructor (an intentional design choice to guarantee immutability), and Jackson deserialization throws `InvalidDefinitionException`
2. **Recommended pattern**: define an inner record that extracts only the fields you need, and load the full data from the aggregate root
   ```java
   record EventPayload(String orderId) {}
   String orderId = objectMapper.readValue(event.getEventData(), EventPayload.class).orderId();
   Order order = orderRepository.findByOrderId(orderId).orElseThrow(...);
   ```
3. Events are **triggers** (they announce "what happened"), not data carriers. See Section 4 of the [SPI Reference](./guides/SPI_REFERENCE.md)

---

## Cache Module (fast-ddd-cache)

Two-level cache: Caffeine (L1 local) + Redis (L2 distributed), zero configuration, works out of the box.

### Add the dependency

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-cache</artifactId>
</dependency>
```

No Redis configuration → automatically degrades to a pure local cache. Without this dependency → the cache module does not load.

### Usage option 1: Spring Cache annotations (recommended)

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

**Type safety**: the framework stores `{"__type":"className","__value":{...}}` in Redis, so objects returned from `@Cacheable` have the same type as the original method — you never get a `LinkedHashMap`.

### Usage option 2: programmatic API (CacheService)

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

Both approaches share the same cache instance and operate on the same data.

### Configuration

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

### Runtime scenarios

| Scenario | Condition | Behavior |
|------|------|------|
| Full two-level | Redis configured | L1 → L2 → loader; Pub/Sub synchronizes L1 invalidation |
| Local only | No Redis | Caffeine only; suits monolithic applications |
| Disabled | `enabled=false` | No beans loaded |

For detailed usage see [CACHE_GUIDE.md](guides/CACHE_GUIDE.md).

---

## Search Module (fast-ddd-search)

Lightweight full-text search with three explicit modes — SQL, Meilisearch, and disabled; the SQL mode requires a
MyBatis-Plus `BaseMapper` to be present, the Meilisearch mode requires the SDK to be provided explicitly, and business code stays unchanged.

### Add the dependency

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-search</artifactId>
    <!-- version 由 fastddd-bom 管理 -->
</dependency>
```

### Core interface

```java
// 非泛型 SPI，注入无歧义
@Autowired
private SearchService searchService;

SearchResult<Product> result = searchService.search(query, Product.class);
```

### SearchQuery builder

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

### Filter operators

| Method | Description | Example SQL |
|------|------|---------|
| `eq(val)` | Exact match | `status = 1` |
| `ne(val)` | Not equal | `status != 0` |
| `gt/gte/lt/lte(val)` | Range | `price >= 100` |
| `in(list)` / `notIn(list)` | Set membership | `category_id IN (1,2,3)` |
| `isNull()` / `notNull()` | Null checks | `email IS NOT NULL` |
| `like(pattern)` | Fuzzy match | `name LIKE '%张%'` |

### Backend switching (configuration only)

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

### Runtime scenarios

| Scenario | Condition | Behavior |
|------|------|------|
| SQL LIKE | `backend=sql` (default) + MyBatis-Plus `BaseMapper` | QueryWrapper LIKE query; startup fails if the dependency or Mapper is missing |
| SQL FULLTEXT | `backend=sql` + MySQL | Automatic `MATCH...AGAINST` (requires a FULLTEXT index) |
| Meilisearch | `backend=meilisearch` + SDK on the classpath | Full-text search + highlighting |
| Disabled | `backend=disabled` | No `SearchService` registered; for consumers with no search backend configured |
| Disabled | `enabled=false` | No beans loaded |

For detailed usage see [SEARCH_GUIDE.md](guides/SEARCH_GUIDE.md).

---

## Code Generation

### CLI (Maven plugin)

```bash
# 单表代码生成
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password="$CODEGEN_DB_PASSWORD" \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_

# 项目初始化
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:init-project \
  -Dinit.groupId=com.example \
  -Dinit.artifactId=my-app \
  -Dinit.modelType=RICH \
  -Dinit.databaseType=mysql
```

### Studio (visual)

Visit `http://localhost:8082/fastddd-studio`, which provides:
- **Project initialization**: visually select modules and database type
- **Table design**: design table structures online + execute DDL
- **Code generation**: pick tables to generate; supports both the anemic and rich models
- **AI design**: paste a PRD to auto-generate a table design
- **Multi-app project generation**: multi-module Maven project + security components + Docker deployment configuration

### Generated model selection

| Scenario | Model | Files generated | Fits |
|------|------|-----------|------|
| Simple CRUD tables | ANEMIC | 5 files (Entity/Mapper/Service/ServiceImpl/Controller) | Configuration and dictionary tables |
| Core business entities | RICH | 11+ files (AggregateRoot/PO/Repository/CQRS/Event) | Orders, users, products |

---

## Package Structure Conventions

### Anemic model
```
com.example.{module}
├── entity/          # extends BasePO<ID>
├── mapper/          # extends BaseRepository<T>
├── service/         # extends IService<T, ID>
│   └── impl/        # extends BaseServiceImpl<M, T, ID>
└── controller/      # extends BaseAdminCrudController<S, T, ID> 或 BaseReadOnlyController<S, T, ID>
```

### Rich model
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
│       ├── {Aggregate}PO.java      # extends BasePO<ID> (数据库映射)
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

## Database Conventions

### Table naming
- `snake_case`, with an optional `t_` or `sys_` prefix
- Examples: `user_info`, `order_detail`, `sys_config`

### Standard fields (auto-filled by the framework)

| Field | Type | Description |
|------|------|------|
| `id` | BIGINT AUTO_INCREMENT | Primary key |
| `create_time` | DATETIME | Creation time (auto-filled) |
| `update_time` | DATETIME | Update time (auto-filled) |
| `create_user` | VARCHAR(64) | Created by (auto-filled) |
| `update_user` | VARCHAR(64) | Updated by (auto-filled) |
| `version` | INT DEFAULT 0 | Optimistic lock version (**optional**; only needed by VersionedPO subclasses) |
| `deleted` | TINYINT DEFAULT 0 | Logical delete flag |

### MyBatis Plus configuration
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

> The `id` of `BasePO<ID>` is a strongly typed contract; for example, BIGINT uses `BasePO<Long>`. The framework no longer registers a global `SerializableTypeHandler`, so weakly typed primary keys cannot mask call errors.

---

## Configuration Property Summary

| Prefix | Module | Description |
|------|------|------|
| `fastddd.security.jwt` | security | JWT configuration (secret, expiration, channel) |
| `fastddd.security.cors` | security | CORS configuration (origins, methods, headers) |
| `fastddd.security.http` | security | HTTP security (permit-all-paths) |
| `fastddd.cache` | cache | Two-level cache (L1 Caffeine + L2 Redis, per-cache configuration) |
| `fastddd.search` | search | Full-text search (backend: sql/meilisearch, SQL full-text strategy, Meilisearch connection settings, per-index customization) |
| `fastddd.jackson` | web | JSON serialization (date format, Long→String). ⚠️ **Off by default**: `FastDddJacksonAutoConfiguration` is class-level `@ConditionalOnProperty(enabled, matchIfMissing=false)`; it assembles only with `fastddd.jackson.enabled=true`, otherwise neither Long→String nor the date format takes effect |
| `fastddd.web.response-wrapper` | web | Automatic ApiResponse wrapping. ⚠️ **Off by default**: requires `fastddd.web.response-wrapper.enabled=true`; otherwise Controllers that return raw DTOs miss the `{code,data}` envelope (the `@EnableApiResponseWrapper` annotation is `@Deprecated` as a pure marker and no longer assembles anything) |
| `fastddd.web.knife4j` | web | Knife4j/Swagger documentation |
| `fastddd.monitor.sql` | monitor | SQL monitoring (slow-query threshold, Prometheus) |
| `fastddd.ai` | ai | AI module switch |
| `fastddd.analyzer` | analyzer | Smart table analysis (strategies, weights) |

**Duration fields**: every `Duration`-type configuration property must carry a unit suffix (`s`/`m`/`h`/`d`), e.g. `8h`, `3600s`, `7d`.

---

## Exception Handling

The framework automatically registers a `GlobalExceptionHandler` that handles everything uniformly:

| Exception | HTTP status | Description |
|------|-----------|------|
| `BusinessException` | 400 | Business exception; the client can handle it |
| `SystemException` | 500 | System exception; needs investigation |
| `CommandValidationException` | 400 | Command validation failure (with field-level error details) |
| `MethodArgumentNotValidException` | 400 | Parameter validation failure |
| `AuthenticationException` | 401 | Authentication failure |
| Other `Exception` | 500 | Catch-all handling |

Throwing exceptions in business code:
```java
Assert.notNull(user, "用户不存在");                    // 自动抛 BusinessException
throw new BusinessException("余额不足");                // errorCode=400
throw new BusinessException(1001, "库存不足");          // 自定义 errorCode
throw new SystemException("支付网关超时");               // code=500

// 下游获取错误码: exception.getErrorCode() / exception.getErrorMessage()
```

> **Tip**: `BusinessException` already carries two built-in fields, `errorCode` (Integer) and `errorMessage` (String). It is recommended to plan error-code ranges per business domain (e.g. 1xxx orders, 2xxx payments, 3xxx delivery) so downstream code can handle them structurally via `getErrorCode()`. See Section 3 of the [SPI Reference](./guides/SPI_REFERENCE.md).

---

## Multi-App Project Generation

Studio supports one-click generation of a multi-app Maven multi-module project:

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

Each sub-application is automatically generated with:
- A Security SPI implementation (`AppIdentityService`; the multi-app templates also implement `CodeLoginIdentityService`)
- SMS/email verification-code strategies are built into `fast-ddd-security-starter`; business projects implement `VerificationCodeSender` as needed
- A WeChat authentication strategy generated on demand (`WechatMiniAuthStrategy`)
- An independent JWT secret + channel isolation
- Dockerfile + nginx configuration

---

## Internationalization i18n (fast-ddd-common)

The framework has built-in internationalization; all API response messages and error hints are i18n-aware. Chinese is the default, and English can be switched to.

### Basic usage

```java
import com.gallop.fastddd.common.i18n.Messages;

// 获取消息（自动根据当前 Locale 选择语言）
String msg = Messages.get("web.crud.create.success");         // → "创建成功" (zh) / "Created successfully" (en)

// 带参数的消息
String msg = Messages.get("web.crud.create.error", "主键冲突"); // → "创建失败: 主键冲突"
String msg = Messages.get("web.crud.batch-create.detail", 5);  // → "成功创建 5 条记录"
```

### Locale switching

The framework auto-configures a `LocaleResolver` that resolves the language with the following priorities:

1. **URL parameter**: `?lang=en` → English, `?lang=zh` → Chinese
2. **Accept-Language header**: carried automatically by browsers/clients
3. **Default**: `zh_CN` (Simplified Chinese)

Example: `GET /api/users/1?lang=en` → all response messages become English

### Message code conventions

Format: `{module}.{category}.{operation}.{status}`

| Prefix | Scope | Example |
|------|------|------|
| `common.*` | General | `common.success` |
| `web.crud.*` | BaseController endpoints | `web.crud.create.success`, `web.crud.query.not-found` |
| `web.command.*` | CommandController | `web.command.success`, `web.command.failed` |
| `web.query.*` | QueryController | `web.query.success`, `web.query.not-found` |
| `web.error.*` | GlobalExceptionHandler | `web.error.param-validation`, `web.error.internal` |
| `web.doc.*` | FastDoc document generation | `web.doc.generate.failed` |
| `security.auth.*` | Authentication endpoints | `security.auth.login.success`, `security.auth.user-not-found` |
| `security.access.*` | Access control | `security.access.denied`, `security.access.unauthorized` |

### Custom messages in the business project

Create `messages.properties` (Chinese) and `messages_en.properties` (English) under the business project's `src/main/resources/`; keys with the same name override the framework defaults:

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

Use in business code:

```java
throw new BusinessException(Messages.get("biz.order.cancelled"));
return ApiResponse.success(data, Messages.get("biz.order.paid"));
```

### Auto-configuration

The framework auto-registers the following via `I18nAutoConfiguration`:
- `MessageSource` — loads the `messages` resource bundle (`@ConditionalOnMissingBean`, overridable)
- `I18nWebConfiguration` — `LocaleResolver` + `LocaleChangeInterceptor` (fast-ddd-web module)

---

## Common Pitfalls

| Scenario | Correct practice | Wrong practice |
|------|---------|---------|
| Extending BasePO | `@Getter @Setter`, do not declare id | `@Data` + re-declaring `private Long id` |
| Extending AggregateRoot | `@Getter @Setter` + explicitly implement getId/setId | `@Data` (does not handle the parent's abstract methods) |
| BasePO getId return value | Use `Long id = entity.getId()` (when the entity is `BasePO<Long>`) | Meaningless casts like `(Long) entity.getId()` or raw generics |
| DTO cross-package fields | `private` + `@Data` | Default package-private access (invisible across packages) |
| Duration configuration | `8h`, `3600s`, `7d` | A bare number `28800` (missing unit) |
| CORS allowed-methods | YAML list format | A comma-separated string |
| BasePO subclasses | Do not re-declare id/createTime/updateTime/deleted | Re-declaring causes type conflicts |
| Optimistic lock | Extend VersionedPO / VersionedEntity only when needed | Adding version to every entity (errors when the table has no version column) |
| Security SPI implementation | Just use `@Service`; the framework auto-detects it | Adding `@Import` or `@Component` |
| SecurityUserDetails | The builder must set userId (written into the JWT) | Not setting userId → /userinfo cannot query directly |
| permit-all-paths | Permit exact paths `/api/auth/login`, `/api/auth/code/send` | Writing `/api/auth/**` (userinfo skips authentication) |
| @MapperScan | Must be present on the application class | Missing → BaseRepository is not registered |
| `response-wrapper.enabled` | Off by default; must explicitly opt in with `=true` | Controllers relying on global wrapping return raw DTOs, the frontend interceptor passes them through → business fields undefined (`@EnableApiResponseWrapper` is `@Deprecated` as a pure marker) |
| `jackson.enabled` | Off by default; must explicitly set `=true` for Long→String / date format to assemble | Left off, 19-digit snowflake ids serialize as JSON numbers and the frontend `JSON.parse` loses precision (e.g. "contract/task not found") |
| BasePO primary-key generic | If the PO declares `extends BasePO<Long>`, `getId()` returns `Long` directly | `(Long) po.getId()` is needed only when holding a raw `BasePO` reference; once the generic is parameterized the cast is redundant (harmless but pointless) |
| *ByField field names | Pass the Java camelCase `"createTime"` | Passing the database column name `"create_time"` (also accepted, but not recommended) |
| i18n business messages | Put messages.properties under the business project's resources | Editing the properties inside the framework JAR |

---

## Configuration Switches Off by Default After Upgrading (silent degradation)

FastDDD 1.0 changed some formerly "works out of the box" mechanisms to **explicit opt-in**: the properties default to `false` and **do not error — they just degrade silently** — the backend logs stay clean while problems only surface on the frontend. If pages misbehave after an upgrade or a fresh deployment with no backend error, check these two first:

| Configuration | Default | Symptom when missing | How to check |
|------|------|---------|------|
| `fastddd.web.response-wrapper.enabled` | false | Controllers return raw DTOs (no `{code,data}` envelope), the frontend interceptor passes them through, business fields are undefined | Use `curl` to check whether the response root has the `code/data` envelope |
| `fastddd.jackson.enabled` | false | Long→String not registered; 19-digit snowflake ids lose precision in the frontend `JSON.parse` (e.g. "contract/task not found"); date format not applied | Check with Python `repr(id)` whether it is quoted (strings are quoted, numbers are not) |

Both are best enabled explicitly in the baseline configuration (`application.yml` / the `application.yml.example` template):

```yaml
fastddd:
  web:
    response-wrapper:
      enabled: true
  jackson:
    enabled: true
    long-to-string: true
    date-time-format: "yyyy-MM-dd'T'HH:mm:ss"   # 显式钉回 ISO，避免框架默认空格分隔让前端 new Date 解析失败
```

> **How to spot the pattern**: in upgrade changelogs, **any `@Deprecated` annotation** (e.g. `@EnableApiResponseWrapper`) usually means the original mechanism became a property switch — grep the source for the annotation's new class-level/method-level `@ConditionalOnProperty` to confirm the default value and the consequence of leaving it unset.
>
> **Defense**: local `application.yml` files are often ignored by `.gitignore`; always sync root-cause configuration into the tracked `application.yml.example` template, otherwise new deployments silently regress.

---

## Common Utility Classes

| Class | Purpose |
|----|------|
| `Assert` | Parameter validation; throws `BusinessException` on failure |
| `Messages` | i18n message lookup (`Messages.get(code, args...)`) |
| `CacheService` | Programmatic two-level cache API (get/put/evict/getOrLoad/multiGet) |
| `JsonUtils` | JSON serialization/deserialization |
| `NamingUtils` | Name conversion (camelCase ↔ snakeCase ↔ PascalCase) |
| `Result<T>` | Generic result wrapper (**deprecated**; use `ApiResponse<T>`) |
| `ApiResponse<T>` | Unified response wrapper (recommended), located at `com.gallop.fastddd.web.response`; with `fastddd.web.response-wrapper.enabled=true` opted in, `SmartResponseAdvice` wraps Controller return values automatically |
| `PageResult<T>` | Pagination result wrapper |
| `SearchService` | Full-text search SPI (dual SQL/Meilisearch backends) |
| `SearchQuery` | Search query builder (keyword + fields + filters + sorting + pagination) |
| `SearchResult<T>` | Search results (pagination + highlighting + aggregations + backend identifier) |
| `DomainModelType` | Model type enum (ANEMIC/RICH) |
| `CurrentUserProvider` | Current user ID provider SPI (returns "system" by default) |
