# FastDDD 智能分析功能开发任务清单

> **说明**：本文档将 AI 智能分析功能的开发计划拆分为可执行的小任务，每个任务包含：
> - 任务 ID 和标题
> - 优先级（P0/P1/P2）
> - 依赖任务
> - 预估工作量
> - 验收标准
> - 技术风险

---

## 任务优先级说明

| 优先级 | 说明 | 响应时间 |
|--------|------|----------|
| P0 | 核心功能，必须完成 | 立即处理 |
| P1 | 重要功能，应该完成 | 当周完成 |
| P2 | 增强功能，可以延后 | 有时间再做 |

---

## Phase 1: 基础模型与服务框架

### 任务 1.1：创建独立的模型类型枚举

**ID**: `T001`
**标题**: 在 fast-ddd-common 模块创建 DomainModelType 枚举
**优先级**: P0
**依赖**: 无
**预估**: 0.5 小时

**描述**：
创建独立的模型类型枚举，解决模块依赖问题。当前 `ModelType` 定义在 demo 模块，导致 AI 模块无法引用。

**文件**: `fast-ddd-common/src/main/java/com/gallop/fastddd/common/enums/DomainModelType.java`

**代码框架**:
```java
package com.gallop.fastddd.common.enums;

import lombok.Getter;

@Getter
public enum DomainModelType {
    ANEMIC("贫血模型", "适用于配置、字典、日志等简单表"),
    RICH("充血模型", "适用于核心业务实体，包含状态转换、业务规则");

    private final String displayName;
    private final String description;

    DomainModelType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
```

**验收标准**:
- [ ] 枚举类创建完成
- [ ] 包含 ANEMIC 和 RICH 两个值
- [ ] 通过 `mvn clean compile` 编译
- [ ] JavaDoc 完整

**技术风险**: 无

---

### 任务 1.2：创建 analyzer 模块骨架

**ID**: `T002`
**标题**: 创建 fast-ddd-analyzer-spring-boot-starter 模块
**优先级**: P0
**依赖**: 无
**预估**: 1 小时

**描述**：
创建新的 Spring Boot Starter 模块，用于智能分析功能。

**文件**:
- `fast-ddd-analyzer-spring-boot-starter/pom.xml`
- `fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/AutoConfiguration.java`
- `fast-ddd-analyzer-spring-boot-starter/src/main/java/com/gallop/fastddd/analyzer/properties/AnalyzerProperties.java`

**验收标准**:
- [ ] 模块创建完成
- [ ] pom.xml 配置正确，依赖 fast-ddd-common
- [ ] AutoConfiguration 类添加 `@Configuration` 注解
- [ ] 在 spring.factories 中注册自动配置
- [ ] 模块可以独立编译

**技术风险**: 无

---

### 任务 1.3：创建分析结果模型类

**ID**: `T003`
**标题**: 创建 TableAnalysisResult 和 AggregateInfo 模型
**优先级**: P0
**依赖**: T001, T002
**预估**: 1.5 小时

**描述**：
创建核心的数据模型类，用于封装分析结果。

**文件**:
- `.../analyzer/classifier/model/TableAnalysisResult.java`
- `.../analyzer/classifier/model/AggregateInfo.java`
- `.../analyzer/classifier/model/AnalysisMetrics.java`

**验收标准**:
- [ ] TableAnalysisResult 包含：tableName, modelType, confidence, reason, strategy, aggregateInfo, metrics
- [ ] AggregateInfo 包含：aggregateName, rootTableName, role, memberTables, parentTable, relationshipType
- [ ] AnalysisMetrics 包含：columnCount, relationshipCount, complexityScore, hasStateMachine, hasBusinessRules, isAggregateRoot
- [ ] 使用 Lombok 注解减少样板代码
- [ ] 添加完整的 JavaDoc

**技术风险**: 无

---

## Phase 2: 关系分析器

### 任务 2.1：实现外键关系检测

**ID**: `T004`
**标题**: 实现基于命名模式的外键关系检测
**优先级**: P0
**依赖**: T002
**预估**: 2 小时

**描述**：
实现 RelationshipAnalyzer，通过列名模式检测外键关系（如 `order_id` 引用 `order` 表）。

**文件**: `.../analyzer/analyzer/RelationshipAnalyzer.java`

**关键方法**:
```java
// 检测列引用的表
private String detectReferencedTable(ColumnMetadata column, Map<String, TableMetadata> tableMap)

// 检查表是否引用了另一个表
private boolean referencesTable(TableMetadata table, String targetTableName)
```

**验收标准**:
- [ ] 能识别 `xxx_id` 格式的外键
- [ ] 能处理单数/复数表名映射
- [ ] 能识别带前缀的表名（如 `t_order`）
- [ ] 统计每个表的外键数量和被引用次数
- [ ] 单元测试覆盖 10+ 种命名模式

**技术风险**:
- 命名模式可能不准确，需要后续收集反馈迭代

