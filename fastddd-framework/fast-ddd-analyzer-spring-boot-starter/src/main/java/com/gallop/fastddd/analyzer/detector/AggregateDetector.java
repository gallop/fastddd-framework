package com.gallop.fastddd.analyzer.detector;

import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.constants.FieldPatterns;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 聚合检测器
 * <p>
 * 基于外键关系和命名模式识别聚合边界，这是 DDD 的核心能力之一。
 * </p>
 *
 * <h2>聚合检测规则</h2>
 * <p><b>聚合根候选条件：</b></p>
 * <ul>
 *   <li>不以 <code>_item</code>, <code>_detail</code>, <code>_line</code> 等后缀结尾</li>
 *   <li>被其他表引用（通常是聚合根的标志）</li>
 *   <li>表示核心业务概念（如 Order, Product, Payment）</li>
 * </ul>
 *
 * <p><b>子实体特征：</b></p>
 * <ul>
 *   <li>表名以 <code>_item</code>, <code>_detail</code>, <code>_line</code> 等结尾</li>
 *   <li>引用聚合根表</li>
 *   <li>通常是一对多关系的"多"方</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
public class AggregateDetector {

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

        Map<String, List<String>> aggregates = new LinkedHashMap<>();
        Set<String> processed = new HashSet<>();

        for (TableMetadata table : tables) {
            if (processed.contains(table.getTableName())) {
                continue;
            }

            String tableName = table.getTableName();

            // 检查是否为明显的独立表（系统表、字典表等）
            if (isStandaloneTable(tableName)) {
                aggregates.put(tableName, List.of(tableName));
                processed.add(tableName);
                log.debug("独立表: {}", tableName);
                continue;
            }

            // 检查是否为潜在的聚合根
            if (isPotentialAggregateRoot(tableName, relationships.get(tableName))) {
                String aggregateName = deriveAggregateName(tableName);
                List<String> members = findAggregateMembers(tableName, relationships, processed);
                aggregates.put(aggregateName, members);
                processed.addAll(members);
                log.debug("检测到聚合: {} -> {}", aggregateName, members);
            } else if (!hasParentTable(tableName, relationships)) {
                // 无父表的独立表
                aggregates.put(tableName, List.of(tableName));
                processed.add(tableName);
                log.debug("独立表（无父表）: {}", tableName);
            }
        }

