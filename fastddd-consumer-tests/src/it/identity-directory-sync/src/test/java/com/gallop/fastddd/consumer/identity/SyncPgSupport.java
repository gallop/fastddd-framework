package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalAuthorizePort;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * W3-c 目录同步场景共享夹具（§五 桩边界）：zonky 一次性真实 PG（V1–V3 随
 * initialize-schema 建表）+ 测试专用登录桩/授权 URL 桩/宿主准入放行。
 * 桩仅存在于本测试夹具，不进生产自动配置；真实飞书适配器/源见
 * identity-feishu-login 与 identity-directory-feishu 场景。
 */
@Configuration(proxyBeanMethods = false)
public class SyncPgSupport {

    /** 桩源 scope（目录同步编排与差异报表断言共用）。 */
    public static final DirectoryScope SCOPE = new DirectoryScope("consumer-sync", "sync-app");

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

    /** 测试专用登录桩：code 即外部主体（真实提供者缺失时 C 模式启动失败）。 */
    @Bean
    ExternalLoginProvider externalLoginProvider() {
        return command -> {
            String code = command.credential("code");
            String externalId = code == null ? "ou_default" : code;
            return new VerifiedExternalLogin(
                    new ExternalIdentity("feishu-stub", "consumer-sync-tenant", "consumer-sync-app",
                            "union_id", externalId),
                    "login-" + externalId, "Stub " + externalId);
        };
    }

    /** 授权 URL 桩（浏览器流发起面；真实构造见 identity-feishu-login 场景）。 */
    @Bean
    ExternalAuthorizePort externalAuthorizePort() {
        return (state, challenge) -> "https://idp-stub.example/authorize?state=" + state
                + "&code_challenge=" + challenge + "&code_challenge_method=S256";
    }

    /** 测试夹具显式放行（W21-01：生产宿主必须自带准入策略）。 */
    @Bean
    AdmissionPolicy admissionPolicy() {
        return principal -> {
        };
    }
}
