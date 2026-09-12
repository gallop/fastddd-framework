# FastDDD API Reference

**English** | [简体中文](API_REFERENCE.zh-CN.md)

## Table of Contents

- [1. Application Layer API](#1-application-layer-api)
- [2. Domain Layer API](#2-domain-layer-api)
- [3. Infrastructure Layer API](#3-infrastructure-layer-api)
- [4. Interfaces Layer API](#4-interfaces-layer-api)
- [5. Event API](#5-event-api)

---

## 1. Application Layer API

The application layer provides the core interfaces of the CQRS pattern.

### 1.1 @ApplicationService Annotation

**Location**: `com.gallop.fastddd.application.annotation.ApplicationService`

Marks an application service class, distinguishing it from domain services and infrastructure services.

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

**Usage example**:
```java
@ApplicationService(aggregate = Order.class)
public class OrderApplicationService {
    // ...
}
```

---

### 1.2 Command Base Class

**Location**: `com.gallop.fastddd.application.command.Command`

Base class for the request objects of all write operations.

| Method | Return Type | Description |
|------|----------|------|
| `getCommandType()` | String | Returns the command type (class name) |
| `isValid()` | boolean | Checks whether the command is valid |
| `getCreatedAt()` | Instant | Time when the command was created |
| `getMetadata()` | CommandMetadata | Command metadata |

**Metadata fields**:
| Field | Type | Description |
|------|------|------|
| `correlationId` | String | Correlation ID (used for request-chain tracing) |
| `causationId` | String | Causation ID (the command that triggered this command) |
| `userId` | String | User ID |
| `tenantId` | String | Tenant ID |

---

### 1.3 CommandHandler Interface

**Location**: `com.gallop.fastddd.application.command.CommandHandler`

Functional interface for command handlers.

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

**Usage example**:
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

### 1.4 CommandResult Class

**Location**: `com.gallop.fastddd.application.command.CommandResult`

A type-safe wrapper for command execution results.

| Static Method | Description |
|----------|------|
| `success()` | Creates a success result (no data) |
| `success(T data)` | Creates a success result (with data) |
| `success(String message, T data)` | Creates a success result (with message and data) |
| `failure(String error)` | Creates a failure result |

| Instance Method | Return Type | Description |
|----------|----------|------|
| `isSuccess()` | boolean | Whether the execution succeeded |
| `getData()` | Optional\<T\> | Gets the data (may be empty) |
| `getMessage()` | String | Gets the message |
| `getError()` | String | Gets the error message |

---

### 1.5 Query Base Class

**Location**: `com.gallop.fastddd.application.query.Query`

Base class for the request objects of all read operations.

| Method | Return Type | Description |
|------|----------|------|
| `getQueryType()` | String | Returns the query type (class name) |

---

### 1.6 QueryHandler Interface

**Location**: `com.gallop.fastddd.application.query.QueryHandler`

Functional interface for query handlers.

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

**Location**: `com.gallop.fastddd.application.exception.CommandValidationException`

Command validation exception.

| Constructor | Description |
|----------|------|
| `CommandValidationException(String message)` | Creates a validation exception |
| `CommandValidationException(List<String> errors)` | Creates an exception from a batch of validation errors |
| `CommandValidationException(String field, String message)` | Creates an exception for a field validation error |

| Method | Return Type | Description |
|------|----------|------|
| `getErrors()` | List\<String\> | Gets all validation errors |

---

## 2. Domain Layer API

The domain layer provides base classes for the core DDD patterns.

### 2.1 BaseEntity\<ID\>

**Location**: `com.gallop.fastddd.common.domain.BaseEntity`

Base class for domain entities.

| Field | Type | Description |
|------|------|------|
| `id` | ID | Unique identifier of the entity |
| `createdAt` | LocalDateTime | Creation time |
| `updatedAt` | LocalDateTime | Last update time |
| `version` | Integer | Version number (optimistic lock) |
| `deleted` | Integer | Logical delete flag |

| Method | Return Type | Description |
|------|----------|------|
| `getId()` | ID | Gets the entity ID (abstract method, must be implemented) |
| `setId(ID id)` | void | Sets the entity ID (abstract method, must be implemented) |

---

### 2.2 AggregateRoot\<ID\>

**Location**: `com.gallop.fastddd.common.domain.AggregateRoot`

Base class for aggregate roots, extending BaseEntity.

| Method | Return Type | Description |
|------|----------|------|
| `addDomainEvent(AbstractDomainEvent event)` | void | Adds a domain event |
| `removeDomainEvent(AbstractDomainEvent event)` | void | Removes a domain event |
| `getDomainEvents()` | List\<AbstractDomainEvent\> | Gets all domain events (read-only) |
| `clearDomainEvents()` | void | Clears all domain events |

**Usage example**:
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

**Location**: `com.gallop.fastddd.common.domain.ValueObject`

Base class for value objects (marker interface).

Value object characteristics:
- Immutable
- Equality determined by field values
- No lifecycle identity

**Usage example**:
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

### 2.4 DomainEvent Interface (Legacy, Deprecated)

**Location**: `com.gallop.fastddd.common.domain.DomainEvent`

**@Deprecated** - Use `com.gallop.fastddd.event.DomainEvent` instead

---

### 2.5 AbstractDomainEvent (Legacy, Deprecated)

**Location**: `com.gallop.fastddd.common.domain.AbstractDomainEvent`

**@Deprecated** - Use `com.gallop.fastddd.event.DomainEvent` instead

| Method | Return Type | Description |
|------|----------|------|
| `getEventId()` | String | Gets the event ID |
| `getAggregateId()` | Serializable | Gets the aggregate root ID |
| `getAggregateType()` | String | Gets the aggregate root type |
| `getEventType()` | String | Gets the event type |
| `getOccurredOn()` | LocalDateTime | Gets the time the event occurred |

---

## 3. Infrastructure Layer API

### 3.1 EntityRepository\<E, ID\>

**Location**: `com.gallop.fastddd.crud.entity.EntityRepository`

Repository interface for rich-model entities.

| Method | Return Type | Description |
|------|----------|------|
| `save(E entity)` | E | Saves the entity |
| `saveAndPublishEvents(E entity)` | E | Saves the entity and publishes its domain events |
| `findById(ID id)` | Optional\<E\> | Finds an entity by ID |
| `findAll()` | List\<E\> | Finds all entities |
| `deleteById(ID id)` | void | Deletes an entity by ID |
| `delete(E entity)` | void | Deletes the entity |
| `existsById(ID id)` | boolean | Checks whether an entity exists |
| `count()` | long | Counts entities |

**Usage example**:
```java
public interface OrderRepository extends EntityRepository<Order, Long> {
    // 扩展查询方法
    Optional<Order> findByOrderNo(String orderNo);
    List<Order> findByCustomerId(String customerId);
}
```

---

### 3.2 EntityMapper\<E, P\>

**Location**: `com.gallop.fastddd.crud.entity.EntityMapper`

Entity-PO mapper interface (based on MapStruct).

| Method | Return Type | Description |
|------|----------|------|
| `toEntity(P po)` | E | Converts a PO to an Entity |
| `toPo(E entity)` | P | Converts an Entity to a PO |
| `toEntityList(List\<P\> pos)` | List\<E\> | Batch-converts a list of POs |
| `toPoList(List\<E\> entities)` | List\<P\> | Batch-converts a list of Entities |

**Usage example**:
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

### 3.3 IRepository\<T\> (Anemic Model)

**Location**: `com.gallop.fastddd.crud.dao.IRepository**

Basic PO repository interface.

| Method | Return Type | Description |
|------|----------|------|
| `insert(T entity)` | int | Inserts an entity |
| `deleteById(Serializable id)` | int | Deletes by ID |
| `updateById(T entity)` | int | Updates by ID |
| `selectById(Serializable id)` | T | Queries by ID |
| `selectList(Wrapper\<T\> wrapper)` | List\<T\> | Queries a list by condition |
| `selectPage(Page\<T\> page, Wrapper\<T\> wrapper)` | Page\<T\> | Paginated query |

---

### 3.4 IBaseService\<T\> (Anemic Model)

**Location**: `com.gallop.fastddd.crud.service.IBaseService`

Basic Service interface.

| Method | Return Type | Description |
|------|----------|------|
| `insert(T entity)` | boolean | Inserts an entity |
| `deleteById(Serializable id)` | boolean | Deletes by ID |
| `updateById(T entity)` | boolean | Updates by ID |
| `getById(Serializable id)` | T | Queries by ID |
| `list(Wrapper\<T\> wrapper)` | List\<T\> | Queries a list by condition |
| `page(Page\<T\> page, Wrapper\<T\> wrapper)` | Page\<T\> | Paginated query |

---

## 4. Interfaces Layer API

### 4.1 CommandController\<C, R\>

**Location**: `com.gallop.fastddd.web.controller.CommandController`

Base class for command controllers.

| Endpoint | Method | Description |
|------|------|------|
| `POST /` | `executeCommand(@Valid @RequestBody C command)` | Executes a command |
| `POST /execute` | `executeCommand(@Valid @RequestBody C command)` | Executes a command (alias) |

| Inherited Method | Description |
|----------|------|
| `getCommandHandler()` | Returns the command handler (must be implemented) |

**Usage example**:
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

**Location**: `com.gallop.fastddd.web.controller.QueryController`

Base class for query controllers.

| Endpoint | Method | Description |
|------|------|------|
| `POST /query` | `executeQuery(@Valid @RequestBody Q query)` | Executes a query |
| `POST /find-one` | `findOne(@Valid @RequestBody Q query)` | Queries a single object |
| `POST /search` | `search(@Valid @RequestBody Q query)` | Searches a list |

| Inherited Method | Description |
|----------|------|
| `getQueryHandler()` | Returns the query handler (must be implemented) |

**Usage example**:
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

### 4.3 Controller Base Class Hierarchy (Anemic Model)

The framework provides a layered set of Controller base classes; choose the one that fits your needs:

```
AbstractBaseController              ← 基础设施（service、resolveId），无端点
├── BaseReadOnlyController          ← 只读查询（GET）
│   └── BaseAdminCrudController     ← 完整 CRUD（1.1 推荐使用）
│       └── BaseController          ← @Deprecated 兼容入口
```

#### BaseAdminCrudController\<S, T\> (Recommended)

**Location**: `com.gallop.fastddd.web.controller.BaseAdminCrudController`

Full-CRUD controller for the admin side, with 8 lifecycle hooks.

| Endpoint | HTTP Method | Description |
|------|----------|------|
| `/{id}` | GET | Query by ID |
| `/` | GET | Query a list |
| `/page` | GET | Paginated query |
| `/` | POST | Create |
| `/batch` | POST | Batch create |
| `/{id}` | PUT | Update by ID |
| `/` | PUT | Update by condition |
| `/batch` | PUT | Batch update |
| `/{id}` | DELETE | Delete |
| `/batch` | DELETE | Batch delete |

**Lifecycle Hooks** (empty protected methods that subclasses override as needed):

| Hook | When Triggered | Typical Use |
|------|---------|---------|
| `beforeCreate(T)` | Before create | Parameter validation, audit field population |
| `afterCreate(T)` | After create | Send notifications, publish events |
| `beforeUpdate(Serializable, T)` | Before update | Permission checks |
| `afterUpdate(Serializable, T)` | After update | Cache invalidation |
| `beforeDelete(Serializable)` | Before delete | Referential integrity checks |
| `afterDelete(Serializable)` | After delete | Clean up related data |
| `beforeCreateBatch(List<T>)` | Before batch create | Parameter preprocessing |
| `beforeUpdateBatch(List<T>)` | Before batch update | Parameter preprocessing |

#### BaseReadOnlyController\<S, T\>

**Location**: `com.gallop.fastddd.web.controller.BaseReadOnlyController`

Exposes only GET query endpoints, making it suitable for read-only scenarios such as dictionaries, dropdown options, and data display.

| Endpoint | HTTP Method | Description |
|------|----------|------|
| `/{id}` | GET | Query by ID |
| `/` | GET | Query a list |
| `/page` | GET | Paginated query |

#### BaseController\<S, T\> (@Deprecated)

**Location**: `com.gallop.fastddd.web.controller.BaseController`

> **Deprecated** (since 1.1); extends `BaseAdminCrudController` with unchanged behavior. New projects should use `BaseAdminCrudController` or `BaseReadOnlyController`.

---

### 4.4 ApiResponse\<T\>

**Location**: `com.gallop.fastddd.web.response.ApiResponse`

Unified API response format.

| Field | Type | Description |
|------|------|------|
| `code` | int | Response code (200 = success) |
| `message` | String | Response message |
| `data` | T | Response data |
| `timestamp` | long | Response timestamp |

| Static Method | Description |
|----------|------|
| `success()` | Success response (no data) |
| `success(T data)` | Success response (with data) |
| `success(String message, T data)` | Success response (with message and data) |
| `error(String message)` | Error response |

---

## 5. Event API

### 5.1 DomainEvent (New Version)

**Location**: `com.gallop.fastddd.event.DomainEvent`

Abstract base class for domain events (extends Spring's ApplicationEvent).

| Method | Return Type | Description |
|------|----------|------|
| `getAggregateId()` | String | Gets the aggregate root ID |
| `getAggregateType()` | String | Gets the aggregate root type |
| `getOccurredOn()` | LocalDateTime | Gets the time the event occurred |

---

### 5.2 EventPublisher

**Location**: `com.gallop.fastddd.event.EventPublisher`

Event publisher interface.

| Method | Return Type | Description |
|------|----------|------|
| `publishEvent(DomainEvent event)` | void | Publishes a single event |
| `publishEvents(Collection<? extends DomainEvent> events)` | void | Publishes a batch of events |
| `publishEventAsync(DomainEvent event)` | void | Publishes a single event asynchronously |
| `publishEventsAsync(Collection<? extends DomainEvent> events)` | void | Publishes a batch of events asynchronously |
| `hasListeners(Class<? extends DomainEvent> eventType)` | boolean | Checks whether any listeners exist |
| `getListenerCount(Class<? extends DomainEvent> eventType)` | int | Gets the listener count |

---

### 5.3 DomainEventBridge

**Location**: `com.gallop.fastddd.event.bridge.DomainEventBridge`

Domain event bridge that adapts the legacy `AbstractDomainEvent` to the new `DomainEvent`.

| Method | Return Type | Description |
|------|----------|------|
| `publishDomainEvent(AbstractDomainEvent event)` | void | Publishes a single domain event |
| `publishDomainEvents(Collection<? extends AbstractDomainEvent> events)` | void | Publishes a batch of domain events |
| `publishDomainEventAsync(AbstractDomainEvent event)` | void | Publishes a single domain event asynchronously |
| `publishDomainEventsAsync(Collection<? extends AbstractDomainEvent> events)` | void | Publishes a batch of domain events asynchronously |

---

### 5.4 @EventListener

**Location**: `org.springframework.context.event.EventListener`

Spring's event listener annotation.

**Usage example**:
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

## Appendix: Complete Package Structure

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

## Version Notes

- **Framework Version**: 1.0.0
- **Spring Boot**: 3.5.16
- **Java**: 21+

**Notes**:
1. `common.domain.DomainEvent` and `AbstractDomainEvent` are marked `@Deprecated`
2. Using `DomainEventBridge` for event publishing is recommended
3. The rich model and the anemic model can coexist in the same project
