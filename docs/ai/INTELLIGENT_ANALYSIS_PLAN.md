# FastDDD 一体化生成器 AI 智能化改造计划 v2.0

> **版本说明**：本计划基于专家评审进行了全面优化，主要改进：
> - 规则引擎优先，LLM 作为增强选项
> - 前端流程简化，AI 分类嵌入表选择步骤
> - 新增聚合识别功能（DDD 核心）
> - 优化包结构设计（按聚合边界划分）
> - 调整实施顺序，核心产品功能前置

---

## Context

### Why This Change Is Needed

当前 FastDDD 一体化生成器存在以下核心问题：

1. **全局模型类型限制**：所有表必须统一使用贫血模型或充血模型，无法根据业务复杂度灵活选择
2. **缺乏智能分析**：用户需要手动判断每个表应该使用哪种模型，容易出错
3. **配置不可复用**：每次生成都需要重新配置，无法保存为模板
4. **表选择效率低**：大量表时选择困难，缺少智能推荐
5. **无代码预览**：生成后才能看到代码，修改成本高
6. **缺少聚合识别**：无法识别 DDD 聚合边界，这是 DDD 的核心能力

### Intended Outcome

通过引入智能分析技术，实现：

1. **智能表分类**：基于规则引擎自动推荐贫血/充血模型，LLM 作为可选增强
2. **聚合识别**：自动识别聚合边界和聚合根，这是 DDD 的核心能力
3. **混合模型支持**：同一项目中不同聚合使用不同模型类型
4. **配置模板化**：保存和复用生成配置
5. **增强用户体验**：简化的表选择界面和代码预览

---

## Architecture Design

### 系统架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Frontend (Vue 3)                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 1: Basic Config → Step 2: Data Source → Step 3: Table Selection       │
│                                                    ↓                          │
│                                            [嵌入智能分析]                      │
│  → Step 4: Code Config → Step 5: Generate                                   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Backend API (Spring Boot)                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  /api/unified-generator/tables              - 现有：获取表列表                 │
│  /api/unified-generator/analyze             - 新增：智能分析（规则+可选LLM）   │
│  /api/unified-generator/aggregate-groups    - 新增：获取聚合分组              │
│  /api/unified-generator/aggregate-graph     - 新增：聚合关系图                │
│  /api/unified-generator/preview             - 新增：生成代码预览              │
│  /api/unified-generator/templates           - 新增：配置模板管理              │
└─────────────────────────────────────────────────────────────────────────────┘
                                      ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Intelligent Analysis Engine                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  TableAnalysisService              - 表分析服务（主入口）                      │
│  ↓                                                                                     │
│  AggregateDetector             - 聚合检测器（识别聚合边界）                         │
│  ↓                                                                                     │
│  ModelTypeClassifier           - 模型类型分类器（贫血/充血）                         │
│  ↓                                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐                │
│  │ Strategy Chain (责任链模式)                                  │                │
│  ├─────────────────────────────────────────────────────────────┤                │
│  │ 1. RuleBasedStrategy (主要)     - 增强规则引擎               │                │
│  │ 2. LLMEnhancedStrategy (可选)   - LLM 增强分析               │                │
│  └─────────────────────────────────────────────────────────────┘                │
│  ↓                                                                                     │
│  RelationshipAnalyzer          - 关系分析器（外键、引用关系）                       │
│  ↓                                                                                     │
│  ComplexityScorer             - 复杂度评分器（多维度评分）                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 数据流设计

```
用户选择表
    ↓
收集增强元数据（外键、索引、引用关系）
    ↓
智能分析引擎
    ↓
┌────────────────────────────────────────────────────────────────────┐
│ 输入：表元数据 + 关系信息                                              │
│   - 表名、列名、注释、数据类型                                          │
│   - 外键关系、引用关系                                                 │
│   - 索引信息                                                          │
│                                                                     │
│ 处理：                                                                 │
│   1. 聚合检测：识别聚合边界、聚合根、实体、值对象                        │
│   2. 模型分类：判断贫血/充血模型（规则引擎优先）                         │
│   3. 复杂度评分：多维度评分（列数、关系、状态字段、金额字段等）            │
│   4. 关系分析：构建聚合依赖图                                           │
│                                                                     │
│ 输出：TableAnalysisResult                                            │
│   - tableName: string                                                │
│   - modelType: ANEMIC | RICH                                         │
│   - aggregateInfo: { name, root, role }                              │
│   - confidence: 0.0 - 1.0                                            │
│   - reason: string                                                  │
│   - metrics: { complexityScore, relationshipCount, ... }             │
└────────────────────────────────────────────────────────────────────┘
    ↓
用户审核和调整（可选）
    ↓
代码生成（按聚合边界组织代码）
```

---

## Phase 1: 增强规则引擎与聚合识别 (Week 1-2)

### 1.1 模块结构

创建新模块：`fast-ddd-analyzer-spring-boot-starter`

```
fast-ddd-analyzer-spring-boot-starter/
├── pom.xml
└── src/main/java/com/gallop/fastddd/analyzer/
    ├── AutoConfiguration.java
    ├── properties/
    │   └── AnalyzerProperties.java
    ├── service/
    │   ├── TableAnalysisService.java
    │   └── AggregateDetectionService.java
    ├── classifier/
    │   ├── ModelTypeClassifier.java
    │   ├── model/
    │   │   ├── TableAnalysisResult.java
    │   │   ├── AggregateInfo.java
    │   │   └── AnalysisMetrics.java
    │   └── strategy/
    │       ├── ClassificationStrategy.java
    │       ├── EnhancedRuleBasedStrategy.java    # 增强规则引擎
    │       └── LLMEnhancedStrategy.java          # 可选 LLM 增强
    ├── detector/
    │   ├── AggregateDetector.java
    │   ├── AggregateRootDetector.java
    │   └── EntityRoleDetector.java
    ├── analyzer/
    │   ├── RelationshipAnalyzer.java
    │   ├── ComplexityScorer.java
    │   └── NamingPatternAnalyzer.java
    └── metadata/
        └── EnhancedMetadataCollector.java       # 收集外键关系
```

### 1.2 关键文件设计

**文件 1: `DomainModelType.java` - 独立的枚举定义**

```java
// fast-ddd-common/src/main/java/com/gallop/fastddd/common/enums/DomainModelType.java

package com.gallop.fastddd.common.enums;

import lombok.Getter;

/**
 * 领域模型类型
 *
 * @author gallop
 * @since 1.1.0
 */
@Getter
public enum DomainModelType {
    /**
     * 贫血模型 (Anemic Domain Model)
     * <p>适用于简单的 CRUD 操作，业务逻辑在 Service 层</p>
     */
    ANEMIC("贫血模型", "适用于配置、字典、日志等简单表"),

    /**
     * 充血模型 (Rich Domain Model)
     * <p>适用于复杂业务逻辑，业务逻辑封装在领域对象中</p>
     */
    RICH("充血模型", "适用于核心业务实体，包含状态转换、业务规则");

    private final String displayName;
    private final String description;

    DomainModelType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
```

**文件 2: `AggregateInfo.java` - 聚合信息模型**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/classifier/model/AggregateInfo.java

package com.gallop.fastddd.analyzer.classifier.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 聚合信息
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@Builder
public class AggregateInfo {
    /**
     * 聚合名称（从聚合根表名派生）
     */
    private String aggregateName;

    /**
     * 聚合根表名
     */
    private String rootTableName;

    /**
     * 当前表在聚合中的角色
     */
    private AggregateRole role;

    /**
     * 聚合内的所有表（包括聚合根）
     */
    private List<String> memberTables;

    /**
     * 聚合中的父表（如果有）
     */
    private String parentTable;

    /**
     * 与父表的关系类型
     */
    private RelationshipType relationshipType;

    /**
     * 聚合角色枚举
     */
    public enum AggregateRole {
        /**
         * 聚合根 - 独立的业务实体，是聚合的入口
         */
        AGGREGATE_ROOT,

        /**
         * 实体 - 聚合内的业务对象，通过 ID 引用
         */
        ENTITY,

        /**
         * 值对象 - 通过值嵌入，无独立标识
         */
        VALUE_OBJECT,

        /**
         * 未分组 - 独立表，不属于任何聚合
         */
        STANDALONE
    }

    /**
     * 关系类型枚举
     */
    public enum RelationshipType {
        /**
         * 一对多 (One-to-Many) - 最常见的聚合内关系
         */
        ONE_TO_MANY,

        /**
         * 一对一 (One-to-One) - 值对象模式
         */
        ONE_TO_ONE,

        /**
         * 多对一 (Many-to-One) - 引用其他聚合的聚合根
         */
        MANY_TO_ONE,

