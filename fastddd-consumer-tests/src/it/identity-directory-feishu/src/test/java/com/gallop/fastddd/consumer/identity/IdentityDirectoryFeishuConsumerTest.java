package com.gallop.fastddd.consumer.identity;

import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.feishu.FeishuDirectorySource;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-b 飞书目录源衔接消费者：真实 {@link FeishuDirectorySource} 经 Boot
 * 自动配置顺序（AutoConfiguration.imports → feishu 先于 starter 目录装配）
 * 与 W3-a 衔接——{@code DirectorySyncUseCase} 以真实源装配、fail-closed 守卫放行，
 * 同步编排经真实 HTTP（本地桩）完整走通：合成根 + BFS 部门流 + 人员流。
 *
 * <p>边界（W3 设计）：存储用<b>测试范围替身</b>（真实 PG 目录存储属 W3-c，
 * 生产装配不塞默认替身）；web 非 Worker 形态零调度线程；真实飞书环境不可用，
 * 端点由本地桩按官方契约形态提供（W0-4 2026-09-28 复核口径）。</p>
 *
 * <p>W5-g（M2 候选消费）起两处补齐：①change_log 端口 W5-e 起恒接线，pg 适配器
 * {@code PgDirectoryChangeLog} 对本场景 H2 空库（无 V4 表）不可用——同以测试
 * 范围替身承载；②feishu 源使 {@code DirectoryRecheckQuery} 端口在座 →
 * {@code DirectoryRecheckManualTrigger} 装配，其 W5-f fail-loud 契约要求
 * {@code ExternalRejectionGate}（pg 适配器恒提供）在场时拒绝行 provider 非空
 * ——按真实消费者契约配置 {@code rejection-provider: feishu}（application.yml）。</p>
 */
@SpringBootTest(classes = IdentityDirectoryFeishuConsumerTest.App.class)
class IdentityDirectoryFeishuConsumerTest {

    /** 本地飞书桩：根(0) → od-a；od-a 无子级；两部门各 1 人（根直属 ou_root、od-a 的 ou_dev）。 */
    private static final FeishuDirectoryStub STUB = new FeishuDirectoryStub();

