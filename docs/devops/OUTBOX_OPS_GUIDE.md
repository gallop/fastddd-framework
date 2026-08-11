# Outbox 模式运维指南

> 适用版本：FastDDD `1.0.0-jdk21-SNAPSHOT`（EVT-003/EVT-004 之后）
> 关联 ADR：[`docs/architecture/adr/ADR-003-event-outbox-transport.md`](../architecture/adr/ADR-003-event-outbox-transport.md)
> 关联代码：`fast-ddd-event/.../outbox/`、`fast-ddd-event/.../outbox/dispatcher/`

本文面向 **运维 / SRE / 业务集成方**，说明 Outbox 模式在生产环境的行为契约、可观测指标、人工干预流程，以及**无法在 CI 中自动化验证、需在生产或准生产环境人工核验的可靠性缺口**。

---

## 1. 部署形态与投递语义

### 1.1 架构回顾

```
业务事务 → TransactionalEventPublisher → outbox_events 表（与业务同库同事务）
                                                ↓
                          OutboxDispatcher（框架内置，每实例一个）
                                                ↓ claimBatch（FOR UPDATE + 租约）
                          OutboxTransport（SPI：local / rocketmq / 自定义）
                                                ↓
                          消费者（必须幂等）
```

- **写入侧**：`TransactionalEventPublisher` 在业务事务内将事件写入 `outbox_events`，与业务数据同库同事务提交——**事件不丢**（DB 是事实来源）。
- **投递侧**：`OutboxDispatcher` 是框架内置的调度器（程序化 `ScheduledExecutorService`，2 个守护线程：dispatch + reclaim），每个应用实例各自运行。
- **传输侧**：`OutboxTransport` 是 SPI，默认 `local`（进程内 `ApplicationEventPublisher`），生产环境通常配置 `rocketmq` 或自定义。

### 1.2 投递语义：至少一次（at-least-once）

Outbox 保证 **至少一次投递**，不保证恰好一次。两种场景会产生重复投递，**消费者必须幂等**：

1. **dispatcher 崩溃恢复**：实例 claim 事件（PROCESSING + 租约）后、ack 前进程被杀。租约到期后 reclaim 重置回 PENDING，下一轮被其他实例重新投递。该事件可能**已被 transport 发出但未 ack**——重投递即重复。
2. **transport 超时但实际成功**：`OutboxTransportException` 标记为 `timeout=true` 时，远端可能已收到消息（网络抖动/超时≠失败）。重试即重复。

> **消费者幂等是硬性前置条件**。建议：消费者以 `OutboxEvent.eventId`（全局唯一，DB UNIQUE 约束）做去重表/幂等键，处理前检查是否已处理。

---

## 2. 可观测指标（EVT-004 步骤6）

`OutboxEventService.getStatistics()` 返回 `OutboxEventStatistics`，覆盖四类运维指标：

| 指标 | 字段 | 含义 / 告警判据 |
|------|------|----------------|
| **积压** | `pendingCount` | 待投递事件数。持续增长 → dispatcher 投递能力跟不上事件产生速率，需扩容实例或排查 transport。`getHealthStatus()` 在 >1000 时提示"注意"。 |
| **失败** | `failedCount` | 处于 FAILED（可重试）状态的事件数。`getFailureRate()` >10% 提示"注意"，>20% 提示"警告"。 |
| **死信** | `deadLetterCount` | 重试耗尽（retryCount ≥ maxRetryCount）流转为 DEAD_LETTER 终态的事件数。**>0 即"严重：需人工干预"**（`hasIssues()=true`）。 |
| **延迟** | `averagePublishLatencyMillis` | 已发布事件 `created_at → published_at` 的平均延迟（秒粒度经 SQL `TIMESTAMPDIFF` 聚合后换算毫秒）。持续上升 → 积压的前兆信号。无已发布事件时为 `null`。 |

> **EVT-004 修复**：EVT-002 引入显式 `DEAD_LETTER` 终态后，此前 `getStatistics()` 漏计死信（`totalCount` 未含 DEAD_LETTER）。现已纳入，死信独立计数。

### 2.1 指标采集建议

框架未内置 Micrometer 暴露——集成方可通过定时调用 `getStatistics()` 写入监控系统（Prometheus / 日志）。推荐采集频率 30–60s，与 dispatcher 默认轮询间隔（`pollInterval=5s`、`reclaimInterval=60s`）匹配。

### 2.2 健康判定

`hasIssues()` / `getHealthStatus()` 提供开箱即用的健康判定：