        /**
         * 无关系
         */
        NONE
    }
}
```

**文件 3: `TableAnalysisResult.java` - 分析结果模型**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/classifier/model/TableAnalysisResult.java

package com.gallop.fastddd.analyzer.classifier.model;

import com.gallop.fastddd.common.enums.DomainModelType;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 表分析结果
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@Builder
public class TableAnalysisResult {
    /**
     * 表名
     */
    private String tableName;

    /**
     * 推荐的模型类型
     */
    private DomainModelType modelType;

    /**
     * 分类置信度 (0.0 - 1.0)
     */
    private Double confidence;

    /**
     * 分类原因（自然语言解释）
     */
    private String reason;

    /**
     * 使用的分析策略
     */
    private String strategy;

    /**
     * 聚合信息
     */
    private AggregateInfo aggregateInfo;

    /**
     * 关键指标
     */
    private AnalysisMetrics metrics;

    /**
     * 推荐的模块名称
     */
    private String suggestedModule;

    /**
     * 其他属性（用于前端展示）
     */
    private Map<String, Object> attributes;
}
```

**文件 4: `EnhancedRuleBasedStrategy.java` - 增强规则引擎**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/classifier/strategy/EnhancedRuleBasedStrategy.java

package com.gallop.fastddd.analyzer.classifier.strategy;

import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.classifier.model.AnalysisMetrics;
import com.gallop.fastddd.codegen.model.TableMetadata;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import com.gallop.fastddd.common.enums.DomainModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 增强的基于规则的分类策略
 * <p>这是主要策略，不依赖外部 LLM API，响应快速且稳定</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
public class EnhancedRuleBasedStrategy implements ClassificationStrategy {

    // ==================== 规则配置 ====================

    // 贫血模型关键词（优先级最高）
    private static final Set<String> ANEMIC_PREFIXES = Set.of(
        "sys_", "dict_", "config_", "log_", "audit_", "temp_", "tmp_"
    );

    // 贫血模型后缀
    private static final Set<String> ANEMIC_SUFFIXES = Set.of(
        "_log", "_history", "_audit", "_config", "_relation", "_mapping"
    );

    // 贫血模型全名匹配
    private static final Set<String> ANEMIC_EXACT_MATCHES = Set.of(
        "dictionary", "lookup", "reference", "metadata", "settings",
        "字典", "配置", "字典表", "参数表"
    );

    // 充血模型关键词
    private static final Set<String> RICH_KEYWORDS = Set.of(
        "order", "product", "inventory", "payment", "shipment",
        "invoice", "contract", "account", "wallet", "coupon",
        "订单", "产品", "库存", "支付", "发货", "发票", "合同"
    );

    // 状态字段模式
    private static final Set<String> STATUS_FIELD_PATTERNS = Set.of(
        "status", "state", "order_status", "product_status",
        "payment_status", "audit_status", "verify_status"
    );

    // 金额字段模式
    private static final Set<String> AMOUNT_FIELD_PATTERNS = Set.of(
        "amount", "price", "cost", "fee", "discount", "total",
        "balance", "quota", "limit", "金额", "价格", "费用"
    );

    // 时间范围字段（可能有状态机）
    private static final Set<String> TIME_RANGE_FIELDS = Set.of(
        "start_time", "end_time", "begin_time", "expire_time",
        "effective_time", "valid_from", "valid_until"
    );

    // 子实体模式（通常属于某个聚合）
    private static final Set<String> ENTITY_SUFFIXES = Set.of(
        "_item", "_detail", "_line", "_entry", "_record",
        "_item", "项目", "明细", "记录"
    );

    @Override
    public boolean isAvailable() {
        return true; // 规则策略始终可用
    }

    @Override
    public String getName() {
        return "Enhanced-Rule-Based";
    }

    @Override
    public TableAnalysisResult classify(TableMetadata table, Map<String, Object> context) {
        String tableName = table.getTableName().toLowerCase();
        String tableComment = table.getTableComment() != null ?
            table.getTableComment().toLowerCase() : "";

        log.debug("分析表: {}, 列数: {}", table.getTableName(), table.getColumns().size());

        // ========== 规则 1: 明显的贫血模型表（高置信度） ==========
        if (isAnemicTable(tableName, tableComment)) {
            return buildResult(table, DomainModelType.ANEMIC, 0.9, "规则匹配：系统/配置/字典表模式");
        }

        // ========== 规则 2: 子实体表（跟随聚合根） ==========
        if (isEntityTable(tableName)) {
            // 子实体的模型类型由聚合根决定，这里返回一个标记
            TableAnalysisResult result = buildResult(table, DomainModelType.RICH, 0.75,
                "子实体表，跟随聚合根的模型类型");
            result.getAttributes().put("isEntity", true);
            return result;
        }

        // ========== 规则 3: 明显的充血模型表（核心业务实体） ==========
        if (isRichTable(tableName, tableComment, table)) {
            return buildResult(table, DomainModelType.RICH, 0.85, "核心业务实体，包含状态/金额字段");
        }

        // ========== 规则 4: 基于复杂度评分判断 ==========
        double complexityScore = calculateComplexityScore(table);
        if (complexityScore > 0.6) {
            return buildResult(table, DomainModelType.RICH, 0.7 + complexityScore * 0.2,
                String.format("复杂度评分 %.2f 超过阈值，建议充血模型", complexityScore));
        }

        // ========== 默认：贫血模型 ==========
        return buildResult(table, DomainModelType.ANEMIC, 0.6, "默认分类：简单 CRUD 表");
    }