    @DynamicPropertySource
    static void feishuDirectoryEndpoints(DynamicPropertyRegistry registry) {
        registry.add("fastddd.identity.feishu.directory.api-base-url", STUB::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @SpringBootApplication
    @Import(RecordingStoreDouble.class)
    static class App {
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DirectorySyncUseCase directorySyncUseCase;

    /** 真实飞书源经自动配置衔接装配（宿主未提供 DirectorySource → feishu 不退让）。 */
    @Test
    void realFeishuSourceAssemblesAndWiresIntoDirectoryUseCase() {
        assertThat(context.getBeanNamesForType(DirectorySource.class)).hasSize(1);
        assertThat(context.getBean(DirectorySource.class)).isInstanceOf(FeishuDirectorySource.class);
        // W3-a 衔接：真实源可见于 starter 目录装配（@ConditionalOnBean(DirectorySource) 成立）
        assertThat(context.getBeanNamesForType(DirectorySyncUseCase.class)).hasSize(1);
        // web 非 Worker：零调度线程；M1：无 publisher Bean
        assertThat(context.getBeanNamesForType(DirectorySyncScheduler.class)).isEmpty();
        assertThat(context.getBeanNamesForType(IdentityChangePublisher.class)).isEmpty();
        // 范围维度来自配置（tenant_key=expected-tenant-key，app_scope=app-id）
        assertThat(context.getBean(DirectorySource.class).scope())
                .isEqualTo(new DirectoryScope("tenant-dir-1", "cli-dir-app"));
    }

    /** 经真实源 + 本地桩完整走一轮：合成根 → BFS 部门两页 + 空终页 → 按部门人员流。 */
    @Test
    void fullSyncRunsThroughRealFeishuSourceAgainstLocalStub() {
        RecordingStore store = context.getBean(RecordingStore.class);

        DirectoryPublication publication = directorySyncUseCase.runFullSync(
                context.getBean(DirectorySource.class).scope());

        assertThat(publication.generationId()).isEqualTo("feishu-gen-1");
        // 页序列：合成根页(1d) → children(0) 页(1d) → children(od-a) 空终页 →
        // persons(0) 1 人 → persons(od-a) 1 人；除全局首次外每次源调用前续租，
        // 末页转 completePaging 前续租（W3A-03 口径）
        assertThat(store.calls).containsExactly(
                "begin",
                "write:1d/0p",
                "renew", "write:1d/0p",
                "renew", "write:0d/0p",
                "renew", "write:0d/1p",
                "renew", "write:0d/1p",
                "renew", "completePaging", "validate", "publish");
        // 桩侧命中：children 两击（0、od-a）、persons 两击、tenant token 一次（缓存）
        assertThat(STUB.childrenHits.get()).isEqualTo(2);
        assertThat(STUB.personsHits.get()).isEqualTo(2);
        assertThat(STUB.tenantTokenHits.get()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 本地飞书桩（仅测试夹具；与 feishu 模块测试桩同形态，按官方契约响应）
    // ------------------------------------------------------------------

    static final class FeishuDirectoryStub implements AutoCloseable {

        final AtomicInteger tenantTokenHits = new AtomicInteger();

        final AtomicInteger childrenHits = new AtomicInteger();

        final AtomicInteger personsHits = new AtomicInteger();

        private final HttpServer server;

        FeishuDirectoryStub() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException("本地飞书桩启动失败", e);
            }
            server.createContext("/open-apis/auth/v3/tenant_access_token/internal", exchange -> {
                tenantTokenHits.incrementAndGet();
                respond(exchange, 200, "{\"code\":0,\"expire\":7200,"
                        + "\"tenant_access_token\":\"t-stub-tenant-token\"}");
            });
            server.createContext("/open-apis/contact/v3/departments", exchange -> {
                childrenHits.incrementAndGet();
                if (exchange.getRequestURI().getPath().endsWith("/departments/0/children")) {
                    respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false,"
                            + "\"items\":[{\"open_department_id\":\"od-a\",\"parent_department_id\":\"0\","
                            + "\"name\":\"研发部\"}]}}");
                } else {
                    respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false,"
                            + "\"items\":[]}}");
                }
            });
            server.createContext("/open-apis/contact/v3/users/find_by_department", exchange -> {
                personsHits.incrementAndGet();
                String user = exchange.getRequestURI().getRawQuery() != null
                        && exchange.getRequestURI().getRawQuery().contains("department_id=0")
                        ? "{\"open_id\":\"ou_root\",\"name\":\"根直属\",\"en_name\":\"Root\","
                                + "\"department_ids\":[\"0\"]}"
                        : "{\"open_id\":\"ou_dev\",\"name\":\"研发同学\",\"en_name\":\"Dev\","
                                + "\"department_ids\":[\"od-a\"]}";
                respond(exchange, 200, "{\"code\":0,\"msg\":\"ok\",\"data\":{\"has_more\":false,"
                        + "\"items\":[" + user + "]}}");
            });
            server.setExecutor(Executors.newFixedThreadPool(4));
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    // ------------------------------------------------------------------
    // 测试范围镜像存储替身（真实 PG 目录存储属 W3-c；生产装配不塞默认替身）
    // ------------------------------------------------------------------

    /** 消费者测试范围存储替身：happy-path 直通 + 记录契约序列。 */
    @Configuration(proxyBeanMethods = false)
    static class RecordingStoreDouble {

        @Bean
        RecordingStore recordingStore() {
            return new RecordingStore();
        }

        /** change_log 测试范围替身：H2 空库无 V4 表，pg 适配器退让（{@code @ConditionalOnMissingBean}）。 */
        @Bean
        DirectoryChangeLog directoryChangeLog() {
            return new DirectoryChangeLog() {
                @Override
                public String append(ChangeLogEntry entry) {
                    return entry.id();
                }

                @Override
                public boolean hasGenerationActivation(DirectoryScope scope, String generationId) {
                    return false;
                }
            };
        }
    }

    static class RecordingStore implements DirectoryMirrorStore {

        final List<String> calls = new ArrayList<>();

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            calls.add("begin");
            return new GenerationHandle(scope, "feishu-gen-1", "feishu-token");
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
                    new ObservedSnapshot.ObservedScope(List.of("0", "od-a"), List.of("0", "od-a"),
                            true, true),
                    2, 2, 2, List.of("paging-complete", "relations-resolved"));
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
