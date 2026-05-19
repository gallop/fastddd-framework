# FastDDD DDD 分层最佳实践

## 目录

- [1. 分层架构概述](#1-分层架构概述)
- [2. 各层职责划分](#2-各层职责划分)
- [3. 依赖规则](#3-依赖规则)
- [4. 分层交互模式](#4-分层交互模式)
- [5. 最佳实践](#5-最佳实践)
- [6. 反模式与避免](#6-反模式与避免)

---

## 1. 分层架构概述

FastDDD 遵循标准的 DDD 四层架构，同时支持贫血模型和充血模型两种模式。

### 1.1 四层架构

```
┌─────────────────────────────────────────────────────────────┐
│  用户接口层 (Interfaces Layer)                              │
│  - Controllers, DTOs                                         │
│  - 处理 HTTP 请求，参数验证，响应封装                          │
├─────────────────────────────────────────────────────────────┤
│  应用层 (Application Layer)                                  │
│  - ApplicationServices, Commands, Queries, Handlers          │
│  - 编排用例，事务边界，领域对象协调                           │
├─────────────────────────────────────────────────────────────┤
│  领域层 (Domain Layer)                                      │
│  - Entities, Value Objects, Domain Events                    │
│  - 核心业务逻辑，业务规则，不变性保护                          │
├─────────────────────────────────────────────────────────────┤
│  基础设施层 (Infrastructure Layer)                           │
│  - Repositories, Mappers, POs                                │
│  - 数据访问，外部服务集成，技术实现细节                        │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 双轨并行架构

FastDDD 支持贫血模型和充血模型共存：

| 层次 | 贫血模型路径 | 充血模型路径 |
|------|-------------|-------------|
| 接口层 | `BaseController` | `CommandController` + `QueryController` |
| 应用层 | `IService` + `ServiceImpl` | `@ApplicationService` + Command/Query Handlers |
| 领域层 | `BasePO` (业务逻辑在 Service) | `AggregateRoot` + `Entity` (业务逻辑在领域) |
| 基础设施层 | MyBatis Mapper | `EntityRepository` + `EntityMapper` (MapStruct) |

---

## 2. 各层职责划分

### 2.1 用户接口层 (Interfaces Layer)

**职责**：
- 接收外部请求（HTTP, RPC, 消息等）
- 参数验证（使用 `@Valid`, `@NotNull` 等）
- 调用应用层用例
- 转换 DTO
- 返回标准响应格式

**应该包含**：
- Controllers / REST Controllers
- Request/Response DTOs
- 参数验证注解
- 异常处理（针对接口层）

**不应该包含**：
- 业务逻辑
- 直接的数据访问
- 事务管理

**示例代码**:
```java
@RestController
@RequestMapping("/api/orders")
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {

    private final CreateOrderCommandHandler handler;

    @Override
    protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return handler;
    }

    // 可选：添加额外端点
    @PostMapping("/{id}/pay")
    public ResponseEntity<ApiResponse<OrderDto>> payOrder(
            @PathVariable Long id,
            @Valid @RequestBody PayOrderCommand command) {
        // 参数验证由 @Valid 自动处理
        // 业务逻辑在应用层
        // 响应格式统一封装
    }
}
```

---

### 2.2 应用层 (Application Layer)

**职责**：
- 编排业务用例（协调多个领域对象）
- 定义事务边界
- 权限检查（跨聚合的权限）
- 调用领域服务
- 发布领域事件

**应该包含**：
- ApplicationServices
- Commands / Queries
- CommandHandlers / QueryHandlers
- 事务管理注解 `@Transactional`

**不应该包含**：
- 核心业务逻辑（应在领域层）
- 数据访问细节（应在基础设施层）

**Command vs Query**:
| 类型 | 特点 | 示例 |
|------|------|------|
| Command | 写操作，有副作用，修改状态 | CreateOrderCommand, PayOrderCommand |
| Query | 读操作，无副作用，查询状态 | GetOrderByIdQuery, SearchOrdersQuery |

**示例代码**:
```java
@ApplicationService(aggregate = Order.class)
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;

    /**
     * 创建订单用例
     *
     * 应用层职责：
     * 1. 验证客户存在
     * 2. 调用领域对象创建订单
     * 3. 持久化聚合
     * 4. 发布领域事件
     * 5. 返回结果
     */
    @Transactional
    public OrderDto createOrder(CreateOrderCommand command) {
        // 1. 验证前置条件（应用层）
        Customer customer = customerRepository.findById(command.getCustomerId())
            .orElseThrow(() -> new NotFoundException("客户不存在"));

        // 2. 转换 DTO 到领域对象
        List<OrderItem> items = convertToOrderItems(command.getItems());

        // 3. 调用领域对象（业务逻辑在领域层）
        Order order = Order.create(
            command.getCustomerId(),
            command.getCustomerName(),
            command.getShippingAddress(),
            command.getContactPhone(),
            items,
            command.getRemark()
        );

        // 4. 持久化并发布事件
        orderRepository.saveAndPublishEvents(order);

        // 5. 返回 DTO
        return OrderDto.from(order);
    }
}
```

---

### 2.3 领域层 (Domain Layer)

**职责**：
- 封装核心业务逻辑
- 实现业务规则
- 保护业务不变性
- 管理领域生命周期
- 发布领域事件

**应该包含**：
- 聚合根 (AggregateRoot)
- 实体 (Entity)
- 值对象 (ValueObject)
- 领域事件 (DomainEvent)
- 领域服务 (DomainService)

**不应该包含**：
- 数据访问细节
- 基础设施依赖
- 应用层用例编排

**充血模型 vs 贫血模型**:
| 特性 | 贫血模型 | 充血模型 |
|------|----------|----------|
| 业务逻辑位置 | Service 层 | Entity / AggregateRoot 内部 |
| 实体职责 | 数据容器 | 封装业务规则 |
| 状态管理 | Service 控制 | 自我保护 |
| 可测试性 | 低（依赖数据库） | 高（纯逻辑） |

**示例代码**:
```java
/**
 * 订单聚合根
 *
 * 充血模型：业务逻辑封装在聚合内部
 */
@Getter
public class Order extends AggregateRoot<Long> {

    private String orderNo;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private List<OrderItem> items;

    /**
     * 支付订单 - 业务方法
     *
     * 业务规则：
     * 1. 只有待支付状态可以支付
     * 2. 支付金额不能超过订单总金额
     * 3. 支付完成后状态变为已支付
     * 4. 发布订单支付事件
     */
    public void pay(BigDecimal paymentAmount) {
        // 业务规则验证
        if (!status.canPay()) {
            throw new IllegalStateException(
                "当前状态[" + status + "]不允许支付");
        }

        if (paymentAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException(
                "支付金额[" + paymentAmount +
                "]不能超过订单总金额[" + totalAmount + "]");
        }

        // 状态修改（自我保护）
        this.paidAmount = paymentAmount;
        this.status = OrderStatus.PAID;

        // 发布领域事件
        addDomainEvent(new OrderPaidEvent(getId(), orderNo, paymentAmount));
    }

    /**
     * 计算订单总金额 - 业务方法
     */
    public BigDecimal calculateTotalAmount() {
        return items.stream()
            .map(item -> item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

---

### 2.4 基础设施层 (Infrastructure Layer)

**职责**：
- 数据访问（CRUD）
- Entity 与 PO 转换
- 外部服务集成
- 技术实现细节

**应该包含**：
- Repository 实现
- Mapper 实现
- PO 类
- 外部服务适配器

**不应该包含**：
- 业务逻辑
- 业务规则验证

**Repository 模式**:
```java
/**
 * 订单仓储实现
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;      // MapStruct 映射器
    private final OrderBaseMapper orderBaseMapper; // MyBatis Mapper
    private final DomainEventBridge eventBridge;

    @Override
    public Order save(Order entity) {
        // Entity -> PO 转换
        OrderPO po = orderMapper.toPo(entity);

        // 数据库操作
        if (po.getId() == null) {
            orderBaseMapper.insert(po);
        } else {
            orderBaseMapper.updateById(po);
        }

        // PO -> Entity 转换
        return orderMapper.toEntity(po);
    }

    @Override
    public Order saveAndPublishEvents(Order entity) {
        // 1. 保存聚合
        Order saved = save(entity);

        // 2. 发布领域事件
        eventBridge.publishDomainEvents(entity.getDomainEvents());
        entity.clearDomainEvents();

        return saved;
    }
}
```

---

## 3. 依赖规则

### 3.1 核心原则

**依赖方向**: 上层依赖下层，下层不依赖上层

```
Interfaces → Application → Domain ← Infrastructure
```

### 3.2 各层依赖关系

| 层次 | 可依赖的层 | 说明 |
|------|-----------|------|
| **Interfaces** | Application, Domain, Infrastructure | 可依赖所有下层 |
| **Application** | Domain, Infrastructure | 可依赖领域和基础设施 |
| **Domain** | 无（纯POJO） | 不依赖任何外部框架 |
| **Infrastructure** | Domain | 可依赖领域（实现 Repository 接口） |

### 3.3 依赖倒置原则

基础设施层通过接口依赖领域层：

```java
// 领域层定义接口
public interface OrderRepository extends EntityRepository<Order, Long> {
    Optional<Order> findByOrderNo(String orderNo);
}

// 基础设施层实现接口
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    // 具体实现
}
```

---

## 4. 分层交互模式

### 4.1 典型请求流程（充血模型）

```
用户请求
  ↓
Interfaces Layer: Controller 接收请求
  ↓ 验证参数，转换 DTO
  ↓
Application Layer: CommandHandler.handle()
  ↓ 编排用例，开始事务
  ↓
Domain Layer: AggregateRoot 业务方法
  ↓ 执行业务逻辑，发布事件
  ↓
Infrastructure Layer: Repository.save()
  ↓ 持久化数据
  ↓
Application Layer: 发布事件，提交事务
  ↓
Interfaces Layer: 返回响应
```

### 4.2 Command 流程示例

```
POST /api/orders (CreateOrderCommand)
  ↓
OrderCommandController.executeCommand()
  ↓
CreateOrderCommandHandler.handle()
  ↓
Order.create()         // 领域逻辑
  ↓
OrderRepository.saveAndPublishEvents()
  ↓
OrderMapper.toPo()     // Entity → PO
  ↓
OrderBaseMapper.insert() // MyBatis 操作
  ↓
DomainEventBridge.publishDomainEvents()
  ↓
OrderDto.from()        // PO → DTO
  ↓
ApiResponse<OrderDto>  // 响应
```

### 4.3 Query 流程示例

```
POST /api/orders/query (GetOrderByIdQuery)
  ↓
OrderQueryController.executeQuery()
  ↓
GetOrderByIdQueryHandler.handle()
  ↓
OrderRepository.findById()
  ↓
OrderBaseMapper.selectById()
  ↓
OrderMapper.toEntity() // PO → Entity
  ↓
OrderDto.from()        // Entity → DTO
  ↓
ApiResponse<OrderDto>  // 响应
```

---

## 5. 最佳实践

### 5.1 接口层最佳实践

#### ✅ 推荐做法

```java
@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单管理", description = "订单CRUD操作")
public class OrderController extends BaseController<OrderService, Order> {

    // 使用基类提供的 CRUD 端点

    // 自定义端点也遵循标准格式
    @PostMapping("/{id}/pay")
    public ApiResponse<OrderDto> payOrder(
            @PathVariable Long id,
            @Valid @RequestBody PayOrderRequest request) {
        OrderDto result = orderService.payOrder(id, request);
        return ApiResponse.success(result);
    }
}
```

#### ❌ 避免做法

```java
// ❌ 不要在 Controller 中包含业务逻辑
@PostMapping
public ResponseEntity<Order> createOrder(@RequestBody OrderRequest request) {
    // 业务逻辑应该在应用层或领域层
    Order order = new Order();
    order.setStatus(OrderStatus.PENDING);
    order.setTotalAmount(request.getItems().stream()...);
    orderRepository.save(order);
    return ResponseEntity.ok(order);
}
```

### 5.2 应用层最佳实践

#### ✅ 推荐做法

```java
@ApplicationService(aggregate = Order.class)
@Transactional
public class OrderApplicationService {

    /**
     * 应用服务方法特点：
     * 1. 方法名对应用例意图
     * 2. 参数是 Command 或 Query
     * 3. 返回 DTO
     * 4. 管理事务边界
     */
    public OrderDto createOrder(CreateOrderCommand command) {
        // 1. 前置条件验证
        validateCustomerExists(command.getCustomerId());

        // 2. 领域操作
        Order order = Order.create(...);

        // 3. 持久化和事件发布
        orderRepository.saveAndPublishEvents(order);

        // 4. 返回 DTO
        return OrderDto.from(order);
    }
}
```

#### ❌ 避免做法

```java
// ❌ 不要把业务逻辑放在应用服务中
@ApplicationService
public class OrderApplicationService {

    public OrderDto createOrder(CreateOrderCommand command) {
        Order order = new Order();

        // ❌ 业务规则应该在领域对象中
        if (command.getItems().isEmpty()) {
            throw new BusinessException("订单项不能为空");
        }

        // ❌ 状态转换应该在领域对象中
        order.setStatus(OrderStatus.PENDING);

        return orderDto;
    }
}
```

### 5.3 领域层最佳实践

#### ✅ 推荐做法

```java
/**
 * 充血模型：业务逻辑封装在领域对象中
 */
public class Order extends AggregateRoot<Long> {

    /**
     * 业务方法特点：
     * 1. 封装业务规则
     * 2. 保护不变性
     * 3. 发布领域事件
     */
    public void pay(BigDecimal paymentAmount) {
        // 业务规则验证
        assertCanPay(paymentAmount);

        // 状态修改
        this.paidAmount = paymentAmount;
        this.status = OrderStatus.PAID;

        // 发布事件
        addDomainEvent(new OrderPaidEvent(...));
    }

    private void assertCanPay(BigDecimal amount) {
        if (!status.canPay()) {
            throw new IllegalStateException("当前状态不允许支付");
        }
        if (amount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("支付金额超限");
        }
    }
}
```

#### ❌ 避免做法

```java
// ❌ 不要使用贫血模型
@Data
public class Order {
    private OrderStatus status;
    private BigDecimal paidAmount;

    // 只有 getter/setter，没有业务逻辑
}

// ❌ 业务逻辑泄露到 Service
@Service
public class OrderService {

    public void payOrder(Long orderId, BigDecimal amount) {
        Order order = orderRepository.findById(orderId);

        // ❌ 业务规则应该在 Order 中
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException("状态错误");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAmount(amount);
        orderRepository.save(order);
    }
}
```

### 5.4 基础设施层最佳实践

#### ✅ 推荐做法

```java
/**
 * Repository 实现特点：
 * 1. 只负责数据访问
 * 2. Entity-PO 转换透明
 * 3. 集成事件发布
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper orderMapper;
    private final DomainEventBridge eventBridge;

    @Override
    public Order saveAndPublishEvents(Order entity) {
        // 1. 持久化
        Order saved = save(entity);

        // 2. 发布事件
        eventBridge.publishDomainEvents(entity.getDomainEvents());
        entity.clearDomainEvents();

        return saved;
    }
}
```

#### ❌ 避免做法

```java
// ❌ 不要在 Repository 中包含业务逻辑
@Repository
public class OrderRepositoryImpl {

    public void payOrder(Long orderId, BigDecimal amount) {
        // ❌ 业务逻辑应该在领域层
        OrderPO po = orderBaseMapper.selectById(orderId);
        if (po.getStatus() != 1) {
            throw new RuntimeException("状态错误");
        }
        po.setStatus(2);
        orderBaseMapper.updateById(po);
    }
}
```

### 5.5 Converter 与 PayloadBuilder 位置规范

#### Converter 位置

Converter 负责 Domain ↔ PO 的双向映射，依赖 PO（infrastructure 类型），因此**必须放在 `infrastructure/persistence/`**，不能放在 `domain/`。

```
order/
├── domain/
│   ├── Order.java                    # 聚合根
│   └── OrderRepository.java          # 接口
├── infrastructure/
│   └── persistence/
│       ├── OrderConverter.java       # ✅ 正确位置
│       ├── OrderPO.java
│       └── OrderRepositoryImpl.java
```

#### PayloadBuilder / Assembler 位置

PayloadBuilder 组装跨聚合查询结果（CQRS 读路径），属于应用服务逻辑，**放在 `application/`**。

### 5.6 层规约自检清单

每个聚合实现后逐项检查：

| # | 检查项 | 如何验证 |
|---|--------|---------|
| 1 | Controller 不 import PO/Mapper | `grep "import.*persistence\..*PO" interfaces/` 应无结果 |
| 2 | Service 不 import PO/Mapper | `grep "import.*persistence\..*PO" application/` 应无结果 |
| 3 | Converter 在 infrastructure/persistence/ | 检查文件路径 |
| 4 | Domain 不 import Spring/MyBatis | `grep "import org.springframework\|com.baomidou" domain/` 应无结果 |
| 5 | PayloadBuilder 在 application/ | 检查文件路径 |

---

## 6. 反模式与避免

### 6.1 贫血模型反模式

**问题**：
- 实体只是数据容器（Setter/Getter）
- 业务逻辑全部在 Service 层
- 领域对象失去保护，不变性容易被破坏

**解决方案**：
- 将业务逻辑移回领域对象
- 使用充血模型
- 通过业务方法修改状态

```java
// ❌ 贫血模型
public class Order {
    private OrderStatus status;

    public void setStatus(OrderStatus status) {
        this.status = status;  // 任何人都可以随意修改
    }
}

// ✅ 充血模型
public class Order {
    private OrderStatus status;

    public void pay(BigDecimal amount) {
        if (!canPay()) {
            throw new IllegalStateException("不能支付");
        }
        this.status = OrderStatus.PAID;  // 状态转换有业务规则保护
    }
}
```

### 6.2 God Object 反模式

**问题**：
- ApplicationService 承担过多职责
- 单个类包含数百个方法
- 难以维护和测试

**解决方案**：
- 按 聚合根/用例 拆分 ApplicationService
- 使用 Command/Query 模式
- 每个处理器只处理一个用例

```java
// ❌ God Object
@ApplicationService
public class OrderService {
    public OrderDto createOrder(...) { }
    public OrderDto payOrder(...) { }
    public OrderDto cancelOrder(...) { }
    public OrderDto shipOrder(...) { }
    public OrderDto completeOrder(...) { }
    public OrderDto getOrderById(...) { }
    public List<OrderDto> getOrdersByCustomer(...) { }
    // ... 100+ more methods
}

// ✅ 按职责拆分
@ApplicationService(aggregate = Order.class)
public class OrderApplicationService {
    // 仅包含复杂的编排用例
    public OrderDto createOrderWithInventoryCheck(...) { }
}

// 简单用例使用 Command/Query
@Component
class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> { }
@Component
class PayOrderCommandHandler implements CommandHandler<PayOrderCommand, OrderDto> { }
```

### 6.3 领域层泄露反模式

**问题**：
- 领域层依赖基础设施（如直接使用 Repository 接口）
- 领域对象包含数据库注解（@Entity, @Column）
- 业务逻辑依赖框架类

**解决方案**：
- 领域层保持纯净（POJO）
- 通过接口定义依赖（依赖倒置）
- 基础设施层实现接口

```java
// ❌ 领域层泄露
@Entity
@Table(name = "t_order")
public class Order {

    @Autowired
    private OrderRepository orderRepository;  // ❌ 不要在领域层注入 Repository

    public void pay(BigDecimal amount) {
        // ...
        orderRepository.save(this);  // ❌ 不要在领域层访问数据库
    }
}

// ✅ 纯净领域层
public class Order extends AggregateRoot<Long> {
    // 领域层不依赖任何基础设施
    public void pay(BigDecimal amount) {
        // 纯业务逻辑
    }
}
```

### 6.4 事务边界错误反模式

**问题**：
- 在领域层管理事务
- 事务边界过细（每个方法一个事务）
- 事务边界过粗（整个应用一个事务）

**解决方案**：
- 在应用层管理事务（使用 @Transactional）
- 事务边界对应用例/用例场景
- 一个聚合一个事务

```java
// ❌ 领域层管理事务
@Service
@Transactional  // ❌ 不要在领域层管理事务
public class OrderService {
    public void payOrder(Long orderId, BigDecimal amount) {
        // ...
    }
}

// ✅ 应用层管理事务
@ApplicationService
public class OrderApplicationService {

    @Transactional  // ✅ 事务边界在应用层
    public OrderDto payOrder(PayOrderCommand command) {
        // 可能涉及多个聚合的操作
        order.pay(command.getAmount());
        inventory.consume(command.getItems());
        // 事务在这里提交
    }
}
```

### 6.5 DTO 转换混乱反模式

**问题**：
- DTO 转换逻辑分散在各层
- 重复的转换代码
- Entity、PO、DTO 相互混淆

**解决方案**：
- 每层有明确的转换职责
- 使用 MapStruct 自动生成转换代码
- DTO 只在接口层使用

```java
// ✅ 清晰的转换职责

// Domain → Infrastructure（Repository 实现）
OrderPO po = orderMapper.toPo(entity);  // MapStruct

// Infrastructure → Domain（Repository 实现）
Order entity = orderMapper.toEntity(po);  // MapStruct

// Domain → Interface（DTO 工厂方法）
OrderDto dto = OrderDto.from(order);    // 静态工厂方法

// Interface → Domain（CommandHandler）
Order order = command.toOrder();         // Command 方法
```

---

## 7. 分层检查清单

### 接口层检查

- [ ] Controller 只处理 HTTP 请求/响应
- [ ] 参数验证使用 `@Valid` 注解
- [ ] 返回统一的 `ApiResponse` 格式
- [ ] 不包含业务逻辑

### 应用层检查

- [ ] 使用 `@ApplicationService` 注解
- [ ] 方法对应用例（如 createOrder, payOrder）
- [ ] 参数是 Command 或 Query
- [ ] 管理事务边界（@Transactional）
- [ ] 编排多个领域对象完成用例
- [ ] 返回 DTO 而非领域对象

### 领域层检查

- [ ] 实体包含业务方法（充血模型）
- [ ] 状态转换有业务规则保护
- [ ] 领域对象不依赖外部框架
- [ ] 使用值对象表示概念
- [ ] 领域事件在状态变化时发布

### 基础设施层检查

- [ ] Repository 只负责数据访问
- [ ] 使用 MapStruct 进行 Entity-PO 转换
- [ ] Repository 实现领域层定义的接口
- [ ] 集成领域事件发布

---

## 8. 总结

FastDDD DDD 分层架构的核心原则：

1. **接口层**：处理请求，验证参数，返回响应
2. **应用层**：编排用例，管理事务，协调领域对象
3. **领域层**：封装业务逻辑，保护不变性，发布事件
4. **基础设施层**：数据访问，技术实现

**最佳实践总结**：

| 原则 | 说明 |
|------|------|
| **依赖倒置** | 上层依赖下层，下层通过接口被上层依赖 |
| **充血模型** | 业务逻辑封装在领域对象中 |
| **CQRS** | 命令和查询分离 |
| **事务边界** | 在应用层管理事务 |
| **DTO 隔离** | 外部不直接使用领域对象 |

通过遵循这些最佳实践，可以构建清晰、可维护、可扩展的 DDD 架构应用。
