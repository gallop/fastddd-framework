package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryBatch;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryPublication;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.GenerationHandle;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.Lease;
import com.gallop.fastddd.identity.core.domain.ObservedSnapshot;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.ValidationReport;
import com.gallop.fastddd.identity.core.error.DirectoryLeaseHeldException;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3-c 手动同步触发组件单元测试（§9.4）：运维入口完成/进行中两态、与调度共用
 * 租约互斥的转译（LeaseHeld → in_progress 非错误）、交互入口逐操作裁决 +
 * 缺策略/缺操作者缺省拒绝。纯替身（本夹具），无 Spring 上下文。
 */
class DirectorySyncManualTriggerTest {

    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-manual", "app");

    @Test
    void runFullSyncReturnsCompletedWithPublication() {
        RecordingStore store = new RecordingStore();
        DirectorySyncManualTrigger trigger = newTrigger(store, policyProvider(new RecordingPolicy()));

        DirectorySyncManualTrigger.ManualSyncResult result = trigger.runFullSync();

        assertThat(result.outcome()).isEqualTo(DirectorySyncManualTrigger.ManualSyncResult.OUTCOME_COMPLETED);
        assertThat(result.isInProgress()).isFalse();
        assertThat(result.publication().generationId()).isEqualTo("gen-1");
        assertThat(result.publication().factsInserted()).isEqualTo(2);
        // 完整契约序列（与调度同一用例路径；部门批/人员批分批写、每次源调用前
        // 续租 + 末页转 completePaging 前续租——W3A-03；无策略裁决，策略不进核心用例）
        assertThat(store.calls).containsExactly(
                "begin", "write:1d/0p", "renew", "write:0d/1p", "renew",
                "completePaging", "validate", "publish");
    }

    @Test
    void runFullSyncTranslatesLeaseHeldToInProgressNotError() {
        // 与调度共用存储租约：已有持有者 → LeaseHeld → "同步进行中"（非错误）
        RecordingStore store = new RecordingStore();
        store.leaseHeldOnBegin = true;
        DirectorySyncManualTrigger trigger = newTrigger(store, policyProvider(new RecordingPolicy()));

        DirectorySyncManualTrigger.ManualSyncResult result = trigger.runFullSync();

        assertThat(result.outcome()).isEqualTo(DirectorySyncManualTrigger.ManualSyncResult.OUTCOME_IN_PROGRESS);
        assertThat(result.isInProgress()).isTrue();
        assertThat(result.publication()).isNull();
        assertThat(store.calls).containsExactly("begin:lease-held");
    }

    @Test
    void triggerInteractivelyWithoutPolicyBeanDefaultsToDeny() {
        RecordingStore store = new RecordingStore();
        // 空 bean factory：无 IdentityAdminPolicy bean（admin.enabled=false 形态）
        DirectorySyncManualTrigger trigger = newTrigger(store,
                new DefaultListableBeanFactory().getBeanProvider(IdentityAdminPolicy.class));

        assertThatThrownBy(() -> trigger.triggerInteractively(adminActor()))
                .isInstanceOf(IdentityAdminDeniedException.class)
                .hasMessageContaining("IdentityAdminPolicy");
        // 拒绝即中止：零存储调用
        assertThat(store.calls).isEmpty();
    }

    @Test
    void triggerInteractivelyWithNullActorDefaultsToDeny() {
        RecordingPolicy policy = new RecordingPolicy();
        RecordingStore store = new RecordingStore();
        DirectorySyncManualTrigger trigger = newTrigger(store, policyProvider(policy));

        assertThatThrownBy(() -> trigger.triggerInteractively(null))
                .isInstanceOf(IdentityAdminDeniedException.class)
                .hasMessageContaining("操作者身份");
        // 策略未被咨询（操作者缺失先拒），零存储调用
        assertThat(policy.invocations).isEmpty();
        assertThat(store.calls).isEmpty();
    }

