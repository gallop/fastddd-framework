package com.gallop.fastddd.identity.spring.web;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.IdentityAdminOperation;
import com.gallop.fastddd.identity.core.domain.IdentityAuditRecord;
import com.gallop.fastddd.identity.core.domain.LocalFactView;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.domain.PrincipalId;
import com.gallop.fastddd.identity.core.domain.PrincipalType;
import com.gallop.fastddd.identity.core.domain.ProtectedFactView;
import com.gallop.fastddd.identity.core.domain.RecheckConclusion;
import com.gallop.fastddd.identity.core.domain.RecheckResult;
import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.domain.RetentionReport;
import com.gallop.fastddd.identity.core.error.IdentityAdminDeniedException;
import com.gallop.fastddd.identity.core.error.IdentityRequiredException;
import com.gallop.fastddd.identity.core.port.ActorResolver;
import com.gallop.fastddd.identity.core.port.DirectoryFactMatcher;
import com.gallop.fastddd.identity.core.port.DirectoryFactReader;
import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckCommitter;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;
import com.gallop.fastddd.identity.core.port.DirectoryRetentionExecutor;
import com.gallop.fastddd.identity.core.port.IdentityAdminPolicy;
import com.gallop.fastddd.identity.core.port.IdentityAuditWriter;
import com.gallop.fastddd.identity.core.port.IdentityTransactionRunner;
import com.gallop.fastddd.identity.core.usecase.DirectoryRecheckManualTrigger;
import com.gallop.fastddd.identity.spring.directory.DirectoryRetentionTrigger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5-f f-2 R-8：retention/recheck 两管理端点四态责任序 HTTP 级验收（W5-f 设计
 * §3.4/§7.2-R8；沿 {@code IdentityDirectoryEventsAdminControllerTest} standalone
 * MockMvc 先例——真实控制器 + 真实 Trigger 编排 + 端口桩）。覆盖：401（无认证
 * ——执行器/枚举/策略零触达）/ 400（scope 协议外 + retention preview 标志
 * 缺失）/ 403（策略拒绝——RETENTION_RUN 与 RECHECK_RUN 双操作位语义 + 目标
 * null）/ 200（报告 + 审计行 + best-effort 失败不阻断）+ 策略零咨询断言
 * （401/400 面——W3-c 观察 1 先例）+ recheck 仅 pending 面（M-17）。
 */
class DirectoryRetentionRecheckAdminControllerTest {

    private static final Actor ADMIN = new Actor("tenant-1", PrincipalId.of("ou-admin-1"),
            PrincipalType.HUMAN);
    private static final DirectoryScope SCOPE = new DirectoryScope("tenant-1", "app");

    // ---- retention：① 认证在场缺失 → 401（执行器/策略零触达） ----

    @Test
    void retentionAnonymousGives401WithoutTouchingExecutorOrPolicy() throws Exception {
        StubActorResolver resolver = new StubActorResolver();
        resolver.anonymous = true;
        StubRetentionExecutor executor = new StubRetentionExecutor();
        StubPolicy policy = new StubPolicy();

        mvc(executor, new StubRecheckFixture(), policy, new RecordingAuditWriter(), resolver)
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\",\"preview\":true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("identity_unauthorized"));

        assertThat(executor.previewCalls).isZero();
        assertThat(executor.executeCalls).isZero();
        assertThat(policy.consultations).isZero();
    }

    // ---- retention：② scope 协议外 → 400（策略零咨询） ----

