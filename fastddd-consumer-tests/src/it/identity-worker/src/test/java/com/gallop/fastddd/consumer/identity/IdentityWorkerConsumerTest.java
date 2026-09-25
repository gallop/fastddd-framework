package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.spring.actor.SpringSystemJobContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 Worker（E 形态）消费者（§4.3 条 5）：非 web 进程显式 worker=true。
 *
 * <ol>
 *   <li><b>装配</b>：Actor 桥/员工 provider/事务桥装配；登录用例、任何过滤链、
 *       默认用户名认证不装配。</li>
 *   <li><b>SYSTEM_JOB 身份</b>：显式作业身份下 CRUD 员工身份返回作业 principalId；
 *       无身份拒绝（不降 system）；线程池任务不继承提交者身份。</li>
 * </ol>
 */
@SpringBootTest(classes = IdentityWorkerConsumerTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityWorkerConsumerTest {

    @SpringBootApplication
    @Import({WorkerSupport.class, WorkerAuditedService.class})
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ActorResolver actorResolver;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Test
    void workerAssemblesBridgeWithoutLoginChain() {
        assertThat(context.containsBean("identityActorResolver")).isTrue();
        assertThat(context.containsBean("springIdentityTransactionRunner")).isTrue();
        assertThat(context.containsBean("identityCurrentUserProvider")).isTrue();
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        assertThat(context.getBeanNamesForType(
                org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
    }

    @Test
    void systemJobIdentityFeedsCrudProviderAndDoesNotLeakToPool() throws Exception {
        // 无身份：拒绝而非降级 system
        assertThatThrownBy(currentUserProvider::getCurrentUserId)
                .isInstanceOf(IdentityRequiredException.class);

        // 显式 SYSTEM_JOB：员工身份 = 作业 principalId
        String id = SpringSystemJobContext.run(PrincipalId.forSystemJob("consumer-sync-job"),
                currentUserProvider::getCurrentUserId);
        assertThat(id).isEqualTo("system-job:consumer-sync-job");

        // 线程池任务不继承提交者的 SYSTEM_JOB 身份（连续任务隔离）
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            SpringSystemJobContext.run(PrincipalId.forSystemJob("pool-owner"), () -> {
                // Supplier 体内不可抛受检异常：Future.get() 包装为非受检
                String inside;
                try {
                    inside = pool.submit(() ->
                            String.valueOf(actorResolver.tryCurrentActor())).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                // 池线程视角：无身份（Optional.empty 的字符串形式）
                assertThat(inside).isEqualTo(String.valueOf(java.util.Optional.empty()));
                return null;
            });
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * W21-04：Worker 后台业务 {@code @AuditLog} 真实贯通（生产切面织入）——
     * SYSTEM_JOB 作业身份经统一 ActorResolver 落 legacy 列（username=作业 principalId，
     * identity writer 词表 operation_type=IDENTITY）；缺失身份时业务前拒绝不降
     * anonymous。
     */
    @Test
    void systemJobBusinessAuditIsWiredThroughSelectedWriter() {
        assertThat(context.getBeanNamesForType(
                com.gallop.fastddd.security.audit.AuditLogService.class)).hasSize(1);
        assertThat(context.containsBean("identityAuditLogService")).isTrue();
        assertThat(context.containsBean("auditLogServiceImpl")).isFalse();

        WorkerAuditedService service = context.getBean(WorkerAuditedService.class);
        WorkerAuditedService.EXECUTIONS.set(0);

        // 缺失身份：业务前拒绝（不降 anonymous、不执行业务）
        assertThatThrownBy(service::syncBusiness)
                .isInstanceOf(IdentityRequiredException.class);
        assertThat(WorkerAuditedService.EXECUTIONS.get()).isZero();

        // SYSTEM_JOB：审计行 username=作业 principalId（legacy 列模式，经 writer①）
        SpringSystemJobContext.run(PrincipalId.forSystemJob("worker-audit-job"),
                service::syncBusiness);
        assertThat(WorkerAuditedService.EXECUTIONS.get()).isEqualTo(1);

        javax.sql.DataSource dataSource = context.getBean(javax.sql.DataSource.class);
        var rows = new org.springframework.jdbc.core.JdbcTemplate(dataSource).queryForList(
                "SELECT username, operation_type, success FROM sys_audit_log"
                        + " WHERE operation = ? AND username = ?",
                "worker.audit.sync", "system-job:worker-audit-job");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("operation_type")).isEqualTo("IDENTITY");
        assertThat(rows.get(0).get("success")).isEqualTo(Boolean.TRUE);
    }

    /** 真实后台业务面（生产 AuditLogAspect 织入，同步落库便于断言）。 */
    @org.springframework.stereotype.Component
    static class WorkerAuditedService {

        // 类级计数器：Bean 被 CGLIB 代理（Objenesis 不跑字段初始化），实例字段在代理上为 null
        static final java.util.concurrent.atomic.AtomicInteger EXECUTIONS =
                new java.util.concurrent.atomic.AtomicInteger();

        @com.gallop.fastddd.security.audit.AuditLog(value = "worker.audit.sync", async = false)
        public String syncBusiness() {
            EXECUTIONS.incrementAndGet();
            return "ok";
        }
    }
}
