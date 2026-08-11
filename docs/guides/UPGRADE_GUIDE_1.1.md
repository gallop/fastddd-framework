# FastDDD 1.0 → 1.1 框架升级指引

> 本文档指导依赖 FastDDD 框架（`1.0.0-SNAPSHOT`）的业务项目升级到优化后版本的完整流程。
> 升级前请完整阅读，按顺序执行各项检查和迁移。

---

## 变更概览

| 类别 | 数量 | 影响范围 |
|------|------|---------|
| Breaking Change | 5 项 | 编译失败或运行时行为变更 |
| 配置变更 | 2 项 | 应用启动失败或功能异常 |
| 废弃 API | 2 项 | 编译警告，2.0 移除 |
| 新增功能 | 3 项 | 可选采用 |
| 安全修复 | 1 项 | 强烈建议升级 |

---

## 一、Breaking Changes（必须处理）

### 1.1 BasePO 内联 id 字段

**变更内容**：`BasePO` 新增 `protected Serializable id` 字段及 `getId()`/`setId()` 方法，所有子类无需再自行声明。

**影响**：如果业务项目的 PO 类自行声明了 `id` 字段、`@TableId` 注解或覆写了 `getId()`/`setId()`，会导致编译错误（字段重复声明）。

**迁移步骤**：

```diff
 // 修改前
 @Data
 @EqualsAndHashCode(callSuper = true)
 @TableName("t_order")
 public class OrderPO extends BasePO {
-    @TableId(type = IdType.ASSIGN_ID)
-    private Long id;
-
-    @Override
-    public Serializable getId() { return id; }
-
-    @Override
-    public void setId(Serializable id) { this.id = id; }

     private String orderNo;
     private BigDecimal amount;
 }

 // 修改后
 @Data
 @EqualsAndHashCode(callSuper = true)
 @TableName("t_order")
 public class OrderPO extends BasePO {
     private String orderNo;
     private BigDecimal amount;
 }
```

**要点**：

- 删除子类中的 `private Long id`（或 `private String id`）字段声明
- 删除 `@TableId(...)` 注解
- 删除 `getId()` 和 `setId()` 覆写方法
- ID 策略由全局配置 `mybatis-plus.global-config.db-config.id-type` 控制（默认 `ASSIGN_ID` 雪花算法）
- `getId()` 返回 `Serializable`，如需具体类型请强转：`(Long) entity.getId()`

**自查命令**：

```bash
# 检查项目中是否有 PO 类重复声明了 id 字段
grep -rn "private.*id;" --include="*.java" src/main/java/ | grep -v "userId\|orderId\|itemId"
grep -rn "@TableId" --include="*.java" src/main/java/
```

### 1.2 BaseController 移除 4 个危险端点

**变更内容**：以下 4 个 REST 端点从 `BaseController` 中移除：

| 移除的端点 | HTTP 方法 | 路径 | 移除原因 |
|-----------|----------|------|---------|
| `listByField` | GET | `/field/{field}/{value}` | 字段名直接传入 QueryWrapper，存在注入风险 |
| `listByFields` | POST | `/fields` | 同上 |
| `countByField` | GET | `/count/{field}/{value}` | 同上 |
| `existsByField` | GET | `/exists/{field}/{value}` | 同上 |

**影响**：如果前端或客户端调用了这些端点，将收到 404。

**迁移方案**：

场景 A — 简单字段查询（推荐）：

```java
// 在你的 Controller 中新增专用端点
@GetMapping("/search/by-name")
public ResponseEntity<ApiResponse<List<Order>>> listByName(
        @RequestParam String name) {
    List<Order> list = service.listByField("name", name);
    return ResponseEntity.ok(ApiResponse.success(list));
}
```

> `IService.listByField()`、`IService.listByFields()`、`IService.existsByField()`、`IService.countByField()` 方法在 Service 层仍然可用。仅 Controller 层的通用 HTTP 端点被移除。

场景 B — 多条件查询：

