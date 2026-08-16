package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * CallerContext 紧致校验测试（Contract §5.1 字段表 + AST-04 负向「必选字段缺失 fail-fast」）。
 */
class CallerContextTest {

    private static final OffsetDateTime AUTH_TIME = OffsetDateTime.parse("2026-08-16T03:00:00Z");

    /** Contract §5.1 表序的合法样板（Agent 场景：HUMAN subject + AGENT actor；同包测试复用）。 */
    static CallerContext validAgentCaller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice",
                SubjectType.HUMAN,
                "agent-ar-collector",
                ActorType.AGENT,
                "workload-egress-1",
                "acme",
                "dev",
                null,
                AUTH_TIME,
                null);
    }

    @Test
    void validCallerCarriesVerifiedChain() {
        var caller = assertDoesNotThrow(CallerContextTest::validAgentCaller);
        assertEquals("subject-alice", caller.subjectId());
        assertEquals(SubjectType.HUMAN, caller.subjectType());
        assertEquals(ActorType.AGENT, caller.actorType());
        assertEquals("acme", caller.tenantId());
        assertEquals("dev", caller.environment());
        assertNull(caller.delegatedBy());
        assertNull(caller.identityEvidenceRef());
    }

    @Test
    void missingRequiredFieldFailsFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> new CallerContext(null, "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.2", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", null, SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", " ", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", null, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, null, ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, "a", null,
                "w", "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                null, "t", "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", null, "dev", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", " ", null, AUTH_TIME, null));
        invalid.add(() -> new CallerContext("0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, null, null));

        assertAll("§5.1 全部必选字段逐项 null/blank/非法 + 未知 schemaVersion 必须 fail-fast",
                invalid.stream().map(r -> () -> assertThrows(IllegalArgumentException.class, r::run)));
    }

    @Test
    void optionalFieldBlankIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CallerContext(
                "0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", "  ", AUTH_TIME, null));
        assertThrows(IllegalArgumentException.class, () -> new CallerContext(
                "0.1", "s", SubjectType.HUMAN, "a", ActorType.AGENT,
                "w", "t", "dev", null, AUTH_TIME, "\t"));
    }

    @Test
    void protocolVersionsAreFourIndependentAxes() {
        // wire schemaVersion 不得携带契约文档标识前缀（AA-04 §2.2 四版本轴互不推导）
        assertEquals("0.1", AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION);
        assertEquals(1, AgentAccessProtocolVersions.DIGEST_SCHEMA_VERSION);
    }

    @Test
    void permitCarrierMinimalContract() {
        CallerContext caller = validAgentCaller();
        ExecutionPermitCarrier carrier = () -> caller;
        assertSame(caller, carrier.caller());
    }
}
