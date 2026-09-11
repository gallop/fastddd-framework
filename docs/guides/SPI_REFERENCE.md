# FastDDD SPI 参考手册

> Last verified: 2026-08-10 | Framework version: 1.0.0 | JDK 21 · Spring Boot 3.5.16

本手册列出 FastDDD 所有 SPI 接口的定义、替换方式、默认行为和使用示例。

---

## 1. Import 速查表

常用类的 import 路径，按模块分组。避免猜错路径——直接复制使用。

### common 模块 (`fast-ddd-common`)

| 类名 | import 路径 |
|------|------------|
| AggregateRoot | `com.gallop.fastddd.common.domain.AggregateRoot` |
| BaseEntity | `com.gallop.fastddd.common.domain.BaseEntity` |
| DomainEvent | `com.gallop.fastddd.common.domain.DomainEvent` |
| BusinessException | `com.gallop.fastddd.common.exception.BusinessException` |
| SystemException | `com.gallop.fastddd.common.exception.SystemException` |

### model 模块 (`fast-ddd-model`)

| 类名 | import 路径 |
|------|------------|
| BasePO | `com.gallop.fastddd.model.BasePO` |
| VersionedPO | `com.gallop.fastddd.model.VersionedPO` |

### crud 模块 (`fast-ddd-crud`)

| 类名 | import 路径 |
|------|------------|
| BaseRepository | `com.gallop.fastddd.crud.repository.BaseRepository` |
| BaseServiceImpl | `com.gallop.fastddd.crud.service.impl.BaseServiceImpl` |
| IService | `com.gallop.fastddd.crud.service.IService` |

### web 模块 (`fast-ddd-web`)

| 类名 | import 路径 |
|------|------------|
| ApiResponse | `com.gallop.fastddd.web.response.ApiResponse` |
| BaseController | `com.gallop.fastddd.web.controller.BaseController` |
| BaseReadOnlyController | `com.gallop.fastddd.web.controller.BaseReadOnlyController` |
| BaseAdminCrudController | `com.gallop.fastddd.web.controller.BaseAdminCrudController` |

> **注意**: `com.gallop.fastddd.common.result.Result` 已标记 `@Deprecated(forRemoval = true)`，请使用 `ApiResponse`。

### security-starter 模块 (`fast-ddd-security-starter`)

| 类名 | import 路径 |
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

### event 模块 (`fast-ddd-event`)

| 类名 | import 路径 |
|------|------------|
| OutboxEventRepository | `com.gallop.fastddd.event.outbox.OutboxEventRepository` |
| OutboxEvent | `com.gallop.fastddd.event.outbox.OutboxEvent` |
| OutboxEventStatus | `com.gallop.fastddd.event.outbox.OutboxEventStatus` |
| EventPublisher | `com.gallop.fastddd.event.EventPublisher` |

### MyBatis 相关（第三方）

| 类名 | import 路径 |
|------|------------|
| @TableName | `com.baomidou.mybatisplus.annotation.TableName` |
| @TableId | `com.baomidou.mybatisplus.annotation.TableId` |
| @TableField | `com.baomidou.mybatisplus.annotation.TableField` |
| @MapperScan | `org.mybatis.spring.annotation.MapperScan` |

---

## 2. Security SPI

### 2.1 SecurityIdentityService — 身份桥接（推荐替换）

**用途**: 将你的用户体系桥接到 FastDDD 安全框架。JWT 验证通过后，框架通过此接口加载用户的 `SecurityUserDetails`。

**接口定义**:
```java
public interface SecurityIdentityService {
    SecurityUserDetails loadUserByUsername(String username);
    SecurityUserInfo getUserInfo(String userId);
}
```

**默认行为**: `DefaultSecurityIdentityService` — 所有方法抛 `UnsupportedOperationException`，提示"请配置 SecurityIdentityService Bean"。

**替换方式**: `@Component` 实现即可，框架通过 `@ConditionalOnMissingBean` 自动发现。

**示例**:
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

