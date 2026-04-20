package com.gallop.fastddd.analyzer.analyzer;

import com.gallop.fastddd.analyzer.properties.AnalyzerProperties;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import com.gallop.fastddd.codegen.model.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ComplexityScorer 单元测试
 * <p>
 * 测试复杂度评分算法，验证各维度评分正确性
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplexityScorer 测试")
class ComplexityScorerTest {

    @Mock
    private RelationshipAnalyzer relationshipAnalyzer;

    private ComplexityScorer scorer;
    private AnalyzerProperties properties;

    @BeforeEach
    void setUp() {
        properties = createDefaultProperties();
        scorer = new ComplexityScorer(properties, relationshipAnalyzer);
    }

    // ==================== 列数评分测试 ====================

    @Test
    @DisplayName("少量列的表应得低分")
    void shouldGiveLowScore_WhenFewColumns() {
        // Given
        TableMetadata table = createTableWithColumnCount(5);

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then: 5/25 = 0.2 正好等于列数权重上限
        assertThat(score).isLessThanOrEqualTo(0.2);
    }

    @Test
    @DisplayName("多列的表应得高分")
    void shouldGiveHighScore_WhenManyColumns() {
        // Given
        TableMetadata table = createTableWithColumnCount(25);

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(score).isGreaterThan(0.15);
    }

    @Test
    @DisplayName("列数评分应有上限")
    void shouldCapColumnScore_WhenTooManyColumns() {
        // Given
        TableMetadata table1 = createTableWithColumnCount(30);
        TableMetadata table2 = createTableWithColumnCount(50);

        // When
        double score1 = scorer.calculateComplexityScore(table1, 0, false);
        double score2 = scorer.calculateComplexityScore(table2, 0, false);

        // Then: 列数评分上限是 columnCount 权重 (0.2)
        // 30列已经超过上限(25列)，50列也超过上限
        // 两者评分应该相同或非常接近，都接近 0.2
        assertThat(score1).isLessThan(0.25);
        assertThat(score2).isLessThan(0.25);
        // 两者差异应该很小
        assertThat(Math.abs(score1 - score2)).isLessThan(0.01);
    }

    // ==================== 状态字段评分测试 ====================

    @Test
    @DisplayName("包含状态字段的表应得加分")
    void shouldAddScore_WhenHasStatusField() {
        // Given
        TableMetadata table = createTableWithColumns("test_table", List.of(
            "id", "name", "status", "created_time"
        ));

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(score).isGreaterThan(0);
    }

    @Test
    @DisplayName("包含state字段的表应得加分")
    void shouldAddScore_WhenHasStateField() {
        // Given
        TableMetadata table = createTableWithColumns("test_table", List.of(
            "id", "order_state"
        ));

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(score).isGreaterThan(0);
    }

    @Test
    @DisplayName("应检测状态字段")
    void shouldDetectStatusField() {
        // Given
        TableMetadata tableWithStatus = createTableWithColumns("test", List.of("id", "status"));
        TableMetadata tableWithoutStatus = createTableWithColumns("test", List.of("id", "name"));

        // When & Then
        assertThat(scorer.hasStatusField(tableWithStatus)).isTrue();
        assertThat(scorer.hasStatusField(tableWithoutStatus)).isFalse();
    }

    // ==================== 金额字段评分测试 ====================

    @Test
    @DisplayName("包含金额字段的表应得加分")
    void shouldAddScore_WhenHasAmountField() {
        // Given
        TableMetadata table = createTableWithColumns("test_table", List.of(
            "id", "total_amount", "price"
        ));

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(score).isGreaterThan(0);
    }

    @Test
    @DisplayName("应检测金额字段")
    void shouldDetectAmountField() {
        // Given
        TableMetadata tableWithAmount = createTableWithColumns("test", List.of("id", "amount"));
        TableMetadata tableWithPrice = createTableWithColumns("test", List.of("id", "price"));

        // When & Then
        assertThat(scorer.hasAmountField(tableWithAmount)).isTrue();
        assertThat(scorer.hasAmountField(tableWithPrice)).isTrue();
    }

    // ==================== 外键关系评分测试 ====================

    @Test
    @DisplayName("有外键的表应得加分")
    void shouldAddScore_WhenHasForeignKey() {
        // Given
        TableMetadata table = createTableWithColumns("test_table", List.of("id", "name"));

        // When
        double scoreWithFk = scorer.calculateComplexityScore(table, 3, false);
        double scoreWithoutFk = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(scoreWithFk).isGreaterThan(scoreWithoutFk);
    }