    /**
     * 判断是否为贫血模型表
     */
    private boolean isAnemicTable(String tableName, String tableComment) {
        // 前缀匹配
        if (ANEMIC_PREFIXES.stream().anyMatch(tableName::startsWith)) {
            return true;
        }

        // 后缀匹配
        if (ANEMIC_SUFFIXES.stream().anyMatch(tableName::endsWith)) {
            return true;
        }

        // 注释匹配
        if (ANEMIC_EXACT_MATCHES.stream().anyMatch(tableComment::contains)) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否为子实体表
     */
    private boolean isEntityTable(String tableName) {
        return ENTITY_SUFFIXES.stream().anyMatch(tableName::endsWith);
    }

    /**
     * 判断是否为充血模型表
     */
    private boolean isRichTable(String tableName, String tableComment, TableMetadata table) {
        boolean hasRichKeyword = RICH_KEYWORDS.stream().anyMatch(tableName::contains) ||
                               RICH_KEYWORDS.stream().anyMatch(tableComment::contains);
        boolean hasStatus = hasStatusField(table);
        boolean hasAmount = hasAmountField(table);
        boolean hasManyColumns = table.getColumns().size() >= 10;

        // 核心业务关键词 + (状态字段 或 金额字段 或 列数多)
        return hasRichKeyword && (hasStatus || hasAmount || hasManyColumns);
    }

    /**
     * 检测是否有状态字段
     */
    private boolean hasStatusField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> STATUS_FIELD_PATTERNS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 检测是否有金额字段
     */
    private boolean hasAmountField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> {
                String colName = c.getColumnName().toLowerCase();
                return AMOUNT_FIELD_PATTERNS.stream().anyMatch(colName::contains) ||
                       c.getDataTypeName().toLowerCase().contains("decimal") ||
                       c.getDataTypeName().toLowerCase().contains("money");
            });
    }

    /**
     * 计算复杂度评分（多维度）
     */
    private double calculateComplexityScore(TableMetadata table) {
        double score = 0.0;

        // 1. 列数 (0-0.2)
        int columnCount = table.getColumns().size();
        score += Math.min(columnCount / 25.0, 0.2);

        // 2. 状态字段 (0.15)
        if (hasStatusField(table)) score += 0.15;

        // 3. 金额字段 (0.15) - 需要业务规则
        if (hasAmountField(table)) score += 0.15;

        // 4. 外键关系 (0-0.25) - 从 context 获取
        Integer fkCount = (Integer) table.getAttributes().getOrDefault("foreignKeyCount", 0);
        score += Math.min(fkCount * 0.08, 0.25);

        // 5. 复合主键 (0.1)
        if (table.getPrimaryKeys().size() > 1) score += 0.1;

        // 6. 时间范围字段（可能有状态机）(0.05)
        boolean hasTimeRange = table.getColumns().stream()
            .anyMatch(c -> TIME_RANGE_FIELDS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
        if (hasTimeRange) score += 0.05;

        // 7. 被其他表引用（聚合根候选）(0.1)
        Boolean isReferenced = (Boolean) table.getAttributes().getOrDefault("isReferencedByOthers", false);
        if (isReferenced) score += 0.1;

        return Math.min(score, 1.0);
    }

    /**
     * 构建分类结果
     */
    private TableAnalysisResult buildResult(TableMetadata table, DomainModelType modelType,
                                             double confidence, String reason) {
        double complexityScore = calculateComplexityScore(table);

        return TableAnalysisResult.builder()
            .tableName(table.getTableName())
            .modelType(modelType)
            .confidence(confidence)
            .reason(reason)
            .strategy(getName())
            .metrics(AnalysisMetrics.builder()
                .columnCount(table.getColumns().size())
                .relationshipCount((Integer) table.getAttributes()
                    .getOrDefault("foreignKeyCount", 0))
                .complexityScore(complexityScore)
                .hasStateMachine(hasStatusField(table))
                .hasBusinessRules(hasAmountField(table))
                .isAggregateRoot(Boolean.TRUE.equals(table.getAttributes()
                    .getOrDefault("isReferencedByOthers", false)))
                .build())
            .attributes(new HashMap<>())
            .build();
    }
}
```

**文件 5: `AggregateDetector.java` - 聚合检测器**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/detector/AggregateDetector.java

package com.gallop.fastddd.analyzer.detector;

import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.codegen.model.TableMetadata;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 聚合检测器
 * <p>基于外键关系和命名模式识别聚合边界</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
public class AggregateDetector {

    // 子实体后缀模式
    private static final Set<String> ENTITY_SUFFIXES = Set.of(
        "_item", "_detail", "_line", "_entry", "_record",
        "项目", "明细", "记录"
    );

    /**
     * 检测聚合分组
     *
     * @param tables 所有表
     * @param relationships 外键关系 (tableName -> List<referencedTable>)
     * @return 聚合分组 (aggregateName -> List<tableName>)
     */
    public Map<String, List<String>> detectAggregates(
            List<TableMetadata> tables,
            Map<String, List<String>> relationships) {

        Map<String, List<String>> aggregates = new HashMap<>();
        Set<String> processed = new HashSet<>();

        for (TableMetadata table : tables) {
            if (processed.contains(table.getTableName())) {
                continue;
            }

            String tableName = table.getTableName();

            // 检查是否是聚合根
            if (isPotentialAggregateRoot(tableName, relationships.get(tableName))) {
                String aggregateName = deriveAggregateName(tableName);
                List<String> members = findAggregateMembers(tableName, relationships);
                aggregates.put(aggregateName, members);
                processed.addAll(members);
            } else if (!hasParentTable(tableName, relationships)) {
                // 独立表（不属于任何聚合）
                aggregates.put(tableName, List.of(tableName));
                processed.add(tableName);
            }
        }

        log.info("检测到 {} 个聚合", aggregates.size());
        return aggregates;
    }

    /**
     * 判断是否为潜在的聚合根
     */
    private boolean isPotentialAggregateRoot(String tableName, List<String> referencedTables) {
        // 规则 1: 不以子实体后缀结尾
        if (ENTITY_SUFFIXES.stream().anyMatch(tableName::endsWith)) {
            return false;
        }

        // 规则 2: 被其他表引用（通常是聚合根的标志）
        // 这个信息需要在调用前设置到 table attributes 中
        return true;
    }

    /**
     * 查找聚合成员
     */
    private List<String> findAggregateMembers(String rootTable, Map<String, List<String>> relationships) {
        List<String> members = new ArrayList<>();
        members.add(rootTable);

        // 查找直接引用聚合根的表（子实体）
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            String table = entry.getKey();
            List<String> refs = entry.getValue();

            if (refs.contains(rootTable) && isChildEntity(table)) {
                members.add(table);
            }
        }

        return members;
    }

    /**
     * 判断是否为子实体
     */
    private boolean isChildEntity(String tableName) {
        return ENTITY_SUFFIXES.stream().anyMatch(tableName::endsWith);
    }

    /**
     * 检查是否有父表
     */
    private boolean hasParentTable(String tableName, Map<String, List<String>> relationships) {
        List<String> refs = relationships.get(tableName);
        return refs != null && !refs.isEmpty() && isChildEntity(tableName);
    }

    /**
     * 从表名派生聚合名称
     */
    private String deriveAggregateName(String tableName) {
        // 移除常见前缀
        String name = tableName.replaceAll("^t_", "")
                                 .replaceAll("^sys_", "")
                                 .replaceAll("^tbl_", "");

        // 移除子实体后缀（不应该出现在聚合根名称中）
        for (String suffix : ENTITY_SUFFIXES) {
            name = name.replaceAll(suffix + "$", "");
        }

        return name;
    }

    /**
     * 为每个表分配聚合信息
     */
    public AggregateInfo assignAggregateInfo(
            String tableName,
            Map<String, List<String>> aggregates,
            Map<String, List<String>> relationships) {

        // 查找表所属的聚合
        for (Map.Entry<String, List<String>> entry : aggregates.entrySet()) {
            String aggregateName = entry.getKey();
            List<String> members = entry.getValue();

            if (members.contains(tableName)) {
                String rootTable = members.get(0);
                AggregateInfo.AggregateRole role = determineRole(tableName, rootTable, members);

                return AggregateInfo.builder()
                    .aggregateName(aggregateName)
                    .rootTableName(rootTable)
                    .role(role)
                    .memberTables(members)
                    .parentTable(role == AggregateInfo.AggregateRole.AGGREGATE_ROOT ? null : rootTable)
                    .relationshipType(determineRelationshipType(tableName, rootTable, role))
                    .build();
            }
        }

        // 独立表
        return AggregateInfo.builder()
            .aggregateName(tableName)
            .rootTableName(tableName)
            .role(AggregateInfo.AggregateRole.STANDALONE)
            .memberTables(List.of(tableName))
            .relationshipType(AggregateInfo.RelationshipType.NONE)
            .build();
    }

    private AggregateInfo.AggregateRole determineRole(String tableName, String rootTable, List<String> members) {
        if (tableName.equals(rootTable)) {
            return AggregateInfo.AggregateRole.AGGREGATE_ROOT;
        }
        if (isChildEntity(tableName)) {
            return AggregateInfo.AggregateRole.ENTITY;
        }
        return AggregateInfo.AggregateRole.VALUE_OBJECT;
    }

    private AggregateInfo.RelationshipType determineRelationshipType(
            String tableName, String rootTable, AggregateInfo.AggregateRole role) {
        return switch (role) {
            case AGGREGATE_ROOT -> AggregateInfo.RelationshipType.NONE;
            case ENTITY -> AggregateInfo.RelationshipType.ONE_TO_MANY;
            case VALUE_OBJECT -> AggregateInfo.RelationshipType.ONE_TO_ONE;
            case STANDALONE -> AggregateInfo.RelationshipType.NONE;
        };
    }
}
```

**文件 6: `RelationshipAnalyzer.java` - 关系分析器**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/analyzer/RelationshipAnalyzer.java

package com.gallop.fastddd.analyzer.analyzer;

import com.gallop.fastddd.codegen.model.TableMetadata;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 关系分析器
 * <p>分析表之间的外键关系和引用关系</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
public class RelationshipAnalyzer {

    /**
     * 分析表之间的关系
     *
     * @param tables 所有表
     * @return 关系映射 (tableName -> List<referencedTableName>)
     */
    public Map<String, List<String>> analyzeRelationships(List<TableMetadata> tables) {
        Map<String, List<String>> relationships = new HashMap<>();

        // 构建表名到元数据的映射
        Map<String, TableMetadata> tableMap = new HashMap<>();
        for (TableMetadata table : tables) {
            tableMap.put(table.getTableName().toLowerCase(), table);
        }

        // 分析每个表的外键关系
        for (TableMetadata table : tables) {
            List<String> referencedTables = new ArrayList<>();

            for (ColumnMetadata column : table.getColumns()) {
                // 检测外键（基于命名模式）
                String referencedTable = detectReferencedTable(column, tableMap);
                if (referencedTable != null) {
                    referencedTables.add(referencedTable);
                }
            }

            // 统计被引用次数（用于判断是否是聚合根）
            int referenceCount = (int) tables.stream()
                .filter(t -> t != table)
                .filter(t -> referencesTable(t, table.getTableName()))
                .count();

            table.getAttributes().put("foreignKeyCount", referencedTables.size());
            table.getAttributes().put("isReferencedByOthers", referenceCount > 0);
            table.getAttributes().put("referenceCount", referenceCount);

            if (!referencedTables.isEmpty()) {
                relationships.put(table.getTableName(), referencedTables);
                log.debug("表 {} 引用了: {}", table.getTableName(), referencedTables);
            }
        }

        return relationships;
    }

    /**
     * 检测列引用的表（基于命名模式）
     */
    private String detectReferencedTable(ColumnMetadata column, Map<String, TableMetadata> tableMap) {
        String colName = column.getColumnName().toLowerCase();

        // 模式 1: xxx_id -> xxx 表
        if (colName.endsWith("_id") && !colName.equals("id")) {
            String potentialTable = colName.substring(0, colName.length() - 3);

            // 尝试匹配表名
            if (tableMap.containsKey(potentialTable)) {
                return potentialTable;
            }

            // 尝试匹配复数形式（user_id -> users）
            if (tableMap.containsKey(potentialTable + "s")) {
                return potentialTable + "s";
            }

            // 尝试匹配 t_xxx 模式
            if (tableMap.containsKey("t_" + potentialTable)) {
                return "t_" + potentialTable;
            }
        }

        // 模式 2: xxx_no 可能引用订单表
        if (colName.contains("order_no") || colName.contains("订单号")) {
            for (String tableName : tableMap.keySet()) {
                if (tableName.toLowerCase().contains("order")) {
                    return tableName;
                }
            }
        }

        return null;
    }

