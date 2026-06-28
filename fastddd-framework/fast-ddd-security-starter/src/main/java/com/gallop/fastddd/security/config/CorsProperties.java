package com.gallop.fastddd.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CORS配置属性（生产级）
 * <p>
 * Spring Security CORS最佳实践实现：
 * <ul>
 *   <li>支持配置文件、环境变量、命令行参数三种配置方式</li>
 *   <li>配置优先级：命令行 > 环境变量 > application-{profile}.yml > application.yml</li>
 *   <li>启动时自动验证配置有效性</li>
 *   <li>详细的配置日志，便于问题排查</li>
 * </ul>
 * <p>
 * 环境变量配置示例：
 * <pre>
 * # Linux/Mac
 * export FASTDDD_SECURITY_CORS_ALLOWEDORIGINS="https://admin.example.com,https://app.example.com"
 *
 * # Windows
 * set FASTDDD_SECURITY_CORS_ALLOWEDORIGINS=https://admin.example.com,https://app.example.com
 *
 * # Docker
 * docker run -e FASTDDD_SECURITY_CORS_ALLOWEDORIGINS="https://admin.example.com" ...
 * </pre>
 *
 * @author gallop
 * @date 2025-01-24 10:00:00
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "fastddd.security.cors")
public class CorsProperties {

    /**
     * 是否启用CORS配置
     */
    private boolean enabled = true;

    /**
     * 允许的源地址模式列表
     * <p>
     * 支持格式：
     * <ul>
     *   <li>完整URL：http://localhost:3005, https://admin.example.com</li>
     *   <li>通配符子域名：https://*.example.com</li>
     *   <li>所有来源（仅开发环境）：* （注意：allowCredentials=true时不支持）</li>
     * </ul>
     */
    private List<String> allowedOrigins = new ArrayList<>(Arrays.asList(
        "http://localhost:3005",
        "http://localhost:3006",
        "http://127.0.0.1:3005"
    ));

