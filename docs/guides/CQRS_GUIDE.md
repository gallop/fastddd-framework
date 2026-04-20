# FastDDD CQRS 指南

> 命令查询职责分离（CQRS）模式在 FastDDD 中的实现

## 概述

CQRS（Command Query Responsibility Segregation）是一种将命令（写操作）和查询（读操作）分离的架构模式。FastDDD 通过 `fast-ddd-application` 模块提供完整的 CQRS 支持。

---

## 核心概念

### Command（命令）

表示改变系统状态的意图：

```java
public class CreateOrderCommand extends Command {
    @NotBlank
    private String customerId;

    @NotNull
    private List<OrderItemDto> items;

    // getters and setters
}
```

### Query（查询）

表示读取系统状态的请求：

```java
public class GetOrderByIdQuery extends Query {
    @NotNull
    private Long orderId;

    // getters and setters
}
```

### Handler（处理器）

```java
// Command 处理器
@Component
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {
    @Override
    public OrderDto handle(CreateOrderCommand command) {
        // 处理命令逻辑
    }
}

// Query 处理器
@Component
public class GetOrderByIdQueryHandler implements QueryHandler<GetOrderByIdQuery, OrderDto> {
    @Override
    public OrderDto handle(GetOrderByIdQuery query) {
        // 处理查询逻辑
    }
}
```

---

## 完整示例

### 1. 定义 Command

```java
package com.example.application.order.command;

import com.gallop.fastddd.application.command.Command;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreateOrderCommand extends Command {

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    @NotNull(message = "订单项不能为空")
    private List<OrderItemDto> items;

    @Data
    public static class OrderItemDto {
        private String productId;
        private Integer quantity;
        private java.math.BigDecimal price;
    }
}
```

### 2. 定义 Query

```java
package com.example.application.order.query;

import com.gallop.fastddd.application.query.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GetOrderByIdQuery extends Query {

    @NotNull
    private Long orderId;
}
```

### 3. 实现 Command Handler

```java
package com.example.application.order.handler;

import com.gallop.fastddd.application.command.CommandHandler;
import com.example.application.order.command.CreateOrderCommand;
import com.example.domain.order.Order;
import com.example.domain.order.OrderItem;
import com.example.infrastructure.OrderRepository;
import com.example.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {

    private final OrderRepository orderRepository;

    @Override
    public OrderDto handle(CreateOrderCommand command) {
        // 1. 转换 DTO 到领域对象
        List<OrderItem> items = command.getItems().stream()
            .map(dto -> OrderItem.create(dto.getProductId(), dto.getQuantity(), dto.getPrice()))
            .toList();

        // 2. 执行领域逻辑
        Order order = Order.create(command.getCustomerId(), items);

        // 3. 持久化（并发布领域事件）
        orderRepository.saveAndPublishEvents(order);

        // 4. 返回 DTO
        return OrderDto.from(order);
    }
}
```

### 4. 实现 Query Handler

```java
package com.example.application.order.handler;

import com.gallop.fastddd.application.query.QueryHandler;
import com.example.application.order.query.GetOrderByIdQuery;
import com.example.domain.order.Order;
import com.example.infrastructure.OrderRepository;
import com.example.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetOrderByIdQueryHandler implements QueryHandler<GetOrderByIdQuery, OrderDto> {

    private final OrderRepository orderRepository;

    @Override
    public OrderDto handle(GetOrderByIdQuery query) {
        Order order = orderRepository.findById(query.getOrderId())
            .orElseThrow(() -> new NotFoundException("订单不存在: " + query.getOrderId()));
        return OrderDto.from(order);
    }
}
```

### 5. 创建 Command Controller

```java
package com.example.interfaces.order;

import com.gallop.fastddd.web.controller.CommandController;
import com.example.application.order.command.CreateOrderCommand;
import com.example.application.order.handler.CreateOrderCommandHandler;
import com.example.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDto> {

    private final CreateOrderCommandHandler commandHandler;

    @Override
    protected CommandHandler<CreateOrderCommand, OrderDto> getCommandHandler() {
        return commandHandler;
    }
}
```

### 6. 创建 Query Controller

```java
package com.example.interfaces.order;

import com.gallop.fastddd.web.controller.QueryController;
import com.example.application.order.query.GetOrderByIdQuery;
import com.example.application.order.handler.GetOrderByIdQueryHandler;
import com.example.dto.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderQueryController extends QueryController<GetOrderByIdQuery, OrderDto> {

    private final GetOrderByIdQueryHandler queryHandler;

    @Override
    protected QueryHandler<GetOrderByIdQuery, OrderDto> getQueryHandler() {
        return queryHandler;
    }
}
```

