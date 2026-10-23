package com.gallop.fastddd.identity.spring;

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
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.directory.IdentityDirectoryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3A-01 事务边界复评反例与验收（W3-a 实现独立复评意见）：真实 Spring 事务
 * 设施（无业务库——no-op {@link AbstractPlatformTransactionManager} + 真实
 * TransactionTemplate/TransactionSynchronizationManager）下验证目录编排的唯一
 * 事务所有者：每个存储步骤各自独立短事务（可加入 MANDATORY 写存储）、所有
 * 源调用一律在事务外、宿主已有事务在任何源访问/写入前按 runner 契约拒绝、
 * 失败清理走独立短事务且不掩盖原始异常。
 *
 * <p>旧实现（直接调用 store、无 runner）下：MANDATORY 存储在 begin 即抛
 * IllegalTransactionStateException；宿主事务内调用则源读取观察到
 * transactionActive=true。均与本测试断言冲突——先红后修。PG 同数据源真实
 * 回滚仍属 W3-c，本测试不主张。</p>
 */
class DirectoryTransactionBoundaryTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tx-boundary-tenant", "app");

    /** 真实 Spring 事务设施（无业务库）：空提交/回滚，统计 begin/commit 次数。 */
    private static final CountingNoOpTransactionManager TX = new CountingNoOpTransactionManager();

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentityModeGuardAutoConfiguration.class,
                    IdentityAutoConfiguration.class,
                    IdentityDirectoryAutoConfiguration.class));

    @org.junit.jupiter.api.BeforeEach
    void resetTransactionCounts() {
        TX.begins.set(0);
        TX.commits.set(0);
    }

    // ------------------------------------------------------------- 测试替身

    /** 生产同型 runner：入口已有活动事务即拒绝 + REQUIRED 短事务语义（无 DataSource）。 */
    static IdentityTransactionRunner rejectingRunner() {
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
                if (TransactionSynchronizationManager.isActualTransactionActive()) {
                    throw new IdentityTransactionActiveException(
                            "入口已有活动事务，identity runner 拒绝加入（T2 须独立事务）");
                }
                TransactionTemplate template = new TransactionTemplate(TX);
                return template.execute(status -> callback.doInTransaction());
            }
        };
    }

    /** 源替身：每次调用记录线程上是否处于活动事务（期望恒为 false）。 */
    static final class ObservingSource implements DirectorySource {
        final List<Boolean> observations = new ArrayList<>();
        boolean failPersons;

        @Override
        public DirectoryScope scope() {
            return SCOPE;
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            observations.add(TransactionSynchronizationManager.isActualTransactionActive());
            return DirectoryPage.last(List.of(
                    new DirectoryDepartment("D1", null, "部门一"),
                    new DirectoryDepartment("D2", null, "部门二")));
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            observations.add(TransactionSynchronizationManager.isActualTransactionActive());
            if (failPersons) {
                throw new DirectorySourceUnavailableException("transport", "人员页故障注入");
            }
            return DirectoryPage.last(List.of());
        }
    }

    /** 存储步骤观测记录。 */
    private record StoreStep(String name, boolean activeTransaction) {
    }

    /**
     * MANDATORY 写存储替身：每个方法先记录观测（方法名 + 当时是否有活动事务），
     * 再以 PROPAGATION_MANDATORY 加入事务——无活动事务即抛
     * IllegalTransactionStateException（复评探针同构）。这正是 PG 适配器
     * （W3-c）对写方法的事务契约。
     */
    static final class MandatoryStore implements DirectoryMirrorStore {
        final List<StoreStep> steps = new ArrayList<>();

        private void joinMandatory(String name) {
            steps.add(new StoreStep(name, TransactionSynchronizationManager.isActualTransactionActive()));
            TransactionTemplate template = new TransactionTemplate(TX);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_MANDATORY);
            template.executeWithoutResult(status -> {
            });
        }

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            joinMandatory("begin");
            return new GenerationHandle(scope, "g-tx-boundary", "token-tx");
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            joinMandatory("renew");
            return new Lease(handle.leaseToken(), Instant.now().plusSeconds(600));
        }

        @Override
        public void writeBatch(GenerationHandle handle, com.gallop.fastddd.identity.core.domain.DirectoryBatch batch) {
            joinMandatory("write");
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            joinMandatory("completePaging");
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            joinMandatory("validate");
            // 编排用例只消费校验结果的存在性/异常，报告内容不参与本轮断言
            return null;
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            joinMandatory("publish");
            return new DirectoryPublication(handle.generationId(), 0, 1, 1, 2);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            joinMandatory("abandon");
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return null;
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return List.of();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionBoundaryPorts {

        @Bean
        IdentityTransactionRunner identityTransactionRunner() {
            return rejectingRunner();
        }

        @Bean
        ObservingSource directorySource() {
            return new ObservingSource();
        }

        @Bean
        MandatoryStore directoryMirrorStore() {
            return new MandatoryStore();
        }
    }

    // ----------------------------------------------------------------- 反例

    @Test
    void everyStoreStepJoinsIndependentShortTransaction_andSourceStaysOutside() {
        runner
                .withUserConfiguration(TransactionBoundaryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ObservingSource source = context.getBean(ObservingSource.class);
                    MandatoryStore store = context.getBean(MandatoryStore.class);

                    DirectoryPublication publication =
                            context.getBean(DirectorySyncUseCase.class).runFullSync(SCOPE);

                    assertThat(publication.generationId()).isEqualTo("g-tx-boundary");
                    // 每个存储步骤都观察到活动事务（MANDATORY 加入成功）
                    assertThat(store.steps).isNotEmpty();
                    assertThat(store.steps).allSatisfy(step ->
                            assertThat(step.activeTransaction()).isTrue());
                    // 每步骤一个独立短事务：begin/commit 次数与存储步骤数一致（无嵌套、无长事务）
                    assertThat(TX.begins.get()).isEqualTo(store.steps.size());
                    assertThat(TX.commits.get()).isEqualTo(store.steps.size());
                    // 所有源调用一律无事务
                    assertThat(source.observations).isNotEmpty();
                    assertThat(source.observations).allSatisfy(active -> assertThat(active).isFalse());
                });
    }

    @Test
    void hostTransactionRejectedBeforeAnySourceAccessOrWrite() {
        runner
                .withUserConfiguration(TransactionBoundaryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DirectorySyncUseCase useCase = context.getBean(DirectorySyncUseCase.class);
                    ObservingSource source = context.getBean(ObservingSource.class);
                    MandatoryStore store = context.getBean(MandatoryStore.class);

                    // 宿主已有事务内调用：按 runner 契约在首个存储步骤（begin，先于任何
                    // 源访问/写入）拒绝——不能把分页与远程调用包进宿主长事务
                    TransactionTemplate hostTx = new TransactionTemplate(TX);
                    hostTx.executeWithoutResult(status ->
                            assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                                    .isInstanceOf(IdentityTransactionActiveException.class));

                    assertThat(source.observations).isEmpty();
                    assertThat(store.steps).isEmpty();
                });
    }

    @Test
    void failureCleanupRunsInOwnShortTransaction_withoutMaskingOriginal() {
        runner
                .withUserConfiguration(TransactionBoundaryPorts.class)
                .withPropertyValues("fastddd.identity.enabled=true",
                        "fastddd.identity.directory.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DirectorySyncUseCase useCase = context.getBean(DirectorySyncUseCase.class);
                    ObservingSource source = context.getBean(ObservingSource.class);
                    MandatoryStore store = context.getBean(MandatoryStore.class);
                    source.failPersons = true;

                    assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                            .isInstanceOf(DirectorySourceUnavailableException.class);

                    // abandon 也在自己的独立短事务中执行（观察到活动事务），原始异常不被掩盖
                    assertThat(store.steps).anySatisfy(step -> {
                        assertThat(step.name()).isEqualTo("abandon");
                        assertThat(step.activeTransaction()).isTrue();
                    });
                });
    }

    /** 空实现事务管理器：真实 TransactionTemplate/同步语义，仅计数 begin/commit。 */
    private static final class CountingNoOpTransactionManager extends AbstractPlatformTransactionManager {

        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins.incrementAndGet();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits.incrementAndGet();
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // 空实现：无业务库，回滚仅为协议演练
        }
    }
}
