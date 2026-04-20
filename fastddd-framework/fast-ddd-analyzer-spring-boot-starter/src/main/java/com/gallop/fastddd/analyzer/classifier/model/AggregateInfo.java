package com.gallop.fastddd.analyzer.classifier.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * 聚合信息
 * <p>
 * 描述一个表在聚合中的角色和关系信息。
 * 聚合是 DDD 中的核心概念，表示一组相关联的领域对象的集合。
 * </p>
 *
 * <h2>聚合角色</h2>
 * <ul>
 *   <li><b>AGGREGATE_ROOT</b> - 聚合根：聚合的入口，通过 ID 引用，维护一致性边界</li>
 *   <li><b>ENTITY</b> - 实体：聚合内的业务对象，有唯一标识</li>
 *   <li><b>VALUE_OBJECT</b> - 值对象：通过值嵌入，无独立标识</li>
 *   <li><b>STANDALONE</b> - 独立表：不属于任何聚合</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@Builder
public class AggregateInfo {

    /**
     * 聚合名称
     * <p>从聚合根表名派生，例如：order、product、payment</p>
     */
    private String aggregateName;

    /**
     * 聚合根表名
     * <p>聚合内被其他表引用的主表</p>
     */
    private String rootTableName;

    /**
     * 当前表在聚合中的角色
     */
    private AggregateRole role;

    /**
     * 聚合内的所有表（包括聚合根）
     * <p>按顺序排列，第一个元素总是聚合根</p>
     */
    private List<String> memberTables;

    /**
     * 聚合中的父表（如果有）
     * <p>对于聚合根为 null，对于子实体为聚合根表名</p>
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
         * 聚合根
         * <p>独立的业务实体，是聚合的入口，维护一致性边界。</p>
         * <p>特点：</p>
         * <ul>
         *   <li>有全局唯一标识</li>
         *   <li>被其他聚合引用</li>
         *   <li>负责维护聚合内的业务规则</li>
         *   <li>发布领域事件</li>
         * </ul>
         */
        AGGREGATE_ROOT,

        /**
         * 实体
         * <p>聚合内的业务对象，通过 ID 引用。</p>
         * <p>特点：</p>
         * <ul>
         *   <li>有唯一标识（但只在聚合内唯一）</li>
         *   <li>属于某个聚合根</li>
         *   <li>不能被其他聚合直接引用</li>
         * </ul>
         * <p>典型示例：OrderItem（订单项）属于 Order（订单）聚合</p>
         */
        ENTITY,

        /**
         * 值对象
         * <p>通过值嵌入，无独立标识。</p>
         * <p>特点：</p>
         * <ul>
         *   <li>无唯一标识</li>
         *   <li>不可变性</li>
         *   <li>作为一个整体被引用</li>
         * </ul>
         * <p>典型示例：Address（地址）、Money（金额）</p>
         */
        VALUE_OBJECT,

        /**
         * 独立表
         * <p>不属于任何聚合，通常是简单的 CRUD 表。</p>
         * <p>典型示例：配置表、字典表、日志表</p>
         */
        STANDALONE
    }

    /**
     * 关系类型枚举
     */
    public enum RelationshipType {
        /**
         * 一对多关系 (One-to-Many)
         * <p>最常见的聚合内关系，如订单-订单项</p>
         */
        ONE_TO_MANY,

        /**
         * 一对一关系 (One-to-One)
         * <p>值对象模式，如用户-用户详情</p>
         */
        ONE_TO_ONE,

        /**
         * 多对一关系 (Many-to-One)
         * <p>引用其他聚合的聚合根</p>
         */
        MANY_TO_ONE,

        /**
         * 无关系
         * <p>独立表或聚合根</p>
         */
        NONE
    }

    /**
     * 判断是否为聚合根
     *
     * @return 如果是聚合根返回 true
     */
    public boolean isAggregateRoot() {
        return role == AggregateRole.AGGREGATE_ROOT;
    }

    /**
     * 判断是否为子实体
     *
     * @return 如果是子实体返回 true
     */
    public boolean isEntity() {
        return role == AggregateRole.ENTITY;
    }

    /**
     * 判断是否为独立表
     *
     * @return 如果是独立表返回 true
     */
    public boolean isStandalone() {
        return role == AggregateRole.STANDALONE;
    }

    /**
     * 获取聚合成员数量
     *
     * @return 聚合成员数量，独立表返回 1
     */
    public int getMemberCount() {
        return memberTables != null ? memberTables.size() : 1;
    }

    /**
     * 判断指定表是否属于此聚合
     *
     * @param tableName 表名
     * @return 如果属于此聚合返回 true
     */
    public boolean containsMember(String tableName) {
        return memberTables != null && memberTables.contains(tableName);
    }

    /**
     * 获取当前表在聚合中的索引位置
     *
     * @return 索引位置（从0开始），如果不属于聚合返回 -1
     */
    public int getMemberIndex(String tableName) {
        if (memberTables == null) {
            return -1;
        }
        return memberTables.indexOf(tableName);
    }
}