---

### 任务 2.2：实现关系图构建

**ID**: `T005`
**标题**: 实现关系图数据模型和构建方法
**优先级**: P1
**依赖**: T004
**预估**: 1 小时

**描述**：
构建图数据结构，用于前端可视化展示表关系。

**文件**: `.../analyzer/analyzer/RelationshipAnalyzer.java` (添加方法)

**新增内容**:
```java
public GraphData buildGraphData(List<TableMetadata> tables,
                                Map<String, List<String>> relationships)

@Data
public static class GraphData {
    private List<String> nodes;
    private List<GraphEdge> edges;
}
```

**验收标准**:
- [ ] 图数据包含所有表节点
- [ ] 边表示引用关系
- [ ] 边包含关系类型标签
- [ ] 返回的 JSON 格式符合前端 G6 组件要求

**技术风险**: 无

---

## Phase 3: 复杂度评分器

### 任务 3.1：实现多维度复杂度评分

**ID**: `T006`
**标题**: 实现 ComplexityScorer 多维度评分算法
**优先级**: P0
**依赖**: T002, T004
**预估**: 2 小时

**描述**：
实现复杂度评分算法，综合考虑列数、状态字段、金额字段、外键关系、复合主键等因素。

**文件**: `.../analyzer/analyzer/ComplexityScorer.java`

**评分维度**:
| 维度 | 权重 | 说明 |
|------|------|------|
| 列数 | 0.2 | 列数越多越复杂 |
| 状态字段 | 0.15 | 有状态转换更复杂 |
| 金额字段 | 0.15 | 需要业务规则 |
| 外键关系 | 0.25 | 关系越多越复杂 |
| 复合主键 | 0.1 | 复合主键更复杂 |
| 时间范围字段 | 0.05 | 可能有状态机 |
| 被引用 | 0.1 | 聚合根候选 |

**验收标准**:
- [ ] 实现 `calculateComplexityScore(TableMetadata)` 方法
- [ ] 返回值范围 0.0-1.0
- [ ] 单元测试覆盖各种表结构
- [ ] 评分结果符合直觉（简单表低分，复杂表高分）

**技术风险**:
- 权重设置可能需要调优

---

### 任务 3.2：实现字段特征检测工具方法

**ID**: `T007`
**标题**: 实现状态字段、金额字段等特征检测
**优先级**: P0
**依赖**: T002
**预估**: 1 小时

**描述**：
实现工具方法，检测表是否包含特定类型的字段。

**文件**: `.../analyzer/analyzer/ComplexityScorer.java`

**方法清单**:
```java
boolean hasStatusField(TableMetadata table)
boolean hasAmountField(TableMetadata table)
boolean hasTimeRangeFields(TableMetadata table)
boolean isReferencedByOthers(TableMetadata table)
```

**验收标准**:
- [ ] 状态字段检测：匹配 status, state, _status 等模式
- [ ] 金额字段检测：匹配 amount, price, _amount 等，以及 DECIMAL 类型
- [ ] 时间范围检测：匹配 start_time, end_time, _time 等模式
- [ ] 被引用检测：从 relationshipAnalyzer 获取信息
- [ ] 单元测试覆盖每种检测模式

**技术风险**: 无

---

## Phase 4: 聚合检测器

### 任务 4.1：实现聚合边界检测

**ID**: `T008`
**标题**: 实现 AggregateDetector 聚合边界检测
**优先级**: P0
**依赖**: T004
**预估**: 3 小时

**描述**：
基于外键关系和命名模式，检测聚合边界，识别聚合根。

**文件**: `.../analyzer/detector/AggregateDetector.java`

**核心方法**:
```java
public Map<String, List<String>> detectAggregates(
    List<TableMetadata> tables,
    Map<String, List<String>> relationships
)

private boolean isPotentialAggregateRoot(String tableName, List<String> referencedTables)
private List<String> findAggregateMembers(String rootTable, Map<String, List<String>> relationships)
```

**检测规则**:
1. 不以 `_item`, `_detail` 等后缀结尾的表是聚合根候选
2. 被其他表引用的表很可能是聚合根
3. 引用聚合根的 `_item`, `_detail` 表属于该聚合

**验收标准**:
- [ ] 能正确检测常见的聚合模式
- [ ] 返回聚合名 -> 表列表的映射
- [ ] 聚合根总是列表第一个元素
- [ ] 单元测试覆盖：单表聚合、一对多聚合、多表聚合

**技术风险**:
- 命名不规范可能导致识别错误

---

### 任务 4.2：实现聚合角色分配

**ID**: `T009`
**标题**: 实现聚合内角色识别（聚合根/实体/值对象）
**优先级**: P0
**依赖**: T008
**预估**: 2 小时

**描述**：
为每个表分配在聚合中的角色（AGGREGATE_ROOT, ENTITY, VALUE_OBJECT, STANDALONE）。

