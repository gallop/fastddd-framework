package com.gallop.fastddd.consumer.identity.directorysync;

import com.gallop.fastddd.consumer.identity.SyncPgSupport;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.pg.PgDirectoryMirrorStore;
import com.gallop.fastddd.identity.spring.directory.DirectoryDiffReporter;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncManualTrigger;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W3-c 目录同步消费者（真实集成门禁）：{@code directory.enabled=true} 下经真实
 * Boot 装配<b>真实 {@link PgDirectoryMirrorStore}</b>（reference-pg，zonky 一次性
 * PG，V1–V3 随 initialize-schema 建表）+ 真实 {@link DirectorySyncUseCase} 编排
 * （starter runner 独立短事务）——完整发布链落真实库，并对
 * directory_fact/directory_generation/directory_publish_metadata 与构建代三镜像做
 * 行级断言；W3-c 组件（手动触发 §9.4 / 差异报表 §6.6）在消费者环境装配并消费
 * 真实存储；未变源二次同步为空差异发布（factsInserted=0、水位不推进、仅活动代
 * 前进，§6.2）。
 *
 * <p>源为本地桩（仅测试夹具，§五 桩边界——真实源属 W3-b feishu 适配器，见
 * identity-directory-feishu 场景）；PG 层存储细节与矩阵 1/2/4/7–18/26/28/29 由
 * reference-pg 测试覆盖，本场景只钉消费者级集成面。矩阵 25（目录关闭登录零回归）
 * 见同场景 {@code IdentityDirectoryOffLoginConsumerTest}。</p>
 */
