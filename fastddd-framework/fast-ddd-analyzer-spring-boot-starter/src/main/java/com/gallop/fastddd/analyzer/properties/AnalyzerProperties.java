package com.gallop.fastddd.analyzer.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智能分析器配置属性
 * <p>
 * 配置表分析器的行为参数，包括分类策略、复杂度权重等。
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@ConfigurationProperties(prefix = "fastddd.analyzer")
public class AnalyzerProperties {

    /**
     * 是否启用智能分析功能
     */
    private Boolean enabled = true;

    /**
     * 默认分类策略
     * <p>
     * 可选值：
     * <ul>
     *   <li>rule - 基于规则的分类（默认）</li>
     *   <li>llm - 基于 LLM 的分类（需要配置 API Key）</li>
     *   <li>hybrid - 混合策略</li>
     * </ul>
     */
    private Strategy defaultStrategy = Strategy.RULE;

    /**
     * HYBRID 模式下触发 LLM 增强的置信度阈值
     * 规则引擎置信度低于此值时，使用 LLM 重新分类
     */
    private Double hybridConfidenceThreshold = 0.5;

    /**
     * 复杂度评分权重配置
     */
    private ComplexityWeights complexityWeights = new ComplexityWeights();

    /**
     * 分类策略枚举
     */
    public enum Strategy {
        /**
         * 基于规则的分类策略
         */
        RULE,
        /**
         * 基于 LLM 的分类策略
         */
        LLM,
        /**
         * 混合策略
         */
        HYBRID
    }

    /**
     * 复杂度评分权重配置
     */
    @Data
    public static class ComplexityWeights {
        /**
         * 列数权重 (0.0-1.0)，默认 0.2
         */
        private Double columnCount = 0.2;

        /**
         * 状态字段权重 (0.0-1.0)，默认 0.15
         */
        private Double statusField = 0.15;

        /**
         * 金额字段权重 (0.0-1.0)，默认 0.15
         */
        private Double amountField = 0.15;

        /**
         * 外键关系权重 (0.0-1.0)，默认 0.25
         */
        private Double foreignKey = 0.25;

        /**
         * 复合主键权重 (0.0-1.0)，默认 0.1
         */
        private Double compositeKey = 0.1;

        /**
         * 时间范围字段权重 (0.0-1.0)，默认 0.05
         */
        private Double timeRange = 0.05;

        /**
         * 被引用权重 (0.0-1.0)，默认 0.1
         */
        private Double referenced = 0.1;

        /**
         * 校验权重总和是否在合理范围内
         * <p>允许 0.01 的误差范围</p>
         *
         * @return 如果权重总和约等于 1.0 返回 true
         */
        public boolean isValid() {
            double sum = columnCount + statusField + amountField
                       + foreignKey + compositeKey + timeRange + referenced;
            return Math.abs(sum - 1.0) < 0.01;
        }

        /**
         * 获取权重总和
         *
         * @return 权重总和
         */
        public double getSum() {
            return columnCount + statusField + amountField
                   + foreignKey + compositeKey + timeRange + referenced;
        }
    }
}
