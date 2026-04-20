package com.gallop.fastddd.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Prompt 模板加载器
 * <p>
 * 从 classpath 加载模板文件，支持 {variable} 占位符替换。
 * 模板文件位于 resources/prompts/ 目录下。
 * </p>
 *
 * @author gallop
 * @since 1.0.0-SNAPSHOT
 */
@Slf4j
public class PromptTemplateLoader {

    private static final String PROMPTS_BASE_PATH = "prompts/";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern TEMPLATE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    /**
     * 加载模板并替换变量
     *
     * @param templateName 模板名称（不含路径，如 "ddd-analyze-system"）
     * @param variables    变量映射
     * @return 替换后的文本
     */
    public String load(String templateName, Map<String, String> variables) {
        validateTemplateName(templateName);
        String template = loadRaw(templateName);
        return replaceVariables(template, variables);
    }

    /**
     * 加载原始模板内容（缓存）
     *
     * @param templateName 模板名称
     * @return 模板原始内容
     */
    public String loadRaw(String templateName) {
        validateTemplateName(templateName);
        return templateCache.computeIfAbsent(templateName, this::readTemplateFile);
    }

    /**
     * 清除模板缓存
     */
    public void clearCache() {
        templateCache.clear();
    }

    private String readTemplateFile(String templateName) {
        String path = PROMPTS_BASE_PATH + templateName + ".txt";
        try {
            ClassPathResource resource = new ClassPathResource(path);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String content = reader.lines().collect(Collectors.joining("\n"));
                log.debug("Loaded prompt template: {} ({} chars)", templateName, content.length());
                return content;
            }
        } catch (Exception e) {
            throw new PromptLoadException("Failed to load prompt template: " + path, e);
        }
    }

    private String replaceVariables(String template, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private void validateTemplateName(String templateName) {
        if (templateName == null || !TEMPLATE_NAME_PATTERN.matcher(templateName).matches()) {
            throw new PromptLoadException("Invalid template name: " + templateName +
                    " (must match [a-zA-Z0-9_-]+)", null);
        }
    }

    /**
     * Prompt 模板加载异常
     */
    public static class PromptLoadException extends RuntimeException {
        public PromptLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
