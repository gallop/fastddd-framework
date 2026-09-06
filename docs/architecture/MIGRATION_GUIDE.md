# FastDDD 迁移指南

> 从贫血模型迁移到充血模型的实用指南

## 概述

本指南提供从贫血模型（PO + Service）迁移到充血模型（Entity + ApplicationService + CQRS）的实用步骤和最佳实践。

---

## 迁移策略

### 渐进式迁移

FastDDD 支持同一项目内贫血模型和充血模型并存，因此可以采用渐进式迁移策略：

```
阶段 1: 新功能使用充血模型
    └── 不修改现有代码，新功能采用充血模型

阶段 2: 核心聚合迁移
    └── 优先迁移业务价值高的聚合

阶段 3: 全面迁移
    └── 逐步迁移所有聚合
```

---

## 迁移步骤

### 步骤 1: 识别聚合根

分析现有代码，识别候选聚合根：

```java
// 贫血模型（现有代码）
@Service
public class OrderService {
    public void createOrder(OrderPO order) {
        // 业务逻辑
    }

    public void payOrder(Long orderId) {
        // 业务逻辑
    }

    public void cancelOrder(Long orderId) {
        // 业务逻辑
    }
}
```

分析要点：
- 哪些实体是核心业务对象？
- 哪些对象有明确的生命周期？
- 哪些对象需要维护一致性边界？

### 步骤 2: 创建领域实体

```java
// 1. 创建聚合根（继承 AggregateRoot）
public class Order extends AggregateRoot<Long> {

    private String orderNo;
    private String customerId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private List<OrderItem> items;

    // 工厂方法
    public static Order create(String customerId, List<OrderItem> items) {
        Order order = new Order();
        order.orderNo = generateOrderNo();
        order.customerId = customerId;
        order.items = items;
        order.totalAmount = calculateTotal(items);
        order.status = OrderStatus.PENDING;

        order.addDomainEvent(new OrderCreatedEvent(order));
        return order;
    }

    // 领域方法
    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("只有待支付订单才能支付");
        }
        this.status = OrderStatus.PAID;
        this.addDomainEvent(new OrderPaidEvent(this.getId(), this.totalAmount));
    }

    public void cancel(String reason) {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已完成的订单不能取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.addDomainEvent(new OrderCancelledEvent(this.getId(), reason));
    }

    // 私有构造函数
    private Order() {
        // for persistence
    }
}
```

### 步骤 3: 创建 EntityMapper

```java
@Mapper(componentModel = "spring")
public interface OrderMapper extends EntityMapper<Order, OrderPO> {

    @Mapping(target = "domainEvents", ignore = true)
    @Mapping(target = "new", ignore = true)
    Order toEntity(OrderPO po);

    @InheritInverseConfiguration
    OrderPO toPo(Order entity);
}
```

### 步骤 4: 创建 EntityRepository

```java
public interface OrderRepository extends EntityRepository<Order, Long> {
    // 继承所有 CRUD 方法

    // 可添加自定义查询方法
    List<Order> findByCustomerId(String customerId);
    Optional<Order> findByOrderNo(String orderNo);
}
```

### 步骤 5: 实现 Repository

```java
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper mapper;
    private final OrderBaseMapper baseMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Order save(Order entity) {
        OrderPO po = mapper.toPo(entity);
        if (po.getId() == null) {
            baseMapper.insert(po);
        } else {
            baseMapper.updateById(po);
        }
        return mapper.toEntity(po);
    }

    @Override
    public Order saveAndPublishEvents(Order entity) {
        Order saved = save(entity);

        // 发布领域事件
        entity.getDomainEvents().forEach(event -> {
            eventPublisher.publishEvent(event);
        });
        entity.clearDomainEvents();

        return saved;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(baseMapper.selectById(id))
            .map(mapper::toEntity);
    }

    // ... 其他方法实现
}
```

### 步骤 6: 创建 Command 和 Handler

```java
// Command
@Data
public class CreateOrderCommand extends Command {
    @NotBlank
    private String customerId;

    @NotNull
    private List<OrderItemDto> items;
}

// Handler
@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {

    private final OrderRepository orderRepository;

    @Override
    public OrderDto handle(CreateOrderCommand command) {
        List<OrderItem> items = command.getItems().stream()
            .map(dto -> OrderItem.create(dto.getProductId(), dto.getQuantity(), dto.getPrice()))
            .toList();

        Order order = Order.create(command.getCustomerId(), items);
        orderRepository.saveAndPublishEvents(order);

        return OrderDto.from(order);
    }
}
```

### 步骤 7: 创建 Controller

```java
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {

    private final CreateOrderCommandHandler handler;

    @Override
    protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return handler;
    }
}
```

### 步骤 8: 迁移调用方代码

