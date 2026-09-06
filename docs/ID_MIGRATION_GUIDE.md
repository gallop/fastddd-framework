# FastDDD 1.0 ID 迁移指南

> 适用于从旧弱类型 ID 迁移至 FastDDD 1.0 强类型 ID。迁移清单与已登记例外见
> [ID_TYPE_MIGRATION_CHECKLIST.md](architecture/ID_TYPE_MIGRATION_CHECKLIST.md)。

## 目标形态

```java
public class User extends BasePO<Long> {}

public interface UserService extends IService<User, Long> {}
```

领域模型可使用自己的 ID 值对象；PO 仍使用适配数据库的基础类型。不要让领域层引入 MyBatis 注解。

## 迁移步骤

1. 将 `extends BasePO` 改为 `extends BasePO<Long>`、`BasePO<String>` 或 `BasePO<UUID>`。
2. 将 `IService<T>`、Repository、Controller 的 ID 泛型补全为同一 `ID` 类型。
3. 删除 `(Long) entity.getId()` 等强转，直接使用强类型 `getId()`。
4. 删除子类中重复的 `id` 字段和字段级 `@TableId`；策略由全局 `mybatis-plus.id-type` 决定。
5. Controller 使用对应 `IdCodec<ID>`；自定义 Hashid 或值对象实现自己的 codec。
6. 回归 URL、JSON/OpenAPI、DDL、Mapper、Service 与前端调用，确认 ID 表示一致。

## 禁止项

- 不用 `Serializable` 作为全链路主键类型。
- 不让外部请求直接写 PO 的 `id`、`deleted`、`version` 或审计字段。
- 不为日志、Outbox 或不可变快照强行继承 `BasePO`；此类例外需记录数据模型理由、ID 类型和生命周期。

## 验证

```powershell
$env:JAVA_HOME='<JAVA_21_HOME>'
./mvnw.cmd -B -pl <affected-module> test
```

在业务仓库额外搜索裸 `BasePO`、`(Long) ...getId()`、重复 `@TableId` 与旧 `resolveId` 逻辑，并执行
至少一条真实的 Controller/Mapper 集成回归。

