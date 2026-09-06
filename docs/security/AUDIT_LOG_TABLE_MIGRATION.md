# sys_audit_log 审计表 method 列扩宽迁移说明

- **适用版本**：由早期 FastDDD 框架自动建表（或按旧 DDL 手工建表）的 `sys_audit_log` 存量表
- **登记批次**：ECO-006C3（2026-09-06）；ECO-006C4 修订（2026-09-06）：删除存量表来源推断、补充迁移前核对项、收敛"无影响"表述范围
- **关联缺陷**：自动建表 DDL 曾使用 `method VARCHAR(10)`，而审计切面（`AuditLogAspect`）写入的方法标识格式为 `SimpleClassName.methodName`——绝大多数业务方法标识超过 10 个字符。已复现：MySQL 8 默认严格模式（`STRICT_TRANS_TABLES`）下，超长标识的 INSERT 报 "Data too long for column 'method'" 并失败，审计记录按既有契约回退 SLF4J 日志（不入库）；框架自动建表 DDL 已在 ECO-006C3 修正为 `method VARCHAR(200)`（与 `operation` 列一致，不截断方法标识）。

## 1. 谁需要迁移

**仅**满足以下全部条件的存量库需要迁移：

1. `sys_audit_log` 表的 `method` 列为 `VARCHAR(10)`；
2. 升级到 ECO-006C3 及之后的 FastDDD 版本。

关于存量窄列表的来源（ECO-006C3 实证补充，ECO-006C4 修订表述）：旧版框架的自动建表 DDL 存在两处缺陷——除 `method VARCHAR(10)` 外，主键定义未带列名（拼接结果 `(BIGINT PRIMARY KEY AUTO_INCREMENT, ...` 为非法 DDL，已在 ECO-006C3 一并修复为 `id BIGINT PRIMARY KEY AUTO_INCREMENT`）；旧版自动建表在已验证环境（MySQL 8）中无法成功执行。**本说明不据此推断所有历史 `sys_audit_log` 表的来源**：各项目的存量表可能经自有脚本（schema.sql / Flyway / init SQL）、历史运维操作或其他途径建立，请以下一节查得的实际列属性为准，并与贵项目的建表记录核对后再决定迁移。

**不需要迁移**：

- 全新部署：新 DDL 直接创建 `method VARCHAR(200)`；
- 表由消费者自建（schema.sql / Flyway 等自有脚本）且 `method` 列宽已足够（≥ 方法标识实际长度）；
- 无数据库消费者（审计走 SLF4J 回退，与本表无关）。

## 2. 迁移前核对（列宽判定 + 列属性与执行影响评估）

先查询确认列宽（MySQL 8 示例）：

```sql
SELECT CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_audit_log'
  AND COLUMN_NAME = 'method';
```

结果为 `10` → 按下节迁移；为 `200` → 无需操作。

**除列宽外，迁移前还须核对实际列属性**——`MODIFY COLUMN` 会整体重述列定义，未显式指定的属性（字符集、排序规则、可空性）将按默认值重设；若现状与表默认不一致，须在迁移语句中显式带上：

```sql
-- 列属性核对：类型、可空性、字符集与排序规则
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, CHARACTER_SET_NAME, COLLATION_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_audit_log'
  AND COLUMN_NAME = 'method';
```

**并评估执行影响**（容量与窗口）：

```sql
-- 行数与数据体量：决定 ALTER 的表重建时长与锁表窗口
SELECT TABLE_ROWS, ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 1) AS size_mb
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_audit_log';
```

大表、主从复制（大表 DDL 的复制延迟）、以及以该表为输入的下游（审计归档、BI 抽取）请在执行前按贵方 DBA 惯例逐项确认。

## 3. 迁移 SQL（由 DBA 在维护窗口执行，框架不会自动执行）

`CREATE TABLE IF NOT EXISTS` **不会修改已存在的表结构**，因此框架升级后存量窄列不会被自动扩宽——必须显式执行迁移：

```sql
-- MySQL 8.x（已在专用隔离测试库演练：扩宽后历史数据保留；演练不构成对任意存量库的通用结论，
-- 执行前请完成第 2 节核对）
ALTER TABLE sys_audit_log MODIFY COLUMN method VARCHAR(200);
```

其他自动建表支持的方言（**未在本批演练**，执行前请按贵方环境验证）：

```sql
-- PostgreSQL
ALTER TABLE sys_audit_log ALTER COLUMN method TYPE VARCHAR(200);
-- Oracle
ALTER TABLE sys_audit_log MODIFY (method VARCHAR2(200));
-- SQL Server
ALTER TABLE sys_audit_log ALTER COLUMN method NVARCHAR(200);
```

## 4. 迁移行为与回退

- **数据保留**：`MODIFY COLUMN` 仅扩宽 VARCHAR 长度、不改类型与约束。ECO-006C3 的演练结论仅针对该次演练环境（专用隔离 MySQL 8 测试库、小体量数据：迁移前写入的历史行在迁移后原样保留，迁移后新审计记录以完整方法标识入库）；**本说明不泛称对所有存量表无影响**——实际影响取决于各库该表的真实体量、列属性与复制/下游拓扑，须按第 2 节逐项核对后由 DBA 评估。
- **不自动操作用户数据库**：框架启动时只执行 `CREATE TABLE IF NOT EXISTS`，绝不执行 `ALTER`；本迁移由各项目 DBA 自主决定时机。
- **迁移前的失败模式**（已复现）：窄列 + 严格模式下，超长方法标识的审计写入失败并回退 SLF4J（应用日志可 grep `审计日志入库失败`；短于等于 10 字符的标识仍可正常入库——是否受影响取决于各方法标识实际长度）。迁移完成后该路径消失。
- **无需停机窗口的最低要求**：`ALTER ... MODIFY COLUMN` 在 MySQL 8 对该表会重建（小表通常瞬时完成）；审计表体积较大时请按贵方 DBA 惯例评估锁表窗口。

## 5. 验证（迁移后抽查）

```sql
-- 新触发一次 @AuditLog 入口后，确认完整方法标识入库（未被截断）
SELECT method, operation, success, create_time
FROM sys_audit_log
ORDER BY create_time DESC
LIMIT 5;
```

`method` 应为完整的 `SimpleClassName.methodName`（如 `OrderAdminController.createOrder`），无截断。
