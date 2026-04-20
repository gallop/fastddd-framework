# FastDDD 建表功能开发任务文档

> 文档版本: v1.0
> 创建日期: 2025-01-11
> 预计工期: 16.5天

---

## 一、功能概述

### 1.1 核心目标

实现一个完整的**数据库表结构可视化设计与生成系统**，支持：

- 🎨 **可视化设计**: 拖拽式表结构设计
- 📦 **模板驱动**: 预置5种业务模板
- 🧠 **智能建议**: AI驱动的字段/索引配置推荐
- 📜 **版本控制**: 完整的变更历史与对比
- 🌐 **多数据库**: MySQL/PostgreSQL/Oracle/SQL Server支持

### 1.2 功能架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    建表功能系统架构                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │  字段设计   │  │  索引设计   │  │  关系设计   │           │
│  └────────────┘  └────────────┘  └────────────┘           │
│         │               │               │                   │
│         └───────────────┴───────────────┘                   │
│                         │                                   │
│                    ┌────────────┐                            │
│                    │ 表设计核心  │                            │
│                    └────────────┘                            │
│                         │                                   │
│         ┌───────────────┼───────────────┐                   │
│         ▼               ▼               ▼                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐           │
│  │  版本控制   │  │  SQL生成    │  │  智能建议   │           │
│  └────────────┘  └────────────┘  └────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 二、数据模型设计

### 2.1 ER图

```
┌─────────────────┐       ┌──────────────────┐
│  table_design   │ 1     N│ table_design_fiel│
│  (表设计主表)    │───────│d (表字段设计)     │
│                 │       │                  │
│ id              │       │ id               │
│ name            │       │ design_id        │
│ status          │       │ field_name       │
│ version         │       │ field_type       │
│ ...             │       │ ...              │
└─────────────────┘       └──────────────────┘
        │
        │ 1
        │
        N
┌─────────────────┐       ┌──────────────────┐
│ table_design_i  │1     N│ table_design_vers │
│ ndex (索引设计)   │───────│ion (版本历史)     │
│                 │       │                  │
│ id              │       │ id               │
│ design_id       │       │ design_id        │
│ index_name      │       │ version          │
│ index_type      │       │ design_snapshot   │
│ ...             │       │ change_type      │
└─────────────────┘       └──────────────────┘

┌─────────────────┐       ┌──────────────────┐
│ table_design_r  │       │  table_template   │
│ elation (关系)    │       │  (表模板)         │
│                 │       │                  │
│ id              │       │ id               │
│ design_id       │       │ name             │
│ relation_type   │       │ category         │
│ ...             │       │ template_config  │
└─────────────────┘       └──────────────────┘
```

### 2.2 表结构清单

| 表名 | 说明 | 记录数估算 |
|------|------|-----------|
| table_design | 表设计主表 | 1000+ |
| table_design_field | 表字段设计 | 10000+ |
| table_design_index | 表索引设计 | 3000+ |
| table_design_relation | 表关系设计 | 500+ |
| table_design_version | 版本历史 | 5000+ |
| table_template | 表模板 | 20+ |

---

## 三、开发进度

### ✅ 阶段0: 准备工作（已完成）

- [x] 创建6张数据库表
- [x] 创建6个实体类
- [x] 创建6个Mapper接口
- [x] 插入5个系统模板

**创建的文件列表**:
```
实体类:
├── TableDesign.java              # 表设计主实体
├── TableDesignField.java         # 字段设计实体
├── TableDesignIndex.java         # 索引设计实体
├── TableDesignRelation.java      # 关系设计实体
├── TableDesignVersion.java       # 版本历史实体
└── TableTemplate.java            # 模板实体

Mapper接口:
├── TableDesignMapper.java
├── TableDesignFieldMapper.java
├── TableDesignIndexMapper.java
├── TableDesignRelationMapper.java
├── TableDesignVersionMapper.java
└── TableTemplateMapper.java
```

### 🔄 阶段1: Service层开发（进行中）

