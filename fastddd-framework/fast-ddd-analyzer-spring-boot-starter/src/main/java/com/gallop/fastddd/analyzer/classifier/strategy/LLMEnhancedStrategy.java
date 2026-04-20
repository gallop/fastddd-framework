package com.gallop.fastddd.analyzer.classifier.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.ai.llm.PromptTemplateLoader;
import com.gallop.fastddd.analyzer.analyzer.ComplexityScorer;
import com.gallop.fastddd.analyzer.classifier.model.AggregateInfo;
import com.gallop.fastddd.analyzer.classifier.model.AnalysisMetrics;
import com.gallop.fastddd.analyzer.classifier.model.TableAnalysisResult;
import com.gallop.fastddd.analyzer.properties.AnalyzerProperties;
import com.gallop.fastddd.common.model.ColumnMetadata;
import com.gallop.fastddd.common.model.TableMetadata;
import com.gallop.fastddd.common.enums.DomainModelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LLM 增强的表分类策略
 * <p>
 * 利用大语言模型对表结构进行智能分类，结合上下文信息（关系、聚合、PRD）
 * 给出更精准的贫血/充血模型推荐。
 * </p>
 *
 * <h2>启用条件</h2>
 * <ul>
 *   <li>Spring AI 的 chatModel bean 已注册（即 spring-ai-starter-model-openai 在 classpath 且 API Key 已配置）</li>
 *   <li>策略模式非 RULE（即 defaultStrategy 为 LLM 或 HYBRID）</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(name = "chatModel")
public class LLMEnhancedStrategy implements ClassificationStrategy {

    private final ChatModel chatModel;
    private final PromptTemplateLoader promptTemplateLoader;
    private final AnalyzerProperties properties;
    private final ComplexityScorer complexityScorer;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final long LLM_TIMEOUT_SECONDS = 30;

    @Override
    public boolean isAvailable() {
        return properties.getDefaultStrategy() != AnalyzerProperties.Strategy.RULE;
    }

    @Override
    public String getName() {
        return "LLM-Enhanced";
    }