**文件**: `.../analyzer/detector/AggregateDetector.java`

**核心方法**:
```java
public AggregateInfo assignAggregateInfo(
    String tableName,
    Map<String, List<String>> aggregates,
    Map<String, List<String>> relationships
)

private AggregateRole determineRole(String tableName, String rootTable, List<String> members)
```

**角色判定规则**:
| 条件 | 角色 |
|------|------|
| tableName == rootTable | AGGREGATE_ROOT |
| tableName 以 _item, _detail 结尾 | ENTITY |
| tableName 以 _one, _info 结尾 | VALUE_OBJECT |
| 无所属聚合 | STANDALONE |

**验收标准**:
- [ ] 每个表都有正确的角色分配
- [ ] 聚合根的角色正确
- [ ] 子实体的父表正确
- [ ] 关系类型正确（ONE_TO_MANY, ONE_TO_ONE 等）
- [ ] 单元测试覆盖每种角色类型

**技术风险**: 无

---

## Phase 5: 规则分类引擎

### 任务 5.1：实现增强规则分类引擎

**ID**: `T010`
**标题**: 实现 EnhancedRuleBasedStrategy 核心分类逻辑
**优先级**: P0
**依赖**: T003, T006, T007
**预估**: 4 小时

**描述**：
实现基于规则的分类引擎，这是核心的分类策略。

**文件**: `.../analyzer/classifier/strategy/EnhancedRuleBasedStrategy.java`

**分类规则优先级**:
1. **贫血模型表（最高优先级）**
   - 前缀匹配：`sys_`, `dict_`, `config_`, `log_`, `audit_`, `temp_`, `tmp_`
   - 后缀匹配：`_log`, `_history`, `_audit`, `_config`, `_relation`, `_mapping`
   - 全名匹配：`dictionary`, `lookup`, `reference`, `metadata`, `settings`

2. **子实体表（跟随聚合根）**
   - 后缀匹配：`_item`, `_detail`, `_line`, `_entry`, `_record`

3. **充血模型表（核心业务）**
   - 关键词：`order`, `product`, `inventory`, `payment`, `shipment`, `invoice`, `contract`
   - + (状态字段 OR 金额字段 OR 列数 >= 10)

4. **复杂度评分判断**
   - 复杂度 > 0.6 → 充血模型

5. **默认**
   - 贫血模型

**验收标准**:
- [ ] 实现完整的 classify 方法
- [ ] 置信度计算合理
- [ ] 分类原因描述清晰
- [ ] 单元测试覆盖所有规则分支（至少 20 个测试用例）
- [ ] 常见表类型分类准确率 > 85%

**技术风险**:
- 规则覆盖不全面，需要持续优化

---

### 任务 5.2：实现分类策略接口

**ID**: `T011`
**标题**: 定义 ClassificationStrategy 接口
**优先级**: P0
**依赖**: T002
**预估**: 0.5 小时

**描述**：
定义分类策略接口，支持多种策略（规则、LLM、混合）。

**文件**: `.../analyzer/classifier/strategy/ClassificationStrategy.java`

**接口定义**:
```java
public interface ClassificationStrategy {
    boolean isAvailable();
    String getName();
    TableAnalysisResult classify(TableMetadata table, Map<String, Object> context);
}
```

**验收标准**:
- [ ] 接口定义清晰
- [ ] 包含必需的方法
- [ ] 添加 JavaDoc 说明各方法用途

**技术风险**: 无

---

## Phase 6: 主服务入口

### 任务 6.1：实现 TableAnalysisService 主服务

**ID**: `T012`
**标题**: 实现 TableAnalysisService 协调各组件
**优先级**: P0
**依赖**: T005, T009, T010, T011
**预估**: 3 小时

**描述**：
实现主服务入口，协调关系分析、聚合检测、模型分类等组件。

**文件**: `.../analyzer/service/TableAnalysisService.java`

**核心流程**:
```
1. 分析表关系 (RelationshipAnalyzer)
2. 检测聚合 (AggregateDetector)
3. 分类每个表 (ClassificationStrategy)
4. 后处理：确保子实体跟随聚合根
```

**核心方法**:
```java
public Map<String, TableAnalysisResult> analyzeTables(List<TableMetadata> tables)
public Map<String, List<String>> getAggregateGroups(List<TableMetadata> tables)
public RelationshipAnalyzer.GraphData getRelationshipGraph(List<TableMetadata> tables)
```

**验收标准**:
- [ ] 服务流程正确
- [ ] 子实体跟随聚合根的模型类型
- [ ] 日志输出清晰（分析过程、结果统计）
- [ ] 集成测试覆盖完整流程

**技术风险**:
- 组件协调可能出错，需要仔细测试

---

### 任务 6.2：创建 AutoConfiguration 配置类

**ID**: `T013`
**标题**: 创建 AnalyzerAutoConfiguration 自动配置
**优先级**: P0
**依赖**: T012
**预估**: 1 小时

