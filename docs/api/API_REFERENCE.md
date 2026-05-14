# FastDDD API 参考文档

## 目录

- [1. 应用层 API](#1-应用层-api)
- [2. 领域层 API](#2-领域层-api)
- [3. 基础设施层 API](#3-基础设施层-api)
- [4. 接口层 API](#4-接口层-api)
- [5. 事件 API](#5-事件-api)

---

## 1. 应用层 API

应用层提供 CQRS 模式的核心接口。

### 1.1 @ApplicationService 注解

**位置**: `com.gallop.fastddd.application.annotation.ApplicationService`

用于标记应用服务类，区别于领域服务和基础设施服务。

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface ApplicationService {
    /** 服务描述信息 */
    String value() default "";

    /** 关联的聚合根类型 */
    Class<?> aggregate() default Object.class;
}
```

**使用示例**:
```java
@ApplicationService(aggregate = Order.class)
public class OrderApplicationService {
    // ...
}
```

---

### 1.2 Command 基类

**位置**: `com.gallop.fastddd.application.command.Command`

所有写操作的请求对象基类。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getCommandType()` | String | 获取命令类型（类名） |
| `isValid()` | boolean | 验证命令是否有效 |
| `getCreatedAt()` | Instant | 命令创建时间 |
| `getMetadata()` | CommandMetadata | 命令元数据 |

**元数据字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| `correlationId` | String | 关联ID（用于追踪请求链） |
| `causationId` | String | 因果ID（表示触发当前命令的命令） |
| `userId` | String | 用户ID |
| `tenantId` | String | 租户ID |

---

### 1.3 CommandHandler 接口

**位置**: `com.gallop.fastddd.application.command.CommandHandler`

命令处理器函数式接口。

```java
@FunctionalInterface
public interface CommandHandler<C extends Command, R> {
    /**
     * 处理命令
     * @param command 命令对象
     * @return 处理结果
     */
    R handle(C command);
}
```

**使用示例**:
```java
@Component
public class CreateOrderCommandHandler
    implements CommandHandler<CreateOrderCommand, OrderDto> {

    @Override
    public OrderDto handle(CreateOrderCommand command) {
        // 处理逻辑
    }
}
```

---

### 1.4 CommandResult 类

**位置**: `com.gallop.fastddd.application.command.CommandResult`

类型安全的命令执行结果封装。

| 静态方法 | 说明 |
|----------|------|
| `success()` | 创建成功结果（无数据） |
| `success(T data)` | 创建成功结果（带数据） |
| `success(String message, T data)` | 创建成功结果（带消息和数据） |
| `failure(String error)` | 创建失败结果 |

| 实例方法 | 返回类型 | 说明 |
|----------|----------|------|
| `isSuccess()` | boolean | 是否成功 |
| `getData()` | Optional\<T\> | 获取数据（可能为空） |
| `getMessage()` | String | 获取消息 |
| `getError()` | String | 获取错误信息 |

---

### 1.5 Query 基类

**位置**: `com.gallop.fastddd.application.query.Query`

所有读操作的请求对象基类。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getQueryType()` | String | 获取查询类型（类名） |

---

### 1.6 QueryHandler 接口

**位置**: `com.gallop.fastddd.application.query.QueryHandler`

查询处理器函数式接口。

```java
@FunctionalInterface
public interface QueryHandler<Q extends Query, R> {
    /**
     * 处理查询
     * @param query 查询对象
     * @return 查询结果
     */
    R handle(Q query);
}
```

---

### 1.7 CommandValidationException

**位置**: `com.gallop.fastddd.application.exception.CommandValidationException`

命令验证异常。

| 构造方法 | 说明 |
|----------|------|
| `CommandValidationException(String message)` | 创建验证异常 |
| `CommandValidationException(List<String> errors)` | 批量验证错误 |
| `CommandValidationException(String field, String message)` | 字段验证错误 |

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getErrors()` | List\<String\> | 获取所有验证错误 |

---

## 2. 领域层 API

领域层提供 DDD 核心模式基类。

### 2.1 BaseEntity\<ID\>

**位置**: `com.gallop.fastddd.common.domain.BaseEntity`

领域实体基类。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | ID | 实体唯一标识 |
| `createdAt` | LocalDateTime | 创建时间 |
| `updatedAt` | LocalDateTime | 更新时间 |
| `version` | Integer | 版本号（乐观锁） |
| `deleted` | Integer | 软删除标记 |

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getId()` | ID | 获取实体ID（抽象方法，需实现） |
| `setId(ID id)` | void | 设置实体ID（抽象方法，需实现） |

---

### 2.2 AggregateRoot\<ID\>

**位置**: `com.gallop.fastddd.common.domain.AggregateRoot`

聚合根基类，继承自 BaseEntity。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `addDomainEvent(AbstractDomainEvent event)` | void | 添加领域事件 |
| `removeDomainEvent(AbstractDomainEvent event)` | void | 移除领域事件 |
| `getDomainEvents()` | List\<AbstractDomainEvent\> | 获取所有领域事件（只读） |
| `clearDomainEvents()` | void | 清空领域事件 |

**使用示例**:
```java
public class Order extends AggregateRoot<Long> {
    public void pay(BigDecimal amount) {
        // 业务逻辑
        this.status = OrderStatus.PAID;

        // 发布事件
        addDomainEvent(new OrderPaidEvent(getId(), orderNo, amount));
    }
}
```

---

### 2.3 ValueObject

**位置**: `com.gallop.fastddd.common.domain.ValueObject`

值对象基类（标记接口）。

值对象特点：
- 不可变（Immutable）
- 通过字段值判断相等性
- 无生命周期标识

**使用示例**:
```java
public class Address extends ValueObject {
    private final String province;
    private final String city;
    private final String street;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equal(province, address.province) &&
               Objects.equal(city, address.city) &&
               Objects.equal(street, address.street);
    }
}
```

---

### 2.4 DomainEvent 接口（旧版，已弃用）

**位置**: `com.gallop.fastddd.common.domain.DomainEvent`

**@Deprecated** - 请使用 `com.gallop.fastddd.event.DomainEvent`

---

### 2.5 AbstractDomainEvent（旧版，已弃用）

**位置**: `com.gallop.fastddd.common.domain.AbstractDomainEvent`

**@Deprecated** - 请使用 `com.gallop.fastddd.event.DomainEvent`

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getEventId()` | String | 获取事件ID |
| `getAggregateId()` | Serializable | 获取聚合根ID |
| `getAggregateType()` | String | 获取聚合根类型 |
| `getEventType()` | String | 获取事件类型 |
| `getOccurredOn()` | LocalDateTime | 获取事件发生时间 |

---

## 3. 基础设施层 API

### 3.1 EntityRepository\<E, ID\>

**位置**: `com.gallop.fastddd.crud.entity.EntityRepository`

充血实体仓储接口。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `save(E entity)` | E | 保存实体 |
| `saveAndPublishEvents(E entity)` | E | 保存实体并发布领域事件 |
| `findById(ID id)` | Optional\<E\> | 根据ID查找 |
| `findAll()` | List\<E\> | 查找所有 |
| `deleteById(ID id)` | void | 根据ID删除 |
| `delete(E entity)` | void | 删除实体 |
| `existsById(ID id)` | boolean | 判断是否存在 |
| `count()` | long | 统计数量 |

**使用示例**:
```java
public interface OrderRepository extends EntityRepository<Order, Long> {
    // 扩展查询方法
    Optional<Order> findByOrderNo(String orderNo);
    List<Order> findByCustomerId(String customerId);
}
```

---

### 3.2 EntityMapper\<E, P\>

**位置**: `com.gallop.fastddd.crud.entity.EntityMapper`

Entity-PO 映射器接口（基于 MapStruct）。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `toEntity(P po)` | E | PO 转换为 Entity |
| `toPo(E entity)` | P | Entity 转换为 PO |
| `toEntityList(List\<P\> pos)` | List\<E\> | PO 列表批量转换 |
| `toPoList(List\<E\> entities)` | List\<P\> | Entity 列表批量转换 |

**使用示例**:
```java
@Mapper(componentModel = "spring")
public interface OrderMapper extends EntityMapper<Order, OrderPO> {

    // 自定义类型映射
    default OrderStatus intToOrderStatus(Integer status) {
        return status != null ? OrderStatus.fromCode(status) : null;
    }

    default Integer orderStatusToInt(OrderStatus status) {
        return status != null ? status.getCode() : null;
    }
}
```

---

### 3.3 IRepository\<T\>（贫血模型）

**位置**: `com.gallop.fastddd.crud.dao.IRepository**

基础 PO 仓储接口。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `insert(T entity)` | int | 插入实体 |
| `deleteById(Serializable id)` | int | 根据ID删除 |
| `updateById(T entity)` | int | 根据ID更新 |
| `selectById(Serializable id)` | T | 根据ID查询 |
| `selectList(Wrapper\<T\> wrapper)` | List\<T\> | 条件查询列表 |
| `selectPage(Page\<T\> page, Wrapper\<T\> wrapper)` | Page\<T\> | 分页查询 |

---

### 3.4 IBaseService\<T\>（贫血模型）

**位置**: `com.gallop.fastddd.crud.service.IBaseService`

基础 Service 接口。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `insert(T entity)` | boolean | 插入实体 |
| `deleteById(Serializable id)` | boolean | 根据ID删除 |
| `updateById(T entity)` | boolean | 根据ID更新 |
| `getById(Serializable id)` | T | 根据ID查询 |
| `list(Wrapper\<T\> wrapper)` | List\<T\> | 条件查询列表 |
| `page(Page\<T\> page, Wrapper\<T\> wrapper)` | Page\<T\> | 分页查询 |

---

## 4. 接口层 API

### 4.1 CommandController\<C, R\>

**位置**: `com.gallop.fastddd.web.controller.CommandController`

命令控制器基类。

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /` | `executeCommand(@Valid @RequestBody C command)` | 执行命令 |
| `POST /execute` | `executeCommand(@Valid @RequestBody C command)` | 执行命令（别名） |

| 继承方法 | 说明 |
|----------|------|
| `getCommandHandler()` | 获取命令处理器（需实现） |

**使用示例**:
```java
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {

    @Autowired
    private CreateOrderCommandHandler handler;

    @Override
    protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return handler;
    }
}
```

---

### 4.2 QueryController\<Q, R\>

**位置**: `com.gallop.fastddd.web.controller.QueryController`

查询控制器基类。

| 端点 | 方法 | 说明 |
|------|------|------|
| `POST /query` | `executeQuery(@Valid @RequestBody Q query)` | 执行查询 |
| `POST /find-one` | `findOne(@Valid @RequestBody Q query)` | 查询单个对象 |
| `POST /search` | `search(@Valid @RequestBody Q query)` | 搜索列表 |

| 继承方法 | 说明 |
|----------|------|
| `getQueryHandler()` | 获取查询处理器（需实现） |

**使用示例**:
```java
@RestController
@RequestMapping("/api/orders")
public class OrderQueryController extends QueryController<GetOrderByIdQuery, OrderDto> {

    @Autowired
    private GetOrderByIdQueryHandler handler;

    @Override
    protected QueryHandler<GetOrderByIdQuery, OrderDto> getQueryHandler() {
        return handler;
    }
}
```

---

### 4.3 Controller 基类层次（贫血模型）

框架提供分层 Controller 基类，按需选择：

```
AbstractBaseController              ← 基础设施（service、resolveId），无端点
├── BaseReadOnlyController          ← 只读查询（GET）
│   └── BaseAdminCrudController     ← 完整 CRUD（1.1 推荐使用）
│       └── BaseController          ← @Deprecated 兼容入口
```

#### BaseAdminCrudController\<S, T\>（推荐）

**位置**: `com.gallop.fastddd.web.controller.BaseAdminCrudController`

管理端完整 CRUD 控制器，包含 8 个 lifecycle hooks。

| 端点 | HTTP 方法 | 说明 |
|------|----------|------|
| `/{id}` | GET | 根据ID查询 |
| `/` | GET | 查询列表 |
| `/page` | GET | 分页查询 |
| `/` | POST | 创建 |
| `/batch` | POST | 批量创建 |
| `/{id}` | PUT | 根据ID更新 |
| `/` | PUT | 条件更新 |
| `/batch` | PUT | 批量更新 |
| `/{id}` | DELETE | 删除 |
| `/batch` | DELETE | 批量删除 |

**Lifecycle Hooks**（protected 空方法，子类按需覆写）：

| Hook | 触发时机 | 典型用途 |
|------|---------|---------|
| `beforeCreate(T)` | 创建前 | 参数校验、审计字段填充 |
| `afterCreate(T)` | 创建后 | 发送通知、事件发布 |
| `beforeUpdate(Serializable, T)` | 更新前 | 权限检查 |
| `afterUpdate(Serializable, T)` | 更新后 | 缓存清理 |
| `beforeDelete(Serializable)` | 删除前 | 关联检查 |
| `afterDelete(Serializable)` | 删除后 | 关联数据清理 |
| `beforeCreateBatch(List<T>)` | 批量创建前 | 参数预处理 |
| `beforeUpdateBatch(List<T>)` | 批量更新前 | 参数预处理 |

#### BaseReadOnlyController\<S, T\>

**位置**: `com.gallop.fastddd.web.controller.BaseReadOnlyController`

只暴露 GET 查询端点，适合字典、下拉选项、数据展示等只读场景。

| 端点 | HTTP 方法 | 说明 |
|------|----------|------|
| `/{id}` | GET | 根据ID查询 |
| `/` | GET | 查询列表 |
| `/page` | GET | 分页查询 |

#### BaseController\<S, T\>（@Deprecated）

**位置**: `com.gallop.fastddd.web.controller.BaseController`

> **已废弃**（since 1.1），继承自 `BaseAdminCrudController`，行为不变。新项目请使用 `BaseAdminCrudController` 或 `BaseReadOnlyController`。

---

### 4.4 ApiResponse\<T\>

**位置**: `com.gallop.fastddd.web.response.ApiResponse`

统一 API 响应格式。

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 响应码（200=成功） |
| `message` | String | 响应消息 |
| `data` | T | 响应数据 |
| `timestamp` | long | 响应时间戳 |

| 静态方法 | 说明 |
|----------|------|
| `success()` | 成功响应（无数据） |
| `success(T data)` | 成功响应（带数据） |
| `success(String message, T data)` | 成功响应（带消息和数据） |
| `error(String message)` | 错误响应 |

---

## 5. 事件 API

### 5.1 DomainEvent（新版）

**位置**: `com.gallop.fastddd.event.DomainEvent`

领域事件抽象类（继承 Spring ApplicationEvent）。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getAggregateId()` | String | 获取聚合根ID |
| `getAggregateType()` | String | 获取聚合根类型 |
| `getOccurredOn()` | LocalDateTime | 获取事件发生时间 |

---

### 5.2 EventPublisher

**位置**: `com.gallop.fastddd.event.EventPublisher`

事件发布器接口。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `publishEvent(DomainEvent event)` | void | 发布单个事件 |
| `publishEvents(Collection<? extends DomainEvent> events)` | void | 批量发布事件 |
| `publishEventAsync(DomainEvent event)` | void | 异步发布单个事件 |
| `publishEventsAsync(Collection<? extends DomainEvent> events)` | void | 异步批量发布事件 |
| `hasListeners(Class<? extends DomainEvent> eventType)` | boolean | 判断是否有监听器 |
| `getListenerCount(Class<? extends DomainEvent> eventType)` | int | 获取监听器数量 |

---

### 5.3 DomainEventBridge

**位置**: `com.gallop.fastddd.event.bridge.DomainEventBridge`

领域事件桥接器，将旧版 `AbstractDomainEvent` 桥接到新版 `DomainEvent`。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `publishDomainEvent(AbstractDomainEvent event)` | void | 发布单个领域事件 |
| `publishDomainEvents(Collection<? extends AbstractDomainEvent> events)` | void | 批量发布领域事件 |
| `publishDomainEventAsync(AbstractDomainEvent event)` | void | 异步发布单个领域事件 |
| `publishDomainEventsAsync(Collection<? extends AbstractDomainEvent> events)` | void | 异步批量发布领域事件 |

---

### 5.4 @EventListener

**位置**: `org.springframework.context.event.EventListener`

Spring 事件监听器注解。

**使用示例**:
```java
@Component
public class OrderEventHandler {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        // 处理订单创建事件
    }

    @EventListener
    @Async("taskExecutor")
    public void handleOrderPaid(OrderPaidEvent event) {
        // 异步处理订单支付事件
    }
}
```

---

## 附录：完整包结构

```
com.gallop.fastddd
├── application/                    # 应用层
│   ├── annotation/
│   │   └── ApplicationService.java
│   ├── command/
│   │   ├── Command.java
│   │   ├── CommandHandler.java
│   │   └── CommandResult.java
│   ├── query/
│   │   ├── Query.java
│   │   └── QueryHandler.java
│   └── exception/
│       └── CommandValidationException.java
├── common/                         # 公共模块
│   └── domain/
│       ├── BaseEntity.java
│       ├── AggregateRoot.java
│       ├── ValueObject.java
│       ├── DomainEvent.java (已弃用)
│       └── AbstractDomainEvent.java (已弃用)
├── crud/                           # CRUD模块
│   ├── dao/
│   │   └── IRepository.java
│   ├── service/
│   │   └── IBaseService.java
│   └── entity/
│       ├── EntityRepository.java
│       └── EntityMapper.java
├── event/                          # 事件模块
│   ├── DomainEvent.java
│   ├── EventPublisher.java
│   └── bridge/
│       ├── DomainEventBridge.java
│       └── DefaultDomainEventBridge.java
└── web/                            # Web模块
    ├── controller/
    │   ├── BaseController.java
    │   ├── CommandController.java
    │   └── QueryController.java
    └── response/
        └── ApiResponse.java
```

---

## 版本说明

- **框架版本**: 1.1.0-SNAPSHOT
- **Spring Boot**: 3.2.0+
- **Java**: 17+

**注意事项**:
1. `common.domain.DomainEvent` 和 `AbstractDomainEvent` 已标记为 `@Deprecated`
2. 建议使用 `DomainEventBridge` 进行事件发布
3. 充血模型和贫血模型可共存于同一项目
