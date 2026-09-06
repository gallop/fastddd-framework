# ADR-001：ID 类型系统契约（删除 Serializable-ID，改为强类型 ID）

- **状态**：已冻结（FRM-001 架构 review）
- **日期**：2026-08-11
- **关联**：审计 P1-7（ID 策略在实体/模板/DDL/手册间矛盾）、P1-10；整改方案 §8 ID-001～ID-006
- **决策依据**：用户预决策 #1（"删除旧约"，前提为无 PRODUCTION 消费者）+ 本 ADR 消费者实证（见 `fastddd-consumers.yaml`）

---

## 1. 背景（现状，代码级证据）

FastDDD 的 ID 契约当前是"`Serializable` 染色"模型，且在文档、模板、实体、DDL 之间自相矛盾：

- `BaseEntity<ID extends Serializable>`（`fast-ddd-common/.../domain/BaseEntity.java:16-25`）：`@Data` + `@EqualsAndHashCode(callSuper=false)` + 可变审计字段 → **可变 equals/hashCode**。两条 new 实体（id=null、默认字段相同）在 `HashSet` 里碰撞；插入后改 `updateTime` 破坏哈希不变量。**确认**。
- `AggregateRoot<ID extends Serializable>`（`AggregateRoot.java:19`）：继承上述缺陷。
- `BasePO`（`fast-ddd-model/.../po/BasePO.java:31,40-45`）：`@TableId(type=IdType.ASSIGN_ID) protected Serializable id`，`getId()` 返回 `Serializable`，消费方必须 `(Long) entity.getId()`。
  - **审计 P1-7 的核心矛盾被低估**：`BasePO.java:36-38` 的 Javadoc 声称"可通过 MyBatis-Plus 全局配置 `id-type` 覆盖"——**这是技术上错误的**。字段级 `@TableId` 优先级高于全局配置，全局配置**无法**覆盖它，只能靠子类重声明字段。同样的错误表述还出现在 `FASTDDD_REFERENCE.md:89`、`docs/guides/UPGRADE_GUIDE_1.1.md:234`。
  - 模板 `fastddd-templates/.../templates/project/application.yml.ftl:49` 生成 `id-type: auto`，但 `BasePO` 字段硬编码 `ASSIGN_ID`，使该配置对 `BasePO` 子类**失效**。
- `resolveId(String)`（`fast-ddd-web/.../controller/AbstractBaseController.java:53-73`）：从 `IdType` 反推 Java 类型（ASSIGN_ID/AUTO→Long，其余→原样 String），把"DB 生成策略"和"Java 值类型"混为一谈；UUID/业务 String 仅靠 else 分支侥幸返回 String。消费点：`BaseReadOnlyController.java:37`、`BaseAdminCrudController.java:104,168`。
- `SerializableTypeHandler`（`fast-ddd-crud/.../handler/SerializableTypeHandler.java:21-31`）：`@MappedTypes(Serializable.class)`，在 `FastDddCrudAutoConfiguration.java:67-74` 全局注册——**极宽捕获**，仅为补偿 `BasePO.id` 的 `Serializable` 类型。
- **反证（框架自身都不信这套契约）**：`OutboxEvent.java:32-33`、`AuditLogEntity.java:23`、`TableDesignVersion.java:27` 都是显式 `Long` + `IdType.AUTO`，不走 `BasePO`。
- `IdCodec`、`Identifiable<ID>`、`DomainEntity<ID>`：**代码中不存在**，仅是整改方案 ID-002/ID-005 的提案。
- 拆仓前整改方案编号只到 **ID-006**；**ID-007/ID-008 不存在**。原始方案已归入私有 pre-split 历史档案。

## 2. 决策

**2.1 go/no-go：GO（删除旧契约）**

消费者实证（`fastddd-consumers.yaml`）：所有已登记消费者均为 DEVELOPMENT（studio、examples、archetype 生成项目）；外部因框架**未发布任何 GA 制品**（`1.0.0-jdk21-SNAPSHOT`、内网 Gitea、无 CI 发布）而不存在"对稳定 API 的 PRODUCTION 消费者"；UPG-011 提及的"在研业务系统"处于联调阶段（TEST_UAT/UNKNOWN），其迁移已由 UPG-011 计划覆盖。满足用户预决策 #1 的"无 PRODUCTION 消费者"前提。

> 对方案验收 §6"PRODUCTION、UNKNOWN 不允许无兼容方案的破坏性升级"的偏离说明：该条款针对的是"已发布稳定制品被外部生产系统依赖"的场景。本框架**从未发布 GA**，SNAPSHOT 消费者按定义接受破坏性变更；"UNKNOWN 外部消费者"在无 GA 制品可消费时实质性不成立。故本 ADR 判定 GO，并把该偏离理由显式记录，便于审计回溯。如后续 FRM-001 review 或 M2 执行前发现真实 PRODUCTION 消费者，立即回退到 §3 备选 B。

**2.2 1.0 GA ID 契约（新）**

- 引入 `Identifiable<ID>` + `DomainEntity<ID>`（ID-002），`BaseEntity`/`AggregateRoot` 改为基于 ID 的恒等（移除 `@Data` 全字段 equals/hashCode）。
- `BasePO` 的 `id` 改为强类型（默认 `Long`，子类可声明 `UUID`/`String`/值对象），**移除 `Serializable` 类型、移除字段级 `@TableId(ASSIGN_ID)` 对全局配置的覆盖**；ID 策略由全局配置或子类显式 `@TableId` 决定（ID-003）。
- 引入 `IdCodec<ID>`（ID-005）：`parse(String)`/`format(ID)`，每个聚合一个；`AbstractBaseController` 用 `IdCodec` 取代 `resolveId` 的 IdType 猜测。
- 移除 `SerializableTypeHandler` 全局注册（ID-006）。
- DTO 侧 ID 序列化为 String（ID-005 step 7），修复 OpenAPI schema（当前 `id: Serializable` 是不透明 schema）。

