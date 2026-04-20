package com.gallop.fastddd.analyzer.classifier.model;

import lombok.Builder;
import lombok.Data;

/**
 * 分析指标
 * <p>
 * 封装表分析过程中的各项指标数据，用于辅助分类决策和结果解释。
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@Builder
public class AnalysisMetrics {

    /**
     * 列数量
     * <p>表的列总数，包括主键、外键、普通字段等</p>
     */
    private Integer columnCount;

    /**
     * 关系数量
     * <p>外键关系总数，包括指向其他表和被其他表引用</p>
     */
    private Integer relationshipCount;

    /**
     * 复杂度评分
     * <p>综合评分，范围 0.0-1.0，分数越高表示表越复杂</p>
     * <p>评分维度包括：</p>
     * <ul>
     *   <li>列数（0-0.2）</li>
     *   <li>状态字段（0-0.15）</li>
     *   <li>金额字段（0-0.15）</li>
     *   <li>外键关系（0-0.25）</li>
     *   <li>复合主键（0-0.1）</li>
     *   <li>时间范围字段（0-0.05）</li>
     *   <li>被引用（0-0.1）</li>
     * </ul>
     */
    private Double complexityScore;

    /**
     * 是否包含状态机
     * <p>如果表包含 status、state 等状态字段，且有明显状态转换模式</p>
     */
    private Boolean hasStateMachine;

    /**
     * 是否包含业务规则
     * <p>如果表包含金额、数量等需要业务规则校验的字段</p>
     */
    private Boolean hasBusinessRules;

    /**
     * 是否为聚合根候选
     * <p>如果表被其他表引用，且不是明显的子实体</p>
     */
    private Boolean isAggregateRoot;

    /**
     * 外键数量
     * <p>当前表引用其他表的数量</p>
     */
    private Integer foreignKeyCount;

    /**
     * 被引用数量
     * <p>其他表引用当前表的数量</p>
     */
    private Integer referencedByCount;

    /**
     * 是否包含状态字段
     * <p>检测 status、state、_status 等字段</p>
     */
    private Boolean hasStatusField;

    /**
     * 是否包含金额字段
     * <p>检测 amount、price、_amount 等字段，以及 DECIMAL 类型</p>
     */
    private Boolean hasAmountField;

    /**
     * 是否包含时间范围字段
     * <p>检测 start_time、end_time、begin_time、expire_time 等字段</p>
     */
    private Boolean hasTimeRangeFields;

    /**
     * 是否为复合主键
     * <p>主键字段数 > 1</p>
     */
    private Boolean hasCompositeKey;

    /**
     * 判断是否为高复杂度表
     *
     * @return 如果复杂度评分 > 0.6 返回 true
     */
    public boolean isHighComplexity() {
        return complexityScore != null && complexityScore > 0.6;
    }

    /**
     * 判断是否为低复杂度表
     *
     * @return 如果复杂度评分 < 0.3 返回 true
     */
    public boolean isLowComplexity() {
        return complexityScore != null && complexityScore < 0.3;
    }

    /**
     * 获取复杂度级别描述
     *
     * @return 复杂度级别：低、中、高
     */
    public String getComplexityLevel() {
        if (complexityScore == null) {
            return "未知";
        }
        if (complexityScore < 0.3) {
            return "低";
        } else if (complexityScore < 0.6) {
            return "中";
        } else {
            return "高";
        }
    }
}