**描述**：
创建 Spring Boot 自动配置类，装配所有分析器组件。

**文件**: `.../analyzer/AutoConfiguration.java`

**配置内容**:
```java
@Configuration
@ComponentScan
@EnableConfigurationProperties(AnalyzerProperties.class)
public class AnalyzerAutoConfiguration {

    @Bean
    public TableAnalysisService tableAnalysisService(...) { }

    @Bean
    public RelationshipAnalyzer relationshipAnalyzer() { }

    @Bean
    public AggregateDetector aggregateDetector() { }

    @Bean
    public ComplexityScorer complexityScorer() { }
}
```

**验收标准**:
- [ ] 所有 Bean 正确注册
- [ ] 支持 Spring 自动装配
- [ ] 配置属性类支持外部配置
- [ ] 添加条件注解（可选功能）

**技术风险**: 无

---

## Phase 7: 后端 API 实现

### 任务 7.1：实现表分析 API

**ID**: `T014`
**标题**: 实现 /analyze API 端点
**优先级**: P0
**依赖**: T012, T013
**预估**: 2 小时

**描述**：
在 UnifiedGeneratorController 中添加表分析 API。

**文件**: `fastddd-studio-server/.../controller/UnifiedGeneratorController.java`

**API 规范**:
```
POST /api/unified-generator/analyze
Request: {
  "dataSourceId": "string",
  "tableNames": ["string"],
  "mode": "rule" | "llm"  // 可选，默认 rule
}
Response: ApiResponse<Map<String, TableAnalysisResult>>
```

**验收标准**:
- [ ] API 实现完成
- [ ] 参数校验完整
- [ ] 异常处理完善
- [ ] 返回格式统一
- [ ] 集成测试通过

**技术风险**: 无

---

### 任务 7.2：实现聚合分组 API

**ID**: `T015`
**标题**: 实现 /aggregate-groups API 端点
**优先级**: P0
**依赖**: T012
**预估**: 1 小时

**描述**：
添加获取聚合分组的 API。

**API 规范**:
```
GET /api/unified-generator/aggregate-groups/{dataSourceId}
Response: ApiResponse<Map<String, List<String>>>
```

**验收标准**:
- [ ] API 实现完成
- [ ] 返回聚合名 -> 表列表的映射
- [ ] 支持按数据源过滤

**技术风险**: 无

---

### 任务 7.3：实现关系图 API

**ID**: `T016`
**标题**: 实现 /relationship-graph API 端点
**优先级**: P1
**依赖**: T005, T012
**预估**: 1 小时

**描述**：
添加获取关系图数据的 API，用于前端可视化。

**API 规范**:
```
GET /api/unified-generator/relationship-graph/{dataSourceId}
Response: ApiResponse<GraphData>
```

**验收标准**:
- [ ] API 实现完成
- [ ] 图数据格式符合 G6 组件要求
- [ ] 包含节点和边信息

**技术风险**: 无

---

## Phase 8: 前端类型定义

### 任务 8.1：定义前端 API 类型

**ID**: `T017`
**标题**: 在 unified-generator.ts 中定义新 API 类型
**优先级**: P0
**依赖**: T003
**预估**: 1 小时

**描述**：
添加智能分析相关的 TypeScript 类型定义。

**文件**: `fastddd-studio-ui/src/api/unified-generator.ts`

**新增类型**:
```typescript
export interface TableAnalysisRequest {
  dataSourceId: string
  tableNames: string[]
  mode?: 'rule' | 'llm'
}

export interface TableAnalysisResult {
  tableName: string
  modelType: 'ANEMIC' | 'RICH'
  confidence: number
  reason: string
  strategy: string
  aggregateInfo: AggregateInfo
  metrics: AnalysisMetrics
  suggestedModule?: string
  attributes: Record<string, any>
}

export interface AggregateInfo {
  aggregateName: string
  rootTableName: string
  role: 'AGGREGATE_ROOT' | 'ENTITY' | 'VALUE_OBJECT' | 'STANDALONE'
  memberTables: string[]
  parentTable?: string
  relationshipType: 'ONE_TO_MANY' | 'ONE_TO_ONE' | 'MANY_TO_ONE' | 'NONE'
}

export interface RelationshipGraph {
  nodes: string[]
  edges: Array<{ from: string; to: string; label: string }>
}
```

**验收标准**:
- [ ] 类型定义完整
- [ ] 导出类型供组件使用
- [ ] 类型检查通过

**技术风险**: 无

---

### 任务 8.2：实现前端 API 调用方法

**ID**: `T018`
**标题**: 实现 analyzeTables、getAggregateGroups 等 API 方法
**优先级**: P0
**依赖**: T017
**预估**: 1 小时

**描述**：
在 unifiedGeneratorApi 中添加新的 API 调用方法。

**文件**: `fastddd-studio-ui/src/api/unified-generator.ts`

