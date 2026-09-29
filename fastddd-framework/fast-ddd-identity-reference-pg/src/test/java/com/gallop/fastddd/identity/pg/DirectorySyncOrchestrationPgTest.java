package com.gallop.fastddd.identity.pg;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3-c 目录同步编排 × 真实 PG 验收测试（W3 设计 v3 §8 矩阵 3/4，层次"PG+桩"）：
 * {@link DirectorySyncUseCase}（真实编排：短事务逐步 + 按扫描进度续租）+
 * {@link PgDirectoryMirrorStore}（真实存储）+ 故障注入桩源（真实源属 W3-b 飞书
 * 适配器；瞬时故障有界重试责任在源适配器，源抛出即已耗尽——§5.2 编排约束）。
 */
class DirectorySyncOrchestrationPgTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-orchestra", "app");

    private DataSource dataSource;
    private PgDirectoryMirrorStore store;
    private IdentityTransactionRunner runner;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        dataSource = Support.dataSource();
        Support.cleanDirectoryTables(dataSource);
        store = new PgDirectoryMirrorStore(new JdbcTemplate(dataSource), Instant::now, Duration.ofMinutes(10));
        runner = Support.runner(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void matrix3And4_pagingFailureAbandonsWholeGenerationAndOldActiveStaysReadable() {
        // 预置活动代：D1/D2 + OU1/OU2 已发布（H=1、facts=4）——失败轮的"旧世界"
        GenerationHandle baseline = publishDirect(
                List.of(new DirectoryDepartment("D1", null, "总部"), new DirectoryDepartment("D2", null, "研发")),
                List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1")),
                        new DirectoryPerson("OU2", "李四", null, List.of("D2"))));
        int factsBefore = Support.count(dataSource, "directory_fact");

        // 矩阵 3：第 N 页 5xx 耗尽——D2 人员页抛 Unavailable → 整代 ABANDONED 后原样抛出
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                new FaultingSource(), store, runner, 10, 100);
        assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class);

        // 整代 ABANDONED、原因稳定码；活动代不变、facts 零变化
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_generation WHERE state='ABANDONED'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT abandon_reason FROM directory_generation WHERE state='ABANDONED'",
                String.class)).isEqualTo("source-unavailable");
        assertThat(activeGeneration()).isEqualTo(baseline.generationId());
        assertThat(Support.count(dataSource, "directory_fact")).isEqualTo(factsBefore);
        assertThat(committedSeq()).isEqualTo(1);
        assertThat(appliedSeq()).isEqualTo(1);
        // 租约已清（abandon 事务释放），下轮调度可立即重新开始
        assertThat(leaseToken()).isNull();

        // 矩阵 4：失败后立即读活动快照 = 旧活动代完整内容（不暴露半套组织）
        ObservedSnapshot observation = store.readObservation(SCOPE);
        assertThat(observation.activeGenerationId()).contains(baseline.generationId());
        assertThat(observation.observedScope().departmentIds()).containsExactly("D1", "D2");
        assertThat(observation.observedScope().rootDepartmentIds()).containsExactly("D1", "D2");
        assertThat(observation.departmentCount()).isEqualTo(2);
        assertThat(observation.personCount()).isEqualTo(2);
        assertThat(observation.committedSeq()).isEqualTo(1);
        assertThat(observation.appliedSeq()).isEqualTo(1);
    }

    @Test
    void fullSyncThroughUseCasePublishesAgainstRealStore() {
        // 真实编排 happy-path：begin → 分页写批（页间续租）→ completePaging →
        // validate → publish，全链真实 PG、零 Outbox（矩阵 26 同面）
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                new HealthySource(), store, runner, 10, 100);
        DirectoryPublication publication = useCase.runFullSync(SCOPE);

        assertThat(publication.generationId()).isEqualTo(activeGeneration());
        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);
        assertThat(publication.factsInserted()).isEqualTo(4);
        assertThat(Support.count(dataSource, "directory_fact")).isEqualTo(4);
        assertThat(store.readObservation(SCOPE).personCount()).isEqualTo(2);
    }

    // ---------------------------------------------------------- 源替身

    /** 健康源：D1/D2 两部门终页；D1 人员 OU1、D2 人员 OU2。 */
    static final class HealthySource implements DirectorySource {
        @Override
        public DirectoryScope scope() {
            return SCOPE;
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            return DirectoryPage.last(List.of(
                    new DirectoryDepartment("D1", null, "总部"),
                    new DirectoryDepartment("D2", null, "研发")));
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            return DirectoryPage.last(List.of(new DirectoryPerson(
                    "D1".equals(departmentId) ? "OU1" : "OU2", "成员", null, List.of(departmentId))));
        }
    }

    /** 故障注入源（矩阵 3）：部门流与 D1 人员页正常，D2 人员页抛 Unavailable（5xx 耗尽形态）。 */
    static final class FaultingSource implements DirectorySource {
        @Override
        public DirectoryScope scope() {
            return SCOPE;
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            return DirectoryPage.last(List.of(
                    new DirectoryDepartment("D1", null, "总部"),
                    new DirectoryDepartment("D2", null, "研发")));
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            if ("D1".equals(departmentId)) {
                return DirectoryPage.last(List.of(
                        new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
            }
            throw new DirectorySourceUnavailableException("http-5xx-exhausted", "注入：第 N 页 5xx 重试耗尽");
        }
    }

    // ---------------------------------------------------------- 助手

    /** 直接经存储建代并发布（预置基线，不经编排）。 */
    private GenerationHandle publishDirect(List<DirectoryDepartment> departments,
                                           List<DirectoryPerson> persons) {
        GenerationHandle handle = runner.inTransaction(() -> store.beginGeneration(SCOPE));
        runner.inTransaction(() -> {
            store.writeBatch(handle,
                    com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofDepartments(departments));
            return null;
        });
        runner.inTransaction(() -> {
            store.writeBatch(handle,
                    com.gallop.fastddd.identity.core.domain.DirectoryBatch.ofPersons(persons));
            return null;
        });
        runner.inTransaction(() -> {
            store.completePaging(handle);
            return null;
        });
        runner.inTransaction(() -> {
            store.validate(handle);
            return null;
        });
        runner.inTransaction(() -> store.publish(handle));
        return handle;
    }

    private String activeGeneration() {
        return jdbc.queryForObject(
                "SELECT active_generation FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private long committedSeq() {
        return jdbc.queryForObject(
                "SELECT committed_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private long appliedSeq() {
        return jdbc.queryForObject(
                "SELECT applied_seq FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                Long.class, SCOPE.tenantKey(), SCOPE.appScope());
    }

    private String leaseToken() {
        return jdbc.queryForObject(
                "SELECT lease_token FROM directory_publish_metadata WHERE tenant_id=? AND app_scope=?",
                String.class, SCOPE.tenantKey(), SCOPE.appScope());
    }
}
