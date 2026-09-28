package com.gallop.fastddd.identity.core.directory;

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
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.error.IdentityTransactionActiveException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.person;
import static com.gallop.fastddd.identity.core.directory.DirectoryTestDsl.root;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3A-01 事务所有者测试（core 层，端口抽象、零 Spring）：编排用例把<b>每个</b>
 * 存储方法经 {@link IdentityTransactionRunner} 各自独立短事务执行（可嵌套检测：
 * 一次回调内再次进入 = 违反），源调用一律在回调外；宿主已有事务（模拟）在
 * 首个存储步骤即被拒绝，先于任何源访问/写入；失败清理 abandon 也走自己的
 * 短事务回调。真实 Spring 事务设施下的同语义验证见 starter 的
 * {@code DirectoryTransactionBoundaryTest}。
 */
class DirectoryTransactionOwnershipTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tx-owner-tenant", "app");

    /** 步骤观测（存储方法名 + 调用时是否处于 runner 回调内）。 */
    private record Step(String name, boolean inCallback) {
    }

    /** 模拟事务 runner：回调内 inCallback=true；嵌套进入即违约记录；宿主事务模拟。 */
    static final class SimulatingRunner implements IdentityTransactionRunner {
        private final AtomicReference<Throwable> nestingViolation = new AtomicReference<>();
        private int depth;
        private int callbacks;
        private boolean hostTransaction;

        @Override
        public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
            if (hostTransaction) {
                throw new IdentityTransactionActiveException(
                        "入口已有活动事务，identity runner 拒绝加入（T2 须独立事务）");
            }
            if (depth > 0) {
                nestingViolation.compareAndSet(null, new IllegalStateException(
                        "禁止嵌套：每个存储步骤必须独立短事务（depth=" + depth + "）"));
            }
            depth++;
            callbacks++;
            try {
                return callback.doInTransaction();
            } finally {
                depth--;
            }
        }

        boolean inCallback() {
            return depth > 0;
        }

        int callbacks() {
            return callbacks;
        }

        Throwable nestingViolation() {
            return nestingViolation.get();
        }
    }

    /** 源观测包装：每次远程调用记录是否处于 runner 回调内。 */
    private static final class ObservingSource implements DirectorySource {
        private final ScriptedDirectorySource delegate;
        private final SimulatingRunner runner;
        private final List<Boolean> observations = new ArrayList<>();

        ObservingSource(ScriptedDirectorySource delegate, SimulatingRunner runner) {
            this.delegate = delegate;
            this.runner = runner;
        }

        @Override
        public DirectoryScope scope() {
            return delegate.scope();
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            observations.add(runner.inCallback());
            return delegate.departments(query);
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            observations.add(runner.inCallback());
            return delegate.persons(departmentId, query);
        }
    }

    /** 存储观测包装：每个方法调用记录是否处于 runner 回调内。 */
    private static final class ObservingStore implements DirectoryMirrorStore {
        private final InMemoryDirectoryMirrorStore delegate;
        private final SimulatingRunner runner;
        private final List<Step> steps = new ArrayList<>();

        ObservingStore(InMemoryDirectoryMirrorStore delegate, SimulatingRunner runner) {
            this.delegate = delegate;
            this.runner = runner;
        }

        private void observe(String name) {
            steps.add(new Step(name, runner.inCallback()));
        }

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            observe("begin");
            return delegate.beginGeneration(scope);
        }

        @Override
        public Lease renewLease(GenerationHandle handle) {
            observe("renew");
            return delegate.renewLease(handle);
        }

        @Override
        public void writeBatch(GenerationHandle handle, DirectoryBatch batch) {
            observe("write");
            delegate.writeBatch(handle, batch);
        }

        @Override
        public void completePaging(GenerationHandle handle) {
            observe("completePaging");
            delegate.completePaging(handle);
        }

        @Override
        public ValidationReport validate(GenerationHandle handle) {
            observe("validate");
            return delegate.validate(handle);
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            observe("publish");
            return delegate.publish(handle);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            observe("abandon");
            delegate.abandon(handle, reason);
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return delegate.readObservation(scope);
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return delegate.readProtectedFacts(scope);
        }
    }

    @Test
    void everyStoreStepRunsInOwnCallbackAndSourceStaysOutside() {
        SimulatingRunner runner = new SimulatingRunner();
        ObservingStore store = new ObservingStore(
                new InMemoryDirectoryMirrorStore(Duration.ofMinutes(10)), runner);
        ObservingSource source = new ObservingSource(scripted(false), runner);

        DirectoryPublication publication =
                new DirectorySyncUseCase(source, store, runner, 100, 100).runFullSync(SCOPE);

        assertThat(publication.factsInserted()).isEqualTo(2);
        // 每个存储步骤都在 runner 回调内执行
        assertThat(store.steps).isNotEmpty();
        assertThat(store.steps).allSatisfy(step -> assertThat(step.inCallback()).isTrue());
        // 一次回调 = 一个存储步骤（无嵌套、无合并长事务）
        assertThat(runner.callbacks()).isEqualTo(store.steps.size());
        assertThat(runner.nestingViolation()).isNull();
        // 源调用一律在回调外（事务外）
        assertThat(source.observations).isNotEmpty();
        assertThat(source.observations).allSatisfy(inCallback -> assertThat(inCallback).isFalse());
    }

    @Test
    void hostTransactionRejectedBeforeAnySourceAccessOrWrite() {
        SimulatingRunner runner = new SimulatingRunner();
        ObservingStore store = new ObservingStore(
                new InMemoryDirectoryMirrorStore(Duration.ofMinutes(10)), runner);
        ObservingSource source = new ObservingSource(scripted(false), runner);
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, store, runner, 100, 100);

        runner.hostTransaction = true;
        assertThatThrownBy(() -> useCase.runFullSync(SCOPE))
                .isInstanceOf(IdentityTransactionActiveException.class);

        // 拒绝发生在首个存储步骤（begin）：零源访问、零写入、零回调执行
        assertThat(source.observations).isEmpty();
        assertThat(store.steps).isEmpty();
        assertThat(runner.callbacks()).isZero();
    }

    @Test
    void failureCleanupRunsInOwnCallbackWithoutMaskingOriginal() {
        SimulatingRunner runner = new SimulatingRunner();
        ObservingStore store = new ObservingStore(
                new InMemoryDirectoryMirrorStore(Duration.ofMinutes(10)), runner);
        ObservingSource source = new ObservingSource(scripted(true), runner);

        assertThatThrownBy(() ->
                new DirectorySyncUseCase(source, store, runner, 100, 100).runFullSync(SCOPE))
                .isInstanceOf(DirectorySourceUnavailableException.class);

        // abandon 也在自己的独立回调中执行（短事务），不掩盖原始异常
        assertThat(store.steps).anySatisfy(step -> {
            assertThat(step.name()).isEqualTo("abandon");
            assertThat(step.inCallback()).isTrue();
        });
        assertThat(runner.nestingViolation()).isNull();
    }

    private static ScriptedDirectorySource scripted(boolean failPersons) {
        ScriptedDirectorySource source = new ScriptedDirectorySource(SCOPE,
                List.of(root("D1")), Map.of("D1", List.of(person("P1", "D1"))));
        if (failPersons) {
            source.personsHook = dept -> {
                throw new DirectorySourceUnavailableException("transport", "人员页故障注入");
            };
        }
        return source;
    }
}