    /**
     * 检查表是否引用了另一个表
     */
    private boolean referencesTable(TableMetadata table, String targetTableName) {
        String targetLower = targetTableName.toLowerCase();

        for (ColumnMetadata column : table.getColumns()) {
            String colName = column.getColumnName().toLowerCase();

            // 检查外键命名模式
            if (colName.equals(targetLower + "_id")) {
                return true;
            }

            // 检查组合模式
            if (colName.endsWith("_id")) {
                String potentialRef = colName.substring(0, colName.length() - 3);
                if (potentialRef.equals(targetLower)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 构建关系图数据（用于前端可视化）
     */
    public GraphData buildGraphData(List<TableMetadata> tables, Map<String, List<String>> relationships) {
        GraphData graph = new GraphData();
        Set<String> nodes = new HashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        // 添加节点
        for (TableMetadata table : tables) {
            nodes.add(table.getTableName());
        }

        // 添加边
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                if (nodes.contains(to)) {
                    edges.add(new GraphEdge(from, to, "REFERENCES"));
                }
            }
        }

        graph.setNodes(new ArrayList<>(nodes));
        graph.setEdges(edges);
        return graph;
    }

    /**
     * 图数据模型
     */
    @lombok.Data
    public static class GraphData {
        private List<String> nodes;
        private List<GraphEdge> edges;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GraphEdge {
        private String from;
        private String to;
        private String label;
    }
}
```

**文件 7: `TableAnalysisService.java` - 主服务入口**

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/service/TableAnalysisService.java

package com.gallop.fastddd.analyzer.service;

import com.gallop.fastddd.analyzer.analyzer.ComplexityScorer;
import com.gallop.fastddd.analyzer.analyzer.RelationshipAnalyzer;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.classifier.strategy.ClassificationStrategy;
import com.gallop.fastddd.analyzer.detector.AggregateDetector;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 表分析服务
 * <p>主入口，协调各个分析组件</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableAnalysisService {

    private final List<ClassificationStrategy> strategies;
    private final RelationshipAnalyzer relationshipAnalyzer;
    private final AggregateDetector aggregateDetector;
    private final ComplexityScorer complexityScorer;

    /**
     * 分析多个表
     *
     * @param tables 表列表
     * @return 分析结果映射
     */
    public Map<String, TableAnalysisResult> analyzeTables(List<TableMetadata> tables) {
        log.info("开始分析 {} 个表", tables.size());

        // Step 1: 分析表关系
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        log.debug("检测到 {} 个表有关系", relationships.size());

        // Step 2: 检测聚合
        Map<String, List<String>> aggregates = aggregateDetector.detectAggregates(tables, relationships);
        log.info("检测到 {} 个聚合", aggregates.size());

        // Step 3: 分类每个表
        Map<String, TableAnalysisResult> results = new HashMap<>();
        for (TableMetadata table : tables) {
            TableAnalysisResult result = analyzeTable(table, aggregates, relationships);
            results.put(table.getTableName(), result);
        }

        // Step 4: 后处理：确保子实体跟随聚合根
        postProcessResults(results, aggregates);

        log.info("表分析完成: {} 个贫血模型, {} 个充血模型",
            results.values().stream().filter(r -> r.getModelType().name().equals("ANEMIC")).count(),
            results.values().stream().filter(r -> r.getModelType().name().equals("RICH")).count());

        return results;
    }

    /**
     * 分析单个表
     */
    private TableAnalysisResult analyzeTable(TableMetadata table,
                                              Map<String, List<String>> aggregates,
                                              Map<String, List<String>> relationships) {
        // 准备上下文
        Map<String, Object> context = new HashMap<>();
        context.put("relationships", relationships);
        context.put("aggregates", aggregates);

        // 使用可用策略分类
        for (ClassificationStrategy strategy : strategies) {
            if (strategy.isAvailable()) {
                TableAnalysisResult result = strategy.classify(table, context);

                // 分配聚合信息
                result.setAggregateInfo(aggregateDetector.assignAggregateInfo(
                    table.getTableName(), aggregates, relationships));

                return result;
            }
        }

        throw new IllegalStateException("没有可用的分类策略");
    }

    /**
     * 后处理：确保子实体跟随聚合根的模型类型
     */
    private void postProcessResults(Map<String, TableAnalysisResult> results,
                                    Map<String, List<String>> aggregates) {
        for (List<String> members : aggregates.values()) {
            if (members.size() <= 1) continue;

            String rootTable = members.get(0);
            TableAnalysisResult rootResult = results.get(rootTable);

            for (int i = 1; i < members.size(); i++) {
                String memberTable = members.get(i);
                TableAnalysisResult memberResult = results.get(memberTable);

                // 子实体跟随聚合根
                if (memberResult.getAggregateInfo().getRole() ==
                        com.gallop.fastddd.analyzer.classifier.model.AggregateInfo.AggregateRole.ENTITY) {

                    memberResult.setModelType(rootResult.getModelType());
                    memberResult.setReason("跟随聚合根 " + rootTable + " 使用 " +
                        rootResult.getModelType().getDisplayName());
                }
            }
        }
    }

    /**
     * 获取聚合分组
     */
    public Map<String, List<String>> getAggregateGroups(List<TableMetadata> tables) {
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        return aggregateDetector.detectAggregates(tables, relationships);
    }

    /**
     * 获取关系图数据
     */
    public RelationshipAnalyzer.GraphData getRelationshipGraph(List<TableMetadata> tables) {
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        return relationshipAnalyzer.buildGraphData(tables, relationships);
    }
}
```

### 1.3 新增 API 端点

```java
// fastddd-studio-server/src/main/java/com/gallop/fastddd/demo/controller/UnifiedGeneratorController.java

/**
 * 智能分析表（规则引擎 + 可选 LLM）
 *
 * @param request 分析请求
 * @return 分析结果
 */
@PostMapping("/analyze")
public ApiResponse<Map<String, TableAnalysisResult>> analyzeTables(
    @RequestBody TableAnalysisRequest request) {

    List<TableMetadata> tables = loadTables(request);

    Map<String, TableAnalysisResult> results = tableAnalysisService.analyzeTables(tables);

    return ApiResponse.success(results);
}

/**
 * 获取聚合分组
 *
 * @param dataSourceId 数据源ID
 * @return 聚合分组
 */
@GetMapping("/aggregate-groups/{dataSourceId}")
public ApiResponse<Map<String, List<String>>> getAggregateGroups(@PathVariable Long dataSourceId) {
    List<TableMetadata> tables = unifiedProjectService.loadAllTables(dataSourceId);
    Map<String, List<String>> groups = tableAnalysisService.getAggregateGroups(tables);
    return ApiResponse.success(groups);
}

/**
 * 获取关系图数据
 *
 * @param dataSourceId 数据源ID
 * @return 关系图
 */
@GetMapping("/relationship-graph/{dataSourceId}")
public ApiResponse<RelationshipAnalyzer.GraphData> getRelationshipGraph(@PathVariable Long dataSourceId) {
    List<TableMetadata> tables = unifiedProjectService.loadAllTables(dataSourceId);
    RelationshipAnalyzer.GraphData graph = tableAnalysisService.getRelationshipGraph(tables);
    return ApiResponse.success(graph);
}
```

---

## Phase 2: 前端简化集成 (Week 3)

### 2.1 设计理念调整

**变更前**：增加独立的第 4 步 "AI 分类"
**变更后**：将智能分析嵌入表选择步骤，保持 5 步流程

### 2.2 修改后的前端组件

**文件 8: `TableSelectionPanel.vue` 嵌入式分析**

```vue
<!-- fastddd-studio-ui/src/views/unified-generator/steps/TableSelectionPanel.vue -->

<template>
  <div class="table-selection-panel">
    <!-- 原有表选择区 -->
    <el-card header="选择数据表">
      <!-- 表列表和选择功能 -->
      <el-table
        ref="tableRef"
        :data="filteredTables"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="tableName" label="表名" width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <span>{{ row.tableName }}</span>
              <el-tag v-if="row.tableType === 'RICH'" type="warning" size="small">充血</el-tag>
              <el-tag v-else type="info" size="small">贫血</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="tableComment" label="表注释" show-overflow-tooltip />
        <el-table-column prop="columnCount" label="列数" width="80" />
      </el-table>
    </el-card>

    <!-- 智能分析区（选中表后显示）-->
    <transition name="el-zoom-in-top">
      <el-card v-if="selectedTables.length > 0" class="mt-4 analysis-card">
        <template #header>
          <div class="flex justify-between items-center">
            <div class="flex items-center gap-2">
              <el-icon><MagicStick /></el-icon>
              <span>智能分析</span>
              <el-tag v-if="analysisResults.size > 0" size="small">
                已分析 {{ analysisResults.size }} 个表
              </el-tag>
            </div>
            <div class="flex gap-2">
              <el-button-group>
                <el-button
                  :type="analysisMode === 'rule' ? 'primary' : ''"
                  @click="analysisMode = 'rule'; runAnalysis()"
                  size="small"
                >
                  规则引擎
                </el-button>
                <el-button
                  :type="analysisMode === 'llm' ? 'primary' : ''"
                  @click="analysisMode = 'llm'; runAnalysis()"
                  size="small"
                  :disabled="!llmAvailable"
                >
                  AI 增强
                  <el-tooltip content="需要配置 LLM API Key">
                    <el-icon><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-button>
              </el-button-group>
              <el-button
                type="primary"
                :loading="analyzing"
                :disabled="analysisResults.size > 0"
                @click="runAnalysis"
                size="small"
              >
                <el-icon><Refresh /></el-icon>
                重新分析
              </el-button>
            </div>
          </div>
        </template>

        <!-- 聚合视图 -->
        <div v-if="aggregateView" class="aggregate-view mb-4">
          <div class="flex items-center gap-2 mb-2">
            <el-icon><Share /></el-icon>
            <span class="font-medium">聚合分组</span>
            <el-tag type="info">{{ Object.keys(aggregateGroups).length }} 个聚合</el-tag>
          </div>

          <div class="aggregate-groups">
            <div
              v-for="(members, name) in aggregateGroups"
              :key="name"
              class="aggregate-group"
            >
              <div class="aggregate-header">
                <el-tag :type="getAggregateTagType(members)">
                  {{ name }}
                </el-tag>
                <el-text size="small" type="info">{{ members.length }} 个表</el-text>
              </div>
              <div class="aggregate-members">
                <el-tag
                  v-for="member in members"
                  :key="member"
                  :closable="!isAggregateRoot(member, members)"
                  @close="handleRemoveFromAggregate(member)"
                  size="small"
                  :type="getMemberTagType(member, members)"
                >
                  {{ isAggregateRoot(member, members) ? '🏠' : '📄' }} {{ member }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>

        <!-- 分析结果表格 -->
        <el-table :data="analysisResultsList" stripe max-height="400">
          <el-table-column prop="tableName" label="表名" width="180">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <span>{{ row.tableName }}</span>
                <el-icon v-if="row.aggregateInfo?.role === 'AGGREGATE_ROOT'" color="#f56c6c">
                  <House />
                </el-icon>
                <el-icon v-else-if="row.aggregateInfo?.role === 'ENTITY'" color="#409eff">
                  <Document />
                </el-icon>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="推荐模型" width="110">
            <template #default="{ row }">
              <el-select
                v-model="row.modelType"
                size="small"
                @change="handleModelTypeChange(row)"
              >
                <el-option label="贫血" value="ANEMIC" />
                <el-option label="充血" value="RICH" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="置信度" width="120">
            <template #default="{ row }">
              <div class="confidence-display">
                <el-tag :type="getConfidenceType(row.confidence)" size="small">
                  {{ getConfidenceLabel(row.confidence) }}
                </el-tag>
                <el-tooltip :content="row.reason">
                  <el-icon class="ml-1"><QuestionFilled /></el-icon>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="聚合" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.aggregateInfo" size="small">
                {{ row.aggregateInfo.aggregateName }}
              </el-tag>
              <span v-else class="text-gray">-</span>
            </template>
          </el-table-column>

          <el-table-column prop="metrics.complexityScore" label="复杂度" width="80">
            <template #default="{ row }">
              <el-text
                :type="row.metrics?.complexityScore > 0.6 ? 'danger' : 'info'"
              >
                {{ (row.metrics?.complexityScore * 100).toFixed(0) }}%
              </el-text>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                size="small"
                @click="showAnalysisDetail(row)"
              >
                详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-3 flex justify-between">
          <el-text type="info" size="small">
            <el-icon><InfoFilled /></el-icon>
            提示：子实体表会自动跟随聚合根的模型类型
          </el-text>
          <el-button type="primary" @click="handleConfirmAnalysis">
            确认并继续
          </el-button>
        </div>
      </el-card>
    </transition>

    <!-- 分析详情弹窗 -->
    <el-dialog v-model="detailVisible" title="分析详情" width="600px">
      <div v-if="currentDetail" class="analysis-detail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="表名">{{ currentDetail.tableName }}</el-descriptions-item>
          <el-descriptions-item label="推荐模型">
            <el-tag :type="currentDetail.modelType === 'RICH' ? 'warning' : 'info'">
              {{ currentDetail.modelType === 'RICH' ? '充血模型' : '贫血模型' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="置信度">
            {{ (currentDetail.confidence * 100).toFixed(0) }}%
          </el-descriptions-item>
          <el-descriptions-item label="分析原因">
            {{ currentDetail.reason }}
          </el-descriptions-item>
          <el-descriptions-item label="使用策略">
            {{ currentDetail.strategy }}
          </el-descriptions-item>
          <el-descriptions-item label="聚合信息" v-if="currentDetail.aggregateInfo">
            聚合: {{ currentDetail.aggregateInfo.aggregateName }}<br/>
            角色: {{ currentDetail.aggregateInfo.role }}
          </el-descriptions-item>
          <el-descriptions-item label="复杂度评分">
            {{ (currentDetail.metrics?.complexityScore * 100).toFixed(0) }}%
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useUnifiedGeneratorStore } from '@/stores/unified-generator'
import { unifiedGeneratorApi } from '@/api/unified-generator'
import { ElMessage } from 'element-plus'
import {
  MagicStick, Refresh, QuestionFilled, House, Document,
  InfoFilled, Share
} from '@element-plus/icons-vue'

const store = useUnifiedGeneratorStore()
const emit = defineEmits(['update:selection'])

// 状态
const analyzing = ref(false)
const analysisMode = ref<'rule' | 'llm'>('rule')
const analysisResults = ref<Record<string, any>>({})
const aggregateGroups = ref<Record<string, string[]>>({})
const aggregateView = ref(true)
const llmAvailable = ref(false)
const detailVisible = ref(false)
const currentDetail = ref<any>(null)

// 计算属性
const analysisResultsList = computed(() => {
  return Object.values(analysisResults.value)
})

// 方法
async function runAnalysis() {
  if (store.selectedTables.length === 0) return

  analyzing.value = true
  try {
    const results = await unifiedGeneratorApi.analyzeTables({
      dataSourceId: store.dataSourceConfig.dataSourceId,
      tableNames: store.selectedTables,
      mode: analysisMode.value
    })

    analysisResults.value = results

    // 获取聚合分组
    const groups = await unifiedGeneratorApi.getAggregateGroups(
      store.dataSourceConfig.dataSourceId
    )
    aggregateGroups.value = groups

    ElMessage.success(`分析完成：${Object.keys(results).length} 个表`)
  } catch (error: any) {
    ElMessage.error(error.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

function getConfidenceType(confidence: number) {
  if (confidence >= 0.8) return 'success'
  if (confidence >= 0.6) return 'warning'
  return 'danger'
}

function getConfidenceLabel(confidence: number) {
  if (confidence >= 0.8) return '高置信'
  if (confidence >= 0.6) return '中置信'
  return '建议确认'
}

function isAggregateRoot(tableName: string, members: string[]) {
  return members[0] === tableName
}

function getMemberTagType(tableName: string, members: string[]) {
  return isAggregateRoot(tableName, members) ? 'danger' : 'info'
}

function getAggregateTagType(members: string[]) {
  // 根据聚合规模和类型返回标签颜色
  return members.length > 1 ? 'warning' : 'info'
}

function handleConfirmAnalysis() {
  // 将分析结果保存到 store
  store.setTableAnalysisResults(analysisResults.value)
  emit('update:selection', store.selectedTables)
}

function showAnalysisDetail(row: any) {
  currentDetail.value = row
  detailVisible.value = true
}

function handleModelTypeChange(row: any) {
  // 如果修改聚合根，确认是否影响子实体
  if (row.aggregateInfo?.role === 'AGGREGATE_ROOT') {
    ElMessageBox.confirm(
      '修改聚合根的模型类型将影响整个聚合的表，是否继续？',
      '确认修改',
      { type: 'warning' }
    ).then(() => {
      // 更新聚合内所有表
      updateAggregateModelType(row.aggregateInfo.aggregateName, row.modelType)
    }).catch(() => {
      // 取消，恢复原值
    })
  }
}

// 自动分析：选中表后自动触发
watch(() => store.selectedTables, (newTables) => {
  if (newTables.length > 0 && Object.keys(analysisResults.value).length === 0) {
    runAnalysis()
  }
}, { immediate: true })
</script>

<style scoped>
.analysis-card {
  border: 2px solid #409eff;
  background: linear-gradient(135deg, #f5f7fa 0%, #e8ecf1 100%);
}

.aggregate-view {
  padding: 12px;
  background: #fff;
  border-radius: 4px;
}

.aggregate-groups {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.aggregate-group {
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
}

.aggregate-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.aggregate-members {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.confidence-display {
  display: flex;
  align-items: center;
}
</style>
```

### 2.3 API 类型定义更新

```typescript
// fastddd-studio-ui/src/api/unified-generator.ts

/**
 * 表分析请求
 */
export interface TableAnalysisRequest {
  dataSourceId: string
  tableNames: string[]
  mode?: 'rule' | 'llm'  // 分析模式
}

/**
 * 表分析结果
 */
export interface TableAnalysisResult {
  tableName: string
  modelType: 'ANEMIC' | 'RICH'
  confidence: number
  reason: string
  strategy: string
  aggregateInfo: {
    aggregateName: string
    rootTableName: string
    role: 'AGGREGATE_ROOT' | 'ENTITY' | 'VALUE_OBJECT' | 'STANDALONE'
    memberTables: string[]
    parentTable?: string
    relationshipType: 'ONE_TO_MANY' | 'ONE_TO_ONE' | 'MANY_TO_ONE' | 'NONE'
  }
  metrics: {
    columnCount: number
    relationshipCount: number
    complexityScore: number
    hasStateMachine: boolean
    hasBusinessRules: boolean
    isAggregateRoot: boolean
  }
  suggestedModule?: string
  attributes: Record<string, any>
}

/**
 * 关系图数据
 */
export interface RelationshipGraph {
  nodes: string[]
  edges: Array<{ from: string; to: string; label: string }>
}

// API 扩展
export const unifiedGeneratorApi = {
  // ... 现有方法

  /**
   * 智能分析表
   */
  async analyzeTables(request: TableAnalysisRequest): Promise<Record<string, TableAnalysisResult>> {
    return apiClient.post('/analyze', request)
  },

  /**
   * 获取聚合分组
   */
  async getAggregateGroups(dataSourceId: string): Promise<Record<string, string[]>> {
    return apiClient.get(`/aggregate-groups/${dataSourceId}`)
  },

  /**
   * 获取关系图
   */
  async getRelationshipGraph(dataSourceId: string): Promise<RelationshipGraph> {
    return apiClient.get(`/relationship-graph/${dataSourceId}`)
  }
}
```

---

## Phase 3: 混合模型支持（按聚合边界）(Week 4-5)

### 3.1 数据模型扩展

**文件 9: `UnifiedProjectRequest.java` 扩展**

```java
// fastddd-studio-server/src/main/java/com/gallop/fastddd/demo/dto/unified/UnifiedProjectRequest.java

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedProjectRequest {

    // ==================== 现有字段 ====================

    /**
     * 全局默认模型类型 (当表没有单独配置时使用)
     */
    @Builder.Default
    private ModelType defaultModelType = ModelType.ANEMIC;

    // ==================== 新增：Per-Table 配置 ====================

    /**
     * 每个表的单独配置
     * <p>如果表没有单独配置，使用 defaultModelType</p>
     *
     * @since 1.1.0
     */
    private List<TableConfig> tableConfigs;

    /**
     * 聚合配置
     * <p>按聚合边界组织代码结构</p>
     *
     * @since 1.1.0
     */
    private List<AggregateConfig> aggregateConfigs;

    /**
     * 表配置
     *
     * @since 1.1.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableConfig {
        /**
         * 表名
         */
        private String tableName;

        /**
         * 模型类型 (覆盖全局配置)
         */
        private ModelType modelType;

        /**
         * 是否启用 CQRS
         */
        private Boolean enableCQRS;

        /**
         * 所属聚合
         */
        private String aggregate;

        /**
         * 自定义包名
         */
        private String customPackage;
    }

    /**
     * 聚合配置
     *
     * @since 1.1.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregateConfig {
        /**
         * 聚合名称
         */
        private String aggregateName;

        /**
         * 聚合根表名
         */
        private String rootTable;

        /**
         * 聚合内所有表
         */
        private List<String> memberTables;

        /**
         * 聚合的模型类型
         */
        private ModelType modelType;

        /**
         * 是否启用 CQRS
         */
        private Boolean enableCQRS;

        /**
         * 自定义模块名
         */
        private String moduleName;
    }

    /**
     * 获取表的实际模型类型
     *
     * @param tableName 表名
     * @return 模型类型
     */
    public ModelType getModelTypeForTable(String tableName) {
        // 优先使用表级别配置
        if (tableConfigs != null) {
            return tableConfigs.stream()
                .filter(c -> c.getTableName().equals(tableName))
                .findFirst()
                .map(TableConfig::getModelType)
                .orElse(defaultModelType);
        }

        // 其次使用聚合级别配置
        if (aggregateConfigs != null) {
            return aggregateConfigs.stream()
                .filter(a -> a.getMemberTables().contains(tableName))
                .findFirst()
                .map(AggregateConfig::getModelType)
                .orElse(defaultModelType);
        }

        return defaultModelType;
    }

    /**
     * 获取表所属聚合
     *
     * @param tableName 表名
     * @return 聚合配置
     */
    public AggregateConfig getAggregateForTable(String tableName) {
        if (aggregateConfigs == null) return null;

        return aggregateConfigs.stream()
            .filter(a -> a.getMemberTables().contains(tableName))
            .findFirst()
            .orElse(null);
    }
}
```

### 3.2 优化后的包结构设计

**按聚合边界划分，而非按模型类型划分**：

```
com.example.project.
├── shared.                        # 共享模块
│   ├── dto.
│   │   ├── ApiResponse.java
│   │   └── PageResult.java
│   ├── common.
│   │   ├── exception.
│   │   └── util.
│   └── config.
│
├── system.                        # 系统模块（贫血模型）
│   ├── config.
│   │   ├── entity.
│   │   │   └── SysConfig.java
│   │   ├── mapper.
│   │   ├── service.
│   │   └── controller.
│   └── dict.
│       ├── entity.
│       │   └── DictType.java
│       └── ...
│
└── business.                      # 业务模块（充血模型）
    ├── order.                     # 聚合 1: 订单
    │   ├── domain.
    │   │   ├── Order.java (AggregateRoot)
    │   │   ├── OrderItem.java (Entity)
    │   │   ├── OrderStatus.java (ValueObject)
    │   │   └── event.
    │   │       └── OrderCreatedEvent.java
    │   ├── application.
    │   │   ├── OrderApplicationService.java
    │   │   ├── command.
    │   │   │   ├── CreateOrderCommand.java
    │   │   │   └── CancelOrderCommand.java
    │   │   └── query.
    │   │       ├── GetOrderQuery.java
    │   │       └── OrderQueryService.java
    │   ├── infrastructure.
    │   │   ├── persistence.
    │   │   │   ├── OrderPO.java
    │   │   │   ├── OrderMapper.java
    │   │   │   └── OrderRepositoryImpl.java
    │   │   └── mapper.
    │   │       └── OrderBaseMapper.java
    │   └── interfaces.
    │       └── controller.
    │           ├── OrderCommandController.java
    │           └── OrderQueryController.java
    │
    ├── product.                   # 聚合 2: 产品
    │   ├── domain.
    │   │   ├── Product.java (AggregateRoot)
    │   │   ├── ProductInventory.java (Entity)
    │   │   └── ...
    │   ├── application.
    │   ├── infrastructure.
    │   └── interfaces.
    │
    └── payment.                   # 聚合 3: 支付
        ├── domain.
        │   ├── Payment.java (AggregateRoot)
        │   └── ...
        └── ...
```

### 3.3 服务层修改

```java
// fastddd-studio-server/src/main/java/com/gallop/fastddd/demo/service/UnifiedProjectService.java

/**
 * 生成代码（支持按聚合配置）
 */
public void generateUnifiedProject(UnifiedProjectRequest request) {
    // 1. 如果没有聚合配置，自动分析并创建
    if (request.getAggregateConfigs() == null || request.getAggregateConfigs().isEmpty()) {
        List<AggregateConfig> aggregates = analyzeAndCreateAggregates(request);
        request.setAggregateConfigs(aggregates);
    }

    // 2. 按聚合分组生成代码
    for (AggregateConfig aggregate : request.getAggregateConfigs()) {
        generateAggregateCode(aggregate, request);
    }
}

/**
 * 分析并创建聚合配置
 */
private List<AggregateConfig> analyzeAndCreateAggregates(UnifiedProjectRequest request) {
    // 获取表分析结果
    Map<String, TableAnalysisResult> analysisResults =
        tableAnalysisService.analyzeTables(request.getSelectedTables());

    // 按聚合分组
    Map<String, List<TableAnalysisResult>> groupedByAggregate = analysisResults.values()
        .stream()
        .collect(Collectors.groupingBy(
            r -> r.getAggregateInfo().getAggregateName()
        ));

    // 创建聚合配置
    return groupedByAggregate.entrySet().stream()
        .map(entry -> {
            List<TableAnalysisResult> members = entry.getValue();
            TableAnalysisResult root = members.stream()
                .filter(m -> m.getAggregateInfo().getRole() == AggregateRole.AGGREGATE_ROOT)
                .findFirst()
                .orElse(members.get(0));

            return UnifiedProjectRequest.AggregateConfig.builder()
                .aggregateName(entry.getKey())
                .rootTable(root.getTableName())
                .memberTables(members.stream()
                    .map(TableAnalysisResult::getTableName)
                    .toList())
                .modelType(root.getModelType())
                .enableCQRS(root.getModelType() == ModelType.RICH)
                .moduleName(determineModuleName(entry.getKey()))
                .build();
        })
        .toList();
}

/**
 * 生成单个聚合的代码
 */
private void generateAggregateCode(AggregateConfig aggregate, UnifiedProjectRequest request) {
    File moduleDir = createModuleDirectory(aggregate, request);

    for (String tableName : aggregate.getMemberTables()) {
        TableMetadata table = getTableMetadata(tableName);
        ModelType modelType = aggregate.getModelType();

        // 创建聚合特定的配置
        GeneratorConfig config = createAggregateConfig(aggregate, modelType, request);

        // 生成代码
        Map<String, String> generatedFiles = codeGeneratorService.generateCode(
            table, aggregate.getModuleName(), config
        );

        // 写入文件
        String basePath = getBasePathForAggregate(aggregate, modelType);
        writeGeneratedFiles(moduleDir, basePath, generatedFiles);
    }
}

/**
 * 根据聚合和模型类型获取基础路径
 */
private String getBasePathForAggregate(AggregateConfig aggregate, ModelType modelType) {
    String basePackage = request.getPackageName();

    // 系统模块
    if (isSystemAggregate(aggregate.getAggregateName())) {
        return basePackage + ".system." + aggregate.getAggregateName();
    }

    // 业务模块（充血模型）
    if (modelType == ModelType.RICH) {
        return basePackage + ".business." + aggregate.getAggregateName();
    }

    // 其他（贫血模型）
    return basePackage + "." + aggregate.getAggregateName();
}
```

---

## Phase 4: 核心产品功能 (Week 6-7)

### 4.1 配置模板管理

```java
/**
 * 配置模板服务
 */
@Service
public class GeneratorTemplateService {

    /**
     * 保存配置为模板
     */
    public Long saveTemplate(String name, String description, UnifiedProjectRequest config) {
        GeneratorTemplate template = new GeneratorTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setConfigJson(objectMapper.writeValueAsString(config));
        template.setCreatedAt(LocalDateTime.now());

        return templateRepository.save(template).getId();
    }

    /**
     * 加载模板
     */
    public UnifiedProjectRequest loadTemplate(Long templateId) {
        GeneratorTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new NotFoundException("模板不存在"));

        return objectMapper.readValue(template.getConfigJson(), UnifiedProjectRequest.class);
    }

    /**
     * 列出模板
     */
    public List<TemplateSummary> listTemplates() {
        return templateRepository.findAll().stream()
            .map(t -> new TemplateSummary(t.getId(), t.getName(), t.getDescription()))
            .toList();
    }
}
```

### 4.2 代码预览功能（优化版）

```vue
<template>
  <el-drawer v-model="previewVisible" title="代码预览" size="75%">
    <!-- 筛选区 -->
    <div class="preview-filters mb-4">
      <el-row :gutter="12">
        <el-col :span="6">
          <el-select v-model="filters.aggregate" placeholder="选择聚合" clearable>
            <el-option label="全部" value="" />
            <el-option v-for="a in aggregates" :key="a" :label="a" :value="a" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filters.table" placeholder="选择表" clearable>
            <el-option label="全部" value="" />
            <el-option v-for="t in tables" :key="t" :label="t" :value="t" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select v-model="filters.fileType" placeholder="文件类型" clearable>
            <el-option label="全部" value="" />
            <el-option label="Entity" value="entity" />
            <el-option label="Service" value="service" />
            <el-option label="Controller" value="controller" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button-group>
            <el-button @click="downloadAll" :icon="Download">下载全部</el-button>
            <el-button @click="copyAll" :icon="CopyDocument">复制全部</el-button>
          </el-button-group>
        </el-col>
      </el-row>
    </div>

    <!-- 代码展示区 -->
    <el-tabs v-model="activeFile" type="card" editable>
      <el-tab-pane
        v-for="(content, file) in filteredFiles"
        :key="file"
        :label="getFileName(file)"
        :name="file"
        :closable="false"
      >
        <template #label>
          <div class="flex items-center gap-1">
            <el-icon :color="getFileIconColor(file)">
              <component :is="getFileIcon(file)" />
            </el-icon>
            <span>{{ getShortFileName(file) }}</span>
            <el-tag size="small" :type="getModelTypeTag(file)">
              {{ getModelTypeOfFile(file) }}
            </el-tag>
          </div>
        </template>

        <div class="code-container">
          <div class="code-header">
            <el-text type="info" size="small">{{ file }}</el-text>
            <el-button-group size="small">
              <el-button @click="copyCode(content)">复制</el-button>
              <el-button @click="downloadFile(file, content)">下载</el-button>
            </el-button-group>
          </div>

          <!-- 语法高亮代码展示 -->
          <ShikiHighlight
            :code="content"
            :lang="getLanguage(file)"
            :theme="codeTheme"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>
```

### 4.3 关系图可视化

```vue
<template>
  <el-card header="聚合关系图">
    <div ref="graphRef" class="relationship-graph"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Graph } from '@antv/g6'

const graphRef = ref<HTMLElement>()
const graph = ref<Graph>()

const props = defineProps<{
  dataSourceId: string
  graphData: RelationshipGraph
}>()

async function initGraph() {
  if (!graphRef.value) return

  graph.value = new Graph({
    container: graphRef.value,
    width: graphRef.value.clientWidth,
    height: 400,
    modes: {
      default: ['drag-canvas', 'zoom-canvas', 'drag-node', 'click-select']
    },
    layout: {
      type: 'dagre',
      rankdir: 'LR',
      nodesep: 30,
      ranksep: 50
    },
    defaultNode: {
      type: 'rect',
      size: [120, 40],
      style: {
        fill: '#f0f9ff',
        stroke: '#409eff',
        lineWidth: 2
      },
      labelCfg: {
        style: { fontSize: 12 }
      }
    },
    defaultEdge: {
      type: 'polyline',
      style: {
        stroke: '#409eff',
        endArrow: true
      },
      labelCfg: {
        autoRotate: true,
        refY: 5
      }
    }
  })

  // 转换数据格式
  const g6Data = convertToG6Format(props.graphData)
  graph.value.data(g6Data)
  graph.value.render()

  // 绑定事件
  graph.value.on('node:click', (evt) => {
    const nodeName = evt.item.getModel().label
    emit('node-click', nodeName)
  })
}

function convertToG6Format(data: RelationshipGraph) {
  return {
    nodes: data.nodes.map(n => ({
      id: n,
      label: n,
      style: {
        fill: isAggregateRoot(n) ? '#fff7e6' : '#f0f9ff',
        stroke: isAggregateRoot(n) ? '#fa8c16' : '#409eff'
      }
    })),
    edges: data.edges.map(e => ({
      source: e.from,
      target: e.to,
      label: e.label
    }))
  }
}

watch(() => props.graphData, () => {
  graph.value?.changeData(convertToG6Format(props.graphData))
}, { deep: true })

onMounted(initGraph)
</script>

<style scoped>
.relationship-graph {
  min-height: 400px;
  background: #fafafa;
  border-radius: 4px;
}
</style>
```

---

## Phase 5: 可选 LLM 增强 (Week 8)

### 5.1 LLM 增强策略（可选功能）

```java
// fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/classifier/strategy/LLMEnhancedStrategy.java

package com.gallop.fastddd.analyzer.classifier.strategy;

import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * LLM 增强分类策略（可选）
 * <p>只有在配置了 LLM API Key 时才启用</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fastddd.analyzer.llm.enabled", havingValue = "true")
public class LLMEnhancedStrategy implements ClassificationStrategy {

    private final LLMClient llmClient;
    private final LLMResponseParser responseParser;

    @Override
    public boolean isAvailable() {
        return llmClient.isConfigured();
    }

    @Override
    public String getName() {
        return "LLM-Enhanced";
    }

    @Override
    public TableAnalysisResult classify(TableMetadata table, Map<String, Object> context) {
        try {
            // 构建批量 Prompt（减少 API 调用）
            String prompt = buildEnhancedPrompt(table, context);

            // 带超时和重试的调用
            LLMResponse response = llmClient.completeWithRetry(
                prompt,
                Timeout.ofSeconds(10),
                Retry.maxAttempts(2)
            );

            // 增强的响应解析
            return parseEnhancedResponse(response, table);

        } catch (LLMException e) {
            log.warn("LLM 调用失败，降级到规则引擎: {}", e.getMessage());
            throw new ClassificationException("LLM 不可用", e);
        }
    }

    private String buildEnhancedPrompt(TableMetadata table, Map<String, Object> context) {
        return String.format("""
            你是一个领域驱动设计 (DDD) 专家。请分析以下数据库表。

            ## 表信息
            表名: %s
            表注释: %s
            列数: %d

            ## 列详情
            %s

            ## 关系信息
            外键数量: %d
            被引用次数: %d

            ## 聚合上下文
            %s

            ## 分类标准

            **充血模型 (RICH) 特征:**
            - 核心业务概念（如 Order, Payment, Inventory）
            - 包含状态字段，有状态转换逻辑
            - 包含金额/数量字段，需要业务规则校验
            - 作为聚合根被其他表引用
            - 需要发布领域事件

            **贫血模型 (ANEMIC) 特征:**
            - 系统/配置/字典/日志表
            - 简单 CRUD，无业务逻辑
            - 表名以 sys_, dict_, config_, log_ 开头
            - 列数少，无复杂关系

            ## 输出格式

            以 JSON 格式输出：
            {
              "modelType": "RICH" | "ANEMIC",
              "confidence": 0.0-1.0,
              "reason": "分类原因（中文）",
              "aggregateRole": "AGGREGATE_ROOT" | "ENTITY" | "VALUE_OBJECT" | "STANDALONE",
              "complexityFactors": ["状态字段", "金额字段", "被引用"],
              "suggestedModules": ["order", "payment"]
            }
            """,
            table.getTableName(),
            table.getTableComment(),
            table.getColumns().size(),
            formatColumns(table.getColumns()),
            context.get("foreignKeyCount"),
            context.get("referenceCount"),
            formatAggregateContext(context.get("aggregateInfo"))
        );
    }

    private TableAnalysisResult parseEnhancedResponse(LLMResponse response, TableMetadata table) {
        // 1. 提取 JSON 块（处理 Markdown 包裹）
        String jsonContent = responseParser.extractJSON(response.getText());

        // 2. 宽松解析
        LLMClassificationData data = responseParser.parseLeniently(jsonContent);

        // 3. 转换为 TableAnalysisResult
        return TableAnalysisResult.builder()
            .tableName(table.getTableName())
            .modelType(DomainModelType.valueOf(data.getModelType()))
            .confidence(data.getConfidence())
            .reason(data.getReason())
            .strategy(getName())
            .attributes(Map.of(
                "llmComplexityFactors", data.getComplexityFactors(),
                "suggestedModules", data.getSuggestedModules()
            ))
            .build();
    }
}
```

### 5.2 配置选项

```yaml
# application.yml
fastddd:
  analyzer:
    llm:
      enabled: false  # 默认关闭，按需启用
      provider: openai  # openai | claude | azure
      api-key: ${LLM_API_KEY:}
      model: gpt-4
      timeout: 10s
      max-retries: 2
```

---

## Implementation Roadmap (调整后)

### 里程碑计划

```
Week 1-2: Phase 1 - 增强规则引擎与聚合识别
├── 创建 fast-ddd-analyzer-spring-boot-starter 模块
├── 实现 EnhancedRuleBasedStrategy（核心）
├── 实现 AggregateDetector（聚合检测）
├── 实现 RelationshipAnalyzer（关系分析）
├── 实现 ComplexityScorer（复杂度评分）
├── 添加 analyze、aggregate-groups API
└── 单元测试

Week 3: Phase 2 - 前端简化集成
├── 将智能分析嵌入 TableSelectionPanel
├── 实现聚合视图展示
├── 实现分析结果编辑
├── 关系图可视化
└── API 联调测试

Week 4-5: Phase 3 - 混合模型支持
├── 扩展 UnifiedProjectRequest 支持聚合配置
├── 修改代码生成服务支持按聚合组织
├── 优化包结构（按聚合边界划分）
├── 生成代码测试
└── 验证混合模型编译

Week 6-7: Phase 4 - 核心产品功能
├── 配置模板保存/加载
├── 代码预览功能（语法高亮）
├── 关系图可视化
├── 批量操作支持
└── 集成测试

Week 8: Phase 5 - 可选 LLM 增强
├── 实现 LLMEnhancedStrategy
├── 批量 Prompt 设计
├── 响应解析增强
├── 配置项管理
└── 可选功能测试

Week 9: 文档和发布
├── 用户文档更新
├── 示例项目
├── 发布说明
└── 上线部署
```

### 关键调整

| 调整项 | 原计划 | 新计划 | 理由 |
|--------|--------|--------|------|
| 主要策略 | LLM 优先 | 规则引擎优先 | 规则引擎稳定、快速、无外部依赖 |
| 前端流程 | 新增第 4 步 | 嵌入表选择步骤 | 简化用户流程，减少认知负担 |
| 包结构 | 按模型类型 | 按聚合边界 | 符合 DDD 理念，更自然的组织方式 |
| 聚合识别 | Phase 3 | Phase 1 | 这是 DDD 核心能力，应优先实现 |
| 产品功能 | Phase 4 | Phase 4（前置） | 配置模板和预览是核心需求 |
| LLM 集成 | Phase 1 | Phase 5（可选） | 作为增强功能，非必需 |

### 风险和缓解（更新）

| 风险 | 影响 | 缓解措施 | 状态 |
|------|------|----------|------|
| 规则引擎准确率不足 | 分类不精准 | 多维度评分 + 收集反馈迭代 | ✅ 已增强 |
| 聚合识别错误 | 代码结构混乱 | 提供手动调整功能 | ✅ 已规划 |
| 包结构复杂 | 用户困惑 | 提供清晰的文档和示例 | ✅ 已优化 |
| 用户不信任推荐 | 功能使用率低 | 置信度标签 + 详细原因说明 | ✅ 已实现 |

---

## Critical Files Summary (更新)

### 需要新建的文件

| 文件路径 | 描述 | 优先级 |
|----------|------|--------|
| `fast-ddd-common/.../DomainModelType.java` | 独立的模型类型枚举 | P0 |
| `fast-ddd-analyzer-starter/pom.xml` | 分析器模块 POM | P0 |
| `fast-ddd-analyzer-starter/.../EnhancedRuleBasedStrategy.java` | 增强规则引擎 | P0 |
| `fast-ddd-analyzer-starter/.../AggregateDetector.java` | 聚合检测器 | P0 |
| `fast-ddd-analyzer-starter/.../RelationshipAnalyzer.java` | 关系分析器 | P0 |
| `fast-ddd-analyzer-starter/.../AggregateInfo.java` | 聚合信息模型 | P0 |
| `fast-ddd-analyzer-starter/.../TableAnalysisResult.java` | 分析结果模型 | P0 |
| `fast-ddd-analyzer-starter/.../TableAnalysisService.java` | 主服务入口 | P0 |
| `frontend/.../TableSelectionPanel.vue` (更新) | 嵌入式分析面板 | P0 |

### 可选文件（LLM 增强）

| 文件路径 | 描述 | 优先级 |
|----------|------|--------|
| `fast-ddd-analyzer-starter/.../LLMEnhancedStrategy.java` | LLM 增强策略 | P2 |
| `fast-ddd-analyzer-starter/.../LLMClient.java` | LLM 客户端 | P2 |
| `fast-ddd-analyzer-starter/.../LLMResponseParser.java` | 响应解析器 | P2 |

### 需要修改的文件

| 文件路径 | 主要修改 | 优先级 |
|----------|----------|--------|
| `UnifiedProjectRequest.java` | 添加 aggregateConfigs | P0 |
| `UnifiedProjectService.java` | 支持按聚合组织代码生成 | P0 |
| `CodeGeneratorService.java` | 按聚合调整包路径 | P0 |
| `UnifiedGeneratorController.java` | 添加 analyze, aggregate-groups API | P0 |

---

## Verification Plan (更新)

### 测试场景

1. **规则引擎准确性测试**
   - 准备 30 个典型表（配置表、核心业务表、子实体表）
   - 目标：准确率 > 85%（比 LLM 方案更现实）

2. **聚合识别测试**
   - 准备包含 5-10 个聚合的数据库
   - 验证聚合边界识别准确性
   - 目标：聚合根识别准确率 > 80%

3. **混合模型代码生成测试**
   - 选择 3-5 个聚合，分别指定不同模型类型
   - 生成代码并编译
   - 验证按聚合边界的包结构正确性

4. **端到端测试**
   - 完整流程：基础配置 → 数据源 → 选择表（自动分析）→ 调整 → 生成
   - 验证生成的项目可以编译运行

### 性能指标（调整）

| 指标 | 目标 | 说明 |
|------|------|------|
| 规则分析响应时间 | < 1 秒 (50 个表) | 无需外部 API |
| LLM 分析响应时间 | < 5 秒 (50 个表) | 批量调用 |
| 代码预览生成时间 | < 3 秒 | 增量生成 |
| 完整项目生成时间 | < 20 秒 (20 个表) | 按聚合并行生成 |

---

## 总结

本计划 v2.0 相比原计划的核心改进：

1. **技术可行性更高**：规则引擎优先，不依赖外部 LLM API
2. **用户体验更好**：简化流程，AI 分析嵌入表选择步骤
3. **DDD 能力更强**：增加聚合识别功能，这是 DDD 的核心
4. **包结构更合理**：按聚合边界划分，符合 DDD 最佳实践
5. **实施风险更低**：分阶段发布，核心功能优先
