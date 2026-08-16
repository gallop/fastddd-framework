package com.gallop.fastddd.agentaccess.gateway;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.BusinessActionStatus;
import com.gallop.fastddd.agentaccess.contract.ReconciliationPermit;
import com.gallop.fastddd.agentaccess.contract.ReconciliationScope;
import com.gallop.fastddd.agentaccess.contract.RetryDisposition;
import com.gallop.fastddd.agentaccess.permit.DefaultReconciliationPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.DedupKey;
import com.gallop.fastddd.agentaccess.spi.InMemoryOutcomeStatusStore;
import com.gallop.fastddd.agentaccess.spi.OutcomeRecordView;
import com.gallop.fastddd.agentaccess.spi.OutcomeStatusPort;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import com.gallop.fastddd.agentaccess.spi.ReconciliationPermitVerifier;
import com.gallop.fastddd.agentaccess.spi.StatusQueryResult;
import com.gallop.fastddd.agentaccess.spi.StatusQueryService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §5.7 Status 查询编排不变量：验证先于记录读取、无记录≠FAILED、
 * 验证失败统一 NOT_VISIBLE 不泄露存在性、AAC-16 同构完整性错位保持
 * UNKNOWN（AST-08：不切换新 binding 收敛成功）、tombstone 按原 binding
 * 审计并携带 tombstone 引用。
 */
class DefaultStatusQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";
    private static final String RECOVERY_WORKLOAD = "workload-recovery-01";
    private static final String TENANT = "acme";
    private static final String BOP_ID = "bop-2026-0001";
    private static final String BINDING_REF = "binding-2026-08-16-001";

    private static ReconciliationPermit permit(String issuer, String audience,
                                               OffsetDateTime notBefore, OffsetDateTime expiresAt) {
        return new ReconciliationPermit(AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "recon-2026-0001", issuer, audience, ReconciliationScope.STATUS_QUERY,
                RECOVERY_WORKLOAD, "subject-77", "actor-42", TENANT,
                "ar.createCrmFollowup", "1", BINDING_REF, BOP_ID, "status-minimal",
                notBefore, expiresAt);
    }

    private static ReconciliationPermit validPermit() {
        return permit(TRUSTED_ISSUER, AUDIENCE,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC));
    }

    private static BusinessActionReceipt receipt(ActionOutcomeCode outcomeCode, String tenantId,
                                                 String businessOperationId, String bindingRef) {
        return receipt(outcomeCode, tenantId, businessOperationId, bindingRef,
                "ar.createCrmFollowup", "1", AUDIENCE);
    }

    private static BusinessActionReceipt receipt(ActionOutcomeCode outcomeCode, String tenantId,
                                                 String businessOperationId, String bindingRef,
                                                 String actionId, String actionVersion, String audience) {
        boolean committed = outcomeCode == ActionOutcomeCode.ALREADY_COMPLETED;
        return new BusinessActionReceipt(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, tenantId, businessOperationId,
                actionId, actionVersion, "sha256:a",
                "https://fastddd.target.example", audience, bindingRef, null, outcomeCode,
                committed ? "cmd-1" : null,
                committed ? "arFollowup/FOLLOW-001" : null,
                committed ? "8" : null,
                committed ? OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC) : null,
                RetryDisposition.DO_NOT_RETRY, "trace-77", null);
    }

    private static ReconciliationPermitVerifier verifier() {
        return new DefaultReconciliationPermitVerifier(new PermitVerifierConfig(
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                Duration.ofMinutes(1), false));
    }

    private static DefaultStatusQueryService service(OutcomeRecordView view) {
        InMemoryOutcomeStatusStore store = new InMemoryOutcomeStatusStore();
        if (view != null) {
            store.put(new DedupKey(TENANT, BOP_ID), view);
        }
        return new DefaultStatusQueryService(verifier(), store);
    }

    @Test
    void recordedCommittedReceiptConvergesCompleted() {
        BusinessActionReceipt receipt = receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                TENANT, BOP_ID, BINDING_REF);
        StatusQueryResult result = service(new OutcomeRecordView.Recorded(receipt))
                .queryStatus(validPermit(), RECOVERY_WORKLOAD);
        BusinessActionStatus.Completed completed =
                assertInstanceOf(BusinessActionStatus.Completed.class, result.status());
        assertSame(receipt, completed.receipt());
        assertEquals(ActionOutcomeCode.ALREADY_COMPLETED, result.audit().outcomeCode());
        // 审计投影取自许可本体（§10 白名单）
        assertEquals("subject-77", result.audit().subjectId());
        assertEquals(RECOVERY_WORKLOAD, result.audit().workloadId());
        assertEquals("recon-2026-0001", result.audit().permitId());
    }

    @Test
    void recordedFailureClassificationConvergesFailedFinal() {
        StatusQueryResult result = service(new OutcomeRecordView.Recorded(
                        receipt(ActionOutcomeCode.BUSINESS_RULE_VIOLATION, TENANT, BOP_ID, BINDING_REF)))
                .queryStatus(validPermit(), RECOVERY_WORKLOAD);
        BusinessActionStatus.FailedFinal failed =
                assertInstanceOf(BusinessActionStatus.FailedFinal.class, result.status());
        assertEquals(ActionOutcomeCode.BUSINESS_RULE_VIOLATION, failed.outcomeCode());
        assertEquals(ActionOutcomeCode.BUSINESS_RULE_VIOLATION, result.audit().outcomeCode());
    }

    @Test
    void recordedUnknownOutcomeStaysUnknownNotConverged() {
        // §9.3：UNKNOWN 是可证明性状态，不得作为 COMPLETED/FAILED_FINAL 收敛依据
        StatusQueryResult result = service(new OutcomeRecordView.Recorded(
                        receipt(ActionOutcomeCode.UNKNOWN, TENANT, BOP_ID, BINDING_REF)))
                .queryStatus(validPermit(), RECOVERY_WORKLOAD);
        assertInstanceOf(BusinessActionStatus.Unknown.class, result.status());
        assertEquals(ActionOutcomeCode.UNKNOWN, result.audit().outcomeCode());
    }

    @Test
    void absentRecordIsUnknownNotFailed() {
        // §5.7：无记录≠FAILED；也不授权上游换新 bopId 盲重放
        StatusQueryResult result = service(null).queryStatus(validPermit(), RECOVERY_WORKLOAD);
        assertInstanceOf(BusinessActionStatus.Unknown.class, result.status());
        assertEquals(ActionOutcomeCode.UNKNOWN, result.audit().outcomeCode());
    }

    @Test
    void inFlightRecordIsInProgressWithoutTerminalCode() {
        StatusQueryResult result = service(new OutcomeRecordView.InFlight())
                .queryStatus(validPermit(), RECOVERY_WORKLOAD);
        assertInstanceOf(BusinessActionStatus.InProgress.class, result.status());
        assertNull(result.audit().outcomeCode(), "IN_PROGRESS 无终态结果码，不得伪填");
    }

    @Test
    void bindingTombstoneStaysUnknownAndSurfacesTombstoneRef() {
        // AST-08：按原 binding 审计与判定；tombstone 引用进结果（宿主审计合并记录）
        StatusQueryResult result = service(new OutcomeRecordView.BindingTombstoned(
                "ts-001", "binding.retired", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)))
                .queryStatus(validPermit(), RECOVERY_WORKLOAD);
        assertInstanceOf(BusinessActionStatus.Unknown.class, result.status(),
                "原 binding 已 tombstone 且无稳定结果：可证明状态是 UNKNOWN，不切换新 binding 收敛成功");
        assertEquals("ts-001", result.tombstoneRef());
        assertEquals(ActionOutcomeCode.UNKNOWN, result.audit().outcomeCode());
    }

    @Test
    void verificationFailureIsUniformlyNotVisibleWithoutRecordRead() {
        // 三种失败形态（workload 错配/许可过期/issuer 不受信）响应不可区分，且都不读记录
        int[] findCalls = {0};
        OutcomeStatusPort countingPort = (key, allowedProjection) -> {
            findCalls[0]++;
            return new OutcomeRecordView.Recorded(
                    receipt(ActionOutcomeCode.ALREADY_COMPLETED, TENANT, BOP_ID, BINDING_REF));
        };
        StatusQueryService service = new DefaultStatusQueryService(verifier(), countingPort);

        StatusQueryResult wrongWorkload = service.queryStatus(validPermit(), "workload-OTHER");
        StatusQueryResult expired = service.queryStatus(permit(TRUSTED_ISSUER, AUDIENCE,
                        OffsetDateTime.ofInstant(NOW.minus(Duration.ofHours(2)), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(NOW.minus(Duration.ofHours(1)), ZoneOffset.UTC)),
                RECOVERY_WORKLOAD);
        StatusQueryResult untrustedIssuer = service.queryStatus(permit("https://rogue.example", AUDIENCE,
                        OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                        OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC)),
                RECOVERY_WORKLOAD);

        for (StatusQueryResult result : List.of(wrongWorkload, expired, untrustedIssuer)) {
            assertInstanceOf(BusinessActionStatus.NotVisible.class, result.status());
            assertNull(result.audit().outcomeCode());
        }
        assertEquals(wrongWorkload, expired, "验证失败响应不可区分（存在性不泄露）");
        assertEquals(wrongWorkload, untrustedIssuer, "issuer 失败与 workload 失败同响应");
        assertEquals(0, findCalls[0], "验证失败路径不得读取记录（先验证后读键）");
    }

    @Test
    void verificationPrecedesRecordRead() {
        List<String> events = new ArrayList<>();
        List<String> projections = new ArrayList<>();
        ReconciliationPermitVerifier recordingVerifier = (permit, workloadId) -> {
            events.add("verify");
            return verifier().verify(permit, workloadId);
        };
        InMemoryOutcomeStatusStore store = new InMemoryOutcomeStatusStore();
        OutcomeStatusPort recordingPort = (key, allowedProjection) -> {
            events.add("find");
            projections.add(allowedProjection);
            return store.find(key, allowedProjection);
        };
        StatusQueryService service = new DefaultStatusQueryService(recordingVerifier, recordingPort);
        service.queryStatus(validPermit(), RECOVERY_WORKLOAD);
        assertEquals(List.of("verify", "find"), events, "验证必须先于记录读取（§5.7：授权不依赖二元键，先验证后读键）");
        assertEquals(List.of("status-minimal"), projections,
                "许可冻结的 allowedProjection 必须原样透传宿主（§5.7 绑定面；数据最小化执行点在宿主）");
    }

    @Test
    void misboundRecordedReceiptStaysUnknownUniformly() {
        // AAC-16 同构 + §5.7 绑定面：tenant/bopId/bindingRef/actionId/actionVersion/audience
        // 任一错位都不可证明——统一 UNKNOWN，无差异信道
        List<OutcomeRecordView> misbound = List.of(
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        "OTHER-TENANT", BOP_ID, BINDING_REF)),
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        TENANT, "bop-OTHER", BINDING_REF)),
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        TENANT, BOP_ID, "binding-NEW")),
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        TENANT, BOP_ID, BINDING_REF, "ar.OTHER-action", "1", AUDIENCE)),
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        TENANT, BOP_ID, BINDING_REF, "ar.createCrmFollowup", "9", AUDIENCE)),
                new OutcomeRecordView.Recorded(receipt(ActionOutcomeCode.ALREADY_COMPLETED,
                        TENANT, BOP_ID, BINDING_REF, "ar.createCrmFollowup", "1", "other-service")));
        StatusQueryResult first = null;
        for (OutcomeRecordView view : misbound) {
            StatusQueryResult result = service(view).queryStatus(validPermit(), RECOVERY_WORKLOAD);
            assertInstanceOf(BusinessActionStatus.Unknown.class, result.status(),
                    "原 binding 不可证明：不切换新 binding 收敛成功（AST-08）");
            if (first == null) {
                first = result;
            } else {
                assertEquals(first, result, "错位形态间响应不可区分（细因只进审计不进响应）");
            }
        }
    }

    @Test
    void nullPermitIsProgrammingError() {
        assertThrows(IllegalArgumentException.class,
                () -> service(null).queryStatus(null, RECOVERY_WORKLOAD));
    }

    @Test
    void constructorNullDependenciesAreProgrammingErrors() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefaultStatusQueryService(null, new InMemoryOutcomeStatusStore()));
        assertThrows(IllegalArgumentException.class, () -> new DefaultStatusQueryService(verifier(), null));
        assertDoesNotThrow(() -> new DefaultStatusQueryService(verifier(), new InMemoryOutcomeStatusStore()));
    }
}