```java
// 迁移前（贫血模型）
@Autowired
private OrderService orderService;

public void someMethod() {
    OrderPO order = new OrderPO();
    order.setCustomerId("C123");
    orderService.createOrder(order);
    orderService.payOrder(order.getId());
}

// 迁移后（充血模型）
@Autowired
private CreateOrderCommandHandler createOrderHandler;
@Autowired
private PayOrderCommandHandler payOrderHandler;

public void someMethod() {
    CreateOrderCommand cmd = new CreateOrderCommand();
    cmd.setCustomerId("C123");
    OrderDto order = createOrderHandler.handle(cmd);

    PayOrderCommand payCmd = new PayOrderCommand(order.getOrderId());
    payOrderHandler.handle(payCmd);
}
```

---

## 数据迁移

### 方案 1: 在线迁移（推荐）

1. **双写阶段**：同时写入 PO 和 Entity
2. **双读阶段**：优先读 Entity，回退到 PO
3. **切换阶段**：完全切换到 Entity
4. **清理阶段**：删除旧 PO 代码

### 方案 2: 离线迁移

```sql
-- 1. 备份数据
CREATE TABLE t_order_backup AS SELECT * FROM t_order;

-- 2. 修改表结构（如有必要）

-- 3. 数据转换脚本

-- 4. 验证数据一致性

-- 5. 切换应用
```

---

## 测试策略

### 1. 单元测试

```java
@Test
void should_create_order() {
    // Given
    List<OrderItem> items = List.of(
        OrderItem.create("P001", 2, new BigDecimal("100.00"))
    );

    // When
    Order order = Order.create("C123", items);

    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getTotalAmount()).isEqualTo(new BigDecimal("200.00"));
    assertThat(order.getDomainEvents()).hasSize(1);
}

@Test
void should_pay_order() {
    // Given
    Order order = Order.create("C123", List.of());

    // When
    order.pay();

    // Then
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(order.getDomainEvents()).hasSize(2); // Created + Paid
}
```

### 2. 集成测试

```java
@SpringBootTest
@Transactional
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void should_save_and_load_order() {
        // Given
        Order order = Order.create("C123", List.of());

        // When
        Order saved = orderRepository.saveAndPublishEvents(order);

        // Then
        Optional<Order> loaded = orderRepository.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getOrderNo()).isEqualTo(order.getOrderNo());
    }
}
```

### 3. 对比测试

```java
@Test
void compare_anemic_and_rich_model() {
    // 贫血模型
    OrderPO po = new OrderPO();
    po.setCustomerId("C123");
    orderService.createOrder(po);

    // 充血模型
    CreateOrderCommand cmd = new CreateOrderCommand();
    cmd.setCustomerId("C123");
    OrderDto result = createOrderHandler.handle(cmd);

    // 断言结果一致
    assertThat(po.getCustomerId()).isEqualTo(result.getCustomerId());
}
```

---

## 常见问题

### Q1: 如何处理事务？

```java
@Service
@Transactional
public class OrderApplicationService {

    public OrderDto createOrder(CreateOrderCommand command) {
        // 事务在此方法级别控制
        return createOrderHandler.handle(command);
    }
}
```

### Q2: 如何处理跨聚合操作？

```java
// 使用 Application Service 编排
@Service
public class OrderApplicationService {

    public void placeOrder(PlaceOrderCommand command) {
        // 1. 创建订单
        Order order = Order.create(command.getCustomerId(), command.getItems());

        // 2. 检查库存（另一个聚合）
        inventoryService.checkAvailability(command.getItems());

        // 3. 扣减库存（另一个聚合）
        inventoryService.deductItems(command.getItems());

        // 4. 保存订单
        orderRepository.saveAndPublishEvents(order);
    }
}
```

### Q3: 如何迁移复杂查询？

```java
// 复杂查询保持使用贫血模型方式
// 使用 MyBatis XML 或 QueryWrapper

@Mapper
public interface OrderQueryMapper {
    List<OrderDto> queryOrders(OrderQueryCondition condition);
}
```

---

## 迁移检查清单

### 准备阶段

- [ ] 识别候选聚合根
- [ ] 绘制聚合关系图
- [ ] 制定迁移计划
- [ ] 准备测试用例

### 实施阶段

- [ ] 创建领域实体类
- [ ] 创建 EntityMapper
- [ ] 创建 EntityRepository
- [ ] 创建 Command/Query
- [ ] 创建 Handler
- [ ] 创建 Controller
- [ ] 迁移调用方代码

### 验证阶段

- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试通过
- [ ] 数据一致性验证

---

## 参考资料

- [架构指南](./ARCHITECTURE.md)
- [CQRS 指南](../guides/CQRS_GUIDE.md)
- [快速开始](../guides/QUICK_START.md)
