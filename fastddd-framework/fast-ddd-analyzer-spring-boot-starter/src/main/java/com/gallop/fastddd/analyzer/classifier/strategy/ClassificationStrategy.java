package com.gallop.fastddd.analyzer.classifier.strategy;

import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.codegen.model.TableMetadata;

/**
 * 表分类策略接口
 * <p>
 * 定义表分类的策略接口，支持多种分类实现：
 * <ul>
 *   <li><b>EnhancedRuleBasedStrategy</b> - 基于增强规则的分类（主要策略）</li>
 *   <li><b>LLMEnhancedStrategy</b> - 基于 LLM 的分类（可选增强）</li>
 *   <li><b>HybridStrategy</b> - 混合策略</li>
 * </ul>
 * </p>
 *
 * <h2>策略调用顺序</h2>
 * <p>策略按优先级顺序调用，直到有一个策略返回有效结果：</p>
 * <ol>
 *   <li>检查策略是否可用（{@link #isAvailable()}）</li>
 *   <li>如果可用，调用分类方法（{@link #classify(TableMetadata, Map)}）</li>
 *   <li>如果分类成功，返回结果；否则尝试下一个策略</li>
 * </ol>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface ClassificationStrategy {

    /**
     * 检查策略是否可用
     * <p>
     * 例如：
     * <ul>
     *   <li>规则策略始终可用</li>
     *   <li>LLM 策略需要配置 API Key</li>
     *   <li>混合策略需要至少一个子策略可用</li>
     * </ul>
     *
     * @return 如果策略可用返回 true
     */
    boolean isAvailable();

    /**
     * 获取策略名称
     * <p>用于日志记录和调试</p>
     *
     * @return 策略名称，例如 "Enhanced-Rule-Based"、"LLM-Enhanced"
     */
    String getName();

    /**
     * 对表进行分类
     * <p>
     * 根据表的元数据和上下文信息，判断应该使用贫血模型还是充血模型。
     * </p>
     *
     * @param table 表元数据
     * @param context 上下文信息，可包含：
     *               <ul>
     *                 <li>relationships - 关系映射</li>
     *                 <li>aggregates - 聚合分组</li>
     *                 <li>existingResults - 已有的分类结果</li>
     *                 </ul>
     * @return 分类结果
     * @throws ClassificationException 如果分类失败
     */
    TableAnalysisResult classify(TableMetadata table, java.util.Map<String, Object> context);

    /**
     * 分类异常
     */
    class ClassificationException extends RuntimeException {
        public ClassificationException(String message) {
            super(message);
        }

        public ClassificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