    @Test
    void retentionBlankOrOverlongScopeGives400WithoutConsultingPolicy() throws Exception {
        StubRetentionExecutor executor = new StubRetentionExecutor();
        StubPolicy policy = new StubPolicy();
        MockMvc mvc = mvc(executor, new StubRecheckFixture(), policy,
                new RecordingAuditWriter(), new StubActorResolver());

        mvc.perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\" \",\"appScope\":\"app\",\"preview\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_scope"));

        mvc.perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"" + "a".repeat(65)
                                + "\",\"preview\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_scope"));

        assertThat(executor.previewCalls).isZero();
        assertThat(policy.consultations).isZero();
    }

    // ---- retention：②b preview 标志缺失 → 400（破坏性操作须显式声明形态） ----

    @Test
    void retentionMissingPreviewFlagGives400() throws Exception {
        StubRetentionExecutor executor = new StubRetentionExecutor();

        mvc(executor, new StubRecheckFixture(), new StubPolicy(), new RecordingAuditWriter(),
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_preview"));

        assertThat(executor.previewCalls).isZero();
    }

    // ---- retention：③ 策略拒绝 → 403（RETENTION_RUN，null-target） ----

    @Test
    void retentionPolicyDenialGives403WithRetentionRunSemantics() throws Exception {
        StubPolicy policy = new StubPolicy();
        policy.denied = true;
        StubRetentionExecutor executor = new StubRetentionExecutor();

        mvc(executor, new StubRecheckFixture(), policy, new RecordingAuditWriter(),
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\",\"preview\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("identity_admin_denied"));

        assertThat(policy.seenOperation).isEqualTo(IdentityAdminOperation.RETENTION_RUN);
        assertThat(executor.previewCalls).isZero();
        assertThat(executor.executeCalls).isZero();
    }

    // ---- retention：④ 200 + 审计行 ----

    @Test
    void retentionPreviewGives200AndWritesAuditRow() throws Exception {
        StubRetentionExecutor executor = new StubRetentionExecutor();
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();

        mvc(executor, new StubRecheckFixture(), new StubPolicy(), auditWriter,
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\",\"preview\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(true))
                .andExpect(jsonPath("$.mirrorPersonRows").value(2))
                .andExpect(jsonPath("$.inboxRowsPurged").value(1));

        assertThat(executor.previewCalls).isEqualTo(1);
        assertThat(executor.executeCalls).isZero(); // preview 形态不落执行侧
        assertThat(auditWriter.records).hasSize(1);
        IdentityAuditRecord record = auditWriter.records.get(0);
        assertThat(record.actorPrincipalId()).isEqualTo(ADMIN.principalId());
        assertThat(record.operation()).isEqualTo("directory-retention-run");
        assertThat(record.detail()).contains("preview=true");
        assertThat(record.success()).isTrue();
    }

    /** 审计 best-effort：写入失败只记日志，不影响成功响应（两端点同款）。 */
    @Test
    void retentionAuditFailureDoesNotAffectSuccessResponse() throws Exception {
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();
        auditWriter.failure = true;

        mvc(new StubRetentionExecutor(), new StubRecheckFixture(), new StubPolicy(), auditWriter,
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RETENTION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\",\"preview\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preview").value(false));

        assertThat(auditWriter.records).isEmpty();
    }

    // ---- recheck：① 认证在场缺失 → 401（枚举/策略零触达） ----

    @Test
    void recheckAnonymousGives401WithoutEnumeratingOrConsultingPolicy() throws Exception {
        StubActorResolver resolver = new StubActorResolver();
        resolver.anonymous = true;
        StubRecheckFixture recheck = new StubRecheckFixture();
        StubPolicy policy = new StubPolicy();

        mvc(new StubRetentionExecutor(), recheck, policy, new RecordingAuditWriter(), resolver)
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("identity_unauthorized"));

        assertThat(recheck.mirror.enumerations).isZero();
        assertThat(policy.consultations).isZero();
    }

    // ---- recheck：② scope 协议外 → 400 ----

    @Test
    void recheckBlankScopeGives400WithoutConsultingPolicy() throws Exception {
        StubRecheckFixture recheck = new StubRecheckFixture();
        StubPolicy policy = new StubPolicy();

        mvc(new StubRetentionExecutor(), recheck, policy, new RecordingAuditWriter(),
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"\",\"appScope\":\"app\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("identity_invalid_scope"));

        assertThat(recheck.mirror.enumerations).isZero();
        assertThat(policy.consultations).isZero();
    }

    // ---- recheck：③ 策略拒绝 → 403（RECHECK_RUN，null-target） ----

    @Test
    void recheckPolicyDenialGives403WithRecheckRunSemantics() throws Exception {
        StubPolicy policy = new StubPolicy();
        policy.denied = true;
        StubRecheckFixture recheck = new StubRecheckFixture();

        mvc(new StubRetentionExecutor(), recheck, policy, new RecordingAuditWriter(),
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("identity_admin_denied"));

        assertThat(policy.seenOperation).isEqualTo(IdentityAdminOperation.RECHECK_RUN);
        assertThat(recheck.mirror.enumerations).isZero();
    }

    // ---- recheck：④ 200 + 逐键结论 + 审计行（仅 pending 面，M-17） ----

    @Test
    void recheckSweepGives200WithPendingOnlyOutcomesAndAuditRow() throws Exception {
        StubRecheckFixture recheck = new StubRecheckFixture();
        recheck.mirror.facts.add(pendingPerson("ou-p1"));
        recheck.mirror.facts.add(settledPerson("ou-p2"));
        recheck.recheckQuery.observed = Optional.of(ObservedDirectoryObject.of(
                new DirectoryPerson("ou-p1", "张三", null, List.of()), false));
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();

        mvc(new StubRetentionExecutor(), recheck, new StubPolicy(), auditWriter,
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(1)) // 非 pending 行零回查
                .andExpect(jsonPath("$.outcomes[0].externalId").value("ou-p1"))
                .andExpect(jsonPath("$.outcomes[0].objectType").value("PERSON"))
                .andExpect(jsonPath("$.outcomes[0].conclusion").value("CONTENT_DIFFERS"))
                .andExpect(jsonPath("$.outcomes[0].stale").value(false))
                .andExpect(jsonPath("$.outcomes[0].changeLogRef").value("cl-http"));

        assertThat(recheck.mirror.enumerations).isEqualTo(1);
        assertThat(recheck.committer.evidenceCount).isEqualTo(1);
        assertThat(auditWriter.records).hasSize(1);
        IdentityAuditRecord record = auditWriter.records.get(0);
        assertThat(record.operation()).isEqualTo("directory-recheck-manual");
        assertThat(record.detail()).contains("outcomes=1");
        assertThat(record.success()).isTrue();
    }

    @Test
    void recheckZeroPendingGives200WithEmptyOutcomes() throws Exception {
        mvc(new StubRetentionExecutor(), new StubRecheckFixture(), new StubPolicy(),
                new RecordingAuditWriter(), new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomes.length()").value(0)); // 幂等空扫形态
    }

    @Test
    void recheckAuditFailureDoesNotAffectSuccessResponse() throws Exception {
        RecordingAuditWriter auditWriter = new RecordingAuditWriter();
        auditWriter.failure = true;

        mvc(new StubRetentionExecutor(), new StubRecheckFixture(), new StubPolicy(), auditWriter,
                new StubActorResolver())
                .perform(post(IdentityAdminEndpoints.DIRECTORY_RECHECK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"tenant-1\",\"appScope\":\"app\"}"))
                .andExpect(status().isOk());

        assertThat(auditWriter.records).isEmpty();
    }

    // ------------------------------------------------------------- 夹具

    private static MockMvc mvc(StubRetentionExecutor executor, StubRecheckFixture recheck,
                               StubPolicy policy, RecordingAuditWriter auditWriter,
                               StubActorResolver resolver) {
        DirectoryRetentionTrigger retentionTrigger = new DirectoryRetentionTrigger(
                executor, new RetentionPolicy(Duration.ofDays(7), null), policyProvider(policy));
        DirectoryRecheckManualTrigger recheckTrigger = new DirectoryRecheckManualTrigger(
                recheck.mirror, recheck.factReader, recheck.recheckQuery, recheck.matcher,
                recheck.committer, passthroughRunner(), null, null, null);
        return MockMvcBuilders.standaloneSetup(
                new DirectoryRetentionController(retentionTrigger, auditWriter, resolver),
                new DirectoryRecheckManualController(recheckTrigger, auditWriter, resolver,
                        policyProvider(policy)))
                .build();
    }

    /** ObjectProvider 桩（沿 DirectorySyncManualTriggerTest 先例）。 */
    private static ObjectProvider<IdentityAdminPolicy> policyProvider(IdentityAdminPolicy policy) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("identityAdminPolicy", policy);
        return beanFactory.getBeanProvider(IdentityAdminPolicy.class);
    }

    private static IdentityTransactionRunner passthroughRunner() {
        return new IdentityTransactionRunner() {
            @Override
            public <T> T inTransaction(IdentityTransactionRunner.IdentityTransactionCallback<T> callback) {
                return callback.doInTransaction();
            }
        };
    }

    private static ProtectedFactView pendingPerson(String openId) {
        return new ProtectedFactView(ProtectedFactView.ObjectType.PERSON, openId, 1L, false,
                ProtectedFactView.Origin.RECHECK, "chg-1", true, "g1", 1L);
    }

    private static ProtectedFactView settledPerson(String openId) {
        return new ProtectedFactView(ProtectedFactView.ObjectType.PERSON, openId, 1L, false,
                ProtectedFactView.Origin.RECHECK, "chg-1", false, "g1", 1L);
    }

    /** Actor 解析桩：anonymous 定向 401；缺省返回 ADMIN。 */
    static final class StubActorResolver implements ActorResolver {
        boolean anonymous;

        @Override
        public Actor currentActor() {
            if (anonymous) {
                throw new IdentityRequiredException("无认证在场（测试桩）");
            }
            return ADMIN;
        }

        @Override
        public Optional<Actor> tryCurrentActor() {
            return Optional.of(ADMIN);
        }
    }

    /** 策略桩：denied 定向抛（透传 operation/target 语义），缺省放行。 */
    static final class StubPolicy implements IdentityAdminPolicy {
        boolean denied;
        IdentityAdminOperation seenOperation;
        int consultations;

        @Override
        public void checkPrincipalAdmin(IdentityAdminOperation operation, Actor actor,
                                        PrincipalId targetPrincipalId) {
            consultations++;
            seenOperation = operation;
            if (denied) {
                throw new IdentityAdminDeniedException(operation.name(),
                        targetPrincipalId == null ? null : targetPrincipalId.value(),
                        "非管理员（测试策略桩）");
            }
        }
    }

    /** 审计桩：记录写入行；failure 定向验证 best-effort。 */
    static final class RecordingAuditWriter implements IdentityAuditWriter {
        final List<IdentityAuditRecord> records = new ArrayList<>();
        boolean failure;

        @Override
        public void write(IdentityAuditRecord record) {
            if (failure) {
                throw new IllegalStateException("审计写入失败（测试桩）");
            }
            records.add(record);
        }
    }

    /** 回收执行器桩：preview/execute 计数 + 报告定向。 */
    static final class StubRetentionExecutor implements DirectoryRetentionExecutor {
        int previewCalls;
        int executeCalls;

        @Override
        public RetentionReport preview(DirectoryScope scope, RetentionPolicy policy) {
            previewCalls++;
            return report(true);
        }

        @Override
        public RetentionReport execute(DirectoryScope scope, RetentionPolicy policy) {
            executeCalls++;
            return report(false);
        }

        private static RetentionReport report(boolean preview) {
            return new RetentionReport(SCOPE, 2, 1, 2, 1, 0, false, preview);
        }
    }

    /** 回查五端口桩集（真实 DirectoryRecheckManualTrigger 编排的端口面）。 */
    static final class StubRecheckFixture {
        final StubHttpMirrorStore mirror = new StubHttpMirrorStore();
        final StubHttpFactReader factReader = new StubHttpFactReader();
        final StubHttpRecheckQuery recheckQuery = new StubHttpRecheckQuery();
        final StubHttpMatcher matcher = new StubHttpMatcher();
        final StubHttpCommitter committer = new StubHttpCommitter();
    }

    /** 镜像枚举桩（仅 readProtectedFacts 面；写面不在 HTTP 路径）。 */
    static final class StubHttpMirrorStore implements DirectoryMirrorStore {
        final List<ProtectedFactView> facts = new ArrayList<>();
        int enumerations;

        @Override
        public List<ProtectedFactView> readProtectedFacts(DirectoryScope scope) {
            enumerations++;
            return List.copyOf(facts);
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.GenerationHandle beginGeneration(
                DirectoryScope scope) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.Lease renewLease(
                com.gallop.fastddd.identity.core.domain.GenerationHandle handle) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public void writeBatch(com.gallop.fastddd.identity.core.domain.GenerationHandle handle,
                               com.gallop.fastddd.identity.core.domain.DirectoryBatch batch) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public void completePaging(com.gallop.fastddd.identity.core.domain.GenerationHandle handle) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.ValidationReport validate(
                com.gallop.fastddd.identity.core.domain.GenerationHandle handle) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.DirectoryPublication publish(
                com.gallop.fastddd.identity.core.domain.GenerationHandle handle) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public void abandon(com.gallop.fastddd.identity.core.domain.GenerationHandle handle,
                            String reason) {
            throw new UnsupportedOperationException("HTTP 回查不走写面");
        }

        @Override
        public com.gallop.fastddd.identity.core.domain.ObservedSnapshot readObservation(
                DirectoryScope scope) {
            throw new UnsupportedOperationException("HTTP 回查不走观察面");
        }
    }

    /** 事实读取桩：基线空（→ CONTENT_DIFFERS 派生路径）。 */
    static final class StubHttpFactReader implements DirectoryFactReader {
        @Override
        public Optional<LocalFactView> readFact(DirectoryScope scope,
                                                DirectoryObjectType objectType, String externalId) {
            return Optional.empty();
        }
    }

    /** 回查桩：观测定向。 */
    static final class StubHttpRecheckQuery implements DirectoryRecheckQuery {
        Optional<ObservedDirectoryObject> observed = Optional.empty();

        @Override
        public Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType,
                                                               String externalId) {
            return observed;
        }
    }

    /** 匹配桩：缺省等值。 */
    static final class StubHttpMatcher implements DirectoryFactMatcher {
        @Override
        public boolean matches(String storedFactJson, DirectoryPerson observed) {
            return true;
        }

        @Override
        public boolean matches(String storedFactJson,
                               com.gallop.fastddd.identity.core.domain.DirectoryDepartment observed) {
            return true;
        }
    }

    /** 提交器桩：计数 + 非 stale 结果定向。 */
    static final class StubHttpCommitter implements DirectoryRecheckCommitter {
        int evidenceCount;

        @Override
        public RecheckResult commitCas(DirectoryScope scope,
                                       com.gallop.fastddd.identity.core.domain.RecheckEvidence evidence) {
            evidenceCount++;
            return new RecheckResult(false, false, 2L, 2L, null, "cl-http", List.of());
        }
    }
}