    /**
     * 允许的HTTP方法
     */
    private List<String> allowedMethods = new ArrayList<>(Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    ));

    /**
     * 允许的请求头
     */
    private List<String> allowedHeaders = new ArrayList<>(Arrays.asList("*"));

    /**
     * 是否允许发送凭证
     * <p>
     * ⚠️ 重要：当设置为true时，allowedOrigins不能使用 "*"，必须指定具体域名
     */
    private boolean allowCredentials = true;

    /**
     * 预检请求缓存时间
     */
    private Duration maxAge = Duration.ofHours(1);

    /**
     * 暴露的响应头
     */
    private List<String> exposedHeaders = new ArrayList<>(Arrays.asList(
        "Authorization", "Content-Type"
    ));

    /**
     * 应用CORS的URL路径模式
     */
    private List<String> pathPatterns = new ArrayList<>(Arrays.asList("/**"));

    /**
     * 开发环境是否完全禁用CORS检查
     * <p>
     * 开发环境设置为true时，跳过所有CORS验证，无需配置allowedOrigins
     * 生产环境此选项自动失效（强制启用CORS保护）
     */
    private boolean devDisableCors = true;

    /**
     * 配置验证和启动日志
     * Spring Boot会在配置绑定后自动调用此方法
     */
    @PostConstruct
    public void validateAndLog() {
        // 开发环境检查是否禁用CORS
        if (isDevelopmentProfile() && devDisableCors) {
            log.info("========================================");
            log.info(" Development Mode: CORS DISABLED");
            log.info(" Skipping all CORS checks");
            log.info("========================================");
            return;
        }

        if (!enabled) {
            log.info("========================================");
            log.info("CORS is DISABLED by configuration");
            log.info("========================================");
            return;
        }

        log.info("========================================");
        log.info("CORS Configuration (Best Practices)");
        log.info("========================================");

        // 验证配置
        validateConfiguration();

        // 打印配置摘要
        logConfigurationSummary();

        log.info("========================================");
    }

    /**
     * 验证CORS配置的有效性
     * 启动时发现问题，而不是运行时才发现
     */
    private void validateConfiguration() {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 检查 allowCredentials 与 allowedOrigins 的兼容性
        if (allowCredentials) {
            boolean hasWildcard = allowedOrigins.stream()
                    .anyMatch(origin -> "*".equals(origin.trim()));
            if (hasWildcard) {
                errors.add("allowCredentials=true 与 allowedOrigins='*' 不兼容。浏览器不允许这种组合。" +
                          "请从allowedOrigins中移除'*'或设置allowCredentials=false");
            }
        }

        // 检查是否有允许的源
        if (CollectionUtils.isEmpty(allowedOrigins)) {
            warnings.add("没有配置允许的源地址（allowedOrigins为空），所有跨域请求将被阻止");
        }

        // 检查是否有允许的方法
        if (CollectionUtils.isEmpty(allowedMethods)) {
            warnings.add("没有配置允许的HTTP方法，使用默认方法");
            allowedMethods = Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS");
        }

        // 检查 maxAge 是否合理
        long maxAgeSeconds = maxAge.getSeconds();
        if (maxAgeSeconds < 0) {
            errors.add("max-age必须为正数，当前值：" + maxAgeSeconds);
            this.maxAge = Duration.ofSeconds(3600);
        } else if (maxAgeSeconds > 86400) {
            warnings.add("max-age超过24小时（" + maxAgeSeconds + "s），通常预检请求缓存不需要这么长");
        }

        // 生产环境安全检查
        if (isProductionProfile() && allowCredentials) {
            boolean hasHttpOrigins = allowedOrigins.stream()
                    .anyMatch(origin -> origin.startsWith("http://") && !origin.contains("localhost") && !origin.contains("127.0.0.1"));
            if (hasHttpOrigins) {
                warnings.add("生产环境检测到HTTP源地址且allowCredentials=true。建议生产环境使用HTTPS");
            }
        }

        // 打印警告
        if (!warnings.isEmpty()) {
            log.warn("⚠️  CORS Configuration Warnings:");
            for (String warning : warnings) {
                log.warn("    - {}", warning);
            }
        }

        // 打印错误并可能抛出异常（可选）
        if (!errors.isEmpty()) {
            log.error("❌ CORS Configuration Errors:");
            for (String error : errors) {
                log.error("    - {}", error);
            }
            // 可选：对于严重错误，可以抛出异常阻止启动
            // throw new IllegalStateException("CORS configuration error: " + errors.get(0));
        }
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProductionProfile() {
        String profile = System.getProperty("spring.profiles.active");
        return profile != null && profile.contains("prod");
    }

    /**
     * 判断是否为开发环境
     */
    public boolean isDevelopmentProfile() {
        String profile = System.getProperty("spring.profiles.active");
        return profile != null && (profile.contains("dev") || profile.contains("development"));
    }

    /**
     * 判断是否应该禁用CORS
     * <p>
     * 开发环境且devDisableCors=true时禁用
     * 生产环境强制启用CORS保护
     */
    public boolean shouldDisableCors() {
        return devDisableCors && isDevelopmentProfile();
    }

    /**
     * 打印配置摘要（用于启动时确认）
     */
    private void logConfigurationSummary() {
        log.info("Enabled: {}", enabled);
        log.info("Allowed Origins ({}):", allowedOrigins.size());
        allowedOrigins.forEach(origin -> log.info("  - {}", origin));
        log.info("Allowed Methods: {}", allowedMethods);
        log.info("Allowed Headers: {}", allowedHeaders);
        log.info("Allow Credentials: {}", allowCredentials);
        log.info("Max Age: {}s", maxAge.getSeconds());
        log.info("Exposed Headers: {}", exposedHeaders);
        log.info("Path Patterns: {}", pathPatterns);
    }

    /**
     * 获取用于Spring Security的CorsConfiguration对象
     */
    public org.springframework.web.cors.CorsConfiguration toSpringCorsConfiguration() {
        org.springframework.web.cors.CorsConfiguration config = new org.springframework.web.cors.CorsConfiguration();

        // 设置允许的源（使用setAllowedOriginPatterns支持更灵活的模式）
        if (!CollectionUtils.isEmpty(allowedOrigins)) {
            config.setAllowedOriginPatterns(allowedOrigins);
        }

        // 设置允许的方法
        if (!CollectionUtils.isEmpty(allowedMethods)) {
            config.setAllowedMethods(allowedMethods);
        }

        // 设置允许的请求头
        if (!CollectionUtils.isEmpty(allowedHeaders)) {
            config.setAllowedHeaders(allowedHeaders);
        }

        // 设置凭证
        config.setAllowCredentials(allowCredentials);

        // 设置最大缓存时间
        config.setMaxAge(maxAge.getSeconds());

        // 设置暴露的响应头
        if (!CollectionUtils.isEmpty(exposedHeaders)) {
            config.setExposedHeaders(exposedHeaders);
        }

        return config;
    }
}
