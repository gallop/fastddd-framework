package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.BusinessActionReceipt;
import com.gallop.fastddd.agentaccess.contract.BusinessActionStatus;
import com.gallop.fastddd.agentaccess.contract.RetryDisposition;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 编排结果跨字段紧致校验：审计终态与五态一致、tombstoneRef 仅 UNKNOWN 收敛携带。
 */
class StatusQueryResultTest {

    private static AuditProjection audit(ActionOutcomeCode outcomeCode) {
        return new AuditProjection("acme", "bop-2026-0001", "ar.createCrmFollowup", "1",
                "subject-77", "actor-42", "workload-recovery-01", "recon-2026-0001", null, outcomeCode);
    }

    private static BusinessActionReceipt committedReceipt() {
        return new BusinessActionReceipt(AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "acme", "bop-2026-0001", "ar.createCrmFollowup", "1", "sha256:a",
                "https://fastddd.target.example", "fastddd-ar-service", "binding-1", null,
                ActionOutcomeCode.ALREADY_COMPLETED, "cmd-1", "arFollowup/FOLLOW-001", "8",
                OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC),
                RetryDisposition.DO_NOT_RETRY, "trace-77", null);
    }

    @Test
    void validCombinationsAreAccepted() {
        List<StatusQueryResult> valid = List.of(
                new StatusQueryResult(new BusinessActionStatus.Completed(committedReceipt()),
                        audit(ActionOutcomeCode.ALREADY_COMPLETED), null),
                new StatusQueryResult(new BusinessActionStatus.FailedFinal(ActionOutcomeCode.BUSINESS_RULE_VIOLATION),
                        audit(ActionOutcomeCode.BUSINESS_RULE_VIOLATION), null),
                new StatusQueryResult(new BusinessActionStatus.Unknown(), audit(ActionOutcomeCode.UNKNOWN), "ts-001"),
                new StatusQueryResult(new BusinessActionStatus.Unknown(), audit(ActionOutcomeCode.UNKNOWN), null),
                new StatusQueryResult(new BusinessActionStatus.InProgress(), audit(null), null),
                new StatusQueryResult(new BusinessActionStatus.NotVisible(), audit(null), null));
        for (StatusQueryResult result : valid) {
            assertDoesNotThrow(() -> result.status());
        }
    }

    @Test
    void auditTerminalCodeMustAgreeWithConvergedStatus() {
        // COMPLETED 收敛审计终态不是 ALREADY_COMPLETED
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.Completed(committedReceipt()),
                audit(ActionOutcomeCode.BUSINESS_RULE_VIOLATION), null));
        // FAILED_FINAL 审计终态与失败分类不一致
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.FailedFinal(ActionOutcomeCode.PRECONDITION_CHANGED),
                audit(ActionOutcomeCode.BUSINESS_RULE_VIOLATION), null));
        // UNKNOWN 收敛携带非 UNKNOWN 终态
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.Unknown(), audit(ActionOutcomeCode.ALREADY_COMPLETED), null));
        // UNKNOWN + tombstoneRef 仍不得携带非 UNKNOWN 终态（含 tombstone 场景的矩阵收紧）
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.Unknown(), audit(ActionOutcomeCode.BUSINESS_RULE_VIOLATION), "ts-001"));
        // IN_PROGRESS/NOT_VISIBLE 无终态结果码，不得伪填（§9.3）
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.InProgress(), audit(ActionOutcomeCode.UNKNOWN), null));
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.NotVisible(), audit(ActionOutcomeCode.UNKNOWN), null));
    }

    @Test
    void tombstoneRefOnlyRidesUnknownConvergence() {
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.Completed(committedReceipt()),
                audit(ActionOutcomeCode.ALREADY_COMPLETED), "ts-001"));
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.NotVisible(), audit(null), "ts-001"));
        assertThrows(IllegalArgumentException.class, () -> new StatusQueryResult(
                new BusinessActionStatus.Unknown(), audit(ActionOutcomeCode.UNKNOWN), "  "));
    }

    @Test
    void nullStatusOrAuditIsProgrammingError() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatusQueryResult(null, audit(null), null));
        assertThrows(IllegalArgumentException.class,
                () -> new StatusQueryResult(new BusinessActionStatus.Unknown(), null, null));
    }
}