**2.3 五种 ID 形态覆盖（验收要求）**

| ID 形态 | 新契约支持方式 |
|---|---|
| Long 雪花 | `Long id` + 全局 `id-type: assign_id` + `IdCodec<Long>` |
| DB 自增 | `Long id` + `@TableId(AUTO)` + `IdCodec<Long>` |
| UUID | `UUID id` + `IdCodec<UUID>`（内置） |
| 业务键 String | `String id` + `@TableId(INPUT)` + `IdCodec<String>` |
| 领域值对象 ID | `OrderId id` + `IdCodec<OrderId>`（ID-002 给 `OrderId` 示例） |

## 3. 替代方案

- **备选 B（混合/兼容层）**：1.0 GA 保留 `BasePO` 旧契约 + `@Deprecated`，新强类型 API 并行发布，破坏性删除延后 1.1。优点：GA 公共 API 二进制兼容、生成项目零改动。缺点：1.0.x 期间两套 ID 契约并存（正是审计谴责的"多真源"）；`SerializableTypeHandler` 全局捕获继续掩盖类型错误。**这是用户预决策里"查到 PRODUCTION 消费者就回退"的分支；当前 go 条件成立，不采用，但作为回退保留。**
- **即时非破坏修复（无论删不删都做）**：① 修正 `BasePO.java:36-38`、`FASTDDD_REFERENCE.md:89`、`UPGRADE_GUIDE_1.1.md:234` 关于"全局配置可覆盖字段 `@TableId`"的错误表述；② 模板 `application.yml.ftl:49` 把 `id-type: auto` 改为 `assign_id` 以匹配 `BasePO` 实际策略。这两项**立即关闭 P1-7/P1-10 的"矛盾"部分**，不依赖 M2 删除，建议作为 M1 的一个独立小任务先行。

## 4. 后果与迁移

- **破坏性**：所有 `extends BasePO` 且依赖继承 `Serializable id` 的业务子类须重声明强类型 `id`；`service.getById(Serializable)` 调用方须传正确类型；`extends BaseReadOnlyController/BaseAdminCrudController` 须加 ID 泛型或提供 `IdCodec`；`SerializableTypeHandler` 的隐式依赖消失（业务若依赖须自注册类型处理器）。
- **迁移承担方**：在研业务系统（UPG-011 已计划联调迁移）；studio/examples 随框架一起改；生成模板由 `IdStrategy` 枚举驱动（`IdStrategy.java:36`，已支持显式 `@TableId` 生成）。
- **二进制兼容**：1.0 GA 无前置稳定制品，japicmp 非阻断项（审计 §7.3）；公共 API 可在 GA 前自由调整。
- **正向收益**：编译期 ID 类型安全；移除 `SerializableTypeHandler` 宽捕获；`resolveId` 猜测消失；OpenAPI schema 正确；与框架内部既有实践（`OutboxEvent` 显式 Long）对齐。

## 5. 1.0 GA 范围 vs 延后

- **1.0 GA**：`Identifiable<ID>`/`DomainEntity<ID>`、强类型 `BasePO.id`、`IdCodec<ID>`（含 Long/UUID/String 内置）、移除 `SerializableTypeHandler` 全局注册、移除 `resolveId`、`BaseEntity` 恒等修正、即时文档/模板修复。
- **延后（1.1+）**：值对象 ID 的高级转换器生态、ID 可观测性（ID-008 不存在，不纳入）。

## 6. ID-001～ID-006 对内部 RC / 公开 GA 的约束

| 任务 | 对内部 RC 的约束 | 对公开 GA 的约束 |
|---|---|---|
| ID-001（迁移清单） | 必须先于 ID-002~006 完成，列出所有 ID 消费点 | GA 前清单覆盖 100% 公共 API |
| ID-002（`Identifiable`/`DomainEntity`、移除 `@Data` equals） | RC 期间 studio/examples + 在研系统验证 HashSet/集合行为 | GA 公共基类恒等语义确定 |
| ID-003（`BasePO` 强类型 id、移除字段 `@TableId` 覆盖） | RC 期间生成模板 + DDL 对齐 | GA 公共 PO 契约确定 |
| ID-004（控制器 ID 泛型/codec） | RC 期间 CRUD 端点回归 | GA 公共控制器契约确定 |
| ID-005（`IdCodec`、DTO String 序列化） | RC 期间 OpenAPI schema 校验 | GA 公共 codec SPI 确定 |
| ID-006（移除 `SerializableTypeHandler`） | RC 期间确认无业务隐式依赖 | GA 不含宽捕获 TypeHandler |

> ID-007/ID-008 在方案中不存在；§6 验收"ID-002～ID-008"为方案笔误，已登记待修（限到 ID-006）。

## 7. 参考

- 研究证据：拆仓前架构审计 P1-7/P1-10（已归入私有 pre-split 历史档案）
- 消费者实证：`docs/architecture/fastddd-consumers.yaml`
- 关联 ADR：ADR-002（自动配置，涉 `SerializableTypeHandler` 注册与 `SecurityFilterChain` back-off 无关但同属契约冻结）、ADR-004（安全，涉 `JwtProperties` 不受 ID 影响）
