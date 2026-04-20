package com.gallop.fastddd.analyzer.service;

import com.gallop.fastddd.analyzer.analyzer.ComplexityScorer;
import com.gallop.fastddd.analyzer.analyzer.RelationshipAnalyzer;
import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.classifier.model.AnalysisMetrics;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.classifier.strategy.ClassificationStrategy;
import com.gallop.fastddd.analyzer.detector.AggregateDetector;
import com.gallop.fastddd.analyzer.properties.AnalyzerProperties;
import com.gallop.fastddd.common.enums.DomainModelType;
import com.gallop.fastddd.codegen.model.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 表分析服务
 * <p>
 * 主入口，协调各个分析组件完成完整的表分析流程：
 * </p>
 *
 * <h2>分析流程</h2>
 * <ol>
 *   <li>分析表关系 (RelationshipAnalyzer)</li>
 *   <li>检测聚合 (AggregateDetector)</li>
   *   <li>分类每个表 (ClassificationStrategy)</li>
 *   <li>后处理：确保子实体跟随聚合根的模型类型</li>
 * </ol>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableAnalysisService {

    private final List<ClassificationStrategy> strategies;
    private final RelationshipAnalyzer relationshipAnalyzer;
    private final AggregateDetector aggregateDetector;
    private final ComplexityScorer complexityScorer;
    private final AnalyzerProperties properties;

    /**
     * 分析多个表
     *
     * @param tables 表列表
     * @return 分析结果映射 (tableName -> TableAnalysisResult)
     */
    public Map<String, TableAnalysisResult> analyzeTables(List<TableMetadata> tables) {
        log.info("开始分析 {} 个表", tables.size());

        // Step 1: 分析表关系
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        log.debug("检测到 {} 个表有关系", relationships.size());

        // Step 2: 检测聚合
        Map<String, List<String>> aggregates = aggregateDetector.detectAggregates(tables, relationships);
        log.info("检测到 {} 个聚合", aggregates.size());

        // Step 3: 构建上下文信息
        Map<String, Object> context = buildAnalysisContext(relationships, aggregates);

        // Step 4: 分类每个表
        Map<String, TableAnalysisResult> results = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            TableAnalysisResult result = classifyTable(table, context);
            results.put(table.getTableName(), result);
        }

        // Step 5: 后处理：确保子实体跟随聚合根
        postProcessResults(results, aggregates);

        // 统计结果
        long anemicCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.ANEMIC)
            .count();
        long richCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.RICH)
            .count();

        log.info("表分析完成: {} 个贫血模型, {} 个充血模型", anemicCount, richCount);
        return results;
    }

    /**
     * 分析多个表（含 PRD 上下文）
     * <p>
     * 在标准分析流程基础上，将 PRD 文本注入到分析上下文中，
     * 供 LLM 策略参考以获得更精准的分类结果。
     * </p>
     *
     * @param tables 表列表
     * @param prdText PRD 文档文本
     * @return 分析结果映射 (tableName -> TableAnalysisResult)
     */
    public Map<String, TableAnalysisResult> analyzeTables(List<TableMetadata> tables, String prdText) {
        log.info("开始分析 {} 个表 (含 PRD 上下文)", tables.size());

        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        Map<String, List<String>> aggregates = aggregateDetector.detectAggregates(tables, relationships);
        Map<String, Object> context = buildAnalysisContext(relationships, aggregates);

        if (prdText != null && !prdText.isBlank()) {
            context.put("prdText", prdText);
        }

        Map<String, TableAnalysisResult> results = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            TableAnalysisResult result = classifyTable(table, context);
            results.put(table.getTableName(), result);
        }

        postProcessResults(results, aggregates);

        long anemicCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.ANEMIC)
            .count();
        long richCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.RICH)
            .count();

        log.info("表分析完成: {} 个贫血模型, {} 个充血模型", anemicCount, richCount);
        return results;
    }

    /**
     * 分析多个表（指定分析模式）
     * <p>
     * 支持前端按需选择分析模式：
     * <ul>
     *   <li>"rule" — 仅规则引擎（快速）</li>
     *   <li>"llm" — LLM 优先，失败回退规则引擎（准确但较慢）</li>
     * </ul>
     * </p>
     *
     * @param tables 表列表
     * @param mode 分析模式 ("rule" | "llm")
     * @return 分析结果映射 (tableName -> TableAnalysisResult)
     */
    public Map<String, TableAnalysisResult> analyzeTablesWithMode(List<TableMetadata> tables, String mode) {
        AnalyzerProperties.Strategy effectiveStrategy = resolveEffectiveStrategy(mode);
        log.info("开始分析 {} 个表, 模式: {}, 策略: {}", tables.size(), mode, effectiveStrategy);

        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        Map<String, List<String>> aggregates = aggregateDetector.detectAggregates(tables, relationships);
        Map<String, Object> context = buildAnalysisContext(relationships, aggregates);

        Map<String, TableAnalysisResult> results = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            TableAnalysisResult result = classifyTableWithStrategy(table, context, effectiveStrategy);
            results.put(table.getTableName(), result);
        }

        postProcessResults(results, aggregates);

        long anemicCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.ANEMIC)
            .count();
        long richCount = results.values().stream()
            .filter(r -> r.getModelType() == DomainModelType.RICH)
            .count();

        log.info("表分析完成 (mode={}): {} 个贫血模型, {} 个充血模型", mode, anemicCount, richCount);
        return results;
    }

    /**
     * 将前端 mode 参数映射为内部策略枚举
     */
    private AnalyzerProperties.Strategy resolveEffectiveStrategy(String mode) {
        if ("llm".equalsIgnoreCase(mode)) {
            return AnalyzerProperties.Strategy.LLM;
        }
        if (mode != null && !"rule".equalsIgnoreCase(mode)) {
            log.warn("未知的分析模式 '{}', 回退到 RULE", mode);
        }
        return AnalyzerProperties.Strategy.RULE;
    }

    /**
     * 分析单个表（使用配置文件默认策略）
     */
    private TableAnalysisResult classifyTable(TableMetadata table, Map<String, Object> context) {
        return classifyTableWithStrategy(table, context, properties.getDefaultStrategy());
    }

    /**
     * 分析单个表（指定策略）
     */
    private TableAnalysisResult classifyTableWithStrategy(TableMetadata table, Map<String, Object> context,
                                                          AnalyzerProperties.Strategy strategy) {
        switch (strategy) {
            case LLM:
                return classifyWithLLMFirst(table, context);
            case HYBRID:
                return classifyHybrid(table, context);
            case RULE:
            default:
                return classifyWithFirstAvailable(table, context);
        }
    }

    /**
     * RULE 模式：按优先级尝试所有可用策略
     */
    private TableAnalysisResult classifyWithFirstAvailable(TableMetadata table, Map<String, Object> context) {
        for (ClassificationStrategy strategy : strategies) {
            if (strategy.isAvailable()) {
                try {
                    TableAnalysisResult result = strategy.classify(table, context);
                    log.debug("表 {} 使用 {} 策略分类结果: {} (置信度: {})",
                        table.getTableName(),
                        strategy.getName(),
                        result.getModelType(),
                        result.getConfidence());
                    return result;
                } catch (Exception e) {
                    log.warn("策略 {} 分类表 {} 失败: {}", strategy.getName(), table.getTableName(), e.getMessage());
                }
            }
        }
        return createDefaultResult(table);
    }

    /**
     * LLM 模式：优先尝试 LLM，失败后回退到其他可用策略
     */
    private TableAnalysisResult classifyWithLLMFirst(TableMetadata table, Map<String, Object> context) {
        // 用户主动选择 LLM 模式，跳过 isAvailable() 中的配置策略检查
        TableAnalysisResult llmResult = runStrategy("LLM-Enhanced", table, context, true);
        if (llmResult != null) return llmResult;

        // Fallback to any available strategy
        return classifyWithFirstAvailable(table, context);
    }

    /**
     * HYBRID 模式：先执行规则引擎，低置信度时用 LLM 增强
     */
    private TableAnalysisResult classifyHybrid(TableMetadata table, Map<String, Object> context) {
        // Step 1: Run rule-based first
        TableAnalysisResult ruleResult = runStrategy("Enhanced-Rule-Based", table, context);

        // Step 2: If low confidence, try LLM enhancement
        if (ruleResult != null && ruleResult.getConfidence() != null
            && ruleResult.getConfidence() < properties.getHybridConfidenceThreshold()) {
            log.debug("表 {} 规则引擎置信度 {} 低于阈值 {}, 尝试 LLM 增强",
                table.getTableName(), ruleResult.getConfidence(), properties.getHybridConfidenceThreshold());
            TableAnalysisResult llmResult = runStrategy("LLM-Enhanced", table, context, true);
            if (llmResult != null) return llmResult;
        }

        return ruleResult != null ? ruleResult : createDefaultResult(table);
    }

    /**
     * 按名称运行指定策略（检查可用性）
     */
    private TableAnalysisResult runStrategy(String name, TableMetadata table, Map<String, Object> context) {
        return runStrategy(name, table, context, false);
    }

    /**
     * 按名称运行指定策略
     *
     * @param name 策略名称
     * @param table 表元数据
     * @param context 上下文
     * @param force 是否跳过 isAvailable() 检查（用户主动选择时为 true）
     * @return 分类结果，策略不可用或失败时返回 null
     */
    private TableAnalysisResult runStrategy(String name, TableMetadata table, Map<String, Object> context,
                                            boolean force) {
        for (ClassificationStrategy s : strategies) {
            if (name.equals(s.getName()) && (force || s.isAvailable())) {
                try {
                    return s.classify(table, context);
                } catch (Exception e) {
                    log.warn("策略 {} 分类表 {} 失败: {}", name, table.getTableName(), e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 创建默认分类结果
     */
    private TableAnalysisResult createDefaultResult(TableMetadata table) {
        return TableAnalysisResult.builder()
            .tableName(table.getTableName())
            .modelType(DomainModelType.ANEMIC)
            .confidence(0.5)
            .reason("所有分类策略失败，使用默认分类")
            .strategy("Default")
            .metrics(AnalysisMetrics.builder()
                .columnCount(table.getColumns().size())
                .relationshipCount(0)
                .complexityScore(0.3)
                .hasStateMachine(false)
                .hasBusinessRules(false)
                .isAggregateRoot(false)
                .build())
            .attributes(new HashMap<>())
            .build();
    }

    /**
     * 后处理：确保子实体跟随聚合根的模型类型
     *
     * @param results 分析结果
     * @param aggregates 聚合分组
     */
    private void postProcessResults(Map<String, TableAnalysisResult> results,
                                    Map<String, List<String>> aggregates) {
        for (List<String> members : aggregates.values()) {
            if (members.size() <= 1) continue; // 单表聚合不需要处理

            String rootTable = members.get(0);
            TableAnalysisResult rootResult = results.get(rootTable);

            if (rootResult == null) {
                log.warn("聚合根 {} 的分类结果不存在", rootTable);
                continue;
            }

            DomainModelType rootModelType = rootResult.getModelType();

            // 处理子实体
            for (int i = 1; i < members.size(); i++) {
                String memberTable = members.get(i);
                TableAnalysisResult memberResult = results.get(memberTable);

                // 使用 Optional 避免 NPE
                if (memberResult != null &&
                    Optional.ofNullable(memberResult.getAggregateInfo())
                        .map(AggregateInfo::getRole)
                        .filter(role -> role == AggregateInfo.AggregateRole.ENTITY)
                        .isPresent()) {

                    // 子实体跟随聚合根的模型类型
                    memberResult.setModelType(rootModelType);
                    memberResult.setReason(String.format(
                        "跟随聚合根 %s 使用 %s",
                        rootTable,
                        rootModelType.getDisplayName()
                    ));
                    memberResult.getAttributes().put("followAggregateRoot", true);

                    log.debug("子实体 {} 跟随聚合根 {} 使用 {}",
                        memberTable, rootTable, rootModelType);
                }
            }
        }
    }

    /**
     * 构建分析上下文
     * <p>优化：预先计算被引用次数，避免重复遍历</p>
     *
     * @param relationships 关系映射
     * @param aggregates 聚合分组
     * @return 上下文信息
     */
    private Map<String, Object> buildAnalysisContext(
            Map<String, List<String>> relationships,
            Map<String, List<String>> aggregates) {

        Map<String, Object> context = new HashMap<>();
        context.put("relationships", relationships);
        context.put("aggregates", aggregates);

        // 优化：预先计算每个表的被引用次数，避免重复遍历
        Map<String, Integer> referencedCountMap = new HashMap<>();
        for (List<String> refs : relationships.values()) {
            for (String refTable : refs) {
                referencedCountMap.merge(refTable, 1, Integer::sum);
            }
        }

        // 为每个表添加外键数量和被引用状态
        for (String tableName : aggregates.keySet()) {
            List<String> members = aggregates.get(tableName);

            for (String member : members) {
                // 获取外键数量
                List<String> refs = relationships.get(member);
                int fkCount = refs != null ? refs.size() : 0;

                // 获取被引用次数（从预先计算的 map 中获取）
                int refCount = referencedCountMap.getOrDefault(member, 0);

                context.put(member.toLowerCase() + "_foreignKeyCount", fkCount);
                context.put(member.toLowerCase() + "_isReferenced", refCount > 0);
            }
        }

        return context;
    }

    /**
     * 获取聚合分组
     *
     * @param tables 表列表
     * @return 聚合分组 (aggregateName -> List<tableName>)
     */
    public Map<String, List<String>> getAggregateGroups(List<TableMetadata> tables) {
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        return aggregateDetector.detectAggregates(tables, relationships);
    }

    /**
     * 获取关系图数据
     *
     * @param tables 表列表
     * @return 关系图数据
     */
    public RelationshipAnalyzer.GraphData getRelationshipGraph(List<TableMetadata> tables) {
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        return relationshipAnalyzer.buildGraphData(tables, relationships);
    }

    /**
     * 获取单个表的分析结果
     *
     * @param tables 所有表
     * @param tableName 要分析的表名
     * @return 分析结果
     */
    public Optional<TableAnalysisResult> analyzeTable(List<TableMetadata> tables, String tableName) {
        Map<String, TableAnalysisResult> results = analyzeTables(tables);
        return Optional.ofNullable(results.get(tableName));
    }

    /**
     * 批量获取特定表的分析结果
     *
     * @param tables 所有表
     * @param tableNames 要分析的表名列表
     * @return 分析结果映射
     */
    public Map<String, TableAnalysisResult> analyzeTables(List<TableMetadata> tables,
                                                          List<String> tableNames) {
        Map<String, TableAnalysisResult> allResults = analyzeTables(tables);

        Map<String, TableAnalysisResult> filtered = new HashMap<>();
        for (String tableName : tableNames) {
            TableAnalysisResult result = allResults.get(tableName);
            if (result != null) {
                filtered.put(tableName, result);
            }
        }

        return filtered;
    }

    /**
     * 获取表所属的聚合
     *
     * @param tables 所有表
     * @param tableName 表名
     * @return 聚合名，如果不属于任何聚合返回 null
     */
    public String getAggregateForTable(List<TableMetadata> tables, String tableName) {
        Map<String, List<String>> groups = getAggregateGroups(tables);
        return aggregateDetector.getAggregateName(tableName, groups);
    }

    /**
     * 判断表是否为聚合根
     *
     * @param tables 所有表
     * @param tableName 表名
     * @return 如果是聚合根返回 true
     */
    public boolean isAggregateRoot(List<TableMetadata> tables, String tableName) {
        Map<String, List<String>> groups = getAggregateGroups(tables);
        return aggregateDetector.isAggregateRoot(tableName, groups);
    }

    /**
     * 获取表的关系
     *
     * @param tables 所有表
     * @param tableName 表名
     * @return 引用的表名列表
     */
    public List<String> getRelationships(List<TableMetadata> tables, String tableName) {
        Map<String, List<String>> relationships = relationshipAnalyzer.analyzeRelationships(tables);
        return relationships.getOrDefault(tableName, Collections.emptyList());
    }
}
