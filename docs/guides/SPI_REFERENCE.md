# FastDDD SPI Reference

**English** | [简体中文](SPI_REFERENCE.zh-CN.md)

> Last verified: 2026-08-10 | Framework version: 1.0.0 | JDK 21 · Spring Boot 3.5.16

This reference lists every SPI interface in FastDDD — its definition, how to replace it, its default behavior, and usage examples.

---

## 1. Import Quick Reference

Import paths for frequently used classes, grouped by module. Avoid guessing paths — copy them directly.

### common module (`fast-ddd-common`)

| Class | Import path |
|------|------------|
| AggregateRoot | `com.gallop.fastddd.common.domain.AggregateRoot` |
| BaseEntity | `com.gallop.fastddd.common.domain.BaseEntity` |
| DomainEvent | `com.gallop.fastddd.common.domain.DomainEvent` |
| BusinessException | `com.gallop.fastddd.common.exception.BusinessException` |
| SystemException | `com.gallop.fastddd.common.exception.SystemException` |

### model module (`fast-ddd-model`)

| Class | Import path |
|------|------------|
| BasePO | `com.gallop.fastddd.model.BasePO` |
| VersionedPO | `com.gallop.fastddd.model.VersionedPO` |

### crud module (`fast-ddd-crud`)

| Class | Import path |
|------|------------|
| BaseRepository | `com.gallop.fastddd.crud.repository.BaseRepository` |
| BaseServiceImpl | `com.gallop.fastddd.crud.service.impl.BaseServiceImpl` |
| IService | `com.gallop.fastddd.crud.service.IService` |

### web module (`fast-ddd-web`)

| Class | Import path |
|------|------------|
| ApiResponse | `com.gallop.fastddd.web.response.ApiResponse` |
| BaseController | `com.gallop.fastddd.web.controller.BaseController` |
| BaseReadOnlyController | `com.gallop.fastddd.web.controller.BaseReadOnlyController` |
| BaseAdminCrudController | `com.gallop.fastddd.web.controller.BaseAdminCrudController` |

> **Note**: `com.gallop.fastddd.common.result.Result` is marked `@Deprecated(forRemoval = true)` — use `ApiResponse` instead.

### security-starter module (`fast-ddd-security-starter`)

| Class | Import path |
|------|------------|
| SecurityUserDetails | `com.gallop.fastddd.security.spi.SecurityUserDetails` |
| SecurityUserInfo | `com.gallop.fastddd.security.spi.SecurityUserInfo` |
| SecurityIdentityService | `com.gallop.fastddd.security.spi.SecurityIdentityService` |
| SecurityPermissionService | `com.gallop.fastddd.security.spi.SecurityPermissionService` |
| SecurityAuditService | `com.gallop.fastddd.security.spi.SecurityAuditService` |
| CodeLoginIdentityService | `com.gallop.fastddd.security.spi.CodeLoginIdentityService` |
| AuthStrategy | `com.gallop.fastddd.security.strategy.AuthStrategy` |
| VerificationCodeSender | `com.gallop.fastddd.security.code.VerificationCodeSender` |
| VerificationCodeType | `com.gallop.fastddd.security.code.VerificationCodeType` |
| VerificationCodePurpose | `com.gallop.fastddd.security.code.VerificationCodePurpose` |
| JwtProperties | `com.gallop.fastddd.security.jwt.JwtProperties` |
| CorsProperties | `com.gallop.fastddd.security.config.CorsProperties` |
| SecurityHttpProperties | `com.gallop.fastddd.security.config.SecurityHttpProperties` |

### event module (`fast-ddd-event`)

| Class | Import path |
|------|------------|
| OutboxEventRepository | `com.gallop.fastddd.event.outbox.OutboxEventRepository` |
| OutboxEvent | `com.gallop.fastddd.event.outbox.OutboxEvent` |
| OutboxEventStatus | `com.gallop.fastddd.event.outbox.OutboxEventStatus` |
| EventPublisher | `com.gallop.fastddd.event.EventPublisher` |

### MyBatis (third-party)

| Class | Import path |
|------|------------|
| @TableName | `com.baomidou.mybatisplus.annotation.TableName` |
| @TableId | `com.baomidou.mybatisplus.annotation.TableId` |
| @TableField | `com.baomidou.mybatisplus.annotation.TableField` |
| @MapperScan | `org.mybatis.spring.annotation.MapperScan` |

---

## 2. Security SPI

### 2.1 SecurityIdentityService — identity bridge (recommended replacement point)

**Purpose**: Bridges your own user system into the FastDDD security framework. Once JWT verification succeeds, the framework loads the user's `SecurityUserDetails` through this interface.

**Interface definition**:
```java
public interface SecurityIdentityService {
    SecurityUserDetails loadUserByUsername(String username);
    SecurityUserInfo getUserInfo(String userId);
}
```

**Default behavior**: `DefaultSecurityIdentityService` — every method throws `UnsupportedOperationException` and prompts you to configure a `SecurityIdentityService` Bean.

**How to replace**: Simply implement it as a `@Component`; the framework discovers it automatically through `@ConditionalOnMissingBean`.

