package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §5.7 五态 sealed 形态：visibility 逐态映射、COMPLETED 必携带稳定 Receipt、
 * FAILED_FINAL 拒收 ALREADY_COMPLETED（已提交态应经 COMPLETED 收敛）与
 * UNKNOWN（可证明性状态非错误分类，§9.3）、NOT_VISIBLE 零负载不泄露存在性。
 */
class BusinessActionStatusTest {

    private static BusinessActionReceipt stableReceipt() {
        return new BusinessActionReceipt(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "acme", "bop-2026-0001",
                "ar.createCrmFollowup", "1", "sha256:effect",
                "https://fastddd.target.example", "fastddd-ar-service",
                "binding-2026-08-16-001", null, ActionOutcomeCode.ALREADY_COMPLETED,
                "cmd-0f1e2d3c", "arFollowup/FOLLOW-001", null,
                OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC),
                RetryDisposition.DO_NOT_RETRY, "trace-77", null);
    }

    @Test
    void visibilityMapsPerState() {
        assertEquals(OutcomeVisibility.COMPLETED,
                new BusinessActionStatus.Completed(stableReceipt()).visibility());
        assertEquals(OutcomeVisibility.FAILED_FINAL,
                new BusinessActionStatus.FailedFinal(ActionOutcomeCode.BUSINESS_RULE_VIOLATION).visibility());
        assertEquals(OutcomeVisibility.IN_PROGRESS, new BusinessActionStatus.InProgress().visibility());
        assertEquals(OutcomeVisibility.UNKNOWN, new BusinessActionStatus.Unknown().visibility());
        assertEquals(OutcomeVisibility.NOT_VISIBLE, new BusinessActionStatus.NotVisible().visibility());
    }

    @Test
    void completedRequiresStableReceipt() {
        assertThrows(IllegalArgumentException.class,
                () -> new BusinessActionStatus.Completed(null));
    }

    @Test
    void failedFinalRejectsCommittedUnprovableAndNullClassification() {
        // ALREADY_COMPLETED=已提交（经 COMPLETED 收敛）；UNKNOWN=可证明性状态
        // 不是错误分类（§9.3，经 UNKNOWN 态承载）
        List<ActionOutcomeCode> invalid = java.util.Arrays.asList(
                ActionOutcomeCode.ALREADY_COMPLETED, ActionOutcomeCode.UNKNOWN, null);
        for (ActionOutcomeCode code : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> new BusinessActionStatus.FailedFinal(code), "非法 FAILED_FINAL 分类：" + code);
        }
    }

    @Test
    void unknownAndNotVisibleCarryNoPayload() {
        // NOT_VISIBLE 零负载：对无权与不存在不可区分（形态层保证——无组件即无差异信道）
        assertEquals(new BusinessActionStatus.NotVisible(), new BusinessActionStatus.NotVisible());
        assertEquals(new BusinessActionStatus.Unknown(), new BusinessActionStatus.Unknown());
        assertEquals(0, BusinessActionStatus.NotVisible.class.getRecordComponents().length);
        assertEquals(0, BusinessActionStatus.Unknown.class.getRecordComponents().length);
        assertEquals(0, BusinessActionStatus.InProgress.class.getRecordComponents().length);
    }
}