**新增方法**:
```typescript
async analyzeTables(request: TableAnalysisRequest): Promise<Record<string, TableAnalysisResult>>
async getAggregateGroups(dataSourceId: string): Promise<Record<string, string[]>>
async getRelationshipGraph(dataSourceId: string): Promise<RelationshipGraph>
```

**验收标准**:
- [ ] API 方法实现完成
- [ ] 错误处理正确
- [ ] 返回类型正确

**技术风险**: 无

---

## Phase 9: 前端组件 - 分析面板

### 任务 9.1：创建智能分析面板组件

**ID**: `T019`
**标题**: 创建 IntelligentAnalysisPanel 组件
**优先级**: P0
**依赖**: T017, T018
**预估**: 4 小时

**描述**：
创建独立的智能分析面板组件，可嵌入到表选择步骤中。

**文件**: `fastddd-studio-ui/src/views/unified-generator/steps/IntelligentAnalysisPanel.vue`

**组件功能**:
- [ ] 显示分析模式切换（规则引擎 / AI 增强）
- [ ] 显示分析进度和加载状态
- [ ] 显示分析结果表格
- [ ] 显示聚合分组视图
- [ ] 支持手动修改模型类型
- [ ] 显示置信度和分类原因
- [ ] 支持查看详情

**Props/Emits**:
```typescript
const props = defineProps<{
  dataSourceId: string
  selectedTables: string[]
}>()

const emit = defineEmits<{
  'update:results': [results: Record<string, TableAnalysisResult>]
}>()
```

**验收标准**:
- [ ] 组件渲染正确
- [ ] 分析模式切换工作
- [ ] 结果表格显示完整
- [ ] 聚合视图清晰
- [ ] 手动修改功能正常

**技术风险**:
- 组件交互可能较复杂

---

### 任务 9.2：实现聚合分组视图

**ID**: `T020`
**标题**: 实现聚合分组展示组件
**优先级**: P1
**依赖**: T019
**预估**: 2 小时

**描述**：
创建聚合分组展示子组件，可视化聚合结构。

**文件**: `fastddd-studio-ui/src/views/unified-generator/steps/AggregateGroupsView.vue`

**功能要求**:
- [ ] 卡片式展示每个聚合
- [ ] 聚合根和子实体用不同样式区分
- [ ] 显示聚合名称和成员数量
- [ ] 支持折叠/展开
- [ ] 支持拖拽调整成员

**验收标准**:
- [ ] 聚合分组展示清晰
- [ ] 聚合根标识明显
- [ ] 交互响应流畅

**技术风险**:
- 拖拽功能可能需要额外库

---

### 任务 9.3：实现置信度指示器组件

**ID**: `T021`
**标题**: 实现 ConfidenceIndicator 组件
**优先级**: P1
**依赖**: 无
**预估**: 1 小时

**描述**：
创建置信度指示器组件，直观展示分类置信度。

**文件**: `fastddd-studio-ui/src/components/ConfidenceIndicator.vue`

**功能要求**:
- [ ] 根据置信度显示不同颜色（高/中/低）
- [ ] 悬停显示详细信息
- [ ] 显示置信度百分比
- [ ] 显示分类原因摘要

**验收标准**:
- [ ] 视觉效果清晰
- [ ] 交互体验良好

**技术风险**: 无

---

## Phase 10: 前端集成

### 任务 10.1：修改 TableSelectionPanel 集成分析功能

**ID**: `T022`
**标题**: 在 TableSelectionPanel 中嵌入智能分析
**优先级**: P0
**依赖**: T019
**预估**: 3 小时

**描述**：
修改现有表选择面板，嵌入智能分析功能。

**文件**: `fastddd-studio-ui/src/views/unified-generator/steps/TableSelectionPanel.vue`

**修改内容**:
- [ ] 在选中表后自动触发分析
- [ ] 显示分析结果区域（条件渲染）
- [ ] 将分析结果保存到 store
- [ ] 支持重新分析
- [ ] 处理分析失败情况

**验收标准**:
- [ ] 分析功能嵌入自然
- [ ] 不破坏原有表选择功能
- [ ] 用户流程顺畅

**技术风险**:
- 需要仔细处理状态管理

---

### 任务 10.2：更新 Pinia Store 支持分析结果

**ID**: `T023`
**标题**: 在 useUnifiedGeneratorStore 中添加分析结果状态
**优先级**: P0
**依赖**: T017
**预估**: 1 小时

**描述**：
扩展 Pinia store，存储和管理分析结果。

**文件**: `fastddd-studio-ui/src/stores/unified-generator.ts`

**新增状态和方法**:
```typescript
analysisResults: ref<Record<string, TableAnalysisResult>>({})
aggregateGroups: ref<Record<string, string[]>>({})

function setTableAnalysisResults(results: Record<string, TableAnalysisResult>)
function setAggregateGroups(groups: Record<string, string[]>)
function getModelTypeForTable(tableName: string): ModelType
```