        log.info("聚合检测完成: 检测到 {} 个聚合", aggregates.size());
        return aggregates;
    }

    /**
     * 判断是否为明显的独立表
     *
     * @param tableName 表名
     * @return 如果是独立表返回 true
     */
    private boolean isStandaloneTable(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.ANEMIC_PREFIXES.stream().anyMatch(tableLower::startsWith);
    }

    /**
     * 判断是否为潜在的聚合根
     *
     * @param tableName 表名
     * @param referencedTables 该表引用的其他表
     * @return 如果是聚合根候选返回 true
     */
    private boolean isPotentialAggregateRoot(String tableName, List<String> referencedTables) {
        // 规则 1: 不以子实体后缀结尾
        if (isChildEntity(tableName)) {
            return false;
        }

        // 规则 2: 系统表不是聚合根
        if (isStandaloneTable(tableName)) {
            return false;
        }

        // 规则 3: 有引用关系但不以子实体结尾
        // 默认认为是聚合根候选
        return true;
    }

    /**
     * 查找聚合成员（迭代实现，避免递归栈溢出）
     *
     * @param rootTable 聚合根表
     * @param relationships 关系映射
     * @param processed 已处理的表
     * @return 聚合成员列表（包括聚合根）
     */
    private List<String> findAggregateMembers(String rootTable,
                                               Map<String, List<String>> relationships,
                                               Set<String> processed) {
        List<String> members = new ArrayList<>();
        members.add(rootTable);

        // 使用迭代方式查找子实体，避免递归导致的栈溢出
        Queue<String> queue = new LinkedList<>();
        Set<String> visiting = new HashSet<>(); // 用于检测循环引用

        // 查找直接引用聚合根的表（子实体）
        for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
            String table = entry.getKey();
            List<String> refs = entry.getValue();

            // 如果表引用了聚合根，且是子实体表
            if (refs.contains(rootTable) && isChildEntity(table) && !processed.contains(table)) {
                queue.add(table);
                visiting.add(table);
            }
        }

        // 迭代处理队列中的表
        while (!queue.isEmpty()) {
            String current = queue.poll();

            // 检查循环引用
            if (members.contains(current)) {
                log.warn("检测到循环引用，跳过表: {}", current);
                continue;
            }

            members.add(current);
            processed.add(current);
            log.trace("  -> 添加子实体: {}", current);

            // 查找引用当前表的子实体
            for (Map.Entry<String, List<String>> entry : relationships.entrySet()) {
                String table = entry.getKey();
                List<String> refs = entry.getValue();

                // 如果表引用了当前表，且是子实体，且未被处理
                if (refs.contains(current) && isChildEntity(table)
                        && !table.equals(rootTable)
                        && !processed.contains(table)
                        && !visiting.contains(table)) {
                    queue.add(table);
                    visiting.add(table);
                }
            }
        }

        return members;
    }

    /**
     * 判断是否为子实体表
     *
     * @param tableName 表名
     * @return 如果是子实体返回 true
     */
    public boolean isChildEntity(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.ENTITY_SUFFIXES.stream().anyMatch(tableLower::endsWith);
    }

    /**
     * 检查表是否有父表
     *
     * @param tableName 表名
     * @param relationships 关系映射
     * @return 如果有父表返回 true
     */
    private boolean hasParentTable(String tableName, Map<String, List<String>> relationships) {
        List<String> refs = relationships.get(tableName);
        return refs != null && !refs.isEmpty() && isChildEntity(tableName);
    }

    /**
     * 从表名派生聚合名称
     * <p>修复：统一处理前缀和后缀，避免正则替换顺序敏感问题</p>
     *
     * @param tableName 表名
     * @return 聚合名称
     */
    public String deriveAggregateName(String tableName) {
        String name = tableName;

        // 统一处理前缀（使用正则或运算符）
        name = name.replaceAll("(?i)^(t_|sys_|tbl_|tb_)", "");

        // 统一处理后缀（合并所有需要移除的后缀）
        List<String> allSuffixes = new java.util.ArrayList<>();
        allSuffixes.addAll(FieldPatterns.ENTITY_SUFFIXES);
        allSuffixes.addAll(FieldPatterns.VALUE_OBJECT_SUFFIXES);

        String suffixPattern = String.join("|", allSuffixes);
        name = name.replaceAll("(?i)(" + suffixPattern + ")$", "");

        // 如果名称太短，使用原表名
        if (name.length() < 3) {
            return tableName;
        }

        return name;
    }

    /**
     * 为每个表分配聚合信息
     *
     * @param tableName 表名
     * @param aggregates 聚合分组
     * @param relationships 关系映射
     * @return 聚合信息
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
                AggregateInfo.RelationshipType relationshipType = determineRelationshipType(
                    tableName, rootTable, role, relationships);

                return AggregateInfo.builder()
                    .aggregateName(aggregateName)
                    .rootTableName(rootTable)
                    .role(role)
                    .memberTables(members)
                    .parentTable(role == AggregateInfo.AggregateRole.AGGREGATE_ROOT ? null : rootTable)
                    .relationshipType(relationshipType)
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

    /**
     * 判定表在聚合中的角色
     *
     * @param tableName 表名
     * @param rootTable 聚合根表名
     * @param members 聚合成员列表
     * @return 聚合角色
     */
    private AggregateInfo.AggregateRole determineRole(String tableName,
                                                        String rootTable,
                                                        List<String> members) {
        if (tableName.equals(rootTable)) {
            return AggregateInfo.AggregateRole.AGGREGATE_ROOT;
        }

        if (isChildEntity(tableName)) {
            return AggregateInfo.AggregateRole.ENTITY;
        }

        if (isValueObject(tableName)) {
            return AggregateInfo.AggregateRole.VALUE_OBJECT;
        }

        // 默认作为实体处理
        return AggregateInfo.AggregateRole.ENTITY;
    }

    /**
     * 判断是否为值对象表
     *
     * @param tableName 表名
     * @return 如果是值对象返回 true
     */
    private boolean isValueObject(String tableName) {
        String tableLower = tableName.toLowerCase();
        return FieldPatterns.VALUE_OBJECT_SUFFIXES.stream().anyMatch(tableLower::endsWith);
    }

    /**
     * 判定关系类型
     *
     * @param tableName 表名
     * @param rootTable 聚合根表名
     * @param role 聚合角色
     * @param relationships 关系映射
     * @return 关系类型
     */
    private AggregateInfo.RelationshipType determineRelationshipType(
            String tableName,
            String rootTable,
            AggregateInfo.AggregateRole role,
            Map<String, List<String>> relationships) {

        return switch (role) {
            case AGGREGATE_ROOT -> AggregateInfo.RelationshipType.NONE;
            case ENTITY -> AggregateInfo.RelationshipType.ONE_TO_MANY;
            case VALUE_OBJECT -> AggregateInfo.RelationshipType.ONE_TO_ONE;
            case STANDALONE -> AggregateInfo.RelationshipType.NONE;
        };
    }

    /**
     * 判断表是否为聚合根
     *
     * @param tableName 表名
     * @param aggregates 聚合分组
     * @return 如果是聚合根返回 true
     */
    public boolean isAggregateRoot(String tableName, Map<String, List<String>> aggregates) {
        for (List<String> members : aggregates.values()) {
            if (!members.isEmpty() && members.get(0).equals(tableName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取表所属的聚合
     * <p>修复：单表聚合也应该返回聚合名</p>
     *
     * @param tableName 表名
     * @param aggregates 聚合分组
     * @return 聚合名，如果不属于任何聚合返回 null
     */
    public String getAggregateName(String tableName, Map<String, List<String>> aggregates) {
        for (Map.Entry<String, List<String>> entry : aggregates.entrySet()) {
            List<String> members = entry.getValue();
            // 修复：单表聚合也应该返回聚合名
            if (members.contains(tableName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