    @Test
    @DisplayName("外键评分应有上限")
    void shouldCapForeignKeyScore() {
        // Given
        TableMetadata table = createTableWithColumns("test", List.of("id"));

        // When
        double score3Fk = scorer.calculateComplexityScore(table, 3, false);
        double score10Fk = scorer.calculateComplexityScore(table, 10, false);

        // Then
        // 3个外键和10个外键的分数差异应该很小（因为上限是3个外键达到满权重）
        assertThat(score10Fk - score3Fk).isLessThan(0.01);
    }

    // ==================== 复合主键评分测试 ====================

    @Test
    @DisplayName("有复合主键的表应得加分")
    void shouldAddScore_WhenHasCompositeKey() {
        // Given
        TableMetadata table = new TableMetadata();
        table.setTableName("test");
        table.setColumns(createColumns(5));

        // 设置复合主键
        List<ColumnMetadata> primaryKeys = new ArrayList<>();
        primaryKeys.add(table.getColumns().get(0));
        primaryKeys.add(table.getColumns().get(1));
        table.setPrimaryKeys(primaryKeys);

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(scorer.hasCompositeKey(table)).isTrue();
        assertThat(score).isGreaterThan(0);
    }

    // ==================== 时间范围字段评分测试 ====================

    @Test
    @DisplayName("包含时间范围字段的表应得加分")
    void shouldAddScore_WhenHasTimeRangeFields() {
        // Given
        TableMetadata table = createTableWithColumns("test_table", List.of(
            "id", "start_time", "end_time"
        ));

        // When
        double score = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(scorer.hasTimeRangeFields(table)).isTrue();
        assertThat(score).isGreaterThan(0);
    }

    // ==================== 被引用评分测试 ====================

    @Test
    @DisplayName("被引用的表应得加分")
    void shouldAddScore_WhenIsReferenced() {
        // Given
        TableMetadata table = createTableWithColumns("test", List.of("id"));

        // When
        double scoreReferenced = scorer.calculateComplexityScore(table, 0, true);
        double scoreNotReferenced = scorer.calculateComplexityScore(table, 0, false);

        // Then
        assertThat(scoreReferenced).isGreaterThan(scoreNotReferenced);
    }

    // ==================== 子实体检测测试 ====================

    @Test
    @DisplayName("应正确识别子实体表")
    void shouldDetectEntityTable() {
        // When & Then
        assertThat(scorer.isEntityTable("order_item")).isTrue();
        assertThat(scorer.isEntityTable("order_detail")).isTrue();
        assertThat(scorer.isEntityTable("order")).isFalse();
    }

    // ==================== 复杂度级别测试 ====================

    @Test
    @DisplayName("应正确判断复杂度级别")
    void shouldDetermineComplexityLevel() {
        assertThat(scorer.getComplexityLevel(0.2)).isEqualTo("低");
        assertThat(scorer.getComplexityLevel(0.5)).isEqualTo("中");
        assertThat(scorer.getComplexityLevel(0.8)).isEqualTo("高");
    }

    // ==================== 辅助方法 ====================

    private TableMetadata createTableWithColumnCount(int count) {
        List<String> columns = new ArrayList<>();
        columns.add("id"); // 主键
        for (int i = 1; i < count; i++) {
            columns.add("column_" + i);
        }
        return createTableWithColumns("test", columns);
    }

    private TableMetadata createTableWithColumns(String tableName, List<String> columnNames) {
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);
        table.setColumns(createColumns(columnNames));

        // 设置主键
        List<ColumnMetadata> primaryKeys = new ArrayList<>();
        primaryKeys.add(table.getColumns().get(0));
        table.setPrimaryKeys(primaryKeys);

        return table;
    }

    private List<ColumnMetadata> createColumns(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("column_" + i);
        }
        return createColumns(names);
    }

    private List<ColumnMetadata> createColumns(List<String> names) {
        List<ColumnMetadata> columns = new ArrayList<>();
        for (String name : names) {
            ColumnMetadata column = new ColumnMetadata();
            column.setColumnName(name);
            column.setDataTypeName("VARCHAR");
            columns.add(column);
        }
        return columns;
    }

    private AnalyzerProperties createDefaultProperties() {
        AnalyzerProperties properties = new AnalyzerProperties();

        AnalyzerProperties.ComplexityWeights weights = new AnalyzerProperties.ComplexityWeights();
        weights.setColumnCount(0.2);
        weights.setStatusField(0.15);
        weights.setAmountField(0.15);
        weights.setForeignKey(0.25);
        weights.setCompositeKey(0.1);
        weights.setTimeRange(0.05);
        weights.setReferenced(0.1);

        properties.setComplexityWeights(weights);
        return properties;
    }
}