@SpringBootTest(classes = IdentityDirectorySyncConsumerTest.App.class)
class IdentityDirectorySyncConsumerTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DirectorySyncUseCase directorySyncUseCase;

    @Autowired
    private DirectorySyncManualTrigger manualTrigger;

    @Autowired
    private DirectoryDiffReporter diffReporter;

    @SpringBootApplication
    @Import(SyncPgSupport.class)
    static class App {

        /** 本地桩源（宿主自带源测试替身）：D1 根 + D2 挂 D1，仅 D1 下 1 人员。 */
        @Bean
        DirectorySource directorySource() {
            return new DirectorySource() {
                @Override
                public DirectoryScope scope() {
                    return SyncPgSupport.SCOPE;
                }

                @Override
                public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
                    return DirectoryPage.last(List.of(
                            new DirectoryDepartment("D1", null, "总部"),
                            new DirectoryDepartment("D2", "D1", "研发")));
                }

                @Override
                public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
                    return "D1".equals(departmentId)
                            ? DirectoryPage.last(List.of(new DirectoryPerson(
                                    "OU1", "同步用户", null, List.of("D1"))))
                            : DirectoryPage.last(List.of());
                }
            };
        }
    }

    @BeforeEach
    void cleanDirectoryTables() {
        // 每测试独立水位（无外键约束，单语句截断全部目录镜像表）
        new JdbcTemplate(dataSource).execute(
                "TRUNCATE TABLE directory_fact, directory_relation_mirror,"
                        + " directory_department_mirror, directory_person_mirror,"
                        + " directory_generation, directory_publish_metadata");
    }

    @Test
    void realPgMirrorStoreAndW3cComponentsAssembleInConsumerEnvironment() {
        // 真实参考存储（reference-pg 自动配置；非测试替身）承载目录端口
        assertThat(context.getBean(DirectoryMirrorStore.class)).isInstanceOf(PgDirectoryMirrorStore.class);
        // W3-c 组件：手动触发（§9.4）+ 差异报表（§6.6）装配；web 非 Worker 零调度线程
        assertThat(context.getBeanNamesForType(DirectorySyncUseCase.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DirectorySyncManualTrigger.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DirectoryDiffReporter.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(DirectorySyncScheduler.class)).isEmpty();
    }

    @Test
    void fullSyncPublishesIntoRealPgAndDiffReporterSeesNoDiff() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        DirectoryPublication publication = directorySyncUseCase.runFullSync(SyncPgSupport.SCOPE);
        String generation = publication.generationId();

        // 水位语义（V2-03 冻结口径）：首发布 3 新事实 ⇒ H 0→1，applied=1
        assertThat(publication.factsInserted()).isEqualTo(3);
        assertThat(publication.committedSeqBefore()).isZero();
        assertThat(publication.committedSeqAfter()).isEqualTo(1);
        assertThat(publication.appliedSeq()).isEqualTo(1);

        // 受保护事实层：2 部门 + 1 人员（规则 A 首见；非墓碑/全量扫描/非待核对/首版次）
        List<Map<String, Object>> facts = jdbc.queryForList(
                "SELECT object_type, external_id, tombstone, origin, pending_confirm,"
                        + " local_revision, updated_seq FROM directory_fact"
                        + " ORDER BY object_type, external_id");
        assertThat(facts).hasSize(3);
        assertThat(facts.get(0))
                .containsEntry("object_type", "DEPARTMENT")
                .containsEntry("external_id", "D1")
                .containsEntry("tombstone", false)
                .containsEntry("origin", "FULL_SCAN")
                .containsEntry("pending_confirm", false)
                .containsEntry("local_revision", 1L)
                .containsEntry("updated_seq", 1L);
        assertThat(facts.get(1))
                .containsEntry("object_type", "DEPARTMENT")
                .containsEntry("external_id", "D2");
        assertThat(facts.get(2))
                .containsEntry("object_type", "PERSON")
                .containsEntry("external_id", "OU1");

        // 构建代三镜像（发布后成为活动代 raw）
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_department_mirror WHERE generation_id = ?",
                Integer.class, generation)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_person_mirror WHERE generation_id = ?",
                Integer.class, generation)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_relation_mirror WHERE generation_id = ?",
                Integer.class, generation)).isEqualTo(1);

        // 发布元数据：活动代指针 + 事实线水位
        Map<String, Object> metadata = jdbc.queryForMap(
                "SELECT active_generation, committed_seq, applied_seq FROM directory_publish_metadata"
                        + " WHERE tenant_id = ? AND app_scope = ?",
                "consumer-sync", "sync-app");
        assertThat(metadata)
                .containsEntry("active_generation", generation)
                .containsEntry("committed_seq", 1L)
                .containsEntry("applied_seq", 1L);

        // 代行：T7 幂等发布全等返回的持久化依据（pub_* 四列）
        Map<String, Object> generationRow = jdbc.queryForMap(
                "SELECT state, pub_committed_before, pub_committed_after, pub_applied_seq,"
                        + " pub_facts_inserted FROM directory_generation WHERE id = ?", generation);
        assertThat(generationRow)
                .containsEntry("state", "PUBLISHED")
                .containsEntry("pub_committed_before", 0L)
                .containsEntry("pub_committed_after", 1L)
                .containsEntry("pub_applied_seq", 1L)
                .containsEntry("pub_facts_inserted", 3L);

        // 差异报表（§6.6 管理视图）消费真实存储：观察=事实 ⇒ 全空差集
        DirectoryDiffReporter.DirectoryDiffReport report = diffReporter.report(SyncPgSupport.SCOPE);
        assertThat(report.observation().activeGenerationId()).contains(generation);
        assertThat(report.observation().committedSeq()).isEqualTo(1);
        assertThat(report.observation().appliedSeq()).isEqualTo(1);
        assertThat(report.observation().departmentCount()).isEqualTo(2);
        assertThat(report.observation().personCount()).isEqualTo(1);
        assertThat(report.observation().observedScope().departmentIds())
                .containsExactlyInAnyOrder("D1", "D2");
        assertThat(report.observation().scopeSuspicions()).isEmpty();
        assertThat(report.departmentsObservedNotInFacts()).isEmpty();
        assertThat(report.departmentFactsNotObserved()).isEmpty();
        assertThat(report.factPersonCount()).isEqualTo(1);
        assertThat(report.personCountDelta()).isZero();
        assertThat(report.pendingConfirmations()).isEmpty();
    }

    @Test
    void secondSyncOverUnchangedSourceIsEmptyDiffPublishAndManualTriggerOpsEntryWorks() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DirectoryPublication first = directorySyncUseCase.runFullSync(SyncPgSupport.SCOPE);
        assertThat(first.factsInserted()).isEqualTo(3);
        assertThat(first.committedSeqAfter()).isEqualTo(1);

        // 运维入口（§9.4）：无 IdentityAdminPolicy bean（admin.enabled 缺省 false）
        // 也可执行——策略不进核心用例，交互式入口的裁决属另一方法（单元测试覆盖）
        assertThat(context.getBeanNamesForType(IdentityAdminPolicy.class)).isEmpty();
        DirectorySyncManualTrigger.ManualSyncResult result = manualTrigger.runFullSync();
        assertThat(result.outcome())
                .isEqualTo(DirectorySyncManualTrigger.ManualSyncResult.OUTCOME_COMPLETED);
        assertThat(result.isInProgress()).isFalse();
        DirectoryPublication second = result.publication();
        assertThat(second).isNotNull();
        assertThat(second.generationId()).isNotEqualTo(first.generationId());

        // 空差异发布：零新事实、水位不推进（H_after=H_before）、仅活动代前进（§6.2）
        assertThat(second.factsInserted()).isZero();
        assertThat(second.committedSeqBefore()).isEqualTo(1);
        assertThat(second.committedSeqAfter()).isEqualTo(1);
        assertThat(second.appliedSeq()).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_fact", Integer.class)).isEqualTo(3);
        Map<String, Object> metadata = jdbc.queryForMap(
                "SELECT active_generation, committed_seq, applied_seq FROM directory_publish_metadata"
                        + " WHERE tenant_id = ? AND app_scope = ?", "consumer-sync", "sync-app");
        assertThat(metadata)
                .containsEntry("active_generation", second.generationId())
                .containsEntry("committed_seq", 1L)
                .containsEntry("applied_seq", 1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM directory_generation WHERE state = 'PUBLISHED'",
                Integer.class)).isEqualTo(2);
    }
}