**待创建文件**:
```
Service层:
├── TableDesignService.java        # 表设计核心服务
├── TableDesignFieldService.java   # 字段管理服务
├── TableDesignIndexService.java   # 索引管理服务
├── TableDesignVersionService.java # 版本管理服务
├── TableTemplateService.java      # 模板管理服务
└── DatabaseDialectService.java    # 数据库方言服务

方言实现:
├── DatabaseDialect.java           # 方言接口
├── MySQLDialect.java              # MySQL实现
└── PostgreSQLDialect.java         # PostgreSQL实现
```

### ⏳ 后续阶段

| 阶段 | 功能 | 状态 | 预估工期 |
|:---:|:---|:---:|:---:|
| 0 | 准备工作 | ✅ 完成 | 0.5天 |
| 1 | Service层 | ⏳ 进行中 | 2天 |
| 2 | Controller层 | ⏸️ 待开始 | 1.5天 |
| 3 | 数据库方言 | ⏸️ 待开始 | 2天 |
| 4 | 前端页面 | ⏸️ 待开始 | 4天 |
| 5 | 智能建议 | ⏸️ 待开始 | 2天 |
| 6 | 版本控制 | ⏸️ 待开始 | 3天 |

---

## 四、核心接口设计

### 4.1 表设计接口

```java
@RestController
@RequestMapping("/api/table-design")
public class TableDesignController {

    // === 表设计CRUD ===
    POST   /api/table-design                    # 创建表设计
    GET    /api/table-design/{id}               # 获取表设计详情
    PUT    /api/table-design/{id}               # 更新表设计
    DELETE /api/table-design/{id}               # 删除表设计
    GET    /api/table-design                    # 分页查询表设计
    GET    /api/table-design/list               # 获取列表（不分页）

    // === 字段管理 ===
    POST   /api/table-design/{id}/fields         # 添加字段
    PUT    /api/table-design/{id}/fields/{fieldId} # 更新字段
    DELETE /api/table-design/{id}/fields/{fieldId} # 删除字段
    POST   /api/table-design/{id}/fields/reorder  # 字段排序

    // === 索引管理 ===
    POST   /api/table-design/{id}/indexes         # 添加索引
    PUT    /api/table-design/{id}/indexes/{indexId} # 更新索引
    DELETE /api/table-design/{id}/indexes/{indexId} # 删除索引

    // === 版本管理 ===
    GET    /api/table-design/{id}/versions        # 获取版本历史
    POST   /api/table-design/{id}/versions/{version}/restore # 恢复版本
    GET    /api/table-design/{id}/diff            # 对比两个版本
    POST   /api/table-design/{id}/publish         # 发布新版本

    // === SQL生成 ===
    POST   /api/table-design/preview-sql          # 预览建表SQL
    GET    /api/table-design/{id}/ddl             # 获取DDL

    // === 模板 ===
    GET    /api/table-design/templates            # 获取模板列表
    POST   /api/table-design/from-template        # 基于模板创建
    POST   /api/table-design/save-as-template     # 保存为模板

    // === 智能建议 ===
    GET    /api/table-design/suggest/field        # 字段配置建议
    GET    /api/table-design/suggest/index        # 索引建议
    POST   /api/table-design/apply-suggestions    # 应用建议
}
```

### 4.2 DTO设计

```java
// 表设计DTO
TableDesignDTO {
    id, name, comment, dataSourceId, status, version,
    environment, extendBasePo, fields[], indexes[], relations[]
}

// 字段设计DTO
TableDesignFieldDTO {
    id, designId, fieldName, displayName, fieldType,
    length, precision, scale, nullable, defaultValue,
    isPrimaryKey, isAutoIncrement, comment, sortOrder, source
}

// 索引设计DTO
TableDesignIndexDTO {
    id, designId, indexName, indexType, indexMethod,
    columns: [{columnName, order}], comment
}

// 版本对比DTO
VersionDiffDTO {
    oldVersion, newVersion,
    fieldChanges: [{type, fieldName, old, new}],
    indexChanges: [{type, indexName, old, new}],
    ddl: "ALTER TABLE ..."
}
```

