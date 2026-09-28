package com.gallop.fastddd.identity.spring;

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
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * W3-a 装配测试替身（<b>仅测试夹具，绝不进入生产自动配置</b>——复评钉 ④）：
 * 功能性源（按 query.pageSize() 切页，用于验证配置页数传导）与记录型镜像存储
 * （happy-path 直通，调用序列可断言）；事务 runner 桩使装配测试无需真实
 * DataSource（springIdentityTransactionRunner 以 @CoM 退让）。
 */
final class DirectoryAssemblyDoubles {

    private DirectoryAssemblyDoubles() {
    }

    /** 事务桩：泛型 SAM 不能用 lambda，匿名类直通（装配测试免真实 DataSource）。 */
    static IdentityTransactionRunner txRunner() {
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class DirectoryPorts {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return txRunner();
        }

        @Bean
        DirectorySource directorySource() {
            return new PagedSource();
        }

        @Bean
        DirectoryMirrorStore directoryMirrorStore() {
            return new RecordingMirrorStore();
        }
    }

    /** 仅目录端口替身（不带事务桩）：用于已提供真实 DataSource/事务桥的场景。 */
    @Configuration(proxyBeanMethods = false)
    static class DirectoryPortsOnly {

        @Bean
        DirectorySource directorySource() {
            return new PagedSource();
        }

        @Bean
        DirectoryMirrorStore directoryMirrorStore() {
            return new RecordingMirrorStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DirectoryPortsWithoutSource {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return txRunner();
        }

        @Bean
        DirectoryMirrorStore directoryMirrorStore() {
            return new RecordingMirrorStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DirectoryPortsWithoutStore {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return txRunner();
        }

        @Bean
        DirectorySource directorySource() {
            return new PagedSource();
        }
    }

    /** 允许一切管理操作的宿主策略桩（仅 admin 正向装配测试；生产无默认放行）。 */
    @Configuration(proxyBeanMethods = false)
    static class AdminPolicyDouble {

        @Bean
        com.gallop.fastddd.identity.core.port.IdentityAdminPolicy identityAdminPolicy() {
            return (operation, actor, targetPrincipalId) -> {
            };
        }
    }

    /**
     * 功能性源替身：固定 3 部门，按 query.pageSize() 切页（页大小传导断言依据）；
     * 人员流恒空页。游标 "c&lt;n&gt;" = 已消费页序号。
     */
    static final class PagedSource implements DirectorySource {

        private final List<DirectoryDepartment> departments = List.of(
                new DirectoryDepartment("D1", null, "dept-1"),
                new DirectoryDepartment("D2", null, "dept-2"),
                new DirectoryDepartment("D3", null, "dept-3"));

        @Override
        public DirectoryScope scope() {
            return new DirectoryScope("tenant", "app");
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            int consumedPages = query.cursor() == null ? 0
                    : Integer.parseInt(query.cursor().substring(1));
            int from = consumedPages * query.pageSize();
            int to = Math.min(from + query.pageSize(), departments.size());
            boolean hasMore = to < departments.size();
            return new DirectoryPage<>(departments.subList(from, to),
                    hasMore ? "c" + (consumedPages + 1) : null, hasMore);
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            return DirectoryPage.last(List.of());
        }
    }

    /** 记录型镜像存储替身：happy-path 直通（begin→write→…→publish），零持久化。 */
    static final class RecordingMirrorStore implements DirectoryMirrorStore {

        final List<String> calls = new ArrayList<>();

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            calls.add("begin");
            return new GenerationHandle(scope, "test-gen-1", "test-token");
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
                    new ObservedSnapshot.ObservedScope(List.of(), List.of(), true, true),
                    0, 0, 0, List.of("paging-complete"));
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