```java
@PostMapping("/search")
public ResponseEntity<ApiResponse<List<Order>>> search(
        @RequestBody Map<String, Object> conditions) {
    // 在 Service 层使用，字段名由开发者控制
    List<Order> list = service.listByFields(conditions);
    return ResponseEntity.ok(ApiResponse.success(list));
}
```

**自查命令**：

```bash
# 检查前端是否调用了被移除的端点
grep -rn "/field/\|/fields\|/count/\|/exists/" --include="*.ts" --include="*.tsx" --include="*.vue" --include="*.js"
```

### 1.3 delete() 返回类型变更

**变更内容**：`delete()` 端点返回类型从 `ResponseEntity<Void>`（HTTP 204）变更为 `ResponseEntity<ApiResponse<Boolean>>`（HTTP 200）。

| | 修改前 | 修改后 |
|---|--------|--------|
| HTTP Status | 204 No Content | 200 OK |
| Body | 空 | `{ "code": 200, "data": true, "message": "..." }` |

**影响**：前端 DELETE 请求的响应处理逻辑需要适配。

**迁移步骤**：

```diff
 // 前端调用方
- const response = await fetch(`/api/orders/${id}`, { method: 'DELETE' });
- if (response.status === 204) { /* 成功 */ }
+ const response = await fetch(`/api/orders/${id}`, { method: 'DELETE' });
+ const result = await response.json();
+ if (result.code === 200) { /* 成功 */ }
```

### 1.4 批量操作返回类型变更

**变更内容**：`createBatch`、`updateBatch`、`deleteBatch` 的 `data` 字段从描述字符串改为 `Boolean`。

| 端点 | 修改前 data | 修改后 data |
|------|-----------|-----------|
| `POST /batch` | `"批量创建成功"` | `true` |
| `PUT /batch` | `"批量更新成功"` | `true` |
| `DELETE /batch` | `"批量删除成功"` | `true` |

**影响**：如果前端解析了 `data` 字段的具体字符串值，需要改为检查 `data === true` 或读取 `message` 字段。

**迁移步骤**：

```diff
 // 前端调用方
  const result = await response.json();
- if (result.data === '批量创建成功') { ... }
+ if (result.code === 200 && result.data === true) { ... }
+ // 描述信息已移至 result.message 字段
```

### 1.5 updateById Bug 修复

**变更内容**：`BaseController.updateById()` 现在会将 URL 路径中的 `id` 强制设置到请求体对象上（`entity.setId(id)`），确保路径参数与实体 ID 始终一致。

**影响**：这是一个 Bug 修复。之前 `PUT /api/orders/{id}` 请求体中的 `id` 字段可能与路径不一致，现在路径 `id` 始终覆盖请求体中的 `id`。

**迁移步骤**：通常无需改动。如果业务逻辑刻意依赖请求体 `id` 与路径 `id` 不一致的边界情况（不应该存在），需要调整。

---

## 二、配置变更（必须处理）

### 2.1 属性前缀统一：`fast-ddd` → `fastddd`

**变更内容**：所有 `@ConfigurationProperties` 的前缀从 `fast-ddd.*` 统一改为 `fastddd.*`。

**影响的配置项**：

| 旧前缀 | 新前缀 |
|--------|--------|
| `fast-ddd.ai.enabled` | `fastddd.ai.enabled` |
| `fast-ddd.security.jwt.*` | `fastddd.security.jwt.*` |
| `fast-ddd.security.cors.*` | `fastddd.security.cors.*` |
| `fast-ddd.security.http.*` | `fastddd.security.http.*` |

**迁移步骤**：

```diff
 # application.yml
-fast-ddd:
-  ai:
-    enabled: true
-  security:
-    jwt:
-      secret: your-secret-key
-      expiration: 3600000

 fastddd:
+  ai:
+    enabled: true
   security:
     jwt:
       secret: your-secret-key
       expiration: 3600000
```

**注意**：如果项目中同时存在 `fast-ddd:` 和 `fastddd:` 两个顶级块，需合并为单一的 `fastddd:` 块，否则后者覆盖前者导致部分配置丢失。