**何时需要替换**: 仅在使用 FastDDD 默认 JWT 安全流程（`fastddd.security.jwt.enabled=true`）时需要替换。如果项目已关闭默认安全配置（如使用自定义 SSO），则不需要实现此接口。

### 2.2 CodeLoginIdentityService — 验证码登录

**用途**: 支持通过手机号或邮箱进行验证码登录。继承 `SecurityIdentityService`，增加 `loadUserByPhone` 和 `loadUserByEmail` 方法。

**接口定义**:
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

**替换方式**: `@Component` 实现，同 SecurityIdentityService。

**何时需要**: 当你的登录方式包含手机验证码或邮箱验证码时。如果只用密码登录，实现 `SecurityIdentityService` 即可。

### 2.3 VerificationCodeSender — 验证码发送渠道

**用途**: 定义验证码的发送方式（短信、邮件等）。框架内置 `NoopVerificationCodeSender`（日志输出，开发用）。

**接口定义**:
```java
public interface VerificationCodeSender {
    VerificationCodeType getType();   // SMS 或 EMAIL
    void send(String target, String code, VerificationCodePurpose purpose);
}
```

**多 bean 发现机制**: `DefaultVerificationCodeService` 注入 `List<VerificationCodeSender>`，按 `VerificationCodeType` 建立 `EnumMap` 分发。可以注册多个 sender（一个 SMS、一个 EMAIL），框架按类型选择。

**枚举值**:

`VerificationCodeType`:
| 值 | 说明 |
|----|------|
| `SMS` | 短信验证码 |
| `EMAIL` | 邮箱验证码 |

`VerificationCodePurpose`:
| 值 | 说明 |
|----|------|
| `LOGIN` | 登录（默认） |
| `BIND` | 绑定手机/邮箱 |
| `RESET_PASSWORD` | 重置密码 |

**示例 — 接入短信服务**:
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

### 2.4 SecurityPermissionService — RBAC 权限

**用途**: 提供用户的权限码列表和角色标识列表，用于 `@PreAuthorize` 等方法级权限控制。

**接口定义**:
```java
public interface SecurityPermissionService {
    List<String> getPermissionsByUserId(String userId);   // 如 ["system:user:view", "system:order:create"]
    List<String> getRolesByUserId(String userId);          // 如 ["ADMIN", "USER"]
}
```

**默认行为**: `DefaultSecurityPermissionService` — 抛 `UnsupportedOperationException`。

**何时需要**: 实现动态 RBAC 权限时。V1 不需要 RBAC 的项目可忽略。

### 2.5 SecurityAuditService — 审计日志

**用途**: 持久化审计日志（`@AuditLog` 注解触发的记录）。

**接口定义**:
```java
public interface SecurityAuditService {
    void persist(AuditLogEntity auditLog);
}
```

**默认实现**: `AuditLogServiceImpl` — 基于 JDBC 写入数据库。通常无需替换，除非需要写入消息队列。

### 2.6 AuthStrategy — 自定义认证通道

**用途**: 扩展认证方式（如微信小程序登录、OAuth2 等）。

**接口定义**:
```java
public interface AuthStrategy {
    String getType();                                           // 如 "password", "sms", "wechat-mini"
    Authentication authenticate(AuthenticationRequest request);
    default boolean supports(AuthChannel channel) { return true; }
}
```

**内置策略**: `PasswordAuthStrategy`（密码）、`SmsCodeAuthStrategy`（短信验证码）、`EmailCodeAuthStrategy`（邮箱验证码）。

**扩展方式**: `@Component` + 实现 `getType()` 返回唯一标识，框架自动注册到认证路由。

---

## 3. BusinessException 错误码

`BusinessException` 支持两种构造方式：

```java
// 简单模式 — errorCode 默认 400
throw new BusinessException("余额不足");

// 错误码模式 — 自定义 errorCode，下游可结构化处理
throw new BusinessException(1001, "库存不足");
throw new BusinessException(1002, "订单已取消");
```

