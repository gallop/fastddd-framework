# FastDDD 1.0 AI 开发契约

> 面向 AI 与开发者的最小执行契约。实现前先阅读本文件；详细理由和迁移证据见
> 拆仓前整改方案已归入私有 pre-split 历史档案；本文件是 Community 仓的现行 AI 契约。

## 1. 模型与 ID

- 简单后台 CRUD 使用贫血模型：PO 继承 `BasePO<Long>`，Service 使用 `IService<PO, Long>`。
- 有明确不变量、生命周期和领域事件时使用充血模型：聚合继承 `AggregateRoot<ID>`，持久化 PO 保持
  数据库友好的 `Long`、`String` 或 `UUID`。
- 不使用裸 `BasePO`、`Serializable` 作为业务 ID 类型，也不在子类重新声明 `id`。
- Controller 用 `IdCodec<ID>` 解析路径 ID；Long 雪花 ID 的 API 表示为字符串，不全局改写所有 Long。
- 领域实体的 ID 在创建时确定；不要依赖持久化回填 ID 后改变 `equals` 或 `hashCode`。

## 2. Web 与生成代码

- `fast-ddd-web` 的响应信封和 Jackson 扩展默认关闭；需要时显式设置对应 `fastddd.web` /
  `fastddd.jackson` 开关。不要假设引入依赖就会包装响应。
- 生成的贫血 Controller 默认只读。需要写接口时使用 `writeMode=DTO`，请求体必须是
  Create/Update DTO，不能直接将 PO 暴露为 `@RequestBody`。
- 生成的权限与数据权限钩子是注释；业务接入安全模块后自行取消注释并补齐真实授权规则。

## 3. 自动配置与基础设施

- 自定义 `SecurityFilterChain`、`CacheManager`、`ObjectMapper` 时，框架应退让；不要依赖框架 Bean
  名称或覆盖通用 Bean。
- 缓存使用不同 cache name 隔离命名空间。消费者已有 `CacheManager` 时，它是 Spring Cache 的唯一默认
  选择；FastDDD `CacheService` 仍可独立使用。
- 搜索仅允许 `sql`、`meilisearch`、`disabled`。SQL 需要 MyBatis-Plus `BaseMapper`；Meilisearch 需要
  SDK。未配后端时设置 `fastddd.search.backend=disabled`，不要以空结果掩盖配置错误。
- Outbox、SQL 监控、Redis 等能力按模块配置开启；缺少关键依赖必须获得可执行错误，不得静默降级。

## 4. 安全与配置

- 不提交固定 secret、默认管理员、数据库密码、AI Key 或可用 token。
- JWT 关闭时不要求 secret；开启时 secret 缺失、过短或示例值必须 fail-fast。
- 默认生产安全优先。开发演示数据只能在显式 dev profile 生效。
- 数据源 URL、账号和密码以用户确认的基础 datasource 配置为单一来源；profile 不得重新拼接 URL。
  连接池显式选择 `HIKARI` 或 `DRUID`，属性与依赖必须匹配。

## 5. 每次修改的最低验证

1. 使用 JDK 21。
2. 运行受影响模块的 Maven 测试。
3. 自动配置、optional 依赖或公共契约变更，补一个独立消费者验证。
4. 对 ID、安全、Outbox、SQL 监控和默认凭据做独立 review，再提交。
5. 使用中文 Conventional Commit，例如 `fix(cache): 修复缓存隔离`。

