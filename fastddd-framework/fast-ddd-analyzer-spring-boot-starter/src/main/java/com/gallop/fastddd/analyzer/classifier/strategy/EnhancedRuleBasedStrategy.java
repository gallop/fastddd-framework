package com.gallop.fastddd.analyzer.classifier.strategy;

import com.gallop.fastddd.analyzer.analyzer.ComplexityScorer;
import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.classifier.model.AnalysisMetrics;
import com.gallop.fastddd.analyzer.constants.FieldPatterns;
import com.gallop.fastddd.common.enums.DomainModelType;
import com.gallop.fastddd.common.model.ColumnMetadata;
import com.gallop.fastddd.common.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 增强的基于规则的分类策略
 * <p>
 * 这是主要的分类策略，不依赖外部 LLM API，响应快速且稳定。
 * 通过多维度规则分析表结构，智能推荐贫血模型或充血模型。
 * </p>
 *
 * <h2>分类规则优先级</h2>
 * <ol>
 *   <li><b>P0: 明显的贫血模型表</b> - 系统表、字典表、配置表、日志表</li>
 *   <li><b>P1: 子实体表</b> - 以 _item, _detail 结尾，跟随聚合根</li>
 *   <li><b>P2: 明显的充血模型表</b> - 核心业务实体，包含状态/金额字段</li>
 *   <li><b>P3: 复杂度评分判断</b> - 复杂度 > 0.6 倾向于充血模型</li>
 *   <li><b>P4: 默认</b> - 贫血模型</li>
 * </ol>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedRuleBasedStrategy implements ClassificationStrategy {

    private final ComplexityScorer complexityScorer;

    @Override
    public boolean isAvailable() {
        // 规则策略始终可用
        return true;
    }

    @Override
    public String getName() {
        return "Enhanced-Rule-Based";
    }

    @Override
    public TableAnalysisResult classify(TableMetadata table, Map<String, Object> context) {
        String tableName = table.getTableName();
        String tableComment = table.getTableComment() != null ?
            table.getTableComment().toLowerCase() : "";

        log.debug("分析表: {}, 列数: {}", tableName, table.getColumns().size());

        // ========== 规则 1: 明显的贫血模型表（高置信度） ==========
        if (isAnemicTable(tableName, tableComment)) {
            double confidence = 0.9;
            return buildResult(table, DomainModelType.ANEMIC, confidence,
                "匹配系统/配置/字典/日志表模式", context);
        }

        // ========== 规则 2: 关联表（中等置信度） ==========
        if (isRelationTable(tableName)) {
            double confidence = 0.8;
            return buildResult(table, DomainModelType.ANEMIC, confidence,
                "匹配关联表模式，使用贫血模型", context);
        }

        // ========== 规则 3: 子实体表（标记，等待聚合根处理） ==========
        if (isEntityTable(tableName)) {
            double confidence = 0.7;
            TableAnalysisResult result = buildResult(table, DomainModelType.RICH, confidence,
                "子实体表，跟随聚合根的模型类型", context);
            result.getAttributes().put("isEntity", true);
            result.getAttributes().put("followAggregateRoot", true);
            return result;
        }

        // ========== 规则 4: 明显的充血模型表（核心业务） ==========
        RichTableAnalysis richAnalysis = analyzeRichTable(tableName, tableComment, table);
        if (richAnalysis.isRichTable) {
            // 限制置信度加成上限为 0.15
            double cappedBonus = Math.min(richAnalysis.confidenceBonus, 0.15);
            double confidence = 0.75 + cappedBonus;
            return buildResult(table, DomainModelType.RICH, confidence,
                richAnalysis.reason, context);
        }

        // ========== 规则 5: 基于复杂度评分判断 ==========
        int fkCount = getForeignKeyCount(tableName, context);
        boolean isReferenced = getIsReferenced(tableName, context);
        double complexityScore = complexityScorer.calculateComplexityScore(table, fkCount, isReferenced);

        if (complexityScore > 0.6) {
            double confidence = 0.65 + (complexityScore - 0.6) * 0.5; // 0.65-0.85
            return buildResult(table, DomainModelType.RICH, confidence,
                String.format("表复杂度 %.2f 超过阈值，建议充血模型", complexityScore), context);
        }

        // ========== 默认：贫血模型 ==========
        return buildResult(table, DomainModelType.ANEMIC, 0.6,
            "默认分类：简单 CRUD 表", context);
    }

    /**
     * 判断是否为贫血模型表
     */
    private boolean isAnemicTable(String tableName, String tableComment) {
        String tableLower = tableName.toLowerCase();

        // 前缀匹配
        if (FieldPatterns.ANEMIC_PREFIXES.stream().anyMatch(tableLower::startsWith)) {
            return true;
        }

        // 后缀匹配
        if (FieldPatterns.ANEMIC_SUFFIXES.stream().anyMatch(suffix -> tableLower.endsWith(suffix))) {
            return true;
        }

        // 全名匹配
        if (FieldPatterns.ANEMIC_EXACT_MATCHES.stream().anyMatch(keyword ->
                tableLower.equals(keyword) || tableComment.contains(keyword))) {
            return true;
        }

        return false;
    }

    /**
     * 判断是否为关联表
     */
    private boolean isRelationTable(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.RELATION_SUFFIXES.stream().anyMatch(suffix -> tableLower.endsWith(suffix));
    }

    /**
     * 判断是否为子实体表
     */
    private boolean isEntityTable(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.ENTITY_SUFFIXES.stream().anyMatch(suffix -> tableLower.endsWith(suffix));
    }

    /**
     * 分析是否为充血模型表
     */
    private RichTableAnalysis analyzeRichTable(String tableName, String tableComment, TableMetadata table) {
        boolean hasRichKeyword = hasRichKeyword(tableName, tableComment);
        boolean hasStatus = hasStatusField(table);
        boolean hasAmount = hasAmountField(table);
        boolean hasQuantity = hasQuantityField(table);
        boolean hasBusinessRules = hasStatus || hasAmount || hasQuantity;
        boolean hasTimeRange = hasTimeRangeField(table);
        boolean hasManyColumns = table.getColumns().size() >= 10;

        // 核心业务关键词 + (业务规则 OR 大量列)
        boolean isRichCandidate = hasRichKeyword && (hasBusinessRules || hasTimeRange || hasManyColumns);

        // 计算置信度加成
        double confidenceBonus = 0.0;
        if (hasBusinessRules) confidenceBonus += 0.1;
        if (hasStatus && hasAmount) confidenceBonus += 0.05;
        if (hasManyColumns) confidenceBonus += 0.05;

        String reason = buildRichTableReason(hasRichKeyword, hasStatus, hasAmount, hasManyColumns);

        return new RichTableAnalysis(isRichCandidate, confidenceBonus, reason);
    }

    /**
     * 判断是否包含充血模型关键词
     */
    private boolean hasRichKeyword(String tableName, String tableComment) {
        String tableLower = tableName.toLowerCase();
        String commentLower = tableComment.toLowerCase();

        return FieldPatterns.RICH_KEYWORDS.stream().anyMatch(keyword ->
            tableLower.contains(keyword) || commentLower.contains(keyword)
        );
    }

    /**
     * 检测是否包含状态字段
     */
    private boolean hasStatusField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> FieldPatterns.STATUS_FIELD_PATTERNS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 检测是否包含金额字段
     */
    private boolean hasAmountField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> {
                String colName = c.getColumnName().toLowerCase();
                boolean nameMatches = FieldPatterns.AMOUNT_FIELD_PATTERNS.stream().anyMatch(colName::contains);
                boolean typeMatches = c.getDataTypeName() != null && (
                    c.getDataTypeName().toLowerCase().contains("decimal") ||
                    c.getDataTypeName().toLowerCase().contains("double") ||
                    c.getDataTypeName().toLowerCase().contains("money") ||
                    c.getDataTypeName().toLowerCase().contains("bigint")
                );
                return nameMatches || typeMatches;
            });
    }

    /**
     * 检测是否包含数量字段
     */
    private boolean hasQuantityField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> FieldPatterns.QUANTITY_FIELD_PATTERNS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 检测是否包含时间范围字段
     */
    private boolean hasTimeRangeField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> FieldPatterns.TIME_RANGE_FIELDS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 构建充血模型表分类原因
     */
    private String buildRichTableReason(boolean hasRichKeyword, boolean hasStatus,
                                          boolean hasAmount, boolean hasManyColumns) {
        List<String> reasons = new ArrayList<>();

        if (hasRichKeyword) {
            reasons.add("核心业务实体");
        }
        if (hasStatus) {
            reasons.add("包含状态字段");
        }
        if (hasAmount) {
            reasons.add("包含金额字段");
        }
        if (hasManyColumns) {
            reasons.add("列数较多");
        }

        return String.join(" + ", reasons);
    }

    /**
     * 获取外键数量（从 context 中获取表特定的数据）
     * <p>修复：context 中的 key 格式为 "tableName_foreignKeyCount"</p>
     */
    private Integer getForeignKeyCount(String tableName, Map<String, Object> context) {
        if (context == null) return 0;
        String key = tableName.toLowerCase() + "_foreignKeyCount";
        Object fkCount = context.get(key);
        if (fkCount instanceof Number) {
            return ((Number) fkCount).intValue();
        }
        return 0;
    }

    /**
     * 获取是否被引用（从 context 中获取表特定的数据）
     * <p>修复：context 中的 key 格式为 "tableName_isReferenced"</p>
     */
    private Boolean getIsReferenced(String tableName, Map<String, Object> context) {
        if (context == null) return false;
        String key = tableName.toLowerCase() + "_isReferenced";
        Object isReferenced = context.get(key);
        if (isReferenced instanceof Boolean) {
            return (Boolean) isReferenced;
        }
        return false;
    }

    /**
     * 构建分类结果
     * <p>修复：使用传入的 context 获取正确的表关系数据</p>
     */
    private TableAnalysisResult buildResult(TableMetadata table, DomainModelType modelType,
                                             double confidence, String reason,
                                             Map<String, Object> context) {
        String tableName = table.getTableName();

        // 从 context 获取外键数量和被引用状态
        int fkCount = getForeignKeyCount(tableName, context);
        boolean isReferenced = getIsReferenced(tableName, context);

        // 计算复杂度评分
        double complexityScore = complexityScorer.calculateComplexityScore(
            table, fkCount, isReferenced);

        return TableAnalysisResult.builder()
            .tableName(tableName)
            .modelType(modelType)
            .confidence(confidence)
            .reason(reason)
            .strategy(getName())
            .metrics(AnalysisMetrics.builder()
                .columnCount(table.getColumns().size())
                .relationshipCount(fkCount)
                .complexityScore(complexityScore)
                .hasStateMachine(complexityScorer.hasStatusField(table))
                .hasBusinessRules(complexityScorer.hasAmountField(table))
                .isAggregateRoot(false) // 由后续聚合检测设置
                .hasStatusField(complexityScorer.hasStatusField(table))
                .hasAmountField(complexityScorer.hasAmountField(table))
                .hasTimeRangeFields(complexityScorer.hasTimeRangeFields(table))
                .hasCompositeKey(complexityScorer.hasCompositeKey(table))
                .foreignKeyCount(fkCount)
                .referencedByCount(isReferenced ? 1 : 0)
                .build())
            .attributes(new HashMap<>())
            .build();
    }

    /**
     * 充血表分析结果
     */
    private record RichTableAnalysis(
        boolean isRichTable,
        double confidenceBonus,
        String reason
    ) {}
}