**验收标准**:
- [ ] 状态定义正确
- [ ] 方法实现完整
- [ ] 类型安全

**技术风险**: 无

---

## Phase 11: 关系图可视化

### 任务 11.1：实现关系图可视化组件

**ID**: `T024`
**标题**: 使用 G6 实现关系图组件
**优先级**: P1
**依赖**: T016
**预估**: 4 小时

**描述**：
使用 AntV G6 实现表关系图可视化。

**文件**: `fastddd-studio-ui/src/components/RelationshipGraph.vue`

**功能要求**:
- [ ] 使用 G6 渲染关系图
- [ ] 节点样式区分聚合根和普通表
- [ ] 边显示引用关系
- [ ] 支持缩放和拖拽
- [ ] 支持节点点击事件
- [ ] 支持导出图片

**验收标准**:
- [ ] 图形渲染正确
- [ ] 交互流畅
- [ ] 样式美观

**技术风险**:
- G6 学习曲线
- 大图性能问题

---

### 任务 11.2：集成到分析面板

**ID**: `T025`
**标题**: 将关系图集成到智能分析面板
**优先级**: P1
**依赖**: T019, T024
**预估**: 1 小时

**描述**：
在分析面板中添加关系图视图切换。

**修改文件**: `IntelligentAnalysisPanel.vue`

**验收标准**:
- [ ] 视图切换流畅
- [ ] 图形数据同步
- [ ] 响应式布局

**技术风险**: 无

---

## Phase 12: 后端代码生成支持

### 任务 12.1：扩展 UnifiedProjectRequest 支持聚合配置

**ID**: `T026`
**标题**: 在 UnifiedProjectRequest 中添加 aggregateConfigs 字段
**优先级**: P0
**依赖**: T003
**预估**: 2 小时

**描述**：
扩展请求模型，支持按聚合配置代码生成。

**文件**: `fastddd-studio-server/.../dto/unified/UnifiedProjectRequest.java`

**新增内容**:
```java
private List<AggregateConfig> aggregateConfigs;

@Data
public static class AggregateConfig {
    private String aggregateName;
    private String rootTable;
    private List<String> memberTables;
    private ModelType modelType;
    private Boolean enableCQRS;
    private String moduleName;
}

public ModelType getModelTypeForTable(String tableName)
public AggregateConfig getAggregateForTable(String tableName)
```

**验收标准**:
- [ ] 模型扩展完成
- [ ] 方法实现正确
- [ ] 支持向后兼容（无聚合配置时使用默认）

**技术风险**: 无

---

### 任务 12.2：修改 UnifiedProjectService 支持按聚合生成

**ID**: `T027`
**标题**: 修改代码生成服务支持按聚合组织
**优先级**: P0
**依赖**: T012, T026
**预估**: 4 小时

**描述**：
修改代码生成服务，支持按聚合配置组织生成的代码。

**文件**: `fastddd-studio-server/.../service/UnifiedProjectService.java`

**核心方法**:
```java
private List<AggregateConfig> analyzeAndCreateAggregates(UnifiedProjectRequest request)
private void generateAggregateCode(AggregateConfig aggregate, UnifiedProjectRequest request)
private String getBasePathForAggregate(AggregateConfig aggregate, ModelType modelType)
```

**验收标准**:
- [ ] 无聚合配置时自动分析并创建
- [ ] 按聚合组织目录结构
- [ ] 贫血/充血模型使用不同包结构
- [ ] 生成代码可编译

**技术风险**:
- 包路径计算可能出错

---

### 任务 12.3：调整 CodeGeneratorService 支持聚合包结构

**ID**: `T028`
**标题**: 修改代码生成器支持聚合包路径
**优先级**: P0
**依赖**: T027
**预估**: 3 小时

**描述**：
修改代码生成器，根据聚合配置调整生成的包路径。

**文件**: `fastddd-code-generator/.../service/CodeGeneratorService.java`

**修改内容**:
- [ ] 根据聚合配置设置包名变量
- [ ] 贫血模型：`{package}.system.{aggregate}`
- [ ] 充血模型：`{package}.business.{aggregate}`
- [ ] 共享模块：`{package}.shared`

**验收标准**:
- [ ] 包名计算正确
- [ ] 生成的 import 语句正确
- [ ] 模板变量正确

**技术风险**: 无

---

## Phase 13: 配置模板功能

### 任务 13.1：实现配置模板后端服务

**ID**: `T029`
**标题**: 实现 GeneratorTemplateService
**优先级**: P1
**依赖**: 无
**预估**: 3 小时

**描述**：
实现配置模板的保存、加载、列表功能。

**文件**: `fastddd-studio-server/.../service/GeneratorTemplateService.java`

**API 端点**:
```
POST   /api/unified-generator/templates
GET    /api/unified-generator/templates
GET    /api/unified-generator/templates/{id}
DELETE /api/unified-generator/templates/{id}
```