---

## API 端点

### Command 端点

```
POST /api/orders
Content-Type: application/json

{
    "customerId": "C123",
    "items": [
        {"productId": "P001", "quantity": 2, "price": 100.00},
        {"productId": "P002", "quantity": 1, "price": 50.00}
    ]
}

Response:
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "orderId": 12345,
        "orderNo": "ORD1234567890",
        "totalAmount": 250.00,
        "status": "PENDING"
    },
    "timestamp": 1704067200000
}
```

### Query 端点

```
POST /api/orders/query
Content-Type: application/json

{
    "orderId": 12345
}

Response:
{
    "code": 200,
    "message": "查询成功",
    "data": {
        "orderId": 12345,
        "orderNo": "ORD1234567890",
        "totalAmount": 250.00,
        "status": "PAID"
    },
    "timestamp": 1704067200000
}
```

---

## 高级特性

### 1. Command 元数据

```java
CreateOrderCommand command = new CreateOrderCommand();

// 设置元数据
CommandMetadata metadata = new CommandMetadata();
metadata.setCorrelationId("corr-123");
metadata.setCausationId("cause-456");
metadata.setUserId("user-789");
command.setMetadata(metadata);
```

### 2. Command 结果封装

```java
@Component
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, CommandResult<OrderDto>> {

    @Override
    public CommandResult<OrderDto> handle(CreateOrderCommand command) {
        try {
            Order order = Order.create(command.getCustomerId(), command.getItems());
            orderRepository.save(order);
            return CommandResult.success(OrderDto.from(order));
        } catch (Exception e) {
            return CommandResult.failure("创建订单失败: " + e.getMessage());
        }
    }
}
```

### 3. 复杂查询

```java
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderListQuery extends Query {

    private String customerId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private OrderStatus status;
    private Integer page = 0;
    private Integer size = 20;
    private String sortBy = "orderTime";
    private String sortDirection = "DESC";
}

@Component
public class OrderListQueryHandler implements QueryHandler<OrderListQuery, PageResult<OrderDto>> {

    @Override
    public PageResult<OrderDto> handle(OrderListQuery query) {
        // 实现分页查询逻辑
    }
}
```

---

## 最佳实践

### 1. Command 命名规范

```
✅ CreateOrderCommand
✅ UpdateCustomerInfoCommand
✅ CancelOrderCommand
✅ ProcessPaymentCommand

❌ OrderCommand（太笼统）
❌ DoSomething（不明确）
```

### 2. Query 命名规范

```
✅ GetOrderByIdQuery
✅ SearchOrdersQuery
✅ FindCustomerByNameQuery
✅ ListPendingOrdersQuery

❌ OrderQuery（太笼统）
❌ GetData（不明确）
```

### 3. Handler 单一职责

```java
// ✅ 好的设计：每个 Handler 只处理一个 Command/Query
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> { }

public class UpdateOrderCommandHandler implements CommandHandler<UpdateOrderCommand, OrderDto> { }

// ❌ 避免：一个 Handler 处理多个 Command
public class OrderCommandHandler implements CommandHandler<Command, OrderDto> { }
```

### 4. 异常处理

```java
@Component
public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {

    @Override
    public OrderDto handle(CreateOrderCommand command) {
        try {
            // 业务逻辑
        } catch (BusinessException e) {
            // 业务异常直接抛出，由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            // 系统异常包装后抛出
            throw new SystemException("订单创建失败", e);
        }
    }
}
```

---

## 与传统 Service 的对比

| 传统 Service | CQRS Handler |
|--------------|--------------|
| `UserService.createUser()` | `CreateUserCommandHandler.handle()` |
| `OrderService.createOrder()` | `CreateOrderCommandHandler.handle()` |
| `ProductService.getById()` | `GetProductByIdQueryHandler.handle()` |
| 读写混合 | 读写分离 |
| 返回实体或 void | 返回 DTO 或 CommandResult |
| 事务通常在 Service 层 | 事务可以在 Application 层 |

---

## 参考资料

- [架构指南](./ARCHITECTURE.md)
- [快速开始](./QUICK_START.md)
- [API 参考](./API_REFERENCE.md)
