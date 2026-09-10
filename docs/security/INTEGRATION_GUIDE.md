# FastDDD Security 集成指南

> 版本: v1.0 | 日期: 2026-04-23
> 基于 DataChat 项目集成实践总结，适用于所有新项目接入 fast-ddd-security-starter

---

## 一、集成前提（必须在写接口之前完成）

### 1.1 用户表

集成安全模块**首要条件**是准备好用户表。不要等到接口联调时才发现没有用户数据。

用户表没有固定结构要求，框架通过 SPI 解耦。但必须包含以下字段语义：

| 必要字段 | 说明 | 示例 |
|---------|------|------|
| `id` | 主键 | `BIGSERIAL` |
| `username` | 登录名 | `VARCHAR(100) NOT NULL UNIQUE` |
| `password` | BCrypt 密码哈希 | `VARCHAR(255) NOT NULL` |
| `enabled` | 启用状态 | `SMALLINT DEFAULT 1` |

密码必须是 BCrypt 哈希。生成方式：

```java
// 在 Spring Boot CommandLineRunner 中验证
BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("your-password");
log.info("Hash: {}", hash);
log.info("Verify: {}", encoder.matches("your-password", hash));
```

**关键教训**：预置的密码哈希一定要用代码验证是否匹配，手动从其他项目复制的哈希值可能对应不同的原始密码。

### 1.2 角色权限数据

Phase 1 可以硬编码返回（如 admin 返回 `*` 全部权限），Phase 2 再对接 RBAC 表。但 `SecurityPermissionService` SPI 必须在项目启动前实现，否则框架使用默认实现（抛异常）。

### 1.3 依赖配置

```xml
<dependency>
    <groupId>io.github.gallop.fastddd</groupId>
    <artifactId>fast-ddd-security-starter</artifactId>
    <version>${fastddd.version}</version>
</dependency>
```

Spring Boot 3.x 要求自动配置注册文件位于：
```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
**不是**旧的 `META-INF/spring.factories`。如果自动配置没生效，首先检查这个文件。

---

## 二、必须实现的两 个 SPI

### 2.1 SecurityIdentityService — 用户身份加载

```java
@Service
@RequiredArgsConstructor
public class MyIdentityService implements SecurityIdentityService {

    private final MyUserService userService;

    @Override
    public SecurityUserDetails loadUserByUsername(String username) {
        MyUser user = userService.getByUsername(username);
        if (user == null) return null;
        return SecurityUserDetails.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .enabled(user.getEnabled() == 1)
                .build();
    }

    @Override
    public SecurityUserInfo getUserInfo(String userId) {
        MyUser user = userService.getById(userId).orElse(null);
        if (user == null) return null;
        return SecurityUserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .enabled(user.getEnabled() == 1)
                .build();
    }
}
```

**注意**：
- 用 `@Service`（不是 `@Component`），因为框架的 `@ConditionalOnMissingBean` 按 `SecurityIdentityService` 类型检测
- `loadUserByUsername` 返回 null 表示用户不存在，不要抛异常（`DaoAuthenticationProvider` 会将 `UsernameNotFoundException` 转为 `BadCredentialsException`，避免用户名枚举）
- `getById()` 返回 `Optional<T>`（FastDDD BaseServiceImpl 约定），必须用 `.orElse(null)`

### 2.2 SecurityPermissionService — 权限查询

```java
@Service
public class MyPermissionService implements SecurityPermissionService {

    @Override
    public List<String> getPermissionsByUserId(String userId) {
        // Phase 1: 硬编码
        return List.of("*");
        // Phase 2: 查 RBAC 表
    }

    @Override
    public List<String> getRolesByUserId(String userId) {
        return List.of("ROLE_ADMIN");
    }
}
```

---

## 三、application.yml 配置清单

### 3.1 必须配置

```yaml
fastddd:
  security:
    jwt:
      enabled: true
      # 必填；未设置时框架会拒绝启动。使用至少 64 字节的随机值。
      secret: ${JWT_SECRET}
      expiration: 24h
      refresh-expiration: 7d
      channel: ADMIN            # 认证通道标识，多端场景区分