---

## 五、智能建议规则

### 5.1 字段类型建议引擎

```
字段名模式 → 推荐配置

规则库:
┌──────────────────┬──────────────────────────────────────┐
│ 字段名模式        │ 推荐配置                              │
├──────────────────┼──────────────────────────────────────┤
│ *_id             │ BIGINT + 索引 + 外键建议             │
│ email            │ VARCHAR(100) + UNIQUE + 邮箱验证      │
│ phone/*_phone    │ VARCHAR(20) + 手机号验证              │
│ status/*_state   │ VARCHAR(20) + 默认值 + CHECK约束     │
│ *_time/*_date    │ TIMESTAMP/DATETIME + DEFAULT NOW()   │
│ is_*/*_flag      │ BOOLEAN/TINYINT(1)                   │
│ amount/*_money   │ DECIMAL(19,2) + CHECK(>=0)           │
│ description      │ TEXT/CLOB                            │
│ sort/*_order     │ INT + 索引                            │
│ create_by        │ VARCHAR(64)                           │
│ update_by        │ VARCHAR(64)                           │
└──────────────────┴──────────────────────────────────────┘
```

### 5.2 索引建议引擎

```
触发条件 → 索引建议

规则:
┌──────────────────────────────┬───────────────────────────┐
│ 触发条件                      │ 索引建议                   │
├──────────────────────────────┼───────────────────────────┤
│ 外键字段                      │ 普通索引                   │
│ UNIQUE约束字段                │ 唯一索引                   │
│ 高频查询字段(如status)         │ 普通索引                   │
│ 多字段组合查询                │ 联合索引                   │
│ 全文搜索字段                  │ FULLTEXT索引               │
└──────────────────────────────┴───────────────────────────┘
```

---

## 六、数据库方言设计

### 6.1 接口定义

```java
public interface DatabaseDialect {
    // 获取数据库类型
    DatabaseType getType();

    // 字段类型映射 (Java -> SQL)
    String mapFieldType(String javaType, Integer length,
                      Integer precision, Integer scale);

    // 生成建表DDL
    String generateCreateTableSQL(TableDesign design);

    // 生成建索引DDL
    String generateCreateIndexSQL(TableDesignIndex index);

    // 数据库特定选项
    String getTableOptions();
    String getAutoIncrementSyntax();
    boolean supportsComment();
}
```

### 6.2 MySQL方言实现要点

```java
public class MySQLDialect implements DatabaseDialect {
    // 类型映射示例
    String -> VARCHAR
    Integer -> INT
    Long -> BIGINT
    BigDecimal -> DECIMAL(p,s)
    Boolean -> TINYINT(1)
    LocalDateTime -> TIMESTAMP
    Date -> DATE

    // 建表选项
    ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci

    // 自增语法
    AUTO_INCREMENT
}
```

---

## 七、前端组件设计

### 7.1 页面结构

```
前端路由:
├── /table-design/list           # 表设计列表
├── /table-design/create         # 新建表设计
├── /table-design/edit/:id       # 编辑表设计
├── /table-design/view/:id       # 查看表设计
├── /table-design/templates      # 模板库
└── /table-design/versions/:id   # 版本历史
```

### 7.2 核心组件

```
components/table-design/
├── TableDesignEditor.vue         # 主编辑器
├── FieldDesigner.vue             # 字段设计器
│   └── FieldConfigDrawer.vue     # 字段配置抽屉
├── IndexDesigner.vue             # 索引设计器
├── TemplateGallery.vue           # 模板库
├── VersionDiffViewer.vue         # 版本对比
└── SQLPreview.vue               # SQL预览
```

---

## 八、内置模板列表

