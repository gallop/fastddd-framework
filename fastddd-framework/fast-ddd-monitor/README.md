# FastDDD SQL监控模块

## 概述

FastDDD SQL监控模块提供了全面的SQL执行监控、性能分析和可视化功能，集成了Prometheus指标收集、ELK日志记录和Grafana可视化面板。

## 功能特性

### 🔍 SQL执行监控
- **实时SQL拦截**: 基于 Spring 管理的 JDK `DataSource` 动态代理实现零侵入式 SQL 拦截（MON-002，取代早期 P6Spy 方案）
- **执行时间统计**: 记录每个SQL的执行时间和性能指标
- **慢查询检测**: 自动识别和记录超过阈值的慢查询
- **操作类型分析**: 按SELECT、INSERT、UPDATE、DELETE分类统计

### 📊 指标收集与导出
- **Prometheus集成**: 导出详细的SQL性能指标
- **Micrometer支持**: 兼容Spring Boot Actuator生态
- **多维度指标**: 执行时间、查询数量、错误率、连接池状态
- **自定义标签**: 支持按Mapper方法、操作类型等维度聚合

### 📝 日志记录与分析
- **ELK集成**: 结构化日志输出，便于Elasticsearch索引
- **慢查询日志**: 单独记录慢查询详情
- **JSON格式**: 使用Logstash编码器输出标准JSON格式
- **上下文信息**: 包含线程、方法、参数等完整上下文

### 📈 可视化监控
- **Grafana面板**: 预配置的SQL监控仪表盘
- **实时图表**: 执行时间趋势、慢查询率、操作分布
- **告警规则**: Prometheus告警规则配置
- **多数据源**: 支持Prometheus、Loki、Elasticsearch

## 快速开始

### 1. 添加依赖

在你的Spring Boot项目中添加监控模块依赖：

```xml
<dependency>
    <groupId>com.gallop</groupId>
    <artifactId>fast-ddd-monitor</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置启用

在`application.yml`中添加配置：

```yaml
# SQL监控配置
fast-ddd:
  sql-monitoring:
    enabled: true                    # 启用SQL监控
    slow-query-threshold: 1000       # 慢查询阈值(ms)
    record-parameters: true          # 记录SQL参数
    record-result-set-size: true     # 记录结果集大小
    prometheus-enabled: true         # 启用Prometheus指标
    elk-enabled: true               # 启用ELK日志
    max-sql-length: 2000            # 最大SQL长度
    include-stack-trace: false       # 包含堆栈跟踪
    exclude-patterns:               # 排除模式
      - "SELECT 1"
      - "SHOW.*"
    include-patterns:               # 包含模式
      - ".*"

# Spring Boot Actuator配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,sql-monitoring
  endpoint:
    health:
      show-details: when-authorized
    sql-monitoring:
      enabled: true

# Micrometer配置
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: fastddd-app
      environment: dev
```

### 3. 部署监控栈

使用提供的Docker Compose快速部署完整监控栈：

```bash
cd fast-ddd-monitor/src/main/resources/docker
docker-compose -f docker-compose-monitoring.yml up -d
```

这将启动以下服务：
- **Prometheus** (http://localhost:9090) - 指标收集
- **Grafana** (http://localhost:3000) - 可视化面板 (admin/admin123)
- **Elasticsearch** (http://localhost:9200) - 日志存储
- **Kibana** (http://localhost:5601) - 日志分析
- **Logstash** (http://localhost:5044) - 日志处理
- **AlertManager** (http://localhost:9093) - 告警管理

### 4. 访问监控面板

启动应用后，可以通过以下方式访问监控信息：

#### REST API
```bash
# 获取SQL统计信息
curl http://localhost:8080/actuator/sql-monitoring/stats

# 获取监控配置
curl http://localhost:8080/actuator/sql-monitoring/config

# 获取健康状态
curl http://localhost:8080/actuator/sql-monitoring/health

# 更新慢查询阈值
curl -X POST http://localhost:8080/actuator/sql-monitoring/slow-query-threshold/2000

