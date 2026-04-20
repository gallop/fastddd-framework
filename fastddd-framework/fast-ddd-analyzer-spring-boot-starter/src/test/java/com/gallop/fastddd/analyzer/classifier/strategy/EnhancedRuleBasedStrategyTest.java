package com.gallop.fastddd.analyzer.classifier.strategy;

import com.gallop.fastddd.analyzer.analyzer.ComplexityScorer;
import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.classifier.model.AnalysisMetrics;
import com.gallop.fastddd.common.enums.DomainModelType;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import com.gallop.fastddd.codegen.model.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * EnhancedRuleBasedStrategy 单元测试
 * <p>
 * 测试基于规则的分类引擎，覆盖所有规则分支
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnhancedRuleBasedStrategy 测试")
class EnhancedRuleBasedStrategyTest {

    @Mock
    private ComplexityScorer complexityScorer;

    private EnhancedRuleBasedStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EnhancedRuleBasedStrategy(complexityScorer);
    }

    // ==================== P0: 明显的贫血模型表 ====================

    @Test
    @DisplayName("系统前缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasSystemPrefix() {
        // Given
        TableMetadata table = createTable("sys_user", "系统用户表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
        assertThat(result.getConfidence()).isGreaterThan(0.85);
        assertThat(result.getReason()).contains("系统");
    }

    @Test
    @DisplayName("字典前缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasDictPrefix() {
        // Given
        TableMetadata table = createTable("dict_order_status", "订单状态字典");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
        assertThat(result.getConfidence()).isGreaterThan(0.85);
    }

    @Test
    @DisplayName("配置前缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasConfigPrefix() {
        // Given
        TableMetadata table = createTable("config_system", "系统配置表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
    }

    @Test
    @DisplayName("日志后缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasLogSuffix() {
        // Given
        TableMetadata table = createTable("operation_log", "操作日志表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
    }

    @Test
    @DisplayName("审计后缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasAuditSuffix() {
        // Given
        TableMetadata table = createTable("data_audit", "数据审计表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
    }

    @Test
    @DisplayName("关联后缀表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenHasRelationSuffix() {
        // Given
        TableMetadata table = createTable("user_role_relation", "用户角色关联表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
    }

    @Test
    @DisplayName("全名匹配字典表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenIsDictionaryTable() {
        // Given
        TableMetadata table = createTable("dictionary", "数据字典表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
    }

    // ==================== P1: 子实体表 ====================

    @Test
    @DisplayName("_item 后缀表应标记为子实体")
    void shouldMarkAsEntity_WhenHasItemSuffix() {
        // Given
        TableMetadata table = createTable("order_item", "订单明细表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
        assertThat(result.getAttributes().get("isEntity")).isEqualTo(true);
        assertThat(result.getAttributes().get("followAggregateRoot")).isEqualTo(true);
        assertThat(result.getReason()).contains("子实体");
    }

    @Test
    @DisplayName("_detail 后缀表应标记为子实体")
    void shouldMarkAsEntity_WhenHasDetailSuffix() {
        // Given
        TableMetadata table = createTable("order_detail", "订单详情表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getAttributes().get("isEntity")).isEqualTo(true);
    }

    @Test
    @DisplayName("_line 后缀表应标记为子实体")
    void shouldMarkAsEntity_WhenHasLineSuffix() {
        // Given
        TableMetadata table = createTable("invoice_line", "发票行表");

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getAttributes().get("isEntity")).isEqualTo(true);
    }

    // ==================== P2: 明显的充血模型表 ====================

    @Test
    @DisplayName("订单表应分类为充血模型")
    void shouldClassifyAsRich_WhenIsOrderTable() {
        // Given
        TableMetadata table = createTableWithColumns("order", "订单表",
            List.of("id", "order_no", "customer_id", "total_amount", "status"));

        mockComplexityScore(0.5);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
        assertThat(result.getReason()).contains("核心业务实体");
    }

    @Test
    @DisplayName("产品表应分类为充血模型")
    void shouldClassifyAsRich_WhenIsProductTable() {
        // Given
        TableMetadata table = createTableWithColumns("product", "产品表",
            List.of("id", "product_name", "price", "stock"));

        mockComplexityScore(0.5);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
    }

    @Test
    @DisplayName("支付表应分类为充血模型")
    void shouldClassifyAsRich_WhenIsPaymentTable() {
        // Given
        TableMetadata table = createTableWithColumns("payment", "支付表",
            List.of("id", "order_id", "amount", "status", "pay_time"));

        mockComplexityScore(0.5);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
    }

    @Test
    @DisplayName("库存表应分类为充血模型")
    void shouldClassifyAsRich_WhenIsInventoryTable() {
        // Given
        TableMetadata table = createTableWithColumns("inventory", "库存表",
            List.of("id", "product_id", "quantity", "warehouse_id"));

        mockComplexityScore(0.5);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
    }

    @Test
    @DisplayName("合同表应分类为充血模型")
    void shouldClassifyAsRich_WhenIsContractTable() {
        // Given
        TableMetadata table = createTableWithColumns("contract", "合同表",
            List.of("id", "contract_no", "party_a", "party_b", "amount", "status"));

        mockComplexityScore(0.5);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
    }

    // ==================== P3: 复杂度评分判断 ====================

    @Test
    @DisplayName("高复杂度表应分类为充血模型")
    void shouldClassifyAsRich_WhenComplexityScoreHigh() {
        // Given
        TableMetadata table = createTableWithColumns("business_data", "业务数据表",
            List.of("id", "name", "status", "amount", "create_time", "update_time"));

        mockComplexityScore(0.7);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.RICH);
        assertThat(result.getReason()).contains("复杂度");
    }

    @Test
    @DisplayName("低复杂度表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenComplexityScoreLow() {
        // Given
        TableMetadata table = createTableWithColumns("simple_data", "简单数据表",
            List.of("id", "name"));

        mockComplexityScore(0.3);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
        assertThat(result.getReason()).contains("默认");
    }

    // ==================== P4: 默认分类 ====================

    @Test
    @DisplayName("不匹配任何规则的表应分类为贫血模型")
    void shouldClassifyAsAnemic_WhenNoRulesMatch() {
        // Given
        TableMetadata table = createTable("unknown_table", "未知表");

        mockComplexityScore(0.4);

        // When
        TableAnalysisResult result = strategy.classify(table, new HashMap<>());

        // Then
        assertThat(result.getModelType()).isEqualTo(DomainModelType.ANEMIC);
        assertThat(result.getReason()).contains("默认");
    }

    // ==================== 策略接口测试 ====================

    @Test
    @DisplayName("策略应该始终可用")
    void shouldAlwaysBeAvailable() {
        assertThat(strategy.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("策略名称应正确")
    void shouldHaveCorrectName() {
        assertThat(strategy.getName()).isEqualTo("Enhanced-Rule-Based");
    }

    // ==================== 辅助方法 ====================

    private TableMetadata createTable(String tableName, String comment) {
        return createTableWithColumns(tableName, comment, List.of("id"));
    }

    private TableMetadata createTableWithColumns(String tableName, String comment, List<String> columns) {
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);
        table.setTableComment(comment);

        List<ColumnMetadata> columnList = new ArrayList<>();
        for (String colName : columns) {
            ColumnMetadata column = new ColumnMetadata();
            column.setColumnName(colName);
            column.setDataTypeName("VARCHAR");
            columnList.add(column);
        }
        table.setColumns(columnList);
        table.setPrimaryKeys(List.of(columnList.get(0)));

        return table;
    }

    private void mockComplexityScore(double score) {
        when(complexityScorer.calculateComplexityScore(any(), any(), anyBoolean()))
            .thenReturn(score);
        when(complexityScorer.hasStatusField(any())).thenReturn(false);
        when(complexityScorer.hasAmountField(any())).thenReturn(false);
        when(complexityScorer.hasTimeRangeFields(any())).thenReturn(false);
        when(complexityScorer.hasCompositeKey(any())).thenReturn(false);
    }
}