**下游获取错误码**:
```java
try {
    orderService.pay(orderId);
} catch (BusinessException e) {
    int code = e.getErrorCode();     // 1001
    String msg = e.getErrorMessage(); // "库存不足"
    // 结构化返回给前端: { "code": 1001, "message": "库存不足" }
}
```

**推荐错误码范围规划**:
| 范围 | 用途 |
|------|------|
| 400 | 通用业务异常（默认） |
| 1000-1999 | 订单相关 |
| 2000-2999 | 支付相关 |
| 3000-3999 | 交付相关 |
| 4000-4999 | License 相关 |

**注意**: 错误码是 `Integer` 类型。不建议在 errorMessage 中嵌入字符串前缀（如 `"ORDER_NOT_FOUND: 订单不存在"`）进行匹配——请使用 `getErrorCode()` 做结构化处理。如果确实需要字符串标识，可以创建 `BusinessException` 的子类。

---

## 4. DomainEvent 与 Outbox 事件消费

### 4.1 DomainEvent 设计决策

`DomainEvent` 是不可变抽象基类：
- 构造器自动生成 `eventId`（UUID）和 `createdAt`（Instant）
- **不提供无参构造器** — 这是有意的设计决策，保证事件不可变
- `eventType` 返回类名（`this.getClass().getSimpleName()`）

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

### 4.2 推荐 Outbox 事件消费模式

**核心原则**: 事件作为触发器（通知"发生了什么"），不作为数据载体。完整数据从聚合根加载。

**推荐做法** — 内部 record 提取关键字段：
```java
// 定义最小 payload record
record EventPayload(String orderId) {}

// 消费时只提取需要的字段
String orderId = objectMapper.readValue(event.getEventData(), EventPayload.class).orderId();
Order order = orderRepository.findByOrderId(orderId).orElseThrow(...);
// 用 order 的完整数据执行后续逻辑
```

**错误做法** — 反序列化为 DomainEvent 子类：
```java
// 这样做会失败! DomainEvent 无无参构造器, Jackson 报 InvalidDefinitionException
OrderPaidEvent event = objectMapper.readValue(eventData, OrderPaidEvent.class);
```

**原因**: DomainEvent 构造器中 `eventId = UUID.randomUUID()` 和 `createdAt = Instant.now()`，Jackson 反序列化时找不到无参构造器或 `@JsonCreator`。

---

## 5. 层规约自检清单

DDD 四层包结构的标准依赖方向：

```
interfaces/ → application/ → domain/ ← infrastructure/
    ↓              ↓            ↑              ↓
  Controller    Service     AggregateRoot    RepositoryImpl
  DTO           QueryService ValueObject     Converter
                               Event          PO/Mapper
```

### 禁止的依赖方向

| 规则 | 说明 |
|------|------|
| `domain` 不依赖任何外层 | 领域层是纯净的，不 import Spring、MyBatis 等任何框架类型 |
| `application` 不 import `infrastructure` 类型 | Service 不直接操作 PO/Mapper，通过 Repository 接口 |
| `interfaces` 不 import `infrastructure` 类型 | Controller 不直接操作 PO/Mapper，通过 Service |

### Converter 位置规范

**正确**: `infrastructure/persistence/`

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

**原因**: Converter 依赖 PO（infrastructure 类型），放在 domain 层会违反依赖规则。

### PayloadBuilder / Assembler 位置

**正确**: `application/` 层（或 `interfaces/dto/` 中作为 DTO 的静态工厂方法）

PayloadBuilder 组装查询结果（跨聚合数据组装），属于应用服务逻辑，不属于 infrastructure。

### 自检清单

| # | 检查项 | 如何验证 |
|---|--------|---------|
| 1 | Controller 不 import PO/Mapper | grep `import.*persistence\..*PO` interfaces/ |
| 2 | Service 不 import PO/Mapper | grep `import.*persistence\..*PO` application/ |
| 3 | Converter 在 infrastructure/persistence/ | 检查 Converter 文件位置 |
| 4 | Domain 不 import Spring/MyBatis | grep `import org.springframework\|import com.baomidou` domain/ |
| 5 | PayloadBuilder 在 application/ | 检查文件位置 |