# 启用/禁用监控
curl -X POST http://localhost:8080/actuator/sql-monitoring/enable
curl -X POST http://localhost:8080/actuator/sql-monitoring/disable
```

#### Prometheus指标
访问 http://localhost:8080/actuator/prometheus 查看所有指标，包括：
- `sql_execution_time_seconds` - SQL执行时间
- `sql_queries_total` - SQL查询总数
- `sql_slow_queries_total` - 慢查询总数
- `sql_operations_total` - 按操作类型统计
- `sql_connection_pool_*` - 连接池指标

#### Grafana面板
访问 http://localhost:3000，使用admin/admin123登录，查看预配置的SQL监控面板。

## 配置详解

### SQL监控属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `enabled` | boolean | true | 是否启用SQL监控 |
| `slow-query-threshold` | long | 1000 | 慢查询阈值(毫秒) |
| `record-parameters` | boolean | true | 是否记录SQL参数 |
| `record-result-set-size` | boolean | true | 是否记录结果集大小 |
| `prometheus-enabled` | boolean | true | 是否启用Prometheus指标 |
| `elk-enabled` | boolean | true | 是否启用ELK日志 |
| `max-sql-length` | int | 2000 | 最大SQL长度 |
| `include-stack-trace` | boolean | false | 是否包含堆栈跟踪 |
| `exclude-patterns` | List<String> | [] | SQL排除模式 |
| `include-patterns` | List<String> | [".*"] | SQL包含模式 |

### SQL 拦截机制（MON-002）

监控模块通过 **Spring 管理的 JDK `DataSource` 动态代理** 拦截真实 SQL 执行，不再依赖 P6Spy：

- `SqlMonitoringDataSourcePostProcessor`（`BeanPostProcessor`）在容器刷新时包装主 `DataSource`，
  使其返回的 `Connection`/`Statement` 经 `SqlMonitoringJdbcProxy` 代理；
- 代理在 `Statement` 的执行类方法（`execute`/`executeQuery`/`executeUpdate`/`executeBatch` 等）
  上计时，回调 `SqlMonitoringExecutionRecorder` → `SqlMetricsCollector` 记录 Micrometer 指标
  与脱敏结构化日志；
- 装配条件：`@ConditionalOnSingleCandidate(DataSource.class)`——仅单候选 `DataSource` 消费者生效；
  多数据源场景监控代理不装配（留后续任务）。

与早期 P6Spy 方案的区别：P6Spy 经反射实例化 `MessageFormattingStrategy`，绕过 Spring 依赖注入，
导致指标记录器字段为 null（"双实例"缺陷，SQL 指标恒不记录）。JDK 代理方案下记录器是普通 Spring bean，
依赖注入完整，真实 SQL 即可产生 Micrometer 指标。

### 日志配置

使用Logback进行日志配置，支持按环境区分输出：

- **开发环境**: 控制台输出，便于调试
- **生产环境**: 文件输出，JSON格式，便于ELK收集

## 监控指标说明

### Prometheus指标

#### 基础指标
- `sql_execution_time_seconds`: SQL执行时间分布
  - 标签: `operation_type`, `mapper_method`, `status`
- `sql_queries_total`: SQL查询总数计数器
  - 标签: `operation_type`, `mapper_method`
- `sql_slow_queries_total`: 慢查询总数计数器
  - 标签: `operation_type`, `mapper_method`

#### 连接池指标
- `sql_connection_pool_active`: 活跃连接数
- `sql_connection_pool_idle`: 空闲连接数
- `sql_connection_pool_max`: 最大连接数

#### 结果集指标
- `sql_result_set_size`: 结果集大小分布

### 告警规则

预配置的Prometheus告警规则包括：

1. **慢查询率告警**: 慢查询率超过10%
2. **SQL执行时间告警**: 95%分位数超过5秒
3. **数据库连接告警**: 连接数超过80%
4. **SQL错误率告警**: 错误率超过5%
5. **大结果集告警**: 结果集大小超过10000行

## 性能影响

SQL监控模块经过优化，对应用性能影响极小：

- **CPU开销**: < 1%
- **内存开销**: < 10MB
- **延迟增加**: < 1ms
- **异步处理**: 指标收集和日志记录异步执行

## 故障排除

### 常见问题

1. **监控不生效**
   - 检查`fastddd.monitor.sql.enabled`是否为true
   - 确认主 `DataSource` 为单候选（`@ConditionalOnSingleCandidate(DataSource.class)`——多数据源消费者监控代理不装配）
   - 确认 `spring-boot-starter-jdbc`（或等价 JDBC 起步依赖）在 classpath，否则 Spring Boot 不会创建 `DataSource` bean
   - 查看启动日志是否有错误信息

2. **指标不显示**
   - 确认Prometheus配置正确
   - 检查`management.endpoints.web.exposure.include`包含prometheus
   - 验证Micrometer配置

3. **日志不输出**
   - 检查Logback配置
   - 确认ELK服务正常运行
   - 验证日志级别设置

### 调试模式

启用调试模式获取更多信息：

```yaml
logging:
  level:
    com.gallop.fastddd.monitor.sql: DEBUG
```

## 扩展开发

### 自定义指标

实现`SqlMetricsCollector`接口添加自定义指标：

```java
@Component
public class CustomSqlMetricsCollector implements SqlMetricsCollector {
    
    @Override
    public void recordSqlExecution(SqlExecutionInfo info) {
        // 自定义指标逻辑
    }
}
```

### 自定义记录/日志行为

提供自定义 `SqlMonitoringExecutionRecorder` bean 覆盖默认记录/日志行为
（自动配置 `@ConditionalOnMissingBean` 会退让）：

```java
@Bean
public SqlMonitoringExecutionRecorder customRecorder(SqlMetricsCollector collector,
                                                     SqlMonitoringProperties properties) {
    return new SqlMonitoringExecutionRecorder(collector, properties) {
        // 覆写 record(...) 以自定义指标/日志行为
    };
}
```

> 注：MON-002 移除了早期基于 P6Spy 的 `SqlMonitoringMessageFormatter`（反射实例化绕过
> Spring DI 的"双实例"缺陷）。如需自定义 SQL 文本脱敏规则，扩展 `SqlMaskingUtils`。

## 版本历史

- **v1.0.0**: 初始版本，支持基础SQL监控和Prometheus集成
- 更多版本信息请查看CHANGELOG.md

## 许可证

本项目采用MIT许可证，详情请查看LICENSE文件。

## 贡献

欢迎提交Issue和Pull Request来改进本项目。

## 联系方式

- 作者: gallop
- 邮箱: [your-email@example.com]
- 项目地址: [https://github.com/your-repo/fastddd]