**验收标准**:
- [ ] CRUD 操作完整
- [ ] 配置序列化/反序列化正确
- [ ] 支持个人和公共模板

**技术风险**:
- JSON 序列化可能有问题

---

### 任务 13.2：实现配置模板前端页面

**ID**: `T030`
**标题**: 创建 TemplateManager 组件
**优先级**: P1
**依赖**: T029
**预估**: 3 小时

**描述**：
创建配置模板管理组件，支持保存、加载、删除模板。

**文件**: `fastddd-studio-ui/src/components/TemplateManager.vue`

**功能要求**:
- [ ] 显示模板列表
- [ ] 保存当前配置为模板
- [ ] 加载模板填充配置
- [ ] 删除模板
- [ ] 模板预览

**验收标准**:
- [ ] 功能完整
- [ ] 交互流畅
- [ ] 确认对话框

**技术风险**: 无

---

## Phase 14: 代码预览功能

### 任务 14.1：实现代码预览后端 API

**ID**: `T031`
**标题**: 实现 /preview API 端点
**优先级**: P1
**依赖**: T027
**预估**: 2 小时

**描述**：
实现代码预览 API，返回将要生成的代码内容。

**文件**: `UnifiedGeneratorController.java`

**API 规范**:
```
POST /api/unified-generator/preview
Request: UnifiedProjectRequest
Response: ApiResponse<Map<string, string>>  // 文件路径 -> 内容
```

**验收标准**:
- [ ] API 实现完成
- [ ] 返回所有生成文件
- [ ] 不写入磁盘

**技术风险**: 无

---

### 任务 14.2：实现代码预览前端组件

**ID**: `T032`
**标题**: 创建 CodePreviewDrawer 组件
**优先级**: P1
**依赖**: T031
**预估**: 4 小时

**描述**：
创建代码预览抽屉组件，支持语法高亮和筛选。

**文件**: `fastddd-studio-ui/src/components/CodePreviewDrawer.vue`

**功能要求**:
- [ ] 抽屉式展示
- [ ] 语法高亮（使用 Shiki 或 Prism）
- [ ] 文件筛选（按聚合/表/类型）
- [ ] 复制代码
- [ ] 下载文件
- [ ] 下载全部

**验收标准**:
- [ ] 语法高亮正确
- [ ] 筛选功能正常
- [ ] 复制/下载功能正常

**技术风险**:
- 语法高亮库选择

---

## Phase 15: 可选 LLM 增强

### 任务 15.1：实现 LLM 客户端

**ID**: `T033`
**标题**: 实现 LLMClient 支持多种提供商
**优先级**: P2
**依赖**: T002
**预估**: 4 小时

**描述**：
实现 LLM 客户端，支持 OpenAI、Claude 等提供商。

**文件**: `.../analyzer/llm/LLMClient.java`

**功能要求**:
- [ ] 支持 OpenAI API
- [ ] 支持 Claude API
- [ ] 支持 Azure OpenAI
- [ ] 超时和重试机制
- [ ] 配置管理

**验收标准**:
- [ ] 基本调用功能
- [ ] 错误处理
- [ ] 配置灵活

**技术风险**:
- API 变化

---

### 任务 15.2：实现 LLM 增强分类策略

**ID**: `T034`
**标题**: 实现 LLMEnhancedStrategy
**优先级**: P2
**依赖**: T033
**预估**: 3 小时

**描述**：
实现基于 LLM 的增强分类策略。

**文件**: `.../analyzer/classifier/strategy/LLMEnhancedStrategy.java`

**功能要求**:
- [ ] 构建详细 Prompt
- [ ] 批量分析
- [ ] 响应解析
- [ ] Fallback 到规则引擎

**验收标准**:
- [ ] 分类准确率 > 规则引擎
- [ ] 响应时间 < 5秒（10个表）
- [ ] 失败时自动降级

**技术风险**:
- LLM 准确率不稳定
- API 费用

---

### 任务 15.3：实现 LLM 响应解析器

**ID**: `T035`
**标题**: 实现 LLMResponseParser 处理各种响应格式
**优先级**: P2
**依赖**: T034
**预估**: 2 小时

**描述**：
实现 LLM 响应解析器，处理 Markdown 包裹、格式不规范等情况。

**文件**: `.../analyzer/llm/LLMResponseParser.java`

**功能要求**:
- [ ] 提取 JSON 块
- [ ] 宽松解析模式
- [ ] 常见格式修复
- [ ] 错误处理

**验收标准**:
- [ ] 解析成功率 > 95%
- [ ] 错误信息清晰

**技术风险**: 无

---

## Phase 16: 测试与文档

### 任务 16.1：编写单元测试

**ID**: `T036`
**标题**: 为所有核心组件编写单元测试
**优先级**: P0
**依赖**: 各组件开发完成
**预估**: 8 小时

**描述**：
为所有核心组件编写单元测试，确保覆盖率 > 80%。

