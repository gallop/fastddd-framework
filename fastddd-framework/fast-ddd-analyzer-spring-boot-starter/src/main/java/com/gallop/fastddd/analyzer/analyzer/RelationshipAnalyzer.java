package com.gallop.fastddd.analyzer.analyzer;

import com.gallop.fastddd.analyzer.constants.FieldPatterns;
import com.gallop.fastddd.codegen.model.ColumnMetadata;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 关系分析器
 * <p>
 * 分析表之间的外键关系和引用关系，用于聚合检测和复杂度评分。
 * </p>
 *
 * <h2>检测规则</h2>
 * <p>基于列名模式检测外键关系：</p>
 * <ul>
 *   <li><b>xxx_id</b> → <b>xxx</b> 表（最常见）</li>
 *   <li><b>xxx_no</b> → <b>xxx</b> 表（订单号模式）</li>
 *   <li>支持单数/复数形式（user_id → users）</li>
 *   <li>支持前缀模式（t_xxx_id → t_xxx）</li>
 * </ul>
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
        Map<String, TableMetadata> tableMap = buildTableMap(tables);

        // 统计每个表被引用的次数
        Map<String, Integer> referenceCountMap = new HashMap<>();

        // 分析每个表的外键关系
        for (TableMetadata table : tables) {
            List<String> referencedTables = detectReferencedTables(table, tableMap);

            if (!referencedTables.isEmpty()) {
                relationships.put(table.getTableName(), referencedTables);

                // 更新被引用次数统计
                for (String refTable : referencedTables) {
                    referenceCountMap.merge(refTable, 1, Integer::sum);
                }

                log.debug("表 {} 引用了: {}", table.getTableName(), referencedTables);
            }

            // 存储外键数量到 attributes（如果支持）
            storeForeignKeyCount(table, referencedTables.size());
        }

        // 统计并存储被引用次数
        for (TableMetadata table : tables) {
            int refCount = referenceCountMap.getOrDefault(table.getTableName(), 0);
            storeReferenceCount(table, refCount);

            if (refCount > 0) {
                log.debug("表 {} 被引用 {} 次", table.getTableName(), refCount);
            }
        }

        log.info("关系分析完成：检测到 {} 个表有关系", relationships.size());
        return relationships;
    }

    /**
     * 检测表引用的其他表
     *
     * @param table 当前表
     * @param tableMap 所有表的映射
     * @return 被引用的表名列表
     */
    private List<String> detectReferencedTables(TableMetadata table, Map<String, TableMetadata> tableMap) {
        Set<String> referencedTables = new LinkedHashSet<>();

        for (ColumnMetadata column : table.getColumns()) {
            String referencedTable = detectReferencedTable(column, tableMap);
            if (referencedTable != null) {
                referencedTables.add(referencedTable);
            }
        }

        return new ArrayList<>(referencedTables);
    }

    /**
     * 检测列引用的表（基于命名模式）
     *
     * @param column 列元数据
     * @param tableMap 所有表的映射
     * @return 被引用的表名，如果没有则返回 null
     */
    public String detectReferencedTable(ColumnMetadata column, Map<String, TableMetadata> tableMap) {
        String colName = column.getColumnName().toLowerCase();

        // 模式 1: xxx_id -> xxx 表
        if (colName.endsWith(FieldPatterns.FOREIGN_KEY_SUFFIX) && !colName.equals("id")) {
            String potentialTable = colName.substring(0, colName.length() - FieldPatterns.FOREIGN_KEY_SUFFIX.length());

            // 尝试直接匹配
            if (tableMap.containsKey(potentialTable)) {
                return potentialTable;
            }

            // 尝试复数形式（user_id -> users）
            String pluralForm = potentialTable + "s";
            if (tableMap.containsKey(pluralForm)) {
                return pluralForm;
            }

            // 尝试带前缀的形式（t_user_id -> t_user）
            if (tableMap.containsKey("t_" + potentialTable)) {
                return "t_" + potentialTable;
            }

            // 尝试带前缀的复数形式
            if (tableMap.containsKey("t_" + pluralForm)) {
                return "t_" + pluralForm;
            }
        }

        // 模式 2: xxx_no 可能引用订单表
        if (isOrderNumberColumn(colName)) {
            for (String tableName : tableMap.keySet()) {
                String tableLower = tableName.toLowerCase();
                if (tableLower.contains("order") || tableLower.contains("订单")) {
                    return tableName;
                }
            }
        }

        // 模式 3: xxx_code 可能引用特定表
        if (colName.endsWith("_code") || colName.endsWith("_code")) {
            String potentialTable = colName.substring(0, colName.lastIndexOf('_'));
            if (tableMap.containsKey(potentialTable)) {
                return potentialTable;
            }
        }

        return null;
    }

    /**
     * 检查列是否为订单号相关列
     *
     * @param colName 列名
     * @return 如果是订单号列返回 true
     */
    private boolean isOrderNumberColumn(String colName) {
        return FieldPatterns.ORDER_NUMBER_PATTERNS.stream().anyMatch(colName::contains);
    }

    /**
     * 检查表是否引用了另一个表
     *
     * @param table 当前表
     * @param targetTableName 目标表名
     * @return 如果引用返回 true
     */
    public boolean referencesTable(TableMetadata table, String targetTableName) {
        String targetLower = targetTableName.toLowerCase();

        for (ColumnMetadata column : table.getColumns()) {
            String colName = column.getColumnName().toLowerCase();

            // 检查外键命名模式
            if (colName.equals(targetLower + FieldPatterns.FOREIGN_KEY_SUFFIX)) {
                return true;
            }

            // 检查组合模式（如 user_id 引用 user）
            if (colName.endsWith(FieldPatterns.FOREIGN_KEY_SUFFIX)) {
                String potentialRef = colName.substring(0, colName.length() - FieldPatterns.FOREIGN_KEY_SUFFIX.length());
                if (potentialRef.equals(targetLower)) {
                    return true;
                }
            }

            // 检查订单号模式
            if (isOrderNumberColumn(colName) && isOrderTable(targetTableName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查表是否为订单相关表
     *
     * @param tableName 表名
     * @return 如果是订单表返回 true
     */
    private boolean isOrderTable(String tableName) {
        String tableLower = tableName.toLowerCase();
        return tableLower.contains("order") || tableLower.contains("订单");
    }

    /**
     * 构建表名到元数据的映射
     * <p>
     * 修复：使用 LinkedHashMap 保持顺序，并规范化表名为小写以避免歧义。
     * 如果需要大小写敏感匹配，使用原始表名进行二次查找。
     * </p>
     *
     * @param tables 所有表
     * @return 规范化的表名（小写）-> 表元数据
     */
    private Map<String, TableMetadata> buildTableMap(List<TableMetadata> tables) {
        Map<String, TableMetadata> map = new LinkedHashMap<>();
        Set<String> seenNames = new HashSet<>();

        for (TableMetadata table : tables) {
            String normalizedName = table.getTableName().toLowerCase();
            String originalName = table.getTableName();

            // 如果大小写不同的表名冲突，保留原始表名
            if (seenNames.contains(normalizedName) && !normalizedName.equals(originalName)) {
                // 冲突时，使用原始大小写形式作为 key
                map.put(originalName, table);
                log.warn("表名大小写冲突: {} 和 {}, 使用原始大小写", normalizedName, originalName);
            } else {
                map.put(normalizedName, table);
                seenNames.add(normalizedName);
            }
        }
        return map;
    }

    /**
     * 记录外键数量（用于调试）
     *
     * @param table 表元数据
     * @param count 外键数量
     */
    private void storeForeignKeyCount(TableMetadata table, int count) {
        log.trace("表 {} 的外键数量: {}", table.getTableName(), count);
    }

    /**
     * 记录被引用次数（用于调试）
     *
     * @param table 表元数据
     * @param count 被引用次数
     */
    private void storeReferenceCount(TableMetadata table, int count) {
        log.trace("表 {} 被引用次数: {}", table.getTableName(), count);
    }

    /**
     * 获取外键数量（从关系分析结果中获取）
     *
     * @param tableName 表名
     * @param relationships 关系映射
     * @return 外键数量
     */
    public int getForeignKeyCount(String tableName, Map<String, List<String>> relationships) {
        List<String> refs = relationships.get(tableName);
        return refs != null ? refs.size() : 0;
    }

    /**
     * 获取被引用次数
     *
     * @param tableName 表名
     * @param relationships 关系映射
     * @return 被引用次数
     */
    public int getReferenceCount(String tableName, Map<String, List<String>> relationships) {
        int count = 0;
        for (List<String> refs : relationships.values()) {
            if (refs.contains(tableName)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断表是否被其他表引用（聚合根候选）
     *
     * @param tableName 表名
     * @param relationships 关系映射
     * @return 如果被引用返回 true
     */
    public boolean isReferencedByOthers(String tableName, Map<String, List<String>> relationships) {
        return getReferenceCount(tableName, relationships) > 0;
    }

    /**
     * 图数据模型
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GraphData {
        private List<String> nodes;
        private List<GraphEdge> edges;
    }

    /**
     * 图边数据
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class GraphEdge {
        private String from;
        private String to;
        private String label;
    }

    /**
     * 构建关系图数据（用于前端可视化）
     *
     * @param tables 所有表
     * @param relationships 关系映射
     * @return 图数据
     */
    public GraphData buildGraphData(List<TableMetadata> tables, Map<String, List<String>> relationships) {
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
                    edges.add(new GraphEdge(from, to, "引用"));
                }
            }
        }

        return new GraphData(new ArrayList<>(nodes), edges);
    }
}