- **严重**：`deadLetterCount > 0`（或有遗留的 `failedExceedingMaxRetriesCount > 0`）→ 需人工干预。
- **警告**：失败率 >20%。
- **注意**：失败率 >10%，或 `pendingCount > 1000`。
- **正常**：以上均未命中。

---

## 3. 关键运维行为

### 3.1 租约（lease）与崩溃恢复

- **租约机制**：`claimBatch(limit, leaseTimeout)` 抢占事件时写入 `claimed_at` + `lease_expires_at`（默认 `leaseTimeout=2m`，见 `EventProperties.Dispatcher`）。
- **崩溃恢复**：dispatcher 进程被杀后，被其 claim 的事件卡在 PROCESSING。reclaim 扫描（默认 `reclaimInterval=60s`）将 `lease_expires_at < now` 的 PROCESSING 事件重置回 PENDING，清租约字段、**保留 retryCount**，下一轮重新投递。
- **租约时长调优**：`leaseTimeout` 必须**大于 transport 单次投递的最坏耗时**（含超时）。过短 → 正常投递未完成即被 reclaim 重投递（放大重复）；过长 → 崩溃后事件卡住时间变长。RocketMQ 场景建议 ≥ 发送超时 + 余量。

### 3.2 重试与退避

- `markAsFailed`（dispatcher 失败路径实际调用）将事件置 FAILED、`retryCount++`，并调用 `calculateNextRetryTime()` 写入 `next_retry_at`（指数退避：`2^retryCount` 分钟，即第 1/2/3 次重试分别在 ~1/2/4 分钟后），随后 `updateById` 持久化。
- claim SQL 的 FAILED 分支谓词为 `next_retry_at IS NULL OR next_retry_at <= #{maxRetryTime}`——**已设 `next_retry_at` 的 FAILED 事件会等到退避时间才被重新抢占**，不会立即重试。
- `retryCount < maxRetryCount`（默认 3）时仍可重试；`retryCount ≥ maxRetryCount` 时自动流转 **DEAD_LETTER**（EVT-002），不再重试。
- **已知限制**：退避间隔硬编码为 `2^n` 分钟，`EventProperties.Dispatcher.retryInterval` 属性当前**仅用于日志输出、未接入退避计算**（见 §4 缺口 G2）。

### 3.3 死信干预（DLQ）

DEAD_LETTER 是终态，dispatcher 不再自动处理。干预流程：

1. **检索**：`OutboxEventService.findDeadLetterEvents(page)` 或直接 SQL `SELECT * FROM outbox_events WHERE status='DEAD_LETTER'`。
2. **诊断**：查看该事件的 `error_message`、`retry_count`、`event_type`、`event_data`。
3. **重投递**：修正根因后，`UPDATE outbox_events SET status='PENDING', retry_count=0 WHERE id=?`（或调用 `resetToPending(eventId)`——注意该方法不重置 retryCount，需手动）。
4. **放弃**：确认无法处理，删除或归档该行。

---

## 4. 无法自动化验证的可靠性缺口清单

> **诚实声明**：以下项无法在本仓库 CI（无 Docker runner、单进程单机）中自动化验证，需在生产或准生产环境（多实例 + 真实 MQ + 真实网络）人工核验。CI 覆盖见 §5。