```

**关键配置项说明**：

| 配置项 | 说明 | 踩坑记录 |
|-------|------|---------|
| `channel` | 当前应用的认证通道 | **必须和 login 时生成的 token channel 一致**，否则 JWT filter 会拒绝 token。框架的 `AuthController` 自动从 `jwtProperties.getChannel()` 取值，所以只要配置了就一致 |
| `secret` | HMAC-SHA512 密钥（默认签名策略） | **必须显式设置且 ≥64 字节**；缺失时框架拒绝启动。生产环境从密钥管理系统或环境变量提供，不得配置回退值。如需 RSA/ES256 非对称签名（如跨子系统 SSO），声明 `@Component` 实现 `JwtSigner` 接口即可覆盖默认的 `HmacJwtSigner` |
| `expiration` | Duration 格式带单位 | `24h`、`3600s`、`7d`，不要写纯数字 |

### 3.2 放行路径配置

```yaml
    http:
      permit-all-paths:
        - /api/auth/login       # 只放行 login
        - /api/auth/refresh     # 和 refresh
        - /actuator/health
        - /actuator/info
        - /ws/**
```

**⚠️ 绝对不要用 `/api/auth/**`**！

这个通配符会导致 `/api/auth/userinfo` 和 `/api/auth/logout` 也被放行，这两个端点需要 JWT 认证。用 `/**` 通配符放行认证路径是本项目调试过程中花费最长时间定位的 bug。

### 3.3 CORS 配置

```yaml
    cors:
      enabled: true
      allowed-origins:
        - http://localhost:5173
        - http://localhost:3000
      allowed-methods:          # YAML 列表，不是逗号分隔字符串
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      max-age: 3600s
```

### 3.4 Spring Boot 启动类

```java
@SpringBootApplication(
        scanBasePackages = "com.yourapp",
        exclude = {UserDetailsServiceAutoConfiguration.class}  // 必须排除！
)
```

不排除 `UserDetailsServiceAutoConfiguration` 会导致 Spring Boot 自动生成一个随机密码的内存用户，与 FastDDD 的 `UserDetailsService` 适配器冲突。

**注意**：只排除 `UserDetailsServiceAutoConfiguration`，**不要排除** `SecurityAutoConfiguration`（Spring 的），否则 actuator 等功能会受影响。

---

## 四、认证流程架构

理解这条链路，调试时就知道该查哪一环：

```
                    登录流程
                    ───────
客户端 ──POST /api/auth/login──→ AuthController
                                    │
                                    ├→ AuthStrategyRegistry.getStrategy("password")
                                    │       │
                                    │       └→ PasswordAuthStrategy.authenticate()
                                    │               │
                                    │               └→ AuthenticationManager.authenticate()
                                    │                       │
                                    │                       └→ DaoAuthenticationProvider
                                    │                               │
                                    │                               ├→ UserDetailsService (适配器)
                                    │                               │       │
                                    │                               │       └→ SecurityIdentityService ← 你的实现
                                    │                               │
                                    │                               ├→ PasswordEncoder.matches()
                                    │                               └→ 账户状态检查
                                    │
                                    ├→ permissionService.getPermissionsByUserId()
                                    ├→ jwtTokenProvider.generateAccessToken(auth, channel)
                                    └→ 返回 LoginResponse
```

```
                    请求认证流程
                    ───────────
客户端 ──GET /api/xxx──→ JwtAuthenticationFilter
                            │
                            ├→ shouldNotFilter() ──→ 检查 permitAllPaths，匹配则跳过
                            │
                            ├→ validateToken() ──→ 解析 JWT，校验签名和过期
                            │
                            ├→ channel 校验 ──→ token channel vs 配置 channel
                            │
                            └→ SecurityContextHolder.setAuthentication(username + authorities)

                       Spring Security FilterChain
                            │
                            ├→ permitAllPaths ──→ 直接放行
                            └→ 其他路径 ──→ 检查 Authentication 是否存在
```

### 调试检查点

| 现象 | 检查点 |
|------|--------|
| 登录返回 401 "用户名或密码错误" | `SecurityIdentityService.loadUserByUsername()` 是否返回了正确的 BCrypt 哈希？密码是否匹配？ |
| 登录返回 500 ClassCastException | `UsernamePasswordAuthenticationToken` 的 principal 必须是 `UserDetails`，不能是 String |
| 登录成功但 API 返回 401 | 检查 JWT token 中的 `channel` 是否与 `jwt.channel` 配置一致 |
| API 返回 401 但 token 有效 | 检查 `permit-all-paths` 是否意外匹配了该路径 |
| userinfo 返回 anonymousUser | JWT filter 被 `shouldNotFilter()` 跳过了，检查 permitAllPaths 配置 |
| 返回 "No provider for UsernamePasswordAuthenticationToken" | `UserDetailsService` bean 未注册，`DaoAuthenticationProvider` 无法创建 |
| 返回随机密码日志 | `UserDetailsServiceAutoConfiguration` 未排除 |

---

## 五、集成检查清单

在新项目中按顺序逐项确认：

### 启动前

- [ ] **用户表已创建**，包含 username/password/enabled 字段
- [ ] **种子数据已插入**，密码为 BCrypt 哈希且已用代码验证匹配
- [ ] **依赖已添加**：`fast-ddd-security-starter`
- [ ] **AutoConfiguration.imports** 文件存在（Spring Boot 3.x）

### SPI 实现

- [ ] `SecurityIdentityService` 已实现（`@Service` 注解）
- [ ] `SecurityPermissionService` 已实现（`@Service` 注解）
- [ ] `loadUserByUsername()` 的 `getOne()`/`getById()` 返回值用了 `.orElse(null)`（FastDDD 的 `IService` 返回 `Optional`）

### 配置

- [ ] `jwt.enabled: true`
- [ ] `jwt.secret` 至少 64 字节
- [ ] `jwt.channel` 已设置（单端应用设 `ADMIN` 或 `DEFAULT`）
- [ ] `permit-all-paths` 只放行 `/api/auth/login` 和 `/api/auth/refresh`，**不要**用 `/api/auth/**`
- [ ] `UserDetailsServiceAutoConfiguration` 已在 `@SpringBootApplication` 中排除

### 验证

```bash
# 1. 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-password"}'
# 期望：200，返回 accessToken + refreshToken + user + permissions

# 2. 受保护 API
curl http://localhost:8080/api/v1/xxx \
  -H "Authorization: Bearer <accessToken>"
# 期望：200 + 业务数据

# 3. 无 token 访问
curl http://localhost:8080/api/v1/xxx
# 期望：401

# 4. Token 刷新
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'
# 期望：200，返回新 accessToken

# 5. 用户信息
curl http://localhost:8080/api/auth/userinfo \
  -H "Authorization: Bearer <accessToken>"
# 期望：200，返回 user + roles + permissions

# 6. 登出
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <accessToken>"
# 期望：200
```

---

## 六、常见陷阱汇总

### 陷阱 1： permit-all-paths 通配符过宽

```yaml
# ✗ 错误 — userinfo 和 logout 被匿名放行
permit-all-paths:
  - /api/auth/**

# ✓ 正确 — 只放行不需要认证的端点
permit-all-paths:
  - /api/auth/login
  - /api/auth/refresh
```

**症状**：userinfo 返回 anonymousUser；logout 无效。
**原因**：`JwtAuthenticationFilter.shouldNotFilter()` 和 `SecurityFilterChain.authorizeHttpRequests` 都使用这个列表，通配符导致需要认证的端点被跳过。

### 陷阱 2： JWT channel 不匹配

```yaml
# yml 配置
jwt:
  channel: ADMIN
```

如果 token 生成时 channel 是 `DEFAULT`（代码里用了 `AuthChannel.DEFAULT`），但配置是 `ADMIN`，JWT filter 会因为 channel mismatch 拒绝 token。

**正确做法**：框架的 `AuthController` 自动从 `jwtProperties.getChannel()` 取 channel 值，不需要手动指定。如果自定义 controller，必须显式传 `AuthChannel.ADMIN`。

### 陷阱 3： BCrypt 哈希不匹配

从其他项目复制的密码哈希不一定对应你以为的原始密码。**永远用代码验证**：

```java
@Bean
CommandLineRunner verifyPassword(PasswordEncoder encoder) {
    return args -> {
        String hash = "$2a$10$xxxxx";
        log.info("Match: {}", encoder.matches("admin123", hash));
    };
}
```

### 陷阱 4： @Data 与实体代理冲突

FastDDD 约定：实体类用 `@Getter @Setter`，**不用 `@Data`**。`@Data` 生成 `equals()`/`hashCode()`/`toString()`，与 MyBatis/JPA 代理对象冲突。

### 陷阱 5： getById() 返回 Optional

FastDDD 的 `IService.getById()` 返回 `Optional<T>`，不是 `T`。直接赋值会编译报错。

```java
// ✗ 错误
User user = userService.getById(userId);

// ✓ 正确
User user = userService.getById(userId).orElse(null);
```

### 陷阱 6： catch(Exception) 捕获不了 StackOverflowError

`StackOverflowError` 继承自 `Error`，不继承 `Exception`。如果使用 `ObjectMapper` 序列化可能包含循环引用的对象（如 `HttpServletRequest`），`catch(Exception)` 无法兜底。已修复为 `catch(Throwable)`。

---

## 七、框架内部机制（供框架维护者参考）

### UserDetailsService 适配器

框架在 `FastDddSecurityAutoConfiguration` 中注册了一个 `UserDetailsService` 适配器 Bean，将 FastDDD 的 `SecurityIdentityService` SPI 桥接到 Spring Security 的 `DaoAuthenticationProvider`。

```
SecurityIdentityService (FastDDD SPI)
        ↓ 适配器
UserDetailsService (Spring Security)
        ↓ DaoAuthenticationProvider
AuthenticationManager
        ↓
PasswordAuthStrategy
```

设计原则：**不绕过 Spring Security 管线，而是通过适配器桥接**。这样 Spring Security 的所有特性（账户状态检查、认证事件发布、Provider 链）都自动生效。

### AuthController 的 Bean 替换机制

框架的 `AuthController` 通过 `@ConditionalOnMissingBean(name = "authController")` 注册。如果项目需要自定义登录逻辑，只需创建一个 bean 名为 `authController` 的 `@RestController`：

```java
@RestController("authController")    // bean 名必须是 authController
@RequestMapping("/api/auth")
public class MyAuthController { ... }
```

**但通常不需要自定义**——框架的 `AuthController` 已内置 login/logout/refresh/userinfo 四个端点，通过 SPI 扩展即可覆盖绝大多数场景。
