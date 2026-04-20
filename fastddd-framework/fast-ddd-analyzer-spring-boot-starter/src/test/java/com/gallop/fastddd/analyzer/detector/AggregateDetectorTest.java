package com.gallop.fastddd.analyzer.detector;

import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.constants.FieldPatterns;
import com.gallop.fastddd.common.model.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AggregateDetector 单元测试
 * <p>
 * 测试聚合边界检测、聚合根识别、角色分配等功能
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@DisplayName("AggregateDetector 测试")
class AggregateDetectorTest {

    private AggregateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AggregateDetector();
    }

    // ==================== 单表聚合测试 ====================

    @Test
    @DisplayName("独立表应形成单表聚合")
    void shouldCreateSingleTableAggregate_WhenTableIsStandalone() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("sys_user", "系统用户表"),
            createTable("sys_config", "系统配置表")
        );
        Map<String, List<String>> relationships = new HashMap<>();

        // When
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // Then
        assertThat(aggregates).hasSize(2);
        assertThat(aggregates.get("sys_user")).containsExactly("sys_user");
        assertThat(aggregates.get("sys_config")).containsExactly("sys_config");
    }

    @Test
    @DisplayName("无引用的表应形成单表聚合")
    void shouldCreateSingleTableAggregate_WhenTableHasNoReferences() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("customer", "客户表"),
            createTable("product", "产品表")
        );
        Map<String, List<String>> relationships = new HashMap<>();

        // When
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // Then
        assertThat(aggregates).hasSize(2);
    }

    // ==================== 一对多聚合测试 ====================

    @Test
    @DisplayName("聚合根和子实体应形成聚合")
    void shouldCreateAggregate_WhenRootHasChildEntities() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表"),
            createTable("order_log", "订单日志表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));
        relationships.put("order_log", List.of("order"));

        // When
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // Then
        assertThat(aggregates).hasSize(1);
        List<String> members = aggregates.values().iterator().next();
        assertThat(members).containsExactly("order", "order_item", "order_log");
    }

    @Test
    @DisplayName("聚合根应始终是成员列表的第一个元素")
    void shouldPutRootAsFirstMember() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));

        // When
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // Then
        List<String> members = aggregates.values().iterator().next();
        assertThat(members.get(0)).isEqualTo("order");
    }

    // ==================== 多层聚合测试 ====================

    @Test
    @DisplayName("多层子实体应正确添加到聚合")
    void shouldIncludeMultiLevelChildren() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表"),
            createTable("order_item_log", "订单明细日志表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));
        relationships.put("order_item_log", List.of("order_item"));

        // When
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // Then
        List<String> members = aggregates.values().iterator().next();
        assertThat(members).contains("order", "order_item", "order_item_log");
    }

    // ==================== 聚合角色分配测试 ====================

    @Test
    @DisplayName("聚合根角色应为 AGGREGATE_ROOT")
    void shouldAssignAggregateRootRole() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));

        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // When
        AggregateInfo orderInfo = detector.assignAggregateInfo("order", aggregates, relationships);
        AggregateInfo itemInfo = detector.assignAggregateInfo("order_item", aggregates, relationships);

        // Then
        assertThat(orderInfo.getRole()).isEqualTo(AggregateInfo.AggregateRole.AGGREGATE_ROOT);
        assertThat(orderInfo.getRootTableName()).isEqualTo("order");

        assertThat(itemInfo.getRole()).isEqualTo(AggregateInfo.AggregateRole.ENTITY);
        assertThat(itemInfo.getParentTable()).isEqualTo("order");
    }

    @Test
    @DisplayName("子实体角色应为 ENTITY")
    void shouldAssignEntityRole() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_detail", "订单详情表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_detail", List.of("order"));

        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // When
        AggregateInfo detailInfo = detector.assignAggregateInfo("order_detail", aggregates, relationships);

        // Then
        assertThat(detailInfo.getRole()).isEqualTo(AggregateInfo.AggregateRole.ENTITY);
        assertThat(detailInfo.getRelationshipType()).isEqualTo(AggregateInfo.RelationshipType.ONE_TO_MANY);
    }

    @Test
    @DisplayName("独立表角色应为 AGGREGATE_ROOT (单表聚合)")
    void shouldAssignAggregateRootRole_WhenTableIsStandalone() {
        // Given
        List<TableMetadata> tables = List.of(createTable("sys_config", "系统配置表"));
        Map<String, List<String>> relationships = new HashMap<>();
        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // When
        AggregateInfo configInfo = detector.assignAggregateInfo("sys_config", aggregates, relationships);

        // Then: 单表聚合中，唯一成员既是聚合根也是独立表
        assertThat(configInfo.getRole()).isEqualTo(AggregateInfo.AggregateRole.AGGREGATE_ROOT);
        assertThat(configInfo.getAggregateName()).isEqualTo("sys_config");
        assertThat(configInfo.getMemberTables()).hasSize(1);
    }

    // ==================== 值对象测试 ====================

    @Test
    @DisplayName("使用_info后缀的表应识别为值对象类型")
    void shouldDetectValueObject_WhenHasInfoSuffix() {
        // Given: _info 后缀是值对象后缀
        String tableName = "customer_info";

        // When: 检查是否为值对象后缀
        boolean isValueObject = FieldPatterns.VALUE_OBJECT_SUFFIXES.stream()
            .anyMatch(tableName::endsWith);

        // Then
        assertThat(isValueObject).isTrue();
    }

    // ==================== 聚合名称派生测试 ====================

    @Test
    @DisplayName("应移除常见前缀派生聚合名")
    void shouldRemoveCommonPrefixes_WhenDerivingAggregateName() {
        // When & Then
        assertThat(detector.deriveAggregateName("t_order")).isEqualTo("order");
        assertThat(detector.deriveAggregateName("sys_user")).isEqualTo("user");
        assertThat(detector.deriveAggregateName("tbl_product")).isEqualTo("product");
        assertThat(detector.deriveAggregateName("tb_customer")).isEqualTo("customer");
    }

    @Test
    @DisplayName("应移除子实体后缀派生聚合名")
    void shouldRemoveEntitySuffixes_WhenDerivingAggregateName() {
        // When & Then
        assertThat(detector.deriveAggregateName("order_item")).isEqualTo("order");
        assertThat(detector.deriveAggregateName("order_detail")).isEqualTo("order");
        assertThat(detector.deriveAggregateName("payment_log")).isEqualTo("payment");
    }

    @Test
    @DisplayName("应移除值对象后缀派生聚合名")
    void shouldRemoveValueObjectSuffixes_WhenDerivingAggregateName() {
        // When & Then
        assertThat(detector.deriveAggregateName("customer_profile")).isEqualTo("customer");
        assertThat(detector.deriveAggregateName("user_info")).isEqualTo("user");
    }

    @Test
    @DisplayName("短名称应返回原表名")
    void shouldReturnOriginalName_WhenDerivedNameIsTooShort() {
        // When & Then
        assertThat(detector.deriveAggregateName("ab")).isEqualTo("ab");
        assertThat(detector.deriveAggregateName("t_a")).isEqualTo("t_a");
    }

    // ==================== 聚合检测方法测试 ====================

    @Test
    @DisplayName("应正确识别子实体表")
    void shouldDetectChildEntity() {
        assertThat(detector.isChildEntity("order_item")).isTrue();
        assertThat(detector.isChildEntity("order_detail")).isTrue();
        assertThat(detector.isChildEntity("order_line")).isTrue();
        assertThat(detector.isChildEntity("order")).isFalse();
    }

    @Test
    @DisplayName("应正确判断是否为聚合根")
    void shouldDetectAggregateRoot() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));

        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // When & Then
        assertThat(detector.isAggregateRoot("order", aggregates)).isTrue();
        assertThat(detector.isAggregateRoot("order_item", aggregates)).isFalse();
    }

    @Test
    @DisplayName("应获取表所属的聚合名")
    void shouldGetAggregateNameForTable() {
        // Given
        List<TableMetadata> tables = List.of(
            createTable("order", "订单表"),
            createTable("order_item", "订单明细表")
        );

        Map<String, List<String>> relationships = new HashMap<>();
        relationships.put("order_item", List.of("order"));

        Map<String, List<String>> aggregates = detector.detectAggregates(tables, relationships);

        // When & Then
        assertThat(detector.getAggregateName("order", aggregates)).isEqualTo("order");
        assertThat(detector.getAggregateName("order_item", aggregates)).isEqualTo("order");
    }

    // ==================== 辅助方法 ====================

    private TableMetadata createTable(String tableName, String comment) {
        TableMetadata table = new TableMetadata();
        table.setTableName(tableName);
        table.setTableComment(comment);
        return table;
    }
}