**Example**:
```java
@Component
public class MyIdentityService implements SecurityIdentityService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public SecurityUserDetails loadUserByUsername(String username) {
        UserPO user = userMapper.selectByUsername(username);
        if (user == null) return null;
        return SecurityUserDetails.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    @Override
    public SecurityUserInfo getUserInfo(String userId) {
        UserPO user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) return null;
        return new SecurityUserInfo(user.getId().toString(), user.getUsername(),
                user.getRealName(), user.getEmail(), user.getPhone(),
                user.getAvatar(), user.getEnabled(), user.getUserType(), user.getStatus());
    }
}
```

**When to replace**: Only needed if you use the default FastDDD JWT security flow (`fastddd.security.jwt.enabled=true`). If your project turns off the default security configuration (for example, in favor of a custom SSO), you do not need to implement this interface.

### 2.2 CodeLoginIdentityService — verification-code login

**Purpose**: Enables login with a phone number or email address via verification code. It extends `SecurityIdentityService` with the `loadUserByPhone` and `loadUserByEmail` methods.

**Interface definition**:
```java
public interface CodeLoginIdentityService extends SecurityIdentityService {
    default SecurityUserDetails loadUserByPhone(String phone) {
        return loadUserByUsername(phone);  // 默认 fallback 到 username
    }
    default SecurityUserDetails loadUserByEmail(String email) {
        return loadUserByUsername(email);  // 默认 fallback 到 username
    }
}
```

**How to replace**: Implement it as a `@Component`, same as `SecurityIdentityService`.

**When you need it**: When your login options include SMS or email verification codes. If you only log in with a password, implementing `SecurityIdentityService` is enough.

### 2.3 VerificationCodeSender — verification code delivery channels

**Purpose**: Defines how verification codes are delivered (SMS, email, and so on). The framework ships with `NoopVerificationCodeSender` (logs the code; intended for development).

**Interface definition**:
```java
public interface VerificationCodeSender {
    VerificationCodeType getType();   // SMS 或 EMAIL
    void send(String target, String code, VerificationCodePurpose purpose);
}
```

**Multi-bean discovery**: `DefaultVerificationCodeService` injects `List<VerificationCodeSender>` and dispatches through an `EnumMap` keyed by `VerificationCodeType`. You can register multiple senders (one SMS, one EMAIL); the framework selects one by type.

**Enum values**:

`VerificationCodeType`:
| Value | Description |
|----|------|
| `SMS` | SMS verification code |
| `EMAIL` | Email verification code |

`VerificationCodePurpose`:
| Value | Description |
|----|------|
| `LOGIN` | Login (default) |
| `BIND` | Bind phone/email |
| `RESET_PASSWORD` | Reset password |

**Example — integrating an SMS provider**:
```java
@Component
@ConditionalOnProperty(prefix = "app.sms", name = "enabled", havingValue = "true")
public class SmsVerificationCodeSender implements VerificationCodeSender {

    @Override
    public VerificationCodeType getType() {
        return VerificationCodeType.SMS;
    }

    @Override
    public void send(String phone, String code, VerificationCodePurpose purpose) {
        // 调用短信 API
        smsClient.send(phone, "您的验证码是: " + code);
    }
}
```

### 2.4 SecurityPermissionService — RBAC permissions

**Purpose**: Provides each user's permission codes and role identifiers, used for method-level authorization such as `@PreAuthorize`.

**Interface definition**:
```java
public interface SecurityPermissionService {
    List<String> getPermissionsByUserId(String userId);   // 如 ["system:user:view", "system:order:create"]
    List<String> getRolesByUserId(String userId);          // 如 ["ADMIN", "USER"]
}
```

**Default behavior**: `DefaultSecurityPermissionService` — throws `UnsupportedOperationException`.

**When you need it**: When implementing dynamic RBAC permissions. Projects that do not need RBAC in V1 can skip it.

### 2.5 SecurityAuditService — audit logging

**Purpose**: Persists audit log records (those triggered by the `@AuditLog` annotation).

**Interface definition**:
```java
public interface SecurityAuditService {
    void persist(AuditLogEntity auditLog);
}
```

**Default implementation**: `AuditLogServiceImpl` — writes to the database over JDBC. Replacing it is usually unnecessary unless you need to publish records to a message queue.

### 2.6 AuthStrategy — custom authentication channels

**Purpose**: Extends authentication methods (for example, WeChat Mini Program login, OAuth2, and so on).

**Interface definition**:
```java
public interface AuthStrategy {
    String getType();                                           // 如 "password", "sms", "wechat-mini"
    Authentication authenticate(AuthenticationRequest request);
    default boolean supports(AuthChannel channel) { return true; }
}
```

**Built-in strategies**: `PasswordAuthStrategy` (password), `SmsCodeAuthStrategy` (SMS verification code), `EmailCodeAuthStrategy` (email verification code).

**How to extend**: `@Component` plus an implementation of `getType()` returning a unique identifier; the framework then registers the strategy into the authentication routing automatically.

---

## 3. BusinessException Error Codes

`BusinessException` supports two construction styles:

