package com.gallop.fastddd.analyzer;

import com.gallop.fastddd.analyzer.properties.AnalyzerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * 智能分析器自动配置类
 * <p>
 * 自动配置表分析相关的所有组件，包括：
 * <ul>
 *   <li>关系分析器 (RelationshipAnalyzer)</li>
 *   <li>复杂度评分器 (ComplexityScorer)</li>
 *   <li>聚合检测器 (AggregateDetector)</li>
 *   <li>分类策略 (ClassificationStrategy)</li>
 *   <li>表分析服务 (TableAnalysisService)</li>
 * </ul>
 * </p>
 *
 * <h3>启用条件</h3>
 * <ul>
 *   <li>fastddd.analyzer.enabled = true（默认启用）</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>
 * # application.yml
 * fastddd:
 *   analyzer:
 *     enabled: true
 *     default-strategy: rule
 *     complexity-weights:
 *       column-count: 0.2
 *       status-field: 0.15
 * </pre>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(AnalyzerProperties.class)
@ConditionalOnProperty(prefix = "fastddd.analyzer", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.gallop.fastddd.analyzer")
public class AnalyzerAutoConfiguration {

    public AnalyzerAutoConfiguration(AnalyzerProperties properties) {
        log.info("FastDDD 智能分析器已启用");
        log.info("默认分类策略: {}", properties.getDefaultStrategy());

        // 校验权重配置
        if (!properties.getComplexityWeights().isValid()) {
            log.warn("复杂度评分权重总和不为 1.0 (当前: {})，可能影响评分准确性",
                properties.getComplexityWeights().getSum());
        }

        log.info("LLM 增强功能将通过 fast-ddd-ai-spring-boot-starter 的 LlmClient 按需注入");
    }
}
