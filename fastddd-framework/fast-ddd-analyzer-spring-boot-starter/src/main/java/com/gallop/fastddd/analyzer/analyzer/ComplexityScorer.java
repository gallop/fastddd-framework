package com.gallop.fastddd.analyzer.analyzer;

import com.gallop.fastddd.analyzer.constants.FieldPatterns;
import com.gallop.fastddd.analyzer.properties.AnalyzerProperties;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 复杂度评分器
 * <p>
 * 对表进行多维度复杂度评分，综合考虑列数、状态字段、金额字段、
 * 外键关系、复合主键、时间范围字段和被引用情况等因素。
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityScorer {

    private final AnalyzerProperties properties;
    private final RelationshipAnalyzer relationshipAnalyzer;

    /**
     * 计算表的复杂度评分
     *
     * @param table 表元数据
     * @param foreignKeyCount 外键数量
     * @param isReferenced 是否被其他表引用
     * @return 复杂度评分 (0.0-1.0)
     */
    public double calculateComplexityScore(TableMetadata table,
                                            Integer foreignKeyCount,
                                            Boolean isReferenced) {
        double score = 0.0;

        // 1. 列数贡献 (0-0.2)
        int columnCount = table.getColumns().size();
        double columnWeight = properties.getComplexityWeights().getColumnCount();
        score += Math.min(columnCount / 25.0, columnWeight);
        log.trace("表 {} 列数 {} 得分: {}", table.getTableName(), columnCount, Math.min(columnCount / 25.0, columnWeight));

        // 2. 状态字段贡献 (0.15)
        double statusWeight = properties.getComplexityWeights().getStatusField();
        if (hasStatusField(table)) {
            score += statusWeight;
            log.trace("表 {} 包含状态字段 得分: {}", table.getTableName(), statusWeight);
        }

        // 3. 金额字段贡献 (0.15)
        double amountWeight = properties.getComplexityWeights().getAmountField();
        if (hasAmountField(table)) {
            score += amountWeight;
            log.trace("表 {} 包含金额字段 得分: {}", table.getTableName(), amountWeight);
        }

        // 4. 外键关系贡献 (0-0.25)
        // 修复：限制最多3个外键达到满权重，避免计算溢出
        double fkWeight = properties.getComplexityWeights().getForeignKey();
        int fkCount = foreignKeyCount != null ? foreignKeyCount : 0;
        final int MAX_FK_COUNT = 3;
        double fkScore = Math.min(fkCount, MAX_FK_COUNT) * fkWeight / MAX_FK_COUNT;
        score += fkScore;
        log.debug("表 {} 外键数 {} 得分: {}", table.getTableName(), fkCount, fkScore);

        // 5. 复合主键贡献 (0.1)
        double compositeKeyWeight = properties.getComplexityWeights().getCompositeKey();
        if (table.getPrimaryKeys().size() > 1) {
            score += compositeKeyWeight;
            log.trace("表 {} 复合主键 得分: {}", table.getTableName(), compositeKeyWeight);
        }

        // 6. 时间范围字段贡献 (0.05)
        double timeRangeWeight = properties.getComplexityWeights().getTimeRange();
        if (hasTimeRangeFields(table)) {
            score += timeRangeWeight;
            log.trace("表 {} 包含时间范围字段 得分: {}", table.getTableName(), timeRangeWeight);
        }

        // 7. 被引用贡献 (0.1)
        double referencedWeight = properties.getComplexityWeights().getReferenced();
        if (Boolean.TRUE.equals(isReferenced)) {
            score += referencedWeight;
            log.trace("表 {} 被其他表引用 得分: {}", table.getTableName(), referencedWeight);
        }

        double finalScore = Math.min(score, 1.0);
        log.debug("表 {} 复杂度评分: {}", table.getTableName(), finalScore);
        return finalScore;
    }

    /**
     * 检测表是否包含状态字段
     *
     * @param table 表元数据
     * @return 如果包含状态字段返回 true
     */
    public boolean hasStatusField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> FieldPatterns.STATUS_FIELD_PATTERNS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 检测表是否包含金额字段
     *
     * @param table 表元数据
     * @return 如果包含金额字段返回 true
     */
    public boolean hasAmountField(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> {
                String colName = c.getColumnName().toLowerCase();
                // 检查列名模式
                boolean nameMatches = FieldPatterns.AMOUNT_FIELD_PATTERNS.stream().anyMatch(colName::contains);
                // 检查数据类型
                boolean typeMatches = c.getDataTypeName() != null && (
                    c.getDataTypeName().toLowerCase().contains("decimal") ||
                    c.getDataTypeName().toLowerCase().contains("money") ||
                    c.getDataTypeName().toLowerCase().contains("double") ||
                    c.getDataTypeName().toLowerCase().contains("bigint")
                );
                return nameMatches || typeMatches;
            });
    }

    /**
     * 检测表是否包含时间范围字段
     *
     * @param table 表元数据
     * @return 如果包含时间范围字段返回 true
     */
    public boolean hasTimeRangeFields(TableMetadata table) {
        return table.getColumns().stream()
            .anyMatch(c -> FieldPatterns.TIME_RANGE_FIELDS.stream().anyMatch(
                pattern -> c.getColumnName().toLowerCase().contains(pattern)));
    }

    /**
     * 检测表是否为子实体表（String 重载）
     *
     * @param tableName 表名
     * @return 如果是子实体表返回 true
     */
    public boolean isEntityTable(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.ENTITY_SUFFIXES.stream().anyMatch(tableLower::endsWith);
    }

    /**
     * 检测表是否为子实体表（TableMetadata 重载）
     *
     * @param table 表元数据
     * @return 如果是子实体表返回 true
     */
    public boolean isEntityTable(TableMetadata table) {
        return isEntityTable(table.getTableName());
    }

    /**
     * 检测表是否包含复合主键
     *
     * @param table 表元数据
     * @return 如果包含复合主键返回 true
     */
    public boolean hasCompositeKey(TableMetadata table) {
        return table.getPrimaryKeys().size() > 1;
    }

    /**
     * 获取表的列数量
     *
     * @param table 表元数据
     * @return 列数量
     */
    public int getColumnCount(TableMetadata table) {
        return table.getColumns().size();
    }

    /**
     * 判断表的复杂度级别
     *
     * @param score 复杂度评分
     * @return 复杂度级别：低、中、高
     */
    public String getComplexityLevel(double score) {
        if (score < 0.3) {
            return "低";
        } else if (score < 0.6) {
            return "中";
        } else {
            return "高";
        }
    }
}
