package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.domain.LoginResult;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.AdmissionPolicy;
import com.gallop.fastddd.identity.core.port.ExternalLoginProvider;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.pg.FastDddIdentityPgAutoConfiguration;
import com.gallop.fastddd.identity.spring.actor.IdentityAuthenticationToken;
import com.gallop.fastddd.identity.spring.actor.SpringSystemJobContext;
import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.audit.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 修复轮 W21-04：业务 {@code @AuditLog} 真实贯通（经生产切面织入的业务方法，
 * 不是直接调 writer 的单元测试）——C 模式下统一 Actor 解析、缺失身份业务前拒绝、
 * 异步落库前捕获 Actor、单次调用单条记录、两种列模式；security 默认
 * AuditLogServiceImpl 以 @CoM 退让（无新旧双写）。
 */
class IdentityAuditBridgeTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    // 生产切面/@EnableAspectJAutoProxy/@EnableAsync（AuditLogConfiguration）来自这里
                    com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class))
            .withUserConfiguration(AuditApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false",
                    "fastddd.identity.pg.ensure-audit-table=true",
                    "fastddd.identity.audit.actor-columns=true");

    /** actor-columns=false 变体（writer① 仅旧列）。 */
    private final WebApplicationContextRunner legacyRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    com.gallop.fastddd.identity.spring.security.IdentitySecurityAutoConfiguration.class,
                    FastDddIdentityPgAutoConfiguration.class,
                    com.gallop.fastddd.security.FastDddSecurityAutoConfiguration.class))
            .withUserConfiguration(AuditApp.class)
            .withPropertyValues(
                    "fastddd.identity.enabled=true",
                    "fastddd.identity.login.mode=feishu-web",
                    "fastddd.security.jwt.enabled=false",
                    "fastddd.security.local-auth.enabled=false",
                    "fastddd.identity.pg.ensure-audit-table=true",
                    "fastddd.identity.audit.actor-columns=false");

    @Configuration(proxyBeanMethods = false)
    static class AuditApp {

        @Bean
        DataSource dataSource() {
            return PgTestSupport.dataSource();
        }

        @Bean
        ExternalLoginProvider externalLoginProvider() {
            return PgTestSupport.stubLoginProviderByCommand();
        }

        /** 测试夹具显式放行策略（W21-01：放行决策只允许存在于测试夹具）。 */
        @Bean
        AdmissionPolicy admissionPolicy() {
            return principal -> {
            };
        }

        @Bean
        AuditedService auditedService() {
            return new AuditedService();
        }
    }

    /** 真实业务面：被生产 AuditLogAspect 织入（@EnableAspectJAutoProxy 生效于用户 Bean）。 */
    static class AuditedService {

        // 类级计数器：Bean 会被 CGLIB 代理（Objenesis 不跑构造器/字段初始化），
        // 实例字段在代理上为 null；目标对象的执行计数须经类成员读取
        static final AtomicInteger executions = new AtomicInteger();

        @AuditLog(value = "identity.bridge.sync", async = false)
        public String syncBusiness(String input) {
            executions.incrementAndGet();
            return "ok:" + input;
        }

        @AuditLog(value = "identity.bridge.failing", async = false)
        public String failingBusiness() {
            executions.incrementAndGet();
            throw new IllegalStateException("boom");
        }

        @AuditLog("identity.bridge.async") // async 默认 true
        public String asyncBusiness() {
            executions.incrementAndGet();
            return "ok";
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LoginResult loginAs(LocalLoginUseCase login, String externalId) {
        LoginResult result = login.login(PgTestSupport.command(externalId));
        SecurityContextHolder.getContext()
                .setAuthentication(IdentityAuthenticationToken.forSession(result.principal()));
        return result;
    }

    @Test
    void bridgeWiresSingleAuditServiceAndDefaultImplBacksOff() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // 单一 AuditLogService：identity 桥接使 security 默认实现退让（无新旧双写）
            assertThat(context.getBeanNamesForType(AuditLogService.class)).hasSize(1);
            assertThat(context).hasBean("identityAuditLogService");
            assertThat(context).doesNotHaveBean("auditLogServiceImpl");
            assertThat(context).hasBean("identityAuditActorProvider");
            assertThat(context).hasBean("auditLogAspect"); // 切面仍由 security 提供（单一切面）
        });
    }

    @Test
    void humanBusinessCallWritesActorColumnsExactlyOnce() {
        runner.run(context -> {
            LoginResult login = loginAs(context.getBean(LocalLoginUseCase.class), "ou_audit_human");
            String principalId = login.principal().principalId().value();
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            context.getBean(AuditedService.class).syncBusiness("payload");

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT username, actor_principal_id, actor_type, success FROM sys_audit_log"
                            + " WHERE operation = ? AND username = ?", "identity.bridge.sync", principalId);
            assertThat(rows).hasSize(1); // 单次调用单条记录（不重复）
            assertThat(rows.get(0).get("username")).isEqualTo(principalId);
            assertThat(rows.get(0).get("actor_principal_id")).isEqualTo(principalId);
            assertThat(rows.get(0).get("actor_type")).isEqualTo("USER"); // HUMAN→宿主词表
            assertThat(rows.get(0).get("success")).isEqualTo(Boolean.TRUE);
        });
    }

    @Test
    void systemJobBusinessCallWritesServiceAccountActor() {
        runner.run(context -> {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            AuditedService service = context.getBean(AuditedService.class);

            String result = SpringSystemJobContext.run(PrincipalId.forSystemJob("audit-bridge-job"),
                    () -> service.syncBusiness("job"));

            assertThat(result).isEqualTo("ok:job");
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT username, actor_type FROM sys_audit_log"
                            + " WHERE operation = ? AND username = ?",
                    "identity.bridge.sync", "system-job:audit-bridge-job");
            assertThat(row.get("actor_type")).isEqualTo("SERVICE_ACCOUNT"); // SYSTEM_JOB→宿主词表
        });
    }

    @Test
    void missingIdentityRejectsBusinessBeforeExecutionAndWritesNothing() {
        runner.run(context -> {
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            SecurityContextHolder.clearContext(); // 无会话、无 SYSTEM_JOB
            AuditedService service = context.getBean(AuditedService.class);
            int before = AuditedService.executions.get();

            // 身份缺失：业务前拒绝（不降 anonymous）
            assertThatThrownBy(() -> service.syncBusiness("no-identity"))
                    .isInstanceOf(IdentityRequiredException.class);
            assertThat(AuditedService.executions.get()).isEqualTo(before); // 业务未执行

            // 无 Actor 归属的记录不落库（不出现 username 为 null/anonymous 的行）
            Integer anonymousRows = jdbc.queryForObject(
                    "SELECT count(*) FROM sys_audit_log WHERE username IS NULL OR username = 'anonymous'",
                    Integer.class);
            assertThat(anonymousRows).isZero();
        });
    }

    @Test
    void businessExceptionIsAuditedAsFailureAndPropagates() {
        runner.run(context -> {
            LoginResult login = loginAs(context.getBean(LocalLoginUseCase.class), "ou_audit_fail");
            String principalId = login.principal().principalId().value();

            assertThatThrownBy(() -> context.getBean(AuditedService.class).failingBusiness())
                    .isInstanceOf(IllegalStateException.class);

            Map<String, Object> row = new JdbcTemplate(context.getBean(DataSource.class)).queryForMap(
                    "SELECT success, error_message, actor_principal_id FROM sys_audit_log"
                            + " WHERE operation = ? AND username = ?",
                    "identity.bridge.failing", principalId);
            assertThat(row.get("success")).isEqualTo(Boolean.FALSE);
            assertThat(row.get("error_message")).isEqualTo("boom");
            assertThat(row.get("actor_principal_id")).isEqualTo(principalId);
        });
    }

    @Test
    void asyncBusinessCapturesActorBeforeAsyncWrite() {
        runner.run(context -> {
            LoginResult login = loginAs(context.getBean(LocalLoginUseCase.class), "ou_audit_async");
            String principalId = login.principal().principalId().value();
            JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));

            context.getBean(AuditedService.class).asyncBusiness();
            // 调用返回即清空调用线程上下文：若 Actor 未在切面线程捕获，异步线程将写不出正确归属
            SecurityContextHolder.clearContext();

            int count = 0;
            for (int i = 0; i < 50 && count == 0; i++) {
                sleepBriefly();
                count = jdbc.queryForObject(
                        "SELECT count(*) FROM sys_audit_log WHERE operation = ? AND username = ?",
                        Integer.class, "identity.bridge.async", principalId);
            }
            assertThat(count).isEqualTo(1); // 异步落库最终出现且归属正确
        });
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void legacyColumnModeUsesLegacyWriterWithoutActorColumns() {
        legacyRunner.run(context -> {
            assertThat(context).hasNotFailed();
            // writer①（仅旧列）被选中：actor-columns=false
            assertThat(context.getBean(com.gallop.fastddd.identity.core.port.IdentityAuditWriter.class))
                    .isInstanceOf(com.gallop.fastddd.identity.pg.audit.PgLegacyIdentityAuditWriter.class);
            assertThat(context.getBeanNamesForType(AuditLogService.class)).hasSize(1);

            LoginResult login = loginAs(context.getBean(LocalLoginUseCase.class), "ou_audit_legacy");
            String principalId = login.principal().principalId().value();
            context.getBean(AuditedService.class).syncBusiness("legacy");

            Map<String, Object> row = new JdbcTemplate(context.getBean(DataSource.class)).queryForMap(
                    "SELECT username, operation_type, method FROM sys_audit_log"
                            + " WHERE operation = ? AND username = ?",
                    "identity.bridge.sync", principalId);
            assertThat(row.get("username")).isEqualTo(principalId);
            assertThat(row.get("operation_type")).isEqualTo("IDENTITY"); // identity writer 落库（非旧 INSERT 列组）
            assertThat(row.get("method")).isEqualTo("identity");
        });
    }
}