| 模板名 | 图标 | 说明 | 适用场景 |
|--------|------|------|----------|
| 单表模板 | table | 最简单的业务表 | 基础CRUD表 |
| 主从表模板 | copy | 1对N关系 | 订单-订单项 |
| 树形表模板 | share | 父子关系 | 部门树、菜单树 |
| 字典表模板 | notebook | 字典维护 | 系统字典 |
| 日志表模板 | document | 操作日志 | 审计日志 |

---

## 九、开发检查清单

### 阶段1: Service层 (2天)

- [ ] TableDesignService - CRUD操作
- [ ] TableDesignFieldService - 字段管理
- [ ] TableDesignIndexService - 索引管理
- [ ] TableDesignVersionService - 版本创建
- [ ] TableTemplateService - 模板应用
- [ ] DatabaseDialect接口设计
- [ ] MySQLDialect基础实现
- [ ] SQL生成服务

### 阶段2: Controller层 (1.5天)

- [ ] TableDesignController - 所有接口
- [ ] 请求参数验证
- [ ] 统一异常处理
- [ ] 权限注解配置
- [ ] API文档注解

### 阶段3: 数据库方言 (2天)

- [ ] 完善MySQLDialect
- [ ] 实现PostgreSQLDialect
- [ ] 字段类型映射测试
- [ ] DDL生成单元测试

### 阶段4-6: 前端与增强 (9天)

- [ ] 表设计编辑器
- [ ] 字段设计器
- [ ] 索引设计器
- [ ] 模板库
- [ ] 版本对比
- [ ] SQL预览
- [ ] 智能建议集成

---

## 十、快速启动指南

### 10.1 当前状态

**已完成**:
- ✅ 数据库表结构（6张表）
- ✅ 实体类（6个）
- ✅ Mapper接口（6个）
- ✅ 5个系统模板数据

**下一步**:
- ⏳ 创建Service层
- ⏳ 重启后端加载新表

### 10.2 继续开发命令

```bash
# 1. 重启后端（加载新表）
cd fastddd-studio-server
mvn spring-boot:run

# 2. 创建Service层（按顺序）
TableDesignService.java
TableDesignFieldService.java
TableDesignIndexService.java
TableDesignVersionService.java
TableTemplateService.java

# 3. 创建方言接口和实现
DatabaseDialect.java (接口)
MySQLDialect.java (实现)
```

### 10.3 代码位置参考

```
后端源码:
fastddd-studio-server/src/main/java/com/gallop/fastddd/demo/
├── entity/        # 已创建6个实体
├── mapper/        # 已创建6个Mapper
├── service/       # ⏳ 待创建Service
├── controller/    # ⏸️ 待创建Controller
└── dialect/       # ⏸️ 待创建方言实现

数据库脚本:
fastddd-studio-server/src/main/resources/sql/
└── schema-h2.sql  # 已添加建表功能相关表
```

---

## 十一、技术栈与依赖

### 后端技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 框架 |
| MyBatis Plus | 3.5.3 | ORM |
| H2/MySQL | - | 数据库 |
| Lombok | - | 简化代码 |
| Jackson | - | JSON处理 |

### 前端技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Vue 3 | - | 框架 |
| TypeScript | - | 类型系统 |
| Element Plus | - | UI组件 |
| Pinia | - | 状态管理 |
| Vite | - | 构建工具 |

---

## 十二、注意事项

### 12.1 开发规范

1. **命名规范**: 遵循FastDDD的命名约定
2. **异常处理**: 统一使用ApiResponse包装
3. **权限控制**: 使用@PreAuthorize注解
4. **审计日志**: 关键操作添加@AuditLog

### 12.2 数据库约束

1. **软删除**: 所有表使用deleted字段
2. **乐观锁**: table_design使用version字段
3. **级联删除**: 字段/索引/关系随主表删除

### 12.3 JSON字段

1. **索引字段**: columns存储JSON数组
2. **设计快照**: design_snapshot存储完整JSON
3. **模板配置**: template_config存储JSON配置

---

**文档结束**

*最后更新: 2025-01-11*
*下次继续: 从Service层开发开始*
