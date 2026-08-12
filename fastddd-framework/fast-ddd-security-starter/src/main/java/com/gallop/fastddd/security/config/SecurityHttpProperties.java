package com.gallop.fastddd.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 安全属性（SEC-003：白名单最小化）。
 *
 * <p>SEC-003 步骤 2：通用默认白名单删除业务/开发专用路径——
 * H2 Console（仅开发期、生产暴露风险）、WebSocket（非通用）、
 * Studio 专属（/api/initializr、/api/ai-design/health）。
 * 框架认证端点（/auth/**、/actuator/health/**）与文档端点
 * （swagger-ui、v3/api-docs）保留为通用默认。</p>
 *
 * <p>业务项目可通过 {@code fastddd.security.http.permit-all-paths} 覆盖或追加白名单。</p>
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Data
@ConfigurationProperties(prefix = "fastddd.security.http")
public class SecurityHttpProperties {

    private List<String> permitAllPaths = new ArrayList<>(List.of(
            "/public/**",
            "/auth/**",
            "/api/auth/login",
            "/api/auth/code/send",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/doc.html",
            "/error"
    ));
}
