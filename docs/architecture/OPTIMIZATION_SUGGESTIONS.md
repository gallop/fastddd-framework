# FastDDD 框架优化建议

> 来源：DataChat 项目实战反馈（2026-05），基于 Spring Boot 3.4 + MyBatis Plus 3.5.9 的真实业务场景。

---

## 目录

- [C1. BaseController `updateById` 未将路径 ID 设置到实体（Bug）](#c1-basecontroller-updatebyid-未将路径-id-设置到实体bug)
- [C2. BasePO 的 `id` 字段声明导致大量模板代码](#c2-basepo-的-id-字段声明导致大量模板代码)
- [C3. BaseController 缺少扩展 Hook 方法](#c3-basecontroller-缺少扩展-hook-方法)
- [C4. BaseController 返回类型不一致](#c4-basecontroller-返回类型不一致)
- [I1. 缺少多租户/项目上下文基础设施](#i1-缺少多租户项目上下文基础设施)
- [I2. CurrentUserProvider 与 Security 模块断裂](#i2-currentuserprovider-与-security-模块断裂)
- [I3. SecurityUserDetails 不携带权限](#i3-securityuserdetails-不携带权限)
- [I4. 双重响应类型 `ApiResponse` 与 `Result` 并存](#i4-双重响应类型-apiresponse-与-result-并存)
- [I5. 软删除与唯一索引无感知](#i5-软删除与唯一索引无感知)
- [I6. BaseServiceImpl 字段注入 + batchSize 被忽略](#i6-baseserviceimpl-字段注入--batchsize-被忽略)
- [优先级汇总](#优先级汇总)

---

## C1. BaseController `updateById` 未将路径 ID 设置到实体（Bug）

**严重程度**：Critical — 数据正确性
**涉及文件**：`fast-ddd-web/.../BaseController.java:206-223`

### 问题描述

`updateById(@PathVariable Serializable id, @RequestBody T entity)` 方法中，路径变量 `id` 仅用于存在性校验（实际上连校验都没有做），从未赋值到 `entity` 上。如果请求体的 `entity.id` 为 `null` 或与路径 `id` 不一致，会导致更新失败或更新错误记录。

```java
// 当前代码（第 206-223 行）
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<T>> updateById(
        @PathVariable("id") @NotNull Serializable id,
        @Valid @RequestBody T entity) {
    try {
        // 注意：这里假设实体已经包含正确的ID，或者由具体实现来处理ID设置
        boolean success = service.updateById(entity);  // ← entity.getId() 可能是 null！
        ...
    }
}
```

### 建议修复

```java
@PutMapping("/{id}")
public ResponseEntity<ApiResponse<T>> updateById(
        @PathVariable("id") @NotNull Serializable id,
        @Valid @RequestBody T entity) {
    try {
        // 将路径 ID 设置到实体上，确保一致性
        entity.setId(id);
        boolean success = service.updateById(entity);
        ...
    }
}
```

**注意**：`BasePO.setId()` 当前是 `abstract`，需要确保子类都实现了 `setId()`。这与 [C2](#c2-basepo-的-id-字段声明导致大量模板代码) 相关。

---

## C2. BasePO 的 `id` 字段声明导致大量模板代码

**严重程度**：Critical — 开发体验
**涉及文件**：`fast-ddd-model/.../BasePO.java`

### 问题描述

`BasePO` 没有声明 `id` 字段，只声明了 `abstract Serializable getId()`。每个子类必须：

1. 声明 `private Long id;` + `@TableId(type = IdType.AUTO)`
2. 实现 `getId()` 方法
3. 注意 Lombok `@Data` 与抽象方法的交互问题

在实际项目中，CLAUDE.md 专门花了大段文字警告这个坑，使用者频繁踩错。

```java
// 当前：每个子类都要重复这段代码
@Data  // 注意：@Data 不处理继承的抽象方法，必须用 @Getter/@Setter
@EqualsAndHashCode(callSuper = true)
public class DcProject extends VersionedPO {
    @TableId(type = IdType.AUTO)
    private Long id;

    @Override
    public Serializable getId() { return id; }
    // 最好再加 setId()，否则 updateById(C1) 无法工作
}
```

### 建议方案

**方案 A（推荐）**：在 `BasePO` 中内联 `id` 字段，消除模板代码。

```java
@Data
public abstract class BasePO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    public Serializable getId() { return id; }
    public void setId(Serializable id) { this.id = (Long) id; }

    // createTime, updateTime, createUser, updateUser, deleted 不变
}
```

子类直接继承，无需再声明 `id`：

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dc_project")
public class DcProject extends VersionedPO {
    private String name;      // 直接写业务字段
    private String description;
}
```

**方案 B（兼容性优先）**：如果不想改 `BasePO`，可以在框架文档中给出清晰的模板和注意事项。

### Breaking Change 评估

- **已有子类**：如果 `BasePO` 加了 `id` 字段，子类现有的 `id` 声明会遮蔽父类字段，需要删除子类的 `id` 声明和 `getId()` 实现。
- **影响范围**：所有 `BasePO` 的子类（贫血模型模块）。
- **迁移成本**：低，机械性删除即可，可提供迁移脚本。

---

## C3. BaseController 缺少扩展 Hook 方法

**严重程度**：Critical — 开发体验
**涉及文件**：`fast-ddd-web/.../BaseController.java`

### 问题描述

子类需要给 create/update/delete 加权限检查时，必须完整 override 整个方法，精确匹配返回类型签名。在实际开发中，使用者花了大量时间调试 `ResponseEntity<ApiResponse<T>>` vs `ResponseEntity<ApiResponse<Boolean>>` vs `ResponseEntity<Void>` 的区别。

```java
// 当前：override 整个方法，必须精确匹配返回类型
@Override
@PostMapping
public ResponseEntity<ApiResponse<DcProject>> create(@RequestBody DcProject entity) {
    assertPlatformAdmin();  // 只是想加这一行
    return super.create(entity);  // 其他全部重复
}
```

### 建议方案

在 `BaseController` 中增加 Hook 方法，子类只需 override Hook：

```java
// BaseController.java 新增
protected void beforeCreate(T entity) {}
protected void afterCreate(T entity) {}
protected void validateCreate(T entity) {}

protected void beforeUpdate(T entity) {}
protected void afterUpdate(T entity) {}

protected void beforeDelete(Serializable id) {}
protected void afterDelete(Serializable id) {}

// 在现有方法中调用 Hook
@PostMapping
public ResponseEntity<ApiResponse<T>> create(@Validated @RequestBody T entity) {
    try {
        validateCreate(entity);   // ← 新增
        beforeCreate(entity);     // ← 新增
        boolean success = service.save(entity);
        if (success) {
            afterCreate(entity);  // ← 新增
            return ResponseEntity.status(HttpStatus.CREATED)...
        }
    }
}
```

子类使用：

```java
public class DcProjectController extends BaseController<DcProjectService, DcProject> {
    @Override
    protected void beforeCreate(DcProject entity) {
        assertPlatformAdmin();
    }
    // 不需要关心返回类型
}
```

---

## C4. BaseController 返回类型不一致

**严重程度**：Important — 前端集成体验
**涉及文件**：`fast-ddd-web/.../BaseController.java`

### 问题描述

不同端点的返回类型不一致，前端需要为每个端点写不同的解包逻辑：

| 方法 | 返回类型 | 前端解包方式 |
|------|---------|-------------|
| `create` | `ApiResponse<T>` | `res.data.data` |
| `update` | `ApiResponse<Boolean>` | `res.data.data` (boolean) |
| `updateById` | `ApiResponse<T>` | `res.data.data` |
| `delete` | `Void`（204） | 检查 status code |
| `deleteBatch` | `ApiResponse<String>` | `res.data.data` (string) |

### 建议方案

统一所有端点的返回格式：

```java
// 方案：所有端点统一返回 ApiResponse<T>
// delete 返回被删除的实体或 Boolean
@DeleteMapping("/{id}")
public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable("id") @NotNull Serializable id) {
    try {
        boolean success = service.removeById(id);
        if (success) {
            return ResponseEntity.ok(ApiResponse.success(true, Messages.get("web.crud.delete.success")));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(Messages.get("web.crud.delete.not-found")));
        }
    }
}
```

这样前端所有端点都可以统一用 `res.data.data` 解包，delete 不再需要特殊处理 status code。

---

## I1. 缺少多租户/项目上下文基础设施

**严重程度**：Important — 企业级功能
**涉及模块**：`fast-ddd-security-starter`, `fast-ddd-crud`

### 问题描述

DataChat 需要多项目（类似多租户）隔离，框架目前提供零支持：

1. `Command.CommandMetadata` 和 `Query.QueryMetadata` 有 `tenantId` 字段，但从未被填充或消费
2. 没有 `TenantContext` ThreadLocal
3. 没有 Web 拦截器自动提取租户信息
4. 没有 MyBatis Plus `TenantLineInnerInterceptor` 自动追加 `WHERE project_id = ?`
5. 没有基于角色的访问控制（RBAC）基础设施

DataChat 被迫从零搭建了 `ProjectContext` + `ProjectContextInterceptor` + 手动 WHERE 过滤。

### 建议方案

提供可选的多租户 starter（如 `fast-ddd-tenant-starter`）：

```java
// 1. 租户上下文
public final class TenantContext {
    private static final ThreadLocal<TenantInfo> HOLDER = new ThreadLocal<>();

    public static void set(Long tenantId, Long userId, String roleType) { ... }
    public static Long getTenantId() { ... }
    public static void clear() { HOLDER.remove(); }
}

// 2. Web 拦截器基类
public abstract class AbstractTenantInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        TenantInfo info = resolveTenant(request);  // 子类实现解析逻辑
        TenantContext.set(info);
        return true;
    }

    @Override
    public void afterCompletion(...) {
        TenantContext.clear();
    }

    protected abstract TenantInfo resolveTenant(HttpServletRequest request);
}

// 3. 自动 WHERE 注入（集成 MyBatis Plus TenantLineInnerInterceptor）
// 所有查询自动追加 tenant_id 条件，无需业务代码手动处理
```

这样 DataChat 只需实现 `resolveTenant()` 方法即可，不再需要手动写拦截器和 WHERE 过滤。

---

## I2. CurrentUserProvider 与 Security 模块断裂

**严重程度**：Important — 审计数据正确性
**涉及文件**：
- `fast-ddd-crud/.../provider/CurrentUserProvider.java`
- `fast-ddd-crud/.../provider/DefaultCurrentUserProvider.java`
- `fast-ddd-security-starter/.../spi/SecurityIdentityService.java`

### 问题描述

CRUD 模块的 `AutoFillMetaObjectHandler` 依赖 `CurrentUserProvider` 来自动填充 `createUser`/`updateUser`。默认实现永远返回 `"system"`。

Security 模块有完整的 JWT 认证链（`JwtTokenProvider` → `JwtAuthenticationFilter` → `SecurityContextHolder`），但没有提供桥接到 `CurrentUserProvider` 的实现。

结果：所有启用了安全模块的项目，`createUser`/`updateUser` 字段始终是 `"system"`，审计信息无效。

### 建议方案

在 `fast-ddd-security-starter` 中提供一个自动桥接实现：

```java
package com.gallop.fastddd.security.provider;

@Component
@ConditionalOnClass(name = "com.gallop.fastddd.crud.provider.CurrentUserProvider")
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }
}
```

这样引入 `fast-ddd-security-starter` 后，审计字段自动正确填充，零配置。

---

## I3. SecurityUserDetails 不携带权限

**严重程度**：Important — 授权功能
**涉及文件**：`fast-ddd-security-starter/.../spi/SecurityUserDetails.java`

### 问题描述

`SecurityUserDetails.getAuthorities()` 硬编码返回空列表：

```java
@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.emptyList();
}
```

框架有 `SecurityPermissionService` SPI 可以查询用户权限，但只在 `AuthController` 的 refresh token 流程中手动映射为 `SimpleGrantedAuthority`。登录流程中 `Authentication` token 不携带任何 authorities。

导致：
- `@PreAuthorize("hasRole('ADMIN')")` 等 Spring Security 注解不生效
- `SecurityContextHolder.getContext().getAuthentication().getAuthorities()` 始终为空
- 无法在 Controller 层使用声明式权限控制

### 建议方案

**方案 A（推荐）**：在 `SecurityUserDetails` 中增加 `authorities` 字段：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityUserDetails implements UserDetails {
    private Long userId;
    private String username;
    private String password;
    private boolean enabled;
    private Collection<? extends GrantedAuthority> authorities;  // ← 新增

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities != null ? authorities : Collections.emptyList();
    }
}
```

在 `AuthController` 登录流程中，通过 `SecurityPermissionService` 加载权限并传入：

```java
List<String> permissions = securityPermissionService.getPermissionsByUserId(user.getUserId());
List<SimpleGrantedAuthority> authorities = permissions.stream()
        .map(SimpleGrantedAuthority::new)
        .toList();
SecurityUserDetails details = SecurityUserDetails.builder()
        .userId(user.getUserId())
        .username(user.getUsername())
        .password(user.getPassword())
        .enabled(user.isEnabled())
        .authorities(authorities)  // ← 携带权限
        .build();
```

**方案 B**：提供 `AuthoritiesLoader` 接口，延迟加载：

```java
public interface AuthoritiesLoader {
    Collection<? extends GrantedAuthority> loadAuthorities(Long userId);
}
```

---

## I4. 双重响应类型 `ApiResponse` 与 `Result` 并存

**严重程度**：Important — 代码一致性
**涉及文件**：
- `fast-ddd-web/.../response/ApiResponse.java`
- `fast-ddd-common/.../result/Result.java`

### 问题描述

框架中存在两个功能重叠的响应类型：

| 特性 | `ApiResponse<T>` | `Result<T>` |
|------|-------------------|-------------|
| 模块 | fast-ddd-web | fast-ddd-common |
| 时间戳 | `Long` (millis) | `LocalDateTime` |
| 成功标识 | `code == 200` | `success: Boolean` |
| 工厂方法 | `success()` / `error()` | `success()` / `failure()` |
| 自动包装 | `SmartResponseAdvice` | 无 |

使用者经常困惑该用哪个，以及为什么需要两个。

### 建议方案

1. **废弃 `Result<T>`**，标记 `@Deprecated`，统一使用 `ApiResponse<T>`
2. 或者在 `ApiResponse` 中增加 `success` 字段，兼容两种使用习惯
3. 如果保留两个，在文档中明确说明各自的适用场景

---

## I5. 软删除与唯一索引无感知

**严重程度**：Important — 数据完整性
**涉及文件**：`fast-ddd-model/.../BasePO.java`（`deleted` 字段）

### 问题描述

`BasePO.deleted` 使用 `@TableLogic` 实现软删除，MyBatis Plus 自动在查询时追加 `WHERE deleted = 0`。但数据库层面的唯一索引不感知软删除：

- 唯一索引 `UNIQUE(username)` 在 `deleted=1` 的记录也会冲突
- 同一个 username 被删除后，无法重新创建

框架没有提供任何工具或指导。

### 建议方案

**方案 A（工具方法）**：在 `BaseRepository` 中增加唯一性检查方法：

```java
/**
 * 检查字段值在未删除记录中是否唯一（已排除软删除记录）
 */
default boolean isUniqueAmongActive(String field, Object value) {
    return countByField(field, value) == 0;  // MyBatis Plus 自动追加 deleted=0
}
```

**方案 B（文档指导）**：在框架文档中增加"软删除与唯一索引"章节：

```sql
-- PostgreSQL: 使用 partial unique index
CREATE UNIQUE INDEX idx_user_username ON dc_user(username) WHERE deleted = 0;

-- MySQL: 使用虚拟列（8.0+）
ALTER TABLE dc_user ADD COLUMN active_username VARCHAR(100) 
    GENERATED ALWAYS AS (IF(deleted = 0, username, NULL)) STORED;
CREATE UNIQUE INDEX idx_active_username ON dc_user(active_username);
```

**方案 C（代码生成器）**：在代码生成器中，当检测到唯一索引时，自动生成 partial index 的 SQL migration 脚本。

---

## I6. BaseServiceImpl 字段注入 + batchSize 被忽略

**严重程度**：Minor — 代码质量
**涉及文件**：`fast-ddd-crud/.../service/impl/BaseServiceImpl.java`

### 问题描述

1. **字段注入**：使用 `@Autowired protected M baseMapper` 而非构造器注入，不利于测试和不可变性
2. **batchSize 被忽略**：`saveBatch(Collection, int batchSize)` 的 `batchSize` 参数被完全忽略，始终一次性全量插入

```java
// 当前
@Transactional(rollbackFor = Exception.class)
@Override
public boolean saveBatch(Collection<T> entityList, int batchSize) {
    baseMapper.batchInsert((List<T>) entityList);  // batchSize 被忽略
    return true;
}
```

### 建议方案

1. 将 `baseMapper` 改为构造器注入（如果是 Spring 4.3+，单一构造器自动注入）
2. 实现 batchSize 分批插入：

```java
@Transactional(rollbackFor = Exception.class)
@Override
public boolean saveBatch(Collection<T> entityList, int batchSize) {
    List<T> list = entityList instanceof List ? (List<T>) entityList : new ArrayList<>(entityList);
    for (int i = 0; i < list.size(); i += batchSize) {
        int end = Math.min(i + batchSize, list.size());
        baseMapper.batchInsert(list.subList(i, end));
    }
    return true;
}
```

---

## 优先级汇总

| # | 建议 | 严重程度 | 改动难度 | Breaking Change |
|---|------|---------|---------|----------------|
| C1 | `updateById` 设置 path ID 到 entity | Critical | 极低 | 否 |
| C2 | `BasePO` 内联 `id` 字段 | Critical | 低 | 是（子类需删除 id） |
| C3 | BaseController Hook 方法 | Critical | 低 | 否（新增方法） |
| C4 | 统一返回类型 | Important | 低 | 是（前端适配） |
| I1 | 多租户基础设施 | Important | 中 | 否（新模块） |
| I2 | CurrentUserProvider 桥接 | Important | 低 | 否 |
| I3 | SecurityUserDetails 携带权限 | Important | 低 | 否 |
| I4 | 统一 Response 类型 | Important | 低 | 是（废弃 Result） |
| I5 | 软删除唯一性指导 | Important | 低 | 否 |
| I6 | 构造器注入 + batchSize | Minor | 低 | 否 |

### 建议实施顺序

1. **立即修复**：C1（Bug 修复，无风险）
2. **短期**：C2 + C3（显著提升开发体验，ROI 最高）
3. **中期**：I2 + I3（安全模块完善）
4. **长期**：I1（多租户新模块）
5. **按需**：C4、I4、I5、I6

---

## 附录：DataChat 项目实战背景

DataChat 是基于 FastDDD 构建的 AI 数据分析平台，包含以下模块：

- **dc-admin-api**：Spring Boot 入口，安全认证，项目/用户管理
- **dc-indicator**：指标库（Rich Model，CQRS）
- **dc-chat**：AI 对话（Rich Model，CQRS + WebSocket）
- **dc-dataset/dc-datasource/dc-knowledge**：数据管理（Anemic Model）
- **dc-frontend**：Vue 3 + Element Plus 前端

在实现用户认证、项目隔离、角色权限的过程中，暴露了上述框架层面的优化点。这些建议均来自真实业务场景的切身体会，而非理论推演。