    @Override
    public TableAnalysisResult classify(TableMetadata table, Map<String, Object> context) {
        String tableName = table.getTableName();
        log.debug("LLM-Enhanced 策略开始分析表: {}", tableName);

        try {
            // Step 1: Build template variables
            Map<String, String> variables = buildTemplateVariables(table, context);

            // Step 2: Load prompts
            String systemPrompt = promptTemplateLoader.loadRaw("table-classify-system");
            String userPrompt = promptTemplateLoader.load("table-classify-user", variables);

            // Step 3: Call LLM
            ChatOptions chatOptions = OpenAiChatOptions.builder()
                .temperature(0.2)
                .maxTokens(300)
                .build();

            Prompt prompt = new Prompt(
                List.of(
                    new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                    new org.springframework.ai.chat.messages.UserMessage(userPrompt)
                ),
                chatOptions
            );

            ChatResponse response = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> chatModel.call(prompt))
                .get(LLM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Step 4: Extract response text
            String responseText = response.getResult().getOutput().getText();
            if (responseText == null || responseText.isBlank()) {
                throw new ClassificationException("LLM 返回空结果");
            }

            log.debug("LLM 原始响应 (表 {}): {}", tableName, responseText);

            // Step 5: Clean markdown wrapping
            String cleanedJson = cleanMarkdownWrapping(responseText);

            // Step 6: Parse JSON
            JsonNode jsonNode = parseJson(cleanedJson);

            // Step 7: Build result
            return buildResultFromJson(table, jsonNode);

        } catch (ClassificationException e) {
            throw e;
        } catch (TimeoutException e) {
            throw new ClassificationException(
                "LLM 分类表 " + tableName + " 超时 (" + LLM_TIMEOUT_SECONDS + "s)", e);
        } catch (Exception e) {
            throw new ClassificationException(
                "LLM 分类表 " + tableName + " 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 Prompt 模板变量
     */
    private Map<String, String> buildTemplateVariables(TableMetadata table, Map<String, Object> context) {
        Map<String, String> variables = new LinkedHashMap<>();

        // Basic table info
        variables.put("table_name", table.getTableName());
        variables.put("table_comment", table.getTableComment() != null ? table.getTableComment() : "");
        variables.put("column_count", String.valueOf(table.getColumns().size()));

        // Columns JSON
        variables.put("columns_json", buildColumnsJson(table.getColumns()));

        // Relationships context
        variables.put("relationships_context", formatContextEntry(context, "relationships"));

        // Aggregates context
        variables.put("aggregates_context", formatContextEntry(context, "aggregates"));

        // PRD section
        Object prdText = context.get("prdText");
        if (prdText != null && !prdText.toString().isBlank()) {
            variables.put("prd_section", "## PRD 参考\n" + prdText.toString());
        } else {
            variables.put("prd_section", "");
        }

        return variables;
    }

    /**
     * 构建列元数据的 JSON 数组字符串
     * <p>使用简单的字符串拼接，避免复杂序列化</p>
     */
    private String buildColumnsJson(List<ColumnMetadata> columns) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < columns.size(); i++) {
            ColumnMetadata col = columns.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{");
            sb.append("\"name\":\"").append(escapeJson(col.getColumnName())).append("\"");
            sb.append(",\"type\":\"").append(escapeJson(col.getDataTypeName() != null ? col.getDataTypeName() : "")).append("\"");
            sb.append(",\"comment\":\"").append(escapeJson(col.getColumnComment() != null ? col.getColumnComment() : "")).append("\"");
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * JSON 字符串中的特殊字符转义
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    /**
     * 格式化上下文条目为文本
     */
    @SuppressWarnings("unchecked")
    private String formatContextEntry(Map<String, Object> context, String key) {
        Object value = context.get(key);
        if (value == null) {
            return "无";
        }
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            Map<String, ?> map = (Map<String, ?>) value;
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString().trim();
        }
        return value.toString();
    }

    /**
     * 清理 Markdown 代码块包装
     */
    private String cleanMarkdownWrapping(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }

    /**
     * 解析 JSON 字符串为 JsonNode
     */
    private JsonNode parseJson(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            // Try to extract JSON object from text
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return OBJECT_MAPPER.readTree(json.substring(start, end + 1));
                } catch (Exception ex) {
                    throw new ClassificationException("JSON 解析失败: " + ex.getMessage(), ex);
                }
            }
            throw new ClassificationException("无法从响应中提取 JSON: " + json);
        }
    }

    /**
     * 从 JSON 构建分类结果
     */
    private TableAnalysisResult buildResultFromJson(TableMetadata table, JsonNode json) {
        String tableName = table.getTableName();

        // Parse model type
        String modelTypeStr = json.has("modelType") ? json.get("modelType").asText() : "ANEMIC";
        DomainModelType modelType = DomainModelType.fromNameOrDefault(modelTypeStr, DomainModelType.ANEMIC);

        // Parse confidence
        double confidence = json.has("confidence") ? json.get("confidence").asDouble() : 0.5;

        // Parse reason
        String reason = json.has("reason") ? json.get("reason").asText() : "LLM 分类结果";

        // Parse aggregate info (optional)
        AggregateInfo aggregateInfo = null;
        if (json.has("aggregateName")) {
            String aggregateName = json.get("aggregateName").asText();
            String role = json.has("aggregateRole") ? json.get("aggregateRole").asText() : "STANDALONE";
            aggregateInfo = AggregateInfo.builder()
                .aggregateName(aggregateName)
                .rootTableName(aggregateName)
                .role(parseAggregateRole(role))
                .build();
        }

        // Calculate complexity score
        double complexityScore = complexityScorer.calculateComplexityScore(table, 0, false);

        return TableAnalysisResult.builder()
            .tableName(tableName)
            .modelType(modelType)
            .confidence(confidence)
            .reason(reason)
            .strategy("LLM-Enhanced")
            .aggregateInfo(aggregateInfo)
            .metrics(AnalysisMetrics.builder()
                .columnCount(table.getColumns().size())
                .complexityScore(complexityScore)
                .build())
            .attributes(new HashMap<>())
            .build();
    }

    /**
     * 解析聚合角色字符串
     */
    private AggregateInfo.AggregateRole parseAggregateRole(String roleStr) {
        if (roleStr == null) return AggregateInfo.AggregateRole.STANDALONE;
        try {
            return AggregateInfo.AggregateRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AggregateInfo.AggregateRole.STANDALONE;
        }
    }
}
