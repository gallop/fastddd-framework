package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.core.usecase.LocalLoginUseCase;
import com.gallop.fastddd.identity.spring.actor.SpringSystemJobContext;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
 *   <li><b>Worker+目录（W3-a 补修 W3A-04）</b>：directory.enabled=true 以
 *       <b>测试范围端口替身</b>满足 fail-closed 端口要求（真实源/存储属
 *       W3-b/W3-c）——目录用例与 Worker 专属调度装配，缺端口拒装校验不放松
 *       （starter 装配测试覆盖）。</li>
 * </ol>
 */
@SpringBootTest(classes = IdentityWorkerConsumerTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityWorkerConsumerTest {

    @SpringBootApplication
    @Import({WorkerSupport.class, WorkerAuditedService.class, WorkerDirectoryDoubles.class})
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

    /**
     * W3A-04：Worker+目录装配面——测试范围合法端口替身满足 fail-closed 端口
     * 要求后，目录用例与 Worker 专属调度（SmartLifecycle）装配且随上下文启动；
     * 目录开不改变 Worker 无登录链形态。缺省间隔 6h：首轮在一个完整间隔之后，
     * 测试窗口零同步回调（替身零调用）。
     */
    @Test
    void workerFormsAssembleDirectoryUseCaseAndScheduler() {
        assertThat(context.getBeanNamesForType(DirectorySyncUseCase.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DirectorySyncScheduler.class)).hasSize(1);
        assertThat(context.getBean(DirectorySyncScheduler.class).isRunning()).isTrue();
        // 目录开不改变 Worker 形态：仍无登录链
        assertThat(context.getBeanNamesForType(LocalLoginUseCase.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SecurityFilterChain.class)).isEmpty();
        // 缺省 6h 间隔——测试窗口零同步回调
        assertThat(context.getBean(WorkerRecordingStore.class).calls).isEmpty();
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

    /** 消费者测试范围目录端口替身（W3A-04；真实源/存储属 W3-b/W3-c，仅测试夹具）。 */
    @Configuration(proxyBeanMethods = false)
    static class WorkerDirectoryDoubles {

        @Bean
        DirectorySource directorySource() {
            return new DirectorySource() {
                @Override
                public DirectoryScope scope() {
                    return new DirectoryScope("worker-consumer", "app");
                }

                @Override
                public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
                    return DirectoryPage.last(List.of(
                            new DirectoryDepartment("WD1", null, "worker-dept-1"),
                            new DirectoryDepartment("WD2", null, "worker-dept-2")));
                }

                @Override
                public DirectoryPage<DirectoryPerson> persons(String departmentId,
                                                              DirectoryPageQuery query) {
                    return "WD1".equals(departmentId)
                            ? DirectoryPage.last(List.of(new DirectoryPerson(
                                    "WOU1", "worker-user", null, List.of("WD1"))))
                            : DirectoryPage.last(List.of());
                }
            };
        }

        @Bean
        WorkerRecordingStore directoryMirrorStore() {
            return new WorkerRecordingStore();
        }
    }

    /** 记录型镜像存储替身（W3A-04）：happy-path 直通，零持久化零外呼。 */
    static class WorkerRecordingStore implements DirectoryMirrorStore {

        final List<String> calls = new ArrayList<>();

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            calls.add("begin");
            return new GenerationHandle(scope, "worker-gen-1", "worker-token");
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            calls.add("renew");
            return new Lease(handle.leaseToken(), Instant.now().plus(Duration.ofMinutes(10)));
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            calls.add("write:" + batch.departments().size() + "d/" + batch.persons().size() + "p");
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            calls.add("completePaging");
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            calls.add("validate");
            return new ValidationReport(handle.generationId(),
                    new ObservedSnapshot.ObservedScope(List.of("WD1", "WD2"), List.of("WD1", "WD2"),
                            true, true),
                    2, 1, 1, List.of("paging-complete"));
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            calls.add("publish");
            return new DirectoryPublication(handle.generationId(), 0, 0, 0, 0);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            calls.add("abandon:" + reason);
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return new ObservedSnapshot("", 0, 0,
                    new ObservedSnapshot.ObservedScope(List.of(), List.of(), false, false),
                    0, 0, List.of());
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return List.of();
        }
    }
}