```java
// 简单模式 — errorCode 默认 400
throw new BusinessException("余额不足");

// 错误码模式 — 自定义 errorCode，下游可结构化处理
throw new BusinessException(1001, "库存不足");
throw new BusinessException(1002, "订单已取消");
```

**Reading the error code downstream**:
```java
try {
    orderService.pay(orderId);
} catch (BusinessException e) {
    int code = e.getErrorCode();     // 1001
    String msg = e.getErrorMessage(); // "库存不足"
    // 结构化返回给前端: { "code": 1001, "message": "库存不足" }
}
```

**Recommended error code range plan**:
| Range | Purpose |
|------|------|
| 400 | General business exception (default) |
| 1000-1999 | Order related |
| 2000-2999 | Payment related |
| 3000-3999 | Delivery related |
| 4000-4999 | License related |

**Note**: Error codes are `Integer` values. Embedding string prefixes in errorMessage for matching (such as `"ORDER_NOT_FOUND: 订单不存在"`) is not recommended — use `getErrorCode()` for structured handling instead. If you genuinely need string identifiers, create a subclass of `BusinessException`.

---

## 4. DomainEvent and Outbox Event Consumption

### 4.1 DomainEvent design decisions

`DomainEvent` is an immutable abstract base class:
- The constructor automatically generates `eventId` (UUID) and `createdAt` (Instant)
- **No no-arg constructor is provided** — a deliberate design decision that keeps events immutable
- `eventType` returns the class name (`this.getClass().getSimpleName()`)

```java
// 正确: 定义事件类
public class OrderPaidEvent extends DomainEvent {
    private final String orderId;
    private final BigDecimal amount;

    public OrderPaidEvent(Serializable aggregateId, String orderId, BigDecimal amount) {
        super(aggregateId, "Order");
        this.orderId = orderId;
        this.amount = amount;
    }
}
```

### 4.2 Recommended Outbox event consumption pattern

**Core principle**: An event is a trigger (announcing what happened), not a data carrier. Load the full data from the aggregate root.

**Recommended approach** — extract key fields into an inner record:
```java
// 定义最小 payload record
record EventPayload(String orderId) {}

// 消费时只提取需要的字段
String orderId = objectMapper.readValue(event.getEventData(), EventPayload.class).orderId();
Order order = orderRepository.findByOrderId(orderId).orElseThrow(...);
// 用 order 的完整数据执行后续逻辑
```

**Anti-pattern** — deserializing into a DomainEvent subclass:
```java
// 这样做会失败! DomainEvent 无无参构造器, Jackson 报 InvalidDefinitionException
OrderPaidEvent event = objectMapper.readValue(eventData, OrderPaidEvent.class);
```

**Why**: The `DomainEvent` constructor runs `eventId = UUID.randomUUID()` and `createdAt = Instant.now()`, so Jackson can find neither a no-arg constructor nor a `@JsonCreator` during deserialization.

---

## 5. Layer Convention Self-Check Checklist

The standard dependency direction for the four DDD package layers:

```
interfaces/ → application/ → domain/ ← infrastructure/
    ↓              ↓            ↑              ↓
  Controller    Service     AggregateRoot    RepositoryImpl
  DTO           QueryService ValueObject     Converter
                               Event          PO/Mapper
```

### Forbidden dependency directions

| Rule | Description |
|------|------|
| `domain` depends on no outer layer | The domain layer stays pure — it must not import Spring, MyBatis, or any other framework type |
| `application` must not import `infrastructure` types | Services never touch PO/Mapper directly; they go through Repository interfaces |
| `interfaces` must not import `infrastructure` types | Controllers never touch PO/Mapper directly; they go through Services |

### Converter placement

**Correct**: `infrastructure/persistence/`

```
order/
├── domain/
│   ├── Order.java                    # 聚合根
│   └── OrderRepository.java          # 接口
├── application/
│   └── OrderService.java             # 应用服务
├── infrastructure/
│   └── persistence/
│       ├── OrderConverter.java       # ✅ PO ↔ Domain 转换
│       ├── OrderPO.java              # 持久化对象
│       ├── OrderMapper.java          # MyBatis Mapper
│       └── OrderRepositoryImpl.java  # 仓储实现
└── interfaces/
    ├── OrderController.java
    └── dto/
```

**Why**: A Converter depends on POs (an infrastructure type); placing it in the domain layer would violate the dependency rules.

### PayloadBuilder / Assembler placement

**Correct**: the `application/` layer (or as static factory methods on DTOs under `interfaces/dto/`)

A PayloadBuilder assembles query results (composing data across aggregates); that is application service logic, not infrastructure.

### Self-check checklist

| # | Check | How to verify |
|---|--------|---------|
| 1 | Controller does not import PO/Mapper | grep `import.*persistence\..*PO` interfaces/ |
| 2 | Service does not import PO/Mapper | grep `import.*persistence\..*PO` application/ |
| 3 | Converter lives in infrastructure/persistence/ | Check the Converter file location |
| 4 | Domain does not import Spring/MyBatis | grep `import org.springframework\|import com.baomidou` domain/ |
| 5 | PayloadBuilder lives in application/ | Check the file location |
