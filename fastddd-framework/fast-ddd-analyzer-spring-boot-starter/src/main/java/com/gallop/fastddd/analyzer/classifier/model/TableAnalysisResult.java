package com.gallop.fastddd.analyzer.classifier.model;

import com.gallop.fastddd.common.enums.DomainModelType;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

/**
 * 表分析结果
 * <p>
 * 封装单个表的完整分析结果，包括模型类型推荐、聚合信息、
 * 置信度评分和详细的指标数据。
 * </p>
 *
 * @author gallop
 * @since 1.1.0
 */
@Data
@Builder
public class TableAnalysisResult {

    /**
     * 表名
     */
    private String tableName;

    /**
     * 推荐的模型类型
     * <p>ANEMIC（贫血模型）或 RICH（充血模型）</p>
     */
    private DomainModelType modelType;

    /**
     * 分类置信度
     * <p>范围 0.0-1.0，表示分类结果的可信程度</p>
     * <ul>
     *   <li>0.8-1.0: 高置信度 - 分类规则明确匹配</li>
     *   <li>0.6-0.8: 中置信度 - 多个指标支持分类</li>
     *   <li>0.4-0.6: 低置信度 - 分类依据较弱</li>
     *   <li>0.0-0.4: 很低置信度 - 建议人工确认</li>
     * </ul>
     */
    private Double confidence;

    /**
     * 分类原因
     * <p>自然语言描述的分类依据，帮助用户理解分类结果</p>
     */
    private String reason;

    /**
     * 使用的分析策略
     * <p>例如：Enhanced-Rule-Based、LLM-Enhanced、Hybrid</p>
     */
    private String strategy;

    /**
     * 聚合信息
     * <p>描述表在聚合中的角色和关系</p>
     */
    private AggregateInfo aggregateInfo;

    /**
     * 关键指标
     * <p>详细的评分和分析数据</p>
     */
    private AnalysisMetrics metrics;

    /**
     * 推荐的模块名称
     * <p>建议将此表放置的模块，例如：system、business、shared</p>
     */
    private String suggestedModule;

    /**
     * 其他属性
     * <p>用于前端展示和扩展的额外信息</p>
     */
    private Map<String, Object> attributes;

    /**
     * 判断是否为高置信度分类
     *
     * @return 如果置信度 >= 0.8 返回 true
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }

    /**
     * 判断是否为低置信度分类
     *
     * @return 如果置信度 < 0.6 返回 true
     */
    public boolean isLowConfidence() {
        return confidence != null && confidence < 0.6;
    }

    /**
     * 判断是否建议使用充血模型
     *
     * @return 如果推荐充血模型返回 true
     */
    public boolean isRichModel() {
        return modelType != null && modelType.isRich();
    }

    /**
     * 判断是否为聚合根
     *
     * @return 如果是聚合根返回 true
     */
    public boolean isAggregateRoot() {
        return aggregateInfo != null && aggregateInfo.isAggregateRoot();
    }

    /**
     * 判断是否为独立表
     *
     * @return 如果是独立表返回 true
     */
    public boolean isStandalone() {
        return aggregateInfo != null && aggregateInfo.isStandalone();
    }

    /**
     * 获取置信度级别描述
     *
     * @return 置信度级别：高、中、低、很低
     */
    public String getConfidenceLevel() {
        if (confidence == null) {
            return "未知";
        }
        if (confidence >= 0.8) {
            return "高";
        } else if (confidence >= 0.6) {
            return "中";
        } else if (confidence >= 0.4) {
            return "低";
        } else {
            return "很低";
        }
    }

    /**
     * 获取置信度对应的颜色代码（用于前端展示）
     *
     * @return 颜色代码：success、warning、danger、info
     */
    public String getConfidenceColor() {
        if (confidence == null) {
            return "info";
        }
        if (confidence >= 0.8) {
            return "success";  // 绿色
        } else if (confidence >= 0.6) {
            return "warning";  // 橙色
        } else {
            return "danger";   // 红色
        }
    }

    /**
     * 判断是否建议人工确认
     *
     * @return 如果置信度 < 0.6 或为子实体返回 true
     */
    public boolean shouldManualConfirm() {
        return isLowConfidence() || (aggregateInfo != null && aggregateInfo.isEntity());
    }

    /**
     * 获取模块名称
     * <p>修复：简化逻辑，添加 null 检查</p>
     *
     * @return 推荐的模块名称，如果没有则根据模型类型推断
     */
    public String getModuleName() {
        // 优先使用建议的模块名
        if (suggestedModule != null && !suggestedModule.isBlank()) {
            return suggestedModule;
        }

        // 独立表或贫血模型 → system 模块
        if (isStandalone()) {
            return "system";
        }

        if (modelType == DomainModelType.ANEMIC) {
            return "system";
        }

        // 充血模型且有聚合信息 → business.聚合名
        if (aggregateInfo != null && aggregateInfo.getAggregateName() != null) {
            return "business." + aggregateInfo.getAggregateName();
        }

        // 默认 business 模块
        return "business";
    }
}
