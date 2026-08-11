# ADR-003：事件/Outbox 传输 SPI（传输与调度分离，修复 FOR UPDATE 与语句碰撞）

- **状态**：已冻结（FRM-001 架构 review）
- **日期**：2026-08-11
- **关联**：审计 P0-3（多实例重复投递）、P1-4/P1-5（Outbox SPI 缺失）；整改方案 §9 EVT-001～EVT-NNN
- **决策依据**：代码级核验 RocketMQPublisher 双重职责、claim 方法无事务、语句 ID 碰撞

---

## 1. 背景（现状，代码级证据）

- **传输与调度耦合（承重）**：`RocketMQPublisher`（`fast-ddd-event/.../mq/RocketMQPublisher.java:29-269`）同时承担①传输（`syncSend` 行 106-144）与②调度（`@Scheduled` 行 53/186、`SELECT ... FOR UPDATE` claim 行 69/202、ack/nack）。职责不可替换，且"用 RocketMQ 客户端做本地表轮询调度"是明显的层级错位。审计 P1-4。
- **多实例重复投递（P0 级 bug）**：claim 方法（行 69/202）用 `FOR UPDATE` 抢占 outbox 行，但方法**无 `@Transactional`**。在没有事务边界保护下，多实例并发 claim 后 ack/发送交错会导致同一事件被多个实例投递。审计 P0-3。**确认**（`RocketMQPublisher.java:53-54` `@Scheduled` 方法无 `@Transactional`，`:69` `selectPendingEventsWithLock` 的 FOR UPDATE 锁在 SELECT 语句结束即释放，`:82` ack 在循环中单独执行 → 双实例可同时拿到同一批事件；`:51` Javadoc"使用悲观锁防止多实例同时处理"与实际行为不符）。
- **MyBatis 语句 ID 碰撞风险**：`OutboxEventRepository` 有两个同名 `countPendingEvents` 方法（`:127-131` 带 `maxRetryTime` 参数、`:204-205` 无参），均 `@Select` 注解。MyBatis 按**方法名**绑定 statement id，mapper 接口不支持方法重载 → 同名方法存在 namespace 碰撞风险（启动报错或静默覆盖，取决于处理顺序与版本）。**未观察到运行期故障（364 测试基线全绿）**，属代码异味 + 潜在风险，建议重命名消歧。
- **无 OutboxTransport SPI**：传输实现（local/RocketMQ/RabbitMQ/Redis Streams）无法插拔，全部硬编码在 publisher。审计 P1-5。
- **异步发布名不副实**：`TransactionalEventPublisher.publishEventAsync` 实际委托给同步路径，是"lie"。
- **DLQ 是 stub**：无 `DEAD_LETTER` 状态枚举；`DeadLetterQueueHandler` 为占位实现。
- **文档漂移**：`AbstractDomainEvent` 仅在文档/文章中提及，**代码中不存在**（`DomainEvent` 是唯一类型）。CLAUDE.md 的"rich model"章节引用 `AbstractDomainEvent` 需修正。

## 2. 决策

**2.1 引入 `OutboxTransport` SPI，传输与调度分离**

- `OutboxDispatcher`（框架内置，单实例或分布式锁保护）：负责 claim/lease/retry/ack/nack/DLQ，操作 outbox 表。
- `OutboxTransport`（SPI，业务/模块可插拔）：`send(OutboxEvent)`，纯传输。实现：`LocalOutboxTransport`（Spring `ApplicationEventPublisher`，1.0 内置）、`RocketMqOutboxTransport`（1.0 内置）、`RabbitMqOutboxTransport`/`RedisStreamsOutboxTransport`（1.1）。
- `OutboxEvent` 状态机显式化：`PENDING → CLAIMED → SENT → ACKED`；失败路径 `CLAIMED → RETRY`，耗尽重试 `→ DEAD_LETTER`。

**2.2 即时 bug 修复（不依赖 SPI 重构，1.0 GA 前必做）**

- 给 claim 方法补 `@Transactional`，并用短租约（`claimed_at`+`lease_expires_at`）替代裸 `FOR UPDATE`，关闭多实例重复投递（P0-3）。
- 重命名 `countPendingEvents` 重载（如 `countRetryableEvents(LocalDateTime)` vs `countPendingEvents()`），消除 MyBatis statement-id 碰撞风险。
- 修正 `TransactionalEventPublisher.publishEventAsync`：真正异步（线程池/`@Async`），或显式 deprecate 改名。
- 新增 `DEAD_LETTER` 状态枚举，`DeadLetterQueueHandler` 落实重试耗尽入死信。

**2.3 修复文档漂移**

- 修正 CLAUDE.md/文章中对 `AbstractDomainEvent` 的引用，统一为 `DomainEvent`。

## 3. 替代方案

- **备选 B（保留 RocketMQPublisher，仅补 @Transactional）**：改动最小，但传输与调度耦合仍在，RabbitMQ/Redis Streams 无从接入，审计 P1-4/P1-5 不解。仅作为 P0-3 的临时止血，不作为终态。
- **备选 C（Eventify/事件总线全替换为 Spring ApplicationEvent）**：失去 outbox 的事务性保证。不采用。

## 4. 后果与迁移

- **公共 SPI 新增**：`OutboxTransport`、`OutboxDispatcher`、`OutboxEventStatus` 枚举为公共契约。
- **破坏性（受控）**：现有直接依赖 `RocketMQPublisher` 私有调度逻辑的业务代码须迁移到 SPI；1.0 GA 前无稳定制品，japicmp 非阻断。
- **配置兼容**：`fastddd.event.outbox.transport` 属性选择实现（`local`/`rocketmq`），默认 `local`。
- **运维变化**：多实例部署下需配置分布式锁（Redis/DB）或单实例 dispatcher 调度器。

## 5. 1.0 GA 范围 vs 延后

- **1.0 GA**：`OutboxTransport` SPI、`LocalOutboxTransport`+`RocketMqOutboxTransport` 内置、claim 的 `@Transactional`+租约修复、语句碰撞删除、`publishEventAsync` 修正、`DEAD_LETTER` 状态、文档漂移修正。
- **延后（1.1+）**：`RabbitMqOutboxTransport`、`RedisStreamsOutboxTransport`、分布式锁的多种后端适配。

## 6. 对内部 RC / 公开 GA 的约束

- **RC**：`OutboxTransport` SPI 多实现回归；多实例 claim 不重复投递（Testcontainers，本地跑，无 CI runner 见 [[ci-infrastructure]]）；DLQ 路径验证。
- **GA**：`OutboxTransport`/`OutboxDispatcher`/`OutboxEventStatus` 为公共契约；`fastddd.event.outbox.transport` 属性语义冻结。

## 7. 参考

- 研究证据：审计 P0-3（行 270-296）、P1-4/P1-5（行 346-360）
- 关联 ADR：ADR-002（event 模块自动配置门禁与本 ADR 的 SPI 共同构成事件子系统的加载契约）
