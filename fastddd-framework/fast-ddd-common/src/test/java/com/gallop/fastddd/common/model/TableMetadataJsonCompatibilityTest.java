package com.gallop.fastddd.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TableMetadata JSON 兼容性测试（1.0.2 语义扩列三键的向后兼容锁）
 *
 * @author gallop
 * @since 2026-10-12
 * @version 1.0.2
 */
class TableMetadataJsonCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyJsonWithoutSemanticKeysDeserializesWithNulls() throws Exception {
        // 1.0.1 及更早的序列化产物不含三键——反序列化后三键须为 null（向后兼容）
        String legacyJson = "{\"tableName\":\"t_order\",\"tableComment\":\"订单表\","
                + "\"entityName\":\"Order\",\"columns\":[],\"primaryKeys\":[]}";
        TableMetadata metadata = objectMapper.readValue(legacyJson, TableMetadata.class);
        assertNull(metadata.getAggregateName());
        assertNull(metadata.getDddRole());
        assertNull(metadata.getRelationsSummary());
        assertEquals("t_order", metadata.getTableName());
    }

    @Test
    void semanticKeysRoundTripThroughJson() throws Exception {
        // 三键序列化→反序列化往返恒等
        TableMetadata metadata = new TableMetadata();
        metadata.setTableName("t_order");
        metadata.setAggregateName("订单聚合");
        metadata.setDddRole("AGGREGATE_ROOT");
        metadata.setRelationsSummary("ONE_TO_MANY:t_order_item");
        String json = objectMapper.writeValueAsString(metadata);
        assertTrue(json.contains("\"aggregateName\""));
        TableMetadata restored = objectMapper.readValue(json, TableMetadata.class);
        assertEquals("订单聚合", restored.getAggregateName());
        assertEquals("AGGREGATE_ROOT", restored.getDddRole());
        assertEquals("ONE_TO_MANY:t_order_item", restored.getRelationsSummary());
    }

    @Test
    void semanticColumnGettersExistForTemplateReflection() throws Exception {
        // 模板引擎经 bean getter 反射访问三键——反射断言三 getter 在场
        for (String getter : new String[]{"getAggregateName", "getDddRole", "getRelationsSummary"}) {
            Method method = TableMetadata.class.getMethod(getter);
            assertEquals(String.class, method.getReturnType());
        }
    }
}
