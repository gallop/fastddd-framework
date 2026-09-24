package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.ExternalIdentity;
import com.gallop.fastddd.identity.core.domain.ExternalLoginCommand;
import com.gallop.fastddd.identity.core.domain.VerifiedExternalLogin;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.pg.PgIdentitySchema;

import javax.sql.DataSource;

/**
 * starter 测试共用支撑：zonky embedded PostgreSQL（真实 PG，专用一次性）+
 * schema 初始化 + 测试专用外部登录桩（§五 桩边界：仅存在于测试/隔离夹具，
 * 不进生产自动配置；桩通过 ≠ 飞书登录通过，浏览器回调留 W2-2）。
 */
final class PgTestSupport {

    /** JVM 级单例（starter 测试与 reference-pg 测试分属不同模块测试 JVM）。 */
    static final io.zonky.test.db.postgres.embedded.EmbeddedPostgres PG;

    static {
        try {
            PG = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start();
        } catch (Exception e) {
            throw new IllegalStateException("embedded PostgreSQL 启动失败", e);
        }
    }

    private PgTestSupport() {
    }

    static DataSource dataSource() {
        DataSource dataSource = PG.getDatabase("postgres", "postgres");
        PgIdentitySchema.initialize(dataSource);
        PgIdentitySchema.initializeAuditTable(dataSource, false);
        return dataSource;
    }

    /** 测试专用登录桩：固定返回一个已验证外部身份（真实提供者由宿主/W2-2 提供）。 */
    static ExternalLoginProvider stubLoginProvider() {
        return command -> new VerifiedExternalLogin(
                new ExternalIdentity("feishu-stub", "tenant-starter", "app-starter", "union_id", "ou_starter_1"),
                "stub-login", "Stub User");
    }

    /** 按命令 token 区分主体的桩（并发/多主体场景）。 */
    static ExternalLoginCommand command(String externalId) {
        return new ExternalLoginCommand(java.util.Map.of("externalId", externalId));
    }

    static ExternalLoginProvider stubLoginProviderByCommand() {
        return command -> {
            String externalId = command.credential("externalId") == null ? "ou_default"
                    : command.credential("externalId");
            return new VerifiedExternalLogin(
                    new ExternalIdentity("feishu-stub", "tenant-starter", "app-starter", "union_id", externalId),
                    "login-" + externalId, "Stub " + externalId);
        };
    }
}