| 编号 | 缺口 | 原因 | 核验方式 | 归属 |
|------|------|------|----------|------|
| **G1** | 真实多进程 FOR UPDATE 竞争 | CI 仅 Testcontainers 单容器 + 同 JVM 多线程（`OutboxMultiInstanceMysqlTest`）。多进程跨连接的 InnoDB 行锁行为理论上等价，但未在 CI 实证。 | 准生产：2+ 应用实例连同一 MySQL，压测并发事件，核对每事件投递恰好一次（消费者去重计数）。 | EVT-004 遗留 |
| **G2** | 退避间隔不可配置 | `markAsFailed` 已自动调用 `calculateNextRetryTime()` 写 `next_retry_at`（退避生效），但退避公式硬编码为 `2^retryCount` 分钟；`EventProperties.Dispatcher.retryInterval` 属性仅出现在 dispatcher 日志（`OutboxDispatcher:92`），未接入退避计算——配置该属性不会改变实际重试节奏。 | 生产：按需调整 `maxRetryCount`；若需自定义退避曲线，覆盖 `OutboxEvent.calculateNextRetryTime()` 或自定义 `OutboxEventService.markAsFailed`。 | 已知技术债，1.1 接入 `retryInterval` |
| **G3** | transport 超时的"已发未 ack"重复 | `RocketMqOutboxTransport` 用类名启发式判定 timeout（含 `Timeout`/`RemotingTimeout`），但远端真实状态无法在框架侧确认。 | 生产：消费者以 `eventId` 幂等去重为唯一可靠保障；监控重复投递率。 | 设计语义（at-least-once），消费者幂等前置 |
| **G4** | 跨实例时钟漂移影响 reclaim | reclaim 以应用进程 `LocalDateTime.now()` 判定租约过期。多实例时钟不一致可能导致过期判定偏差（提前 reclaim 或延迟 reclaim）。 | 生产：NTP 同步所有实例时钟；`leaseTimeout` 留足余量吸收毫秒级漂移。 | 部署约束 |
| **G5** | 真实 RocketMQ 端到端投递 | CI 用 mock `RocketMQTemplate`（`RocketMqOutboxTransportTest`）。真实 RocketMQ broker 的 syncSend 语义、消息存储、消费者 push 行为未在 CI 覆盖。 | 准生产：连真实 RocketMQ，端到端验证 KEYS/TAGS 头、消费、重试。 | EVT-003 遗留，依赖集成环境 |
| **G6** | dispatcher 线程池在容器内的优雅关闭 | `OutboxDispatcher` 用 `@PreDestroy` + `ScheduledExecutorService.shutdown`（非守护调度，10s await）。容器 SIGTERM 到 SIGKILL 的宽限期内未完成的投递批次可能中断（事件已 claim 未 ack → 下次 reclaim 恢复）。 | 生产：容器 `terminationGracePeriodSeconds` ≥ dispatcher shutdown 宽限 + reclaim 周期；观测重启后的 reclaim 恢复。 | 部署约束 |
| **G7** | 数据库故障期间的 claim/reclaim 行为 | DB 不可用时 claimBatch/reclaim 抛异常，dispatcher 由 `dispatchSafely`/`reclaimSafely` 捕获后继续下一轮（不退出）。但长时间 DB 故障期间积压无法投递。 | 生产：以 `pendingCount` + `averagePublishLatencyMillis` 监控 DB 故障期间的积压增长；DB 恢复后 dispatcher 自动消化。 | 运维监控 |

---

## 5. CI 自动化覆盖对照

| EVT-004 验收点 | CI 覆盖 | 测试类 |
|----------------|---------|--------|
| 步骤1：不依赖基础设施的确定性竞争测试 | ✅ 默认运行 | `OutboxDispatcherConcurrencyTest`（4 用例） |
| 步骤2：MySQL Testcontainers 多实例竞争 | ⚠️ Docker 门控（`-Dfastddd.test.docker=true`） | `OutboxMultiInstanceMysqlTest`（3 用例） |
| 步骤3：同一事件只被一个实例 claim | ✅ 确定性 + ⚠️ MySQL | 上述两类 |
| 步骤4：崩溃后 lease 恢复 | ✅ 确定性 + ⚠️ MySQL | 上述两类 |
| 步骤5：transport 重试不重复改变业务状态 | ✅ 确定性 | `OutboxDispatcherConcurrencyTest` |
| 步骤6：积压/失败/死信/延迟指标 | ✅ H2 真实 SQL | `OutboxEventIntegrationTest#statistics_reportsBacklogFailureDeadLetterAndLatency` |
| 步骤7：运维说明 + 可靠性缺口清单 | ✅ 本文档 | 本文件 |

---

## 6. 配置参考

`EventProperties`（`fastddd.event.*`）关键项：

```yaml
fastddd:
  event:
    mode: outbox                              # outbox 模式
    outbox:
      transport: local                        # local | rocketmq | <自定义 bean>
      dispatcher:
        enabled: true                         # false 时关闭框架自动调度（仅写侧 / 测试隔离）
        batch-size: 100                       # 每轮 claim 上限
        poll-interval: 5s                     # dispatch 轮询间隔
        retry-interval: 30s                   # 失败后重新 dispatch 间隔（dispatch 复用 poll）
        lease-timeout: 2m                     # 租约时长（> transport 最坏耗时）
        reclaim-interval: 60s                 # reclaim 过期租约扫描间隔
```

**生产 RocketMQ 配置示例**：

```yaml
fastddd:
  event:
    mode: outbox
    outbox:
      transport: rocketmq     # 自动装配 RocketMqOutboxTransport（需 RocketMQTemplate 在容器中）
      dispatcher:
        lease-timeout: 30s    # > rocketmq 发送超时（默认 3s）+ 余量
```

未装配任何 transport 且 `transport=local/rocketmq` 时，`outboxTransportGuard` 抛 `MissingOutboxTransportException`，由 `OutboxTransportFailureAnalyzer` 输出可读诊断，**应用启动即失败**（fail-fast），避免静默丢事件。