**自查命令**：

```bash
grep -rn "fast-ddd:" --include="*.yml" --include="*.yaml" --include="*.properties" src/
```

### 2.2 MyBatis-Plus ID 策略建议配置

**新增建议**：由于 `BasePO.id` 不再使用 `@TableId` 注解（子类无需声明），建议在全局配置中明确 ID 生成策略：

```yaml
mybatis-plus:
  global-config:
    db-config:
      id-type: assign_id    # 雪花算法（推荐，默认值）
      # id-type: auto       # 数据库自增
      # id-type: input      # 手动输入
```

> `BasePO.id` 为强类型 `Long` 且**不**携带字段级 `@TableId`，ID 策略唯一由全局配置 `id-type`（默认 `assign_id`）决定。如果业务需要不同策略，子类自行重声明 `id` 字段并指定 `@TableId(type=...)`。

---

## 三、废弃 API（编译警告）

### 3.1 Result 类废弃

**变更内容**：`com.gallop.fastddd.common.result.Result` 标记为 `@Deprecated(forRemoval = true, since = "1.1.0")`，将在 2.0 版本移除。

**替代类**：`com.gallop.fastddd.web.response.ApiResponse`

**迁移对照**：

```diff
-import com.gallop.fastddd.common.result.Result;
+import com.gallop.fastddd.web.response.ApiResponse;

 // 成功响应
-Result.success(data)
+ApiResponse.success(data)

-Result.success(data, "操作成功")
+ApiResponse.success(data, "操作成功")

 // 失败响应
-Result.failure("操作失败")
+ApiResponse.error("操作失败")

-Result.failure(400, "参数错误")
+ApiResponse.error(400, "参数错误")
```

**结构差异**：

| 字段 | Result (废弃) | ApiResponse |
|------|--------------|-------------|
| `timestamp` | `LocalDateTime` | `Long`（毫秒时间戳） |
| `success` | `Boolean` 字段 | 无（通过 `code == 200` 判断） |
| 判断成功 | `result.isSuccess()` | `result.isSuccess()` |

**注意**：`Result` 类在 1.1 版本仍然可用，只是会产生编译警告。建议在 2.0 发布前完成迁移。

### 3.2 BaseController 废弃

**变更内容**：`BaseController<S, T>` 标记为 `@Deprecated(since = "1.1", forRemoval = false)`，改为继承 `BaseAdminCrudController` 的空壳类。

**替代方案**：

| 场景 | 替代基类 | 说明 |
|------|---------|------|
| 后台管理完整 CRUD | `BaseAdminCrudController<S, T>` | 与原 `BaseController` 行为一致，命名带 Admin 语义 |
| 只读查询 | `BaseReadOnlyController<S, T>` | 仅暴露 GET 端点，无写操作风险 |
| 敏感业务 API | 不继承任何 CRUD 基类 | 显式声明每个端点 |

**迁移步骤**：

```diff
 // 后台管理资源（推荐迁移）
 @RestController
-@RequestMapping("/api/users")
-public class UserController extends BaseController<UserService, User> {
+@RequestMapping("/api/admin/users")
+public class UserAdminController extends BaseAdminCrudController<UserService, User> {
 }

 // 只读资源（推荐迁移）
 @RestController
 @RequestMapping("/api/dictionaries")
-public class DictionaryController extends BaseController<DictionaryService, DictionaryPO> {
+public class DictionaryController extends BaseReadOnlyController<DictionaryService, DictionaryPO> {
 }
```

**注意**：`BaseAdminCrudController` 和 `BaseReadOnlyController` 本身不带 `@RestController`，具体子类需要自己加。

**影响**：现有继承 `BaseController` 的代码**无需修改**，编译和运行均不受影响，仅产生 IDE 警告。

---

## 四、新增功能（可选采用）

### 4.1 分层 Controller 基类

**新增内容**：`BaseController` 拆分为分层结构：

