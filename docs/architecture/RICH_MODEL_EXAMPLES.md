# FastDDD 充血模型示例文档

## 目录

- [1. 概述](#1-概述)
- [2. 完整示例：订单聚合](#2-完整示例订单聚合)
  - [2.1 领域层 (Domain Layer)](#21-领域层-domain-layer)
  - [2.2 应用层 (Application Layer)](#22-应用层-application-layer)
  - [2.3 基础设施层 (Infrastructure Layer)](#23-基础设施层-infrastructure-layer)
  - [2.4 接口层 (Interfaces Layer)](#24-接口层-interfaces-layer)
- [3. 关键实现细节](#3-关键实现细节)
- [4. 最佳实践](#4-最佳实践)

---

## 1. 概述

本文档通过一个完整的**订单聚合**示例，展示如何在 FastDDD 框架中实现充血模型（Rich Domain Model）。

### 示例业务场景

- **订单（Order）**：聚合根，包含订单基本信息和订单项列表
- **订单项（OrderItem）**：实体，表示订单中的商品明细
- **订单状态（OrderStatus）**：值对象（枚举），表示订单的当前状态

### 支持的操作

| 操作 | 类型 | 说明 |
|------|------|------|
| 创建订单 | Command | 创建新订单，生成订单号 |
| 支付订单 | Command | 支付订单，状态从待支付变为已支付 |
| 取消订单 | Command | 取消订单，状态从待支付变为已取消 |
| 发货订单 | Command | 订单发货，状态从已支付变为已发货 |
| 完成订单 | Domain Event | 订单签收，状态从已发货变为已完成 |
| 根据ID查询 | Query | 根据订单ID查询订单详情 |
| 根据订单号查询 | Query | 根据订单号查询订单详情 |
| 查询客户订单 | Query | 查询指定客户的所有订单 |

---

## 2. 完整示例：订单聚合

### 2.1 领域层 (Domain Layer)

领域层是充血模型的核心，包含业务逻辑和领域规则。

#### 2.1.1 聚合根：Order

```java
package com.gallop.fastddd.studio.rich.order.domain;

import com.gallop.fastddd.common.domain.AggregateRoot;
import com.gallop.fastddd.common.domain.AbstractDomainEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单聚合根
 *
 * 职责：
 * - 封装订单业务规则（状态转换、金额计算）
 * - 管理领域事件发布
 * - 维护聚合内部一致性
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Order extends AggregateRoot<Long> {

    // ==================== 基本属性 ====================

    /** 订单号（业务唯一标识） */
    private String orderNo;

    /** 客户ID */
    private String customerId;

    /** 客户姓名 */
    private String customerName;

    /** 收货地址 */
    private String shippingAddress;

    /** 联系电话 */
    private String contactPhone;

    /** 订单项列表 */
    private List<OrderItem> items;

    /** 订单状态 */
    private OrderStatus status;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 已支付金额 */
    private BigDecimal paidAmount;

    /** 备注 */
    private String remark;

    /** 下单时间 */
    private LocalDateTime orderTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 发货时间 */
    private LocalDateTime shipTime;

    // ==================== 工厂方法 ====================

    /**
     * 创建订单
     *
     * 业务规则：
     * - 自动生成唯一订单号
     * - 初始状态为待支付
     * - 自动计算订单总金额
     * - 发布订单创建事件
     */
    public static Order create(String customerId, String customerName,
                              String shippingAddress, String contactPhone,
                              List<OrderItem> items, String remark) {
        Order order = new Order();
        order.orderNo = generateOrderNo();
        order.customerId = customerId;
        order.customerName = customerName;
        order.shippingAddress = shippingAddress;
        order.contactPhone = contactPhone;
        order.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        order.status = OrderStatus.PENDING;
        order.totalAmount = calculateTotalAmount(order.items);
        order.paidAmount = BigDecimal.ZERO;
        order.remark = remark;
        order.orderTime = LocalDateTime.now();

        // 发布领域事件
        order.addDomainEvent(new OrderCreatedEvent(
            order.getId(),
            order.orderNo,
            order.customerId,
            order.totalAmount
        ));

        return order;
    }

    // ==================== 业务方法 ====================

    /**
     * 支付订单
     *
     * 业务规则：
     * - 只有待支付状态可以支付
     * - 支付金额不能超过订单总金额
     * - 支付完成后状态变为已支付
     * - 发布订单支付事件
     */
    public void pay(BigDecimal paymentAmount) {
        if (!status.canPay()) {
            throw new IllegalStateException("当前状态[" + status + "]不允许支付");
        }

        if (paymentAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("支付金额[" + paymentAmount +
                "]不能超过订单总金额[" + totalAmount + "]");
        }

        this.paidAmount = paymentAmount;
        this.status = OrderStatus.PAID;
        this.payTime = LocalDateTime.now();

        addDomainEvent(new OrderPaidEvent(
            getId(), orderNo, paymentAmount, payTime
        ));
    }

    /**
     * 取消订单
     *
     * 业务规则：
     * - 只有待支付状态可以取消
     * - 已支付订单需要先退款才能取消
     * - 发布订单取消事件
     */
    public void cancel() {
        if (!status.canCancel()) {
            throw new IllegalStateException("当前状态[" + status + "]不允许取消");
        }

        this.status = OrderStatus.CANCELLED;

        addDomainEvent(new OrderCancelledEvent(getId(), orderNo));
    }

    /**
     * 发货订单
     *
     * 业务规则：
     * - 只有已支付状态可以发货
     * - 记录物流单号
     * - 发布订单发货事件
     */
    public void ship(String trackingNumber) {
        if (!status.canShip()) {
            throw new IllegalStateException("当前状态[" + status + "]不允许发货");
        }

        this.status = OrderStatus.SHIPPED;
        this.shipTime = LocalDateTime.now();
        // 可以扩展添加物流单号字段

        addDomainEvent(new OrderShippedEvent(getId(), orderNo, trackingNumber));
    }

    /**
     * 完成订单
     *
     * 业务规则：
     * - 只有已发货状态可以完成
     * - 发布订单完成事件
     */
    public void complete() {
        if (!status.canComplete()) {
            throw new IllegalStateException("当前状态[" + status + "]不允许完成");
        }

        this.status = OrderStatus.COMPLETED;

        addDomainEvent(new OrderCompletedEvent(getId(), orderNo));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成订单号
     * 格式：ORD + 时间戳 + 随机数
     */
    private static String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() +
               (int)(Math.random() * 1000);
    }

    /**
     * 计算订单总金额
     */
    private static BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
            .map(item -> item.getUnitPrice().multiply(
                BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 继承方法实现 ====================

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
```

#### 2.1.2 实体：OrderItem

```java
package com.gallop.fastddd.studio.rich.order.domain;

import com.gallop.fastddd.common.domain.BaseEntity;
import java.math.BigDecimal;

/**
 * 订单项实体
 *
 * 职责：
 * - 表示订单中的商品明细
 * - 封装小计计算逻辑
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem extends BaseEntity<Long> {

    /** 商品ID */
    private String productId;

    /** 商品名称 */
    private String productName;

    /** 商品SKU */
    private String sku;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private Integer quantity;

    /**
     * 计算小计
     */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
```

#### 2.1.3 值对象：OrderStatus

```java
package com.gallop.fastddd.studio.rich.order.domain;

/**
 * 订单状态枚举
 *
 * 职责：
 * - 定义订单的所有可能状态
 * - 提供状态转换规则
 */
public enum OrderStatus {

    PENDING("待支付", 1),
    PAID("已支付", 2),
    CANCELLED("已取消", 3),
    SHIPPED("已发货", 4),
    COMPLETED("已完成", 5);

    private final String description;
    private final int code;

    OrderStatus(String description, int code) {
        this.description = description;
        this.code = code;
    }

    /**
     * 是否可以支付
     */
    public boolean canPay() {
        return this == PENDING;
    }

    /**
     * 是否可以取消
     */
    public boolean canCancel() {
        return this == PENDING;
    }

    /**
     * 是否可以发货
     */
    public boolean canShip() {
        return this == PAID;
    }

    /**
     * 是否可以完成
     */
    public boolean canComplete() {
        return this == SHIPPED;
    }

    /**
     * 根据代码获取状态
     */
    public static OrderStatus fromCode(int code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status code: " + code);
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }
}
```

#### 2.1.4 领域事件

```java
// OrderCreatedEvent.java
package com.gallop.fastddd.studio.rich.order.domain;

import com.gallop.fastddd.common.domain.AbstractDomainEvent;
import java.math.BigDecimal;

/**
 * 订单创建事件
 */
@Getter
@AllArgsConstructor
public class OrderCreatedEvent extends AbstractDomainEvent {
    private final String orderNo;
    private final String customerId;
    private final BigDecimal totalAmount;

    public OrderCreatedEvent(Long aggregateId, String orderNo,
                            String customerId, BigDecimal totalAmount) {
        super(aggregateId, "Order");
        this.orderNo = orderNo;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
    }

    @Override
    public String getEventType() {
        return "OrderCreated";
    }
}
```

---

### 2.2 应用层 (Application Layer)

应用层负责协调领域对象完成用例。

#### 2.2.1 命令对象

```java
// CreateOrderCommand.java
package com.gallop.fastddd.studio.rich.order.application.command;

import com.gallop.fastddd.application.command.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 创建订单命令
 */
@Getter
@Setter
public class CreateOrderCommand extends Command {

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    @NotBlank(message = "客户姓名不能为空")
    private String customerName;

    @NotBlank(message = "收货地址不能为空")
    private String shippingAddress;

    @NotBlank(message = "联系电话不能为空")
    private String contactPhone;

    @NotNull(message = "订单项不能为空")
    private List<OrderItemDto> items;

    private String remark;
}
```

#### 2.2.2 命令处理器

```java
// CreateOrderCommandHandler.java
package com.gallop.fastddd.studio.rich.order.application.handler;

import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.studio.rich.order.application.command.CreateOrderCommand;
import com.gallop.fastddd.studio.rich.order.domain.Order;
import com.gallop.fastddd.studio.rich.order.domain.OrderItem;
import com.gallop.fastddd.studio.rich.order.infrastructure.persistence.OrderRepository;
import com.gallop.fastddd.studio.rich.order.interfaces.dto.OrderDto;
import com.gallop.fastddd.studio.rich.order.interfaces.dto.OrderItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 创建订单命令处理器
 *
 * 职责：
 * - 协调领域对象创建订单
 * - 持久化聚合
 * - 发布领域事件
 */
@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {

    private final OrderRepository orderRepository;

    @Override
    public OrderDto handle(CreateOrderCommand command) {
        // 1. 转换 DTO 到 领域对象
        List<OrderItem> items = command.getItems().stream()
            .map(dto -> OrderItem.create(
                dto.getProductId(),
                dto.getProductName(),
                dto.getSku(),
                dto.getUnitPrice(),
                dto.getQuantity()
            ))
            .toList();

        // 2. 调用领域对象创建订单（业务逻辑在领域层）
        Order order = Order.create(
            command.getCustomerId(),
            command.getCustomerName(),
            command.getShippingAddress(),
            command.getContactPhone(),
            items,
            command.getRemark()
        );

        // 3. 持久化并发布事件
        orderRepository.saveAndPublishEvents(order);

        // 4. 返回 DTO
        return OrderDto.from(order);
    }
}
```

---

### 2.3 基础设施层 (Infrastructure Layer)

#### 2.3.1 Repository 实现

```java
// OrderRepositoryImpl.java
package com.gallop.fastddd.studio.rich.order.infrastructure.persistence;

import com.gallop.fastddd.crud.entity.EntityRepository;
import com.gallop.fastddd.studio.rich.order.domain.Order;
import com.gallop.fastddd.studio.rich.order.infrastructure.persistence.OrderPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 订单仓储实现
 *
 * 职责：
 * - Entity 与 PO 转换
 * - 数据库操作
 * - 领域事件发布集成
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements EntityRepository<Order, Long>, OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderBaseMapper orderBaseMapper;
    private final DomainEventBridge domainEventBridge;

    @Override
    public Order save(Order entity) {
        OrderPO po = orderMapper.toPo(entity);
        if (po.getId() == null) {
            orderBaseMapper.insert(po);
            entity.setId(po.getId()); // 更新 ID
        } else {
            orderBaseMapper.updateById(po);
        }
        return orderMapper.toEntity(po);
    }

    @Override
    public Order saveAndPublishEvents(Order entity) {
        Order saved = save(entity);
        // 发布领域事件
        domainEventBridge.publishDomainEvents(entity.getDomainEvents());
        entity.clearDomainEvents();
        return saved;
    }

    @Override
    public Optional<Order> findById(Long id) {
        OrderPO po = orderBaseMapper.selectById(id);
        return po != null ? Optional.of(orderMapper.toEntity(po)) : Optional.empty();
    }

    // ... 其他方法
}
```

#### 2.3.2 MapStruct 映射器

```java
// OrderMapper.java
package com.gallop.fastddd.studio.rich.order.infrastructure.persistence;

import com.gallop.fastddd.crud.entity.EntityMapper;
import com.gallop.fastddd.studio.rich.order.domain.Order;
import com.gallop.fastddd.studio.rich.order.infrastructure.persistence.OrderPO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 订单 Entity-PO 映射器
 */
@Mapper(componentModel = "spring")
public interface OrderMapper extends EntityMapper<Order, OrderPO> {

    @Override
    Order toEntity(OrderPO po);

    @Override
    OrderPO toPo(Order entity);

    /**
     * Integer 状态码转换为 OrderStatus 枚举
     */
    default OrderStatus intToOrderStatus(Integer status) {
        return status != null ? OrderStatus.fromCode(status) : null;
    }

    /**
     * OrderStatus 枚举转换为 Integer 状态码
     */
    default Integer orderStatusToInt(OrderStatus status) {
        return status != null ? status.getCode() : null;
    }
}
```

---

### 2.4 接口层 (Interfaces Layer)

#### 2.4.1 命令控制器

```java
// OrderCommandController.java
package com.gallop.fastddd.studio.rich.order.interfaces.controller;

import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.web.controller.CommandController;
import com.gallop.fastddd.studio.rich.order.application.command.*;
import com.gallop.fastddd.studio.rich.order.interfaces.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单命令控制器
 *
 * 职责：
 * - 处理所有写操作请求
 * - 参数验证
 * - 调用命令处理器
 * - 统一响应格式
 */
@RestController
@RequestMapping("/api/rich/orders")
@RequiredArgsConstructor
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {

    private final CreateOrderCommandHandler createOrderCommandHandler;
    private final PayOrderCommandHandler payOrderCommandHandler;
    private final CancelOrderCommandHandler cancelOrderCommandHandler;
    private final ShipOrderCommandHandler shipOrderCommandHandler;

    @Override
    protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return createOrderCommandHandler;
    }

    // 可以添加更多端点，如：
    // @PostMapping("/{id}/pay")
    // public ApiResponse<OrderDto> payOrder(@PathVariable Long id, @RequestBody PayOrderCommand command)
}
```

---

## 3. 关键实现细节

### 3.1 Lombok 与继承

⚠️ **重要规则**：`@Data` 注解不会为继承的字段生成 getter/setter。

```java
// ❌ 错误写法
@Data  // 不会生成 getId()/setId()
public class Order extends AggregateRoot<Long> {
    private String orderNo;
}

// ✅ 正确写法
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class Order extends AggregateRoot<Long> {
    private String orderNo;

    // 必须显式实现抽象方法
    @Override
    public Long getId() {
        return id;  // 直接访问父类的 protected 字段
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}
```

### 3.2 跨包字段访问

⚠️ **重要规则**：包私有字段（默认访问）不能从其他包访问。

```java
// domain 包
public class Order {
    String orderNo;  // 包私有，其他包无法直接访问
}

// dto 包（不同包）
public class OrderDto {
    private String orderNo;  // 私有 + @Data 生成 getter/setter
}

// 使用 getter/setter 访问
dto.setOrderNo(order.getOrderNo());
```

### 3.3 事件发布集成

```java
// Repository 实现中集成事件发布
public Order saveAndPublishEvents(Order entity) {
    // 1. 保存聚合
    Order saved = save(entity);

    // 2. 发布事件
    domainEventBridge.publishDomainEvents(entity.getDomainEvents());

    // 3. 清空已发布的事件
    entity.clearDomainEvents();

    return saved;
}
```

---

## 4. 最佳实践

### 4.1 聚合设计原则

1. **聚合根唯一标识**：使用业务唯一标识（如订单号）而非数据库 ID
2. **边界清晰**：聚合内部对象只能通过聚合根访问
3. **不变性保护**：状态转换只能在聚合内部通过业务方法完成

### 4.2 命令与查询分离

| 类型 | 特点 | 示例 |
|------|------|------|
| Command | 写操作，有副作用 | CreateOrderCommand, PayOrderCommand |
| Query | 读操作，无副作用 | GetOrderByIdQuery, GetOrdersByCustomerIdQuery |

### 4.3 错误处理

```java
// 业务规则违反 → 业务异常
if (!status.canPay()) {
    throw new IllegalStateException("当前状态[" + status + "]不允许支付");
}

// 参数验证失败 → IllegalArgumentException
if (paymentAmount.compareTo(totalAmount) > 0) {
    throw new IllegalArgumentException("支付金额不能超过订单总金额");
}
```

### 4.4 测试策略

```java
// 1. 单元测试：验证领域规则
@Test
void shouldThrowException_whenPayPendingOrderWithExcessAmount() {
    Order order = Order.create(...);
    assertThrows(IllegalArgumentException.class, () -> {
        order.pay(order.getTotalAmount().add(BigDecimal.ONE));
    });
}

// 2. 集成测试：验证事件发布
@Test
void shouldPublishEvent_whenOrderCreated() {
    Order order = Order.create(...);
    assertThat(order.getDomainEvents()).hasSize(1);
    assertThat(order.getDomainEvents().get(0).getEventType())
        .isEqualTo("OrderCreated");
}
```

---

## 总结

FastDDD 充血模型的核心特点：

1. **业务逻辑封装在领域对象内部**
2. **通过 Repository 模式访问数据**
3. **使用 CQRS 分离读写操作**
4. **领域事件驱动业务流程**
5. **双轨并行架构**：贫血模型和充血模型可共存

通过本示例，您可以了解如何在 FastDDD 框架中实现完整的 DDD 充血模型。
