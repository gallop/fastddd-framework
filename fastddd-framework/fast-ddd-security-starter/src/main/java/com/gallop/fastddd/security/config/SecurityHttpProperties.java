package com.gallop.fastddd.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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
            "/api/initializr/**",
            "/api/ai-design/health",
            "/ws/**",
            "/h2-console/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/doc.html",
            "/error"
    ));
}
