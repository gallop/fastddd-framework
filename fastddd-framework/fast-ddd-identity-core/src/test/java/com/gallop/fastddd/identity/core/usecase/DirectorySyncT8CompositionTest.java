package com.gallop.fastddd.identity.core.usecase;

import com.gallop.fastddd.identity.core.directory.InMemoryDirectoryMirrorStore;
import com.gallop.fastddd.identity.core.directory.ScriptedDirectorySource;
import com.gallop.fastddd.identity.core.domain.ChangeLogEntry;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryGenerationActivatedEvent;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.Ulids;
import com.gallop.fastddd.identity.core.port.DirectoryChangeLog;
import com.gallop.fastddd.identity.core.port.IdentityChangePublisher;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W5-b T8 组合测试（W5 设计 §5.2/§12 ⑤；编排以测试替身验证——真实事务
 * 回滚/Outbox 语义归 starter 集成（W5-e），本件钉组合顺序与判定面）：
 * M1 形态零行为、全量同步 append+事件、同代重发布 no-op（B-5）、
 * publisher 失败异常传播且 append 不留、无 publisher 只 append、
 * publisher 无 changeLog 构造拒绝。
 */
class DirectorySyncT8CompositionTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-a", "app-1");

    /** 直通事务 runner（同 DirectorySyncUseCaseTest：core 层验编排，事务所有者归 starter）。 */
    private static final IdentityTransactionRunner DIRECT_TX = new IdentityTransactionRunner() {
        @Override
        public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
            return callback.doInTransaction();
        }
    };

    private final InMemoryDirectoryMirrorStore store = new InMemoryDirectoryMirrorStore(Duration.ofMinutes(5));
    private final InMemoryChangeLog changeLog = new InMemoryChangeLog();
    private final RecordingPublisher publisher = new RecordingPublisher();

    private ScriptedDirectorySource simpleSource() {
        return new ScriptedDirectorySource(SCOPE,
                List.of(new DirectoryDepartment("D1", null, "dept-D1")),
                Map.of("D1", List.of(new DirectoryPerson("P1", "name-P1", null, List.of("D1")))));
    }

    /** 直驱一途至 VALIDATED：begin → 写批（根部门+人员）→ completePaging → validate。 */
    private GenerationHandle driveToValidated() {
        GenerationHandle handle = store.beginGeneration(SCOPE);
        store.writeBatch(handle, new DirectoryBatch(
                List.of(new DirectoryDepartment("D1", null, "dept-D1")),
                List.of(new DirectoryPerson("P1", "name-P1", null, List.of("D1")))));
        store.completePaging(handle);
        store.validate(handle);
        return handle;
    }

    // -------------------------------------------------------- M1 形态（兼容钉）

    @Test
    void m1FormFullSyncStillPublishesWithoutLogOrEvent() {
        // 5 参构造委托 (null, null)：不 append 不发事件（W3 行为面由 W3-a 套件覆盖，
        // 本件钉委托路径端到端可用——既有装配零改动零新增依赖）
        DirectorySyncUseCase m1 = new DirectorySyncUseCase(simpleSource(), store, DIRECT_TX, 100, 100);

        DirectoryPublication publication = m1.runFullSync(SCOPE);

        assertThat(publication).isNotNull();
        assertThat(publication.factsInserted()).isPositive();
        assertThat(store.activeGenerationId(SCOPE)).isEqualTo(publication.generationId());
    }

    @Test
    void publisherWithoutChangeLogIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new DirectorySyncUseCase(
                simpleSource(), store, DIRECT_TX, 100, 100, null, publisher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changeLogRef");
    }

    // -------------------------------------------------------- T8 组合正路径

    @Test
    void fullSyncAppendsGenerationActivatedEntryAndPublishesEvent() {
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                simpleSource(), store, DIRECT_TX, 100, 100, changeLog, publisher);

        DirectoryPublication publication = useCase.runFullSync(SCOPE);

        assertThat(changeLog.entries).hasSize(1);
        ChangeLogEntry entry = changeLog.entries.get(0);
        assertThat(entry.kind()).isEqualTo(ChangeLogEntry.Kind.GENERATION_ACTIVATED);
        assertThat(entry.scope()).isEqualTo(SCOPE);
        assertThat(Ulids.isValid(entry.id())).as("条目 id 必须为预铸 ULID").isTrue();
        assertThat(entry.generationId()).isEqualTo(store.activeGenerationId(SCOPE));
        assertThat(entry.committedSeq()).isEqualTo(publication.committedSeqAfter());
        assertThat(entry.summary())
                .contains("\"kind\":\"GENERATION_ACTIVATED\"")
                .contains("\"committedSeqAfter\":" + publication.committedSeqAfter())
                .contains("\"factsInserted\":" + publication.factsInserted());

        assertThat(publisher.events).hasSize(1);
        DirectoryGenerationActivatedEvent event = publisher.events.get(0);
        assertThat(event.eventId()).isEqualTo(
                DirectoryGenerationActivatedEvent.eventIdFor(SCOPE, entry.generationId()));
        assertThat(event.changeLogRef()).isEqualTo(entry.id());
        assertThat(event.committedSeq()).isEqualTo(publication.committedSeqAfter());
        assertThat(event.generationId()).isEqualTo(entry.generationId());
    }

    @Test
    void secondPublishOfSameGenerationIsNoOp() {
        // B-5 no-op 判据 = hasGenerationActivation（同 scope 同代已有 GENERATION_ACTIVATED
        // 行）；runFullSync 每轮新开代，故直驱同 handle 二次发布验证。factsInserted=0
        // 非判据（A-9）——本用例首发 factsInserted>0 仍走 append，判据只看载体行。
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                simpleSource(), store, DIRECT_TX, 100, 100, changeLog, publisher);
        GenerationHandle handle = driveToValidated();

        DirectoryPublication first = useCase.publishWithChangeLog(handle);
        assertThat(changeLog.entries).hasSize(1);
        assertThat(publisher.events).hasSize(1);

        DirectoryPublication second = useCase.publishWithChangeLog(handle);
        assertThat(second).isEqualTo(first);
        assertThat(changeLog.entries).as("no-op 不得再 append").hasSize(1);
        assertThat(publisher.events).as("no-op 不得再发事件").hasSize(1);
    }

    @Test
    void changeLogWithoutPublisherAppendsButPublishesNothing() {
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                simpleSource(), store, DIRECT_TX, 100, 100, changeLog, null);
        GenerationHandle handle = driveToValidated();

        DirectoryPublication publication = useCase.publishWithChangeLog(handle);

        assertThat(publication).isNotNull();
        assertThat(changeLog.entries).hasSize(1);
        // publisher 为 null：append 正常、零事件路径（通知关闭形态——装配矩阵格）
    }

    // -------------------------------------------------------- T8 失败路径

    @Test
    void publisherFailurePropagatesAndAppendDoesNotSurviveRollback() {
        // 先库后信：信失败 → 异常传播（真实整体回滚由事务设施承载，core 以
        // 回滚模拟 runner 验证「失败事务内 append 不留」的编排语义）
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(
                simpleSource(), store, DIRECT_TX, 100, 100, changeLog, publisher);
        GenerationHandle handle = driveToValidated();
        publisher.fail = true;
        IdentityTransactionRunner rollbackTx = new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
                try {
                    return callback.doInTransaction();
                } catch (RuntimeException e) {
                    changeLog.clear();
                    throw e;
                }
            }
        };
        DirectorySyncUseCase failing = new DirectorySyncUseCase(
                simpleSource(), store, rollbackTx, 100, 100, changeLog, publisher);

        assertThatThrownBy(() -> failing.publishWithChangeLog(handle))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outbox");
        assertThat(changeLog.entries).as("回滚后 append 不得留存").isEmpty();
        assertThat(publisher.events).isEmpty();
    }

    // ---------------------------------------------------------------- 替身

    /** 内存变更日志替身：append 记录 + 按 scope/代判激活；clear 供回滚模拟。 */
    private static final class InMemoryChangeLog implements DirectoryChangeLog {
        final List<ChangeLogEntry> entries = new ArrayList<>();

        @Override
        public String append(ChangeLogEntry entry) {
            entries.add(entry);
            return entry.id();
        }

        @Override
        public boolean hasGenerationActivation(DirectoryScope scope, String generationId) {
            return entries.stream().anyMatch(e -> e.scope().equals(scope)
                    && e.kind() == ChangeLogEntry.Kind.GENERATION_ACTIVATED
                    && e.generationId() != null && e.generationId().equals(generationId));
        }

        void clear() {
            entries.clear();
        }
    }

    /** 记录型代激活事件发布器替身（fail=true 时抛出模拟 Outbox 入库失败）。 */
    private static final class RecordingPublisher implements IdentityChangePublisher {
        final List<DirectoryGenerationActivatedEvent> events = new ArrayList<>();
        boolean fail;

        @Override
        public void publishGenerationActivated(DirectoryGenerationActivatedEvent event) {
            if (fail) {
                throw new IllegalStateException("outbox unavailable（测试注入）");
            }
            events.add(event);
        }
    }
}
