# FastDDD 双轨并行架构

> 支持贫血模型和充血模型并存的 DDD 架构设计

## 核心设计理念

FastDDD 采用**双轨并行架构**，允许开发者在同一项目中自由选择贫血模型或充血模型，实现渐进式 DDD 转型。

```
┌─────────────────────────────────────────────────────────────┐
│                    FastDDD 双轨并行架构                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  贫血模型轨道            充血模型轨道                         │
│  ┌──────────┐          ┌──────────┐                        │
│  │Controller│          │CommandController                   │
│  │(Base)    │          │QueryController                     │
│  └────┬─────┘          └────┬─────┘                        │
│       │                     │                               │
│  ┌────▼─────┐          ┌────▼──────┐                       │
│  │Service   │          │ApplicationService                │
│  │(IService)│          │(@ApplicationService)              │
│  └────┬─────┘          └────┬──────┘                       │
│       │                     │                               │
│  ┌────▼─────┐          ┌────▼──────┐                       │
│  │PO        │          │Entity                            │
│  │(BasePO)  │          │(AggregateRoot)                   │
│  └────┬─────┘          └────┬──────┘                       │
│       │                     │                               │
│  ┌────▼─────┐          ┌────▼──────┐                       │
│  │Mapper    │◄─────────┤EntityMapper                      │
│  │(MyBatis) │  转换     │(MapStruct)                       │
│  └──────────┘          └────┬──────┘                       │
│                              │                               │
│                        ┌─────▼─────┐                       │
│                        │  PO       │                       │
│                        │(BasePO)   │                       │
│                        └─────┬─────┘                       │
│                              │                               │
│                        ┌─────▼─────┐                       │
│                        │  Mapper   │                       │
│                        │(MyBatis)  │                       │
│                        └───────────┘                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 模块依赖关系

```
fast-ddd-common (核心领域基类)
    ↑
    ├── fast-ddd-model (数据模型)
    │
    ├── fast-ddd-crud (贫血模型CRUD)
    │   ├── BasePO
    │   ├── IService
    │   ├── EntityRepository (充血模型仓储接口)
    │   └── EntityMapper (实体-PO映射器)
    │
    ├── fast-ddd-application (应用层)
    │   ├── @ApplicationService
    │   ├── Command/Query
    │   └── CommandHandler/QueryHandler
    │
    ├── fast-ddd-web (Web层)
    │   ├── BaseController (贫血模型)
    │   ├── CommandController (充血模型)
    │   └── QueryController (充血模型)
    │
    └── fast-ddd-starter (自动配置)
```

---

## 分层架构

### 贫血模型四层架构

```
┌────────────────────────────────────┐
│  User Interface (Controller)       │  ← REST 端点
├────────────────────────────────────┤
│  Application (Service)              │  ← 用例编排
├────────────────────────────────────┤
│  Domain (PO)                       │  ← 数据载体（贫血）
├────────────────────────────────────┤
│  Infrastructure (Mapper)           │  ← MyBatis Plus
└────────────────────────────────────┘
```

### 充血模型四层架构

```
┌────────────────────────────────────┐
│  User Interface (Controller)       │  ← Command/Query 端点
├────────────────────────────────────┤
│  Application (ApplicationService)   │  ← 用例编排 + CQRS
├────────────────────────────────────┤
│  Domain (Entity, Value Object)      │  ← 业务逻辑 + 聚合根
├────────────────────────────────────┤
│  Infrastructure (Repository)       │  ← EntityMapper + MyBatis
└────────────────────────────────────┘
```

---

## 核心基类

### 领域层基类

| 类 | 模块 | 说明 |
|----|------|------|
| `BaseEntity<ID>` | common | 领域实体基类 |
| `AggregateRoot<ID>` | common | 聚合根基类（含事件管理） |
| `ValueObject` | common | 值对象基类 |
| `AbstractDomainEvent` | common | 领域事件抽象实现 |

### 数据层基类

| 类 | 模块 | 说明 |
|----|------|------|
| `BasePO` | model | 持久化对象基类 |
| `IService<T>` | crud | 通用服务接口 |
| `EntityRepository<E,ID>` | crud | 充血实体仓储接口 |
| `EntityMapper<E,P>` | crud | 实体-PO 映射器接口 |

### 应用层基类

| 类 | 模块 | 说明 |
|----|------|------|
| `@ApplicationService` | application | 应用服务注解 |
| `Command` | application | 命令基类 |
| `CommandHandler<C,R>` | application | 命令处理器接口 |
| `CommandResult<T>` | application | 命令执行结果 |
| `Query` | application | 查询基类 |
| `QueryHandler<Q,R>` | application | 查询处理器接口 |

### Web层基类

| 类 | 模块 | 说明 |
|----|------|------|
| `BaseController<S,T>` | web | 贫血模型控制器基类 |
| `CommandController<C,R>` | web | 命令控制器基类 |
| `QueryController<Q,R>` | web | 查询控制器基类 |
| `ApiResponse<T>` | web | 统一响应包装 |

---

## 领域事件系统

### 事件流转

```
AggregateRoot.addDomainEvent()
        ↓
