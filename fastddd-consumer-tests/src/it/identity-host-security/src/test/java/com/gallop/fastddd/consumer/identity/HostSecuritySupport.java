package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

/**
 * C+host-owned 消费者测试夹具（§五 桩边界）：zonky PG 与测试登录桩（宿主链见
 * {@link HostChainSupport}——恢复上下文语义的链由各测试自带，不复用无恢复链）。
 */
@Configuration(proxyBeanMethods = false)
class HostSecuritySupport {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (Exception e) {
            throw new IllegalStateException("embedded PostgreSQL 启动失败", e);
        }
    }

    @Bean
    DataSource dataSource() {
        return PG.getDatabase("postgres", "postgres");
    }

    @Bean
    ExternalLoginProvider externalLoginProvider() {
        return command -> {
            String externalId = command.credential("externalId") == null ? "ou_default"
                    : command.credential("externalId");
            return new VerifiedExternalLogin(
                    new ExternalIdentity("feishu-stub", "consumer-tenant", "consumer-app",
                            "union_id", externalId),
                    "login-" + externalId, "Stub " + externalId);
        };
    }

    /**
     * 测试夹具显式放行策略（W21-01：框架不再提供默认放行策略；放行决策只允许
     * 存在于测试/隔离夹具，生产宿主必须自带）。
     */
    @Bean
    com.gallop.fastddd.identity.core.port.AdmissionPolicy admissionPolicy() {
        return principal -> {
        };
    }

    static ExternalLoginCommand command(String externalId) {
        return new ExternalLoginCommand(Map.of("externalId", externalId));
    }
}