**测试范围**:
- [ ] EnhancedRuleBasedStrategy 测试（20+ 用例）
- [ ] AggregateDetector 测试（10+ 用例）
- [ ] RelationshipAnalyzer 测试（10+ 用例）
- [ ] ComplexityScorer 测试（10+ 用例）
- [ ] TableAnalysisService 测试（5+ 用例）

**验收标准**:
- [ ] 单元测试覆盖率 > 80%
- [ ] 所有测试通过
- [ ] 关键场景有测试

**技术风险**: 无

---

### 任务 16.2：编写集成测试

**ID**: `T037`
**标题**: 编写端到端集成测试
**优先级**: P0
**依赖**: T014, T022
**预估**: 4 小时

**描述**：
编写完整的集成测试，覆盖从前端到后端的完整流程。

**测试场景**:
- [ ] 完整分析流程
- [ ] 混合模型代码生成
- [ ] 配置模板保存/加载
- [ ] 代码预览

**验收标准**:
- [ ] 所有场景测试通过
- [ ] 测试可重复执行
- [ ] 测试数据可维护

**技术风险**: 无

---

### 任务 16.3：编写用户文档

**ID**: `T038`
**标题**: 编写智能分析功能用户文档
**优先级**: P1
**依赖**: 核心功能完成
**预估**: 4 小时

**描述**：
编写完整的用户文档，包括功能说明、使用示例、最佳实践。

**文档内容**:
- [ ] 功能概述
- [ ] 使用指南
- [ ] 配置说明
- [ ] 最佳实践
- [ ] 常见问题
- [ ] 示例截图

**验收标准**:
- [ ] 文档完整
- [ ] 示例清晰
- [ ] 截图准确

**技术风险**: 无

---

## 任务依赖关系图

```
T001 (DomainModelType)
    ↓
T002 (analyzer模块) → T003 (模型类) → T011 (策略接口)
                              ↓
                    T004 (关系分析) → T005 (关系图)
                              ↓
                    T006 (复杂度评分)
                              ↓
T007 (特征检测) → T010 (规则引擎) → T012 (主服务)
                              ↓
T008 (聚合检测) → T009 (角色分配)
                              ↓
                    T013 (自动配置) → T014 (分析API)
                                          ↓
T015 (聚合API) ← T016 (关系图API)
                              ↓
                    T017 (前端类型) → T018 (前端API)
                                          ↓
                              T019 (分析面板)
                                          ↓
T020 (聚合视图) ← T021 (置信度组件)
                              ↓
T022 (集成面板) ← T023 (Store更新)
                              ↓
T024 (关系图) → T025 (集成图)
                              ↓
                    T026 (请求模型) → T027 (服务修改)
                                          ↓
T028 (生成器修改)
                              ↓
T029 (模板服务) → T030 (模板组件)
                              ↓
T031 (预览API) → T032 (预览组件)
                              ↓
T033 (LLM客户端) → T034 (LLM策略) → T035 (响应解析)
                              ↓
T036 (单元测试) → T037 (集成测试) → T038 (文档)
```

---

## 开发里程碑

### Milestone 1: 核心分析引擎（Week 1）
**目标**: 完成后端核心分析功能
**包含任务**: T001-T013
**交付物**:
- 可运行的分析服务
- 单元测试通过

### Milestone 2: API 与前端基础（Week 2）
**目标**: 完成 API 和前端基础组件
**包含任务**: T014-T023
**交付物**:
- API 端点可用
- 前端分析面板可用

### Milestone 3: 可视化与代码生成（Week 3）
**目标**: 完成可视化和代码生成支持
**包含任务**: T024-T028
**交付物**:
- 关系图可视化
- 混合模型代码生成

### Milestone 4: 产品功能完善（Week 4）
**目标**: 完成模板和预览功能
**包含任务**: T029-T032
**交付物**:
- 配置模板管理
- 代码预览功能

### Milestone 5: 测试与文档（Week 5）
**目标**: 完成测试和文档
**包含任务**: T036-T038
**交付物**:
- 测试报告
- 用户文档

---

## 任务执行建议

1. **按顺序执行**: 严格按照依赖关系执行任务
2. **小步验证**: 每完成一个任务立即验证
3. **持续集成**: 每个里程碑完成后运行完整测试
4. **代码审查**: P0 任务完成后进行代码审查
5. **文档同步**: 代码和文档同步更新

---

## 风险跟踪

| 风险 | 影响任务 | 缓解措施 | 状态 |
|------|----------|----------|------|
| 规则引擎准确率不足 | T010 | 收集反馈持续优化 | 监控中 |
| 聚合识别错误 | T008-T009 | 提供手动调整 | 监控中 |
| 包路径计算错误 | T027-T028 | 充分测试 | 监控中 |
| LLM API 不稳定 | T033-T035 | 规则引擎 Fallback | 规划中 |
| 前端状态管理复杂 | T022-T023 | 清晰的状态设计 | 监控中 |
