# FastDDD 1.0 ID Migration Guide

**English** | [简体中文](ID_MIGRATION_GUIDE.zh-CN.md)

> Applies to migrating from the old weakly typed IDs to the FastDDD 1.0 strongly typed IDs. The internal migration checklist (ID_TYPE_MIGRATION_CHECKLIST.md)
> was moved to a private out-of-repository archive on 2026-09-12 and is not published with the open-source repository.

## Target Shape

```java
public class User extends BasePO<Long> {}

public interface UserService extends IService<User, Long> {}
```

The domain model may use its own ID value objects; POs still use base types that fit the database. Do not let the domain layer import MyBatis annotations.

## Migration Steps

1. Change `extends BasePO` to `extends BasePO<Long>`, `BasePO<String>`, or `BasePO<UUID>`.
2. Complete the ID generics of `IService<T>`, Repository, and Controller to the same `ID` type.
3. Delete casts such as `(Long) entity.getId()` and use the strongly typed `getId()` directly.
4. Delete duplicate `id` fields and field-level `@TableId` in subclasses; the strategy is decided by the global `mybatis-plus.id-type`.
5. Controllers use the matching `IdCodec<ID>`; custom Hashids or value objects implement their own codec.
6. Regression-test URLs, JSON/OpenAPI, DDL, Mapper, Service, and frontend calls to confirm the ID representation is consistent.

## Prohibitions

- Do not use `Serializable` as the end-to-end primary key type.
- Do not let external requests write a PO's `id`, `deleted`, `version`, or audit fields directly.
- Do not force `BasePO` inheritance onto logs, Outbox, or immutable snapshots; such exceptions require documenting the data-model rationale, the ID type, and the lifecycle.

## Verification

```powershell
$env:JAVA_HOME='<JAVA_21_HOME>'
./mvnw.cmd -B -pl <affected-module> test
```

Additionally search the business repository for bare `BasePO`, `(Long) ...getId()`, duplicate `@TableId`, and legacy `resolveId` logic, and run
at least one real Controller/Mapper integration regression.