    @Test
    void triggerInteractivelyChecksPolicyThenRunsSamePath() {
        RecordingPolicy policy = new RecordingPolicy();
        RecordingStore store = new RecordingStore();
        DirectorySyncManualTrigger trigger = newTrigger(store, policyProvider(policy));
        Actor actor = adminActor();

        DirectorySyncManualTrigger.ManualSyncResult result = trigger.triggerInteractively(actor);

        // 逐操作裁决：SYNC_TRIGGER + 操作者 + 无固定目标（null），通过后同运维路径执行
        assertThat(policy.invocations)
                .containsExactly("SYNC_TRIGGER:" + actor.principalId().value() + ":null");
        assertThat(result.outcome()).isEqualTo(DirectorySyncManualTrigger.ManualSyncResult.OUTCOME_COMPLETED);
        assertThat(store.calls).containsExactly(
                "begin", "write:1d/0p", "renew", "write:0d/1p", "renew",
                "completePaging", "validate", "publish");
    }

    // ------------------------------------------------------------- 夹具

    private static DirectorySyncManualTrigger newTrigger(RecordingStore store,
                                                         ObjectProvider<IdentityAdminPolicy> policy) {
        return new DirectorySyncManualTrigger(
                new DirectorySyncUseCase(new LocalSource(), store, passthroughRunner(), 10, 100),
                new LocalSource(), policy);
    }

    private static ObjectProvider<IdentityAdminPolicy> policyProvider(IdentityAdminPolicy policy) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("identityAdminPolicy", policy);
        return beanFactory.getBeanProvider(IdentityAdminPolicy.class);
    }

    private static Actor adminActor() {
        return new Actor("tenant-manual",
                PrincipalId.of("00000000-0000-0000-0000-000000000001"), PrincipalType.HUMAN);
    }

    private static IdentityTransactionRunner passthroughRunner() {
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
    }

    /** 单部门单人员终页源。 */
    static final class LocalSource implements DirectorySource {

        @Override
        public DirectoryScope scope() {
            return SCOPE;
        }

        @Override
        public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
            return DirectoryPage.last(List.of(new DirectoryDepartment("D1", null, "总部")));
        }

        @Override
        public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
            return DirectoryPage.last(List.of(new DirectoryPerson("OU1", "张三", null, List.of("D1"))));
        }
    }

    /** 记录型存储替身：happy 直通；可切换 begin 抛 LeaseHeld。 */
    static final class RecordingStore implements DirectoryMirrorStore {

        final List<String> calls = new ArrayList<>();
        boolean leaseHeldOnBegin;

        @Override
        public GenerationHandle beginGeneration(DirectoryScope scope) {
            if (leaseHeldOnBegin) {
                calls.add("begin:lease-held");
                throw new DirectoryLeaseHeldException("同步进行中（测试注入）");
            }
            calls.add("begin");
            return new GenerationHandle(scope, "gen-1", "token-1");
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
                    new ObservedSnapshot.ObservedScope(List.of("D1"), List.of("D1"), true, true),
                    1, 1, 1, List.of("paging-complete", "relations-resolved", "counts-consistent"));
        }

        @Override
        public DirectoryPublication publish(GenerationHandle handle) {
            calls.add("publish");
            return new DirectoryPublication(handle.generationId(), 0, 1, 1, 2);
        }

        @Override
        public void abandon(GenerationHandle handle, String reason) {
            calls.add("abandon:" + reason);
        }

        @Override
        public ObservedSnapshot readObservation(DirectoryScope scope) {
            return new ObservedSnapshot("gen-1", 1, 1,
                    new ObservedSnapshot.ObservedScope(List.of("D1"), List.of("D1"), true, true),
                    1, 1, List.of());
        }

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            return List.of();
        }
    }

    /** 记录型策略替身：按 "操作:操作者:目标" 记录每次逐操作裁决。 */
    static final class RecordingPolicy implements IdentityAdminPolicy {

        final List<String> invocations = new ArrayList<>();

        @Override
        public void checkPrincipalAdmin(IdentityAdminOperation operation, Actor actor,
                                        PrincipalId targetPrincipalId) {
            invocations.add(operation + ":" + actor.principalId().value()
                    + ":" + (targetPrincipalId == null ? "null" : targetPrincipalId.value()));
        }
    }
}