Repository.saveAndPublishEvents()
        ↓
EventPublisher.publishEvent()
        ↓
@EventListener.handle()
```

### 事件定义

```java
// 方式一：使用 common.domain.AbstractDomainEvent
public class OrderCreatedEvent extends AbstractDomainEvent {
    private final String orderNo;
    private final BigDecimal amount;

    public OrderCreatedEvent(String aggregateId, String orderNo, BigDecimal amount) {
        super(aggregateId);
        this.orderNo = orderNo;
        this.amount = amount;
    }
}

// 方式二：使用 event.DomainEvent (推荐)
public class OrderPaidEvent extends DomainEvent {
    private final BigDecimal amount;

    public OrderPaidEvent(String orderId, BigDecimal amount) {
        super(orderId, "Order");
        this.amount = amount;
    }
}
```

---

## CQRS 实现

### Command 端

```
请求 → CommandController → CommandHandler → AggregateRoot → Repository → Database
                              ↓
                         领域事件发布
```

### Query 端

```
请求 → QueryController → QueryHandler → Repository/Database → DTO
                                  ↓
                            （无状态变更）
```

---

## 数据转换

### Entity ↔ PO 转换

```java
// 使用 MapStruct 生成实现
@Mapper(componentModel = "spring")
public interface OrderMapper extends EntityMapper<Order, OrderPO> {
    // 自动生成 toEntity() 和 toPo() 方法
}
```

### Repository 实现

```java
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper mapper;
    private final OrderBaseMapper baseMapper;

    @Override
    public Order save(Order entity) {
        OrderPO po = mapper.toPo(entity);
        baseMapper.insert(po);
        return mapper.toEntity(po);
    }

    @Override
    public Order saveAndPublishEvents(Order entity) {
        Order saved = save(entity);
        // 发布领域事件
        entity.getDomainEvents().forEach(eventPublisher::publishEvent);
        entity.clearDomainEvents();
        return saved;
    }
}
```

---

## 向后兼容

FastDDD 保证完全向后兼容：

1. **现有代码零改动**：贫血模型代码继续工作
2. **可选升级**：按需选择充血模型
3. **渐进迁移**：逐个聚合迁移到充血模型
4. **工具支持**：代码生成器支持两种模型

---

## 最佳实践

### 何时使用贫血模型

- 简单 CRUD 操作
- 领域逻辑简单的场景
- 团队 DDD 经验不足
- 快速原型开发

### 何时使用充血模型

- 复杂业务规则
- 需要领域事件
- 多聚合协作场景
- 需要明确业务边界

### 混合使用建议

```
同一项目内：
- 简单模块使用贫血模型
- 核心业务使用充血模型
- 逐步迁移关键聚合
```

---

## 参考资料

- [CQRS 指南](./CQRS_GUIDE.md)
- [迁移指南](./MIGRATION_GUIDE.md)
- [API 参考](./API_REFERENCE.md)
