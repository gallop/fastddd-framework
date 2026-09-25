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
 * C 模式消费者测试夹具（§五 桩边界）：zonky 一次性 PG + 测试专用登录桩。
 * 桩仅存在于本测试夹具，不进生产自动配置；桩通过 ≠ 飞书登录通过，
 * 浏览器回调（授权码换 token）属 W2-2。
 */
@Configuration(proxyBeanMethods = false)
class CModeSupport {

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

    /** 测试专用登录桩：按命令 token 区分外部主体（真实提供者缺失时 C 模式启动失败）。 */
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
     * 测试夹具显式放行策略（W21-01：框架不再提供默认放行策略——外部凭证验证成功
     * ≠宿主准入；放行决策只允许存在于测试/隔离夹具，生产宿主必须自带）。
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
