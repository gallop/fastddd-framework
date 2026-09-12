# FastDDD Quick Start Guide

**English** | [简体中文](QUICK_START.zh-CN.md)

> Get hands-on with the FastDDD framework's DDD development model in 5 minutes
>
> For new projects, follow the [New Business System Development Guide](NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.md) first; this document keeps only the minimal example.

## Overview

FastDDD supports two development models:

| Model | Description | Best For |
|------|------|----------|
| **Anemic model (default)** | PO + Service + Controller | Simple CRUD, existing projects |
| **Rich model** | Entity + ApplicationService + Repository + CQRS | Complex business logic, domain-driven |

---

## Option 1: Anemic Model (Default)

### 1. Create the Database Table

```sql
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(50),
    email VARCHAR(100),
    created_time DATETIME,
    updated_time DATETIME,
    deleted TINYINT DEFAULT 0
);
```

### 2. Generate Code

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password="$CODEGEN_DB_PASSWORD" \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_
```

### 3. Use the Generated Code

```java
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseAdminCrudController<UserService, User, Long> {
    // 所有 CRUD 端点自动可用
}
```

---

## Option 2: Rich Model (CQRS)

### 1. Define the Aggregate Root

```java
package com.example.domain.order;

import com.gallop.fastddd.common.domain.AggregateRoot;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Order extends AggregateRoot<Long> {

    private String orderNo;
    private String customerId;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime orderTime;
    private List<OrderItem> items = new ArrayList<>();

    private Order() {
        // for persistence
    }

    public static Order create(String customerId, List<OrderItem> items) {
        Order order = new Order();
        order.orderNo = generateOrderNo();
        order.customerId = customerId;
        order.items = items;
        order.totalAmount = calculateTotal(items);
        order.status = OrderStatus.PENDING;
        order.orderTime = LocalDateTime.now();

        // 发布领域事件
        order.addDomainEvent(new OrderCreatedEvent(order));

        return order;
    }

    public void pay() {
        if (this.status != OrderStatus.PENDING) {
            throw new IllegalStateException("只有待支付订单才能支付");
        }
        this.status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(this.getId(), this.totalAmount));
    }

    public void cancel(String reason) {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("已完成的订单不能取消");
        }
        this.status = OrderStatus.CANCELLED;
        addDomainEvent(new OrderCancelledEvent(this.getId(), reason));
    }

    private static String generateOrderNo() {
        return "ORD" + System.currentTimeMillis();
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

### 2. Define the Command

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
        private BigDecimal price;
    }
}
```

### 3. Define the Application Service

```java
package com.example.application.order;

import com.gallop.fastddd.application.annotation.ApplicationService;
import com.example.application.order.command.CreateOrderCommand;
import com.example.application.order.command.CommandHandler;
import com.example.domain.order.Order;
import com.example.infrastructure.persistence.OrderRepository;
import lombok.RequiredArgsConstructor;

@ApplicationService
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    // 创建订单命令处理器
    @org.springframework.stereotype.Component
    @RequiredArgsConstructor
    public static class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDto> {

        private final OrderRepository orderRepository;

        @Override
        public OrderDto handle(CreateOrderCommand command) {
            // 转换 DTO 到领域对象
            List<OrderItem> items = command.getItems().stream()
                .map(dto -> OrderItem.create(dto.getProductId(), dto.getQuantity(), dto.getPrice()))
                .toList();

            // 执行领域逻辑
            Order order = Order.create(command.getCustomerId(), items);

            // 持久化
            orderRepository.save(order);

            // 返回 DTO
            return OrderDto.from(order);
        }
    }
}
```

### 4. Define the Controller

```java
package com.example.interfaces.order;

import com.gallop.fastddd.web.controller.CommandController;
import com.example.application.order.command.CreateOrderCommand;
import com.example.application.order.command.CreateOrderCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

---

## Code Generation (Rich Model)

Use the Maven plugin to generate rich-model code:

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=123456 \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.modelType=RICH \
  -Dcodegen.enableCQRS=true
```

Parameter notes:
- `modelType=RICH`: generates rich-model code
- `enableCQRS=true`: enables the CQRS pattern

---

## Running Both Tracks Side by Side

FastDDD supports using the anemic model and the rich model in the same project at the same time:

```
src/main/java/com/example/
├── anemic/          # 贫血模型代码
│   ├── controller/
│   ├── service/
│   └── repository/
└── rich/            # 充血模型代码
    ├── domain/      # 聚合根、值对象
    ├── application/ # 应用服务、Command/Query
    └── infrastructure/ # 仓储实现
```

---

## Replacing the Default Security Configuration

If your project needs a custom security configuration (for example, integrating SSO or switching to Cookie authentication):

1. **Disable the default in YAML**: `fastddd.security.jwt.enabled: false`
2. **Create a custom SecurityConfiguration** that defines your own `SecurityFilterChain` bean
3. **Watch out for the `matchIfMissing=true` trap** — if you do not disable it explicitly, the default configuration stays active, and with two FilterChains coexisting the behavior is unpredictable

See the [Security Integration Guide](../security/INTEGRATION_GUIDE.md) and the [SPI Reference](./SPI_REFERENCE.md) for details.

---

## Next Steps

- [Architecture Guide](../architecture/ARCHITECTURE.md) - learn about the dual-track architecture
- [CQRS Guide](./CQRS_GUIDE.md) - go deeper into the CQRS pattern
- [SPI Reference](./SPI_REFERENCE.md) - definitions of all SPI interfaces, how to replace them, and sample code
- [Migration Guide](../architecture/MIGRATION_GUIDE.md) - migrate from the anemic model to the rich model
