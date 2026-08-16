package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

class ExecutionPermitTest {

    private static final OffsetDateTime NOT_BEFORE = OffsetDateTime.parse("2026-08-16T03:00:00Z");
    private static final OffsetDateTime EXPIRES_AT = OffsetDateTime.parse("2026-08-16T04:00:00Z");

    /** §5.2 首样板形态：ar.createCrmFollowup / WRITE（Contract §5.2 尾注）。 */
    static ExecutionPermit validWritePermit() {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-0001", "issuer-fastagent-kernel", "audience-fastddd-ar",
                CallerContextTest.validAgentCaller(),
                "ar.createCrmFollowup", "1.0.0", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("ArAccount"), "ar/overdue-set", "tenant-scoped"),
                "dunning.followup",
                "bizop-20260816-0001",
                "sha256:auth-digest-1", "evidence-ref-1",
                "sha256:effect-digest-1", "sha256:binding-digest-1",
                NOT_BEFORE, EXPIRES_AT,
                "idem-0001", "sha256:precond-1",
                Map.of("ar/account-1001", "v42"),
                null, null, null, null);
    }

    @Test
    void validPermitIsCarriable() {
        ExecutionPermit permit = assertDoesNotThrow(ExecutionPermitTest::validWritePermit);
        // record caller() 组件即 ExecutionPermitCarrier 实现（不破坏 7.2 冻结的最小载体）
        assertSame(permit.caller(), permit.caller());
        assertEquals(Effect.WRITE, permit.effect());
        assertEquals("ar.createCrmFollowup", permit.actionId());
        assertEquals(Map.of("ar/account-1001", "v42"), permit.expectedResourceVersions());
    }

    @Test
    void unknownSchemaVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.2", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                null, null, null, null, null, null, null));
    }

    @Test
    void requiredBlankFieldsAreRejected() {
        // issuer 与 businessOperationId 逐项代表 String 必选族（路径同一 requireNonBlank）
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", " ", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", null, "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                null, null, null, null, null, null, null));
    }

    @Test
    void requiredObjectFieldsAreRejected() {
        // caller/effect/resourceConstraints/notBefore 逐项代表对象必选族
        for (int i = 0; i < 4; i++) {
            int slot = i;
            assertThrows(IllegalArgumentException.class, () -> buildWithNullAt(slot));
        }
    }

    private static ExecutionPermit buildWithNullAt(int slot) {
        CallerContext caller = slot == 0 ? null : CallerContextTest.validAgentCaller();
        Effect effect = slot == 1 ? null : Effect.WRITE;
        ResourceConstraints rc = slot == 2 ? null : new ResourceConstraints(java.util.Set.of("R"), null, null);
        OffsetDateTime nb = slot == 3 ? null : NOT_BEFORE;
        return new ExecutionPermit(
                "0.1", "p", "i", "a", caller,
                "act", "1", effect, rc,
                "pu", "bo", "d1", "e1", "d2", "d3",
                nb, EXPIRES_AT,
                null, null, null, null, null, null, null);
    }

    @Test
    void invertedTimeWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                EXPIRES_AT, NOT_BEFORE,
                null, null, null, null, null, null, null));
    }

    @Test
    void optionalFieldsHavePresenceSemantics() {
        // 空白可选字段、非正 usageLimit、空 CAS 映射、空预算值 → 全拒
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                " ", null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                null, null, null, 0L, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionPermit(
                "0.1", "p", "i", "a", CallerContextTest.validAgentCaller(),
                "act", "1", Effect.WRITE,
                new ResourceConstraints(java.util.Set.of("R"), null, null),
                "pu", "bo", "d1", "e1", "d2", "d3",
                NOT_BEFORE, EXPIRES_AT,
                null, null, Map.of(), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DataBudget(0L, null, null));
    }

    @Test
    void resourceConstraintsRequireNonEmptyTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceConstraints(java.util.Set.of(), null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceConstraints(null, null, null));
    }
}
