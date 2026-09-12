# FastDDD 快速开始指南

[English](QUICK_START.md) | **简体中文**

> 5分钟上手 FastDDD 框架的 DDD 开发模式
>
> 新项目请先遵循[新业务系统开发规范](NEW_BUSINESS_SYSTEM_DEVELOPMENT_GUIDE.zh-CN.md)：本文仅保留最小示例。

## 概述

FastDDD 支持两种开发模型：

| 模型 | 描述 | 适用场景 |
|------|------|----------|
| **贫血模型（默认）** | PO + Service + Controller | 简单 CRUD，现有项目 |
| **充血模型** | Entity + ApplicationService + Repository + CQRS | 复杂业务，领域驱动 |

---

## 方式一：贫血模型（默认）

### 1. 创建数据库表

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

### 2. 生成代码

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password="$CODEGEN_DB_PASSWORD" \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.tablePrefix=t_
```

### 3. 使用生成的代码

```java
@RestController
@RequestMapping("/api/users")
public class UserController extends BaseAdminCrudController<UserService, User, Long> {
    // 所有 CRUD 端点自动可用
}
```

---

## 方式二：充血模型（CQRS）

### 1. 定义聚合根

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

### 2. 定义 Command

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

### 3. 定义 Application Service

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

### 4. 定义 Controller

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

## 代码生成（充血模型）

使用 Maven 插件生成充血模型代码：

```bash
mvn io.github.gallop.fastddd:fastddd-maven-plugin:1.0.0:generate-code \
  -Dcodegen.url=jdbc:mysql://localhost:3306/your_db \
  -Dcodegen.username=root \
  -Dcodegen.password=123456 \
  -Dcodegen.basePackage=com.example.project \
  -Dcodegen.modelType=RICH \
  -Dcodegen.enableCQRS=true
```

参数说明：
- `modelType=RICH`: 生成充血模型代码
- `enableCQRS=true`: 启用 CQRS 模式

---

## 双轨并行

FastDDD 支持在同一项目中同时使用贫血模型和充血模型：

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

## 替换默认安全配置

如果项目需要自定义安全配置（如接入 SSO、使用 Cookie 认证等）：

1. **YAML 关闭默认**: `fastddd.security.jwt.enabled: false`
2. **创建自定义 SecurityConfiguration** 定义自己的 `SecurityFilterChain` Bean
3. **注意 `matchIfMissing=true` 陷阱** — 不显式关闭就会激活默认配置，两个 FilterChain 并存时行为不可预测

详见 [安全集成指南](../security/INTEGRATION_GUIDE.md) 和 [SPI 参考手册](./SPI_REFERENCE.zh-CN.md)。

---

## 下一步

- [架构指南](../architecture/ARCHITECTURE.md) - 了解双轨并行架构
- [CQRS 指南](./CQRS_GUIDE.zh-CN.md) - 深入学习 CQRS 模式
- [SPI 参考手册](./SPI_REFERENCE.zh-CN.md) - 所有 SPI 接口的定义、替换方式、示例代码
- [迁移指南](../architecture/MIGRATION_GUIDE.md) - 从贫血模型迁移到充血模型