```
AbstractBaseController              ← 基础设施（service、resolveId），无端点
├── BaseReadOnlyController          ← 只读查询（GET /{id}、GET /、GET /page）
│   └── BaseAdminCrudController     ← 完整 CRUD + 8 个 lifecycle hooks
│       └── BaseController          ← @Deprecated 兼容入口
```

**使用建议**：

- 后台管理 CRUD → `BaseAdminCrudController`（路径建议 `/api/admin/**`）
- 只读查询 → `BaseReadOnlyController`
- 敏感资源 → 不继承 CRUD 基类，显式声明端点

### 4.2 BaseController 生命周期 Hook

**新增内容**：`BaseController` 新增 8 个 `protected` 空方法，子类可按需覆写：

```java
// 单个操作 Hook
protected void beforeCreate(T entity) {}
protected void afterCreate(T entity) {}
protected void beforeUpdate(Serializable id, T entity) {}
protected void afterUpdate(Serializable id, T entity) {}
protected void beforeDelete(Serializable id) {}
protected void afterDelete(Serializable id) {}

// 批量操作 Hook
protected void beforeCreateBatch(List<T> entityList) {}
protected void beforeUpdateBatch(List<T> entityList) {}
```

**使用示例**：

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController extends BaseController<OrderService, OrderPO> {

    @Override
    protected void beforeCreate(OrderPO entity) {
        // 参数校验、权限检查、审计字段填充
        entity.setOrderNo(generateOrderNo());
    }

    @Override
    protected void afterDelete(Serializable id) {
        // 清理关联数据、发送通知
        eventPublisher.publishEvent(new OrderDeletedEvent(id));
    }
}
```

**要点**：

- Hook 运行在 Controller 层（`@Transactional` 外部）
- 适用于参数校验、权限检查、审计字段填充等非事务场景
- 需要事务保证的逻辑应在 Service 层实现

### 4.3 SecurityCurrentUserProvider 自动桥接

**新增内容**：当项目同时引入 `fast-ddd-security-starter` 和 `fast-ddd-crud` 时，框架自动桥接 Spring Security 认证信息到 CRUD 模块的 `CurrentUserProvider`。

- 已认证用户：`createUser`/`updateUser` 自动填充为当前登录用户名
- 未认证请求（如定时任务）：回退为 `"system"`

**使用方式**：无需任何配置，引入两个依赖后自动生效。

```xml
<!-- pom.xml 中同时引入即可 -->
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-crud</artifactId>
</dependency>
<dependency>
    <groupId>com.gallop.fastddd</groupId>
    <artifactId>fast-ddd-security-starter</artifactId>
</dependency>
```

---

## 五、安全修复（强烈建议）

### 5.1 SecurityUserDetails 安全加固

**变更内容**：

- `password` 字段不再出现在 `toString()` 输出中（防止日志泄露）
- `equals()`/`hashCode()` 仅基于 `username`（避免敏感字段参与哈希计算）
- `enabled` 默认值从 `false` 改为 `true`（与 Spring Security `User` 默认行为一致）
- `authorities` 支持 `null` 安全（`getAuthorities()` 返回空列表而非 NPE）

**影响**：

- 如果自定义了 `SecurityUserDetails.builder()` 调用，无需改动
- 如果显式设置了 `.enabled(false)`，行为不变
- 如果依赖了 `toString()` 输出中的密码（不应该），密码字段将显示为 `"<SUPPRESSED>"`

---

## 六、升级检查清单

按顺序逐项确认：

### 第一步：升级前准备

- [ ] 确认当前 FastDDD 版本为 `1.0.0-SNAPSHOT`
- [ ] 创建功能分支用于升级改动
- [ ] 全量运行现有测试并记录基线结果

### 第二步：依赖升级

- [ ] 更新 FastDDD 版本到新的 `1.0.0-SNAPSHOT`（重新 `mvn install`）
- [ ] 确认 `mybatis-plus.global-config.db-config.id-type` 已配置

### 第三步：代码迁移

- [ ] **BasePO 子类**：移除所有 PO 类中重复的 `id` 字段、`@TableId`、`getId()`/`setId()`
- [ ] **Result → ApiResponse**：全局替换 `Result<T>` 为 `ApiResponse<T>`（非强制，但建议）
- [ ] **Controller**：移除对被删除端点（`listByField`、`listByFields`、`countByField`、`existsByField`）的调用
- [ ] **前端适配**：DELETE 返回值从 204 改为 200 + JSON body
- [ ] **前端适配**：批量操作 `data` 字段从字符串改为 `Boolean`

### 第四步：配置迁移

- [ ] YAML 配置中 `fast-ddd:` 前缀全部改为 `fastddd:`
- [ ] 合并分散的 `fast-ddd:` 和 `fastddd:` 块为单一 `fastddd:` 根
- [ ] properties 文件中 `fast-ddd.*` 改为 `fastddd.*`
- [ ] 测试类中 `@TestPropertySource` 的属性引用同步更新

### 第五步：验证

- [ ] `mvn clean compile` 编译通过
- [ ] `mvn test` 全量测试通过
- [ ] 启动应用验证 REST API 正常响应
- [ ] 验证 JWT 认证和审计日志功能正常（如果使用 Security 模块）
- [ ] 验证 AI 功能正常（如果使用 AI 模块）

### 第六步：可选优化

- [ ] 将 `BaseController` 子类迁移为 `BaseAdminCrudController` 或 `BaseReadOnlyController`
- [ ] 管理端 CRUD 路径调整到 `/api/admin/**`
- [ ] 按需覆写 lifecycle Hook
- [ ] 确认 SecurityCurrentUserProvider 自动桥接生效（查看 `createUser` 字段值）

---

## 七、版本兼容性矩阵

| FastDDD 版本 | Spring Boot | MyBatis-Plus | Java | 本次升级最低要求 |
|-------------|-------------|-------------|------|--------------|
| 1.0.0-SNAPSHOT | 3.4.x | 3.5.9+ | 17+ | 即当前版本 |
| 升级后 | 3.4.x | 3.5.9+ | 17+ | 无外部依赖版本变更 |

> 本次升级不涉及 Spring Boot、MyBatis-Plus 或 Java 版本变更，仅为框架内部优化。

---

## 八、常见问题

### Q1：子类需要 String 类型的 id 怎么办？

`BasePO.id` 类型为 `Serializable`，兼容 `Long` 和 `String`。不需要特殊处理，MyBatis-Plus 的 `ASSIGN_ID` 策略生成 `Long` 类型 ID。如果数据库使用 UUID 字符串主键，子类可重新声明：

```java
@TableId(type = IdType.ASSIGN_UUID)
private String id;
```

### Q2：getId() 返回 Serializable，使用不方便？

强转即可：`(Long) entity.getId()` 或 `(String) entity.getId()`。这与升级前的 `abstract Serializable getId()` 契约一致。

### Q3：项目中没有使用 Security 模块，需要改动吗？

不需要。`SecurityCurrentUserProvider` 仅在 classpath 包含 `fast-ddd-security-starter` 时才加载。未使用安全模块的项目继续使用 `DefaultCurrentUserProvider`（返回 `"system"`）。

### Q4：模板生成的代码会自动适配吗？

是的。如果你使用 FastDDD 代码生成器（Maven Plugin 或 Studio），升级框架后重新生成的代码已包含所有变更：
- PO 类不再生成 `id` 字段和 `@TableId`
- DTO/Request 类已排除 BasePO 审计字段

**注意**：已生成的代码不会自动更新。需要手动迁移或删除后重新生成。

### Q5：配置前缀 `fast-ddd` 在 Spring Boot 中不是等效的吗？

Spring Boot 支持 Relaxed Binding（宽松绑定），`fast-ddd` 和 `fastddd` 在 YAML 属性解析时确实等效。但 `@ConfigurationProperties(prefix = "...")` 中的 `prefix` 必须精确匹配。本次变更将 Java 注解中的 `prefix` 从 `fast-ddd` 改为 `fastddd`，因此 YAML 中的 key 也需要同步修改。
