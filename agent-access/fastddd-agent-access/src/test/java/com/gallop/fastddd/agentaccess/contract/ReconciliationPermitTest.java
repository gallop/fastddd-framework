package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §5.7 对账许可紧致校验：全字段必选非空白、scope 仅 STATUS_QUERY、
 * 时间窗结构序、wire 版本冻结。
 */
class ReconciliationPermitTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 8, 16, 4, 5, 0, 0, ZoneOffset.UTC);

    private static final int SCHEMA_VERSION = 0;
    private static final int PERMIT_ID = 1;
    private static final int SCOPE = 4;
    private static final int NOT_BEFORE = 14;
    private static final int EXPIRES_AT = 15;

    private static ReconciliationPermit permit(Object... overrides) {
        Object[] args = new Object[] {
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "recon-2026-0001",
                "https://issuer.fastagent.example", "fastddd-ar-service",
                ReconciliationScope.STATUS_QUERY, "workload-recovery-01",
                "subject-77", "actor-42", "acme", "ar.createCrmFollowup", "1",
                "binding-2026-08-16-001", "bop-2026-0001", "status-minimal",
                T0, T1 };
        for (int i = 0; i < overrides.length; i += 2) {
            args[(Integer) overrides[i]] = overrides[i + 1];
        }
        return new ReconciliationPermit((String) args[0], (String) args[1], (String) args[2],
                (String) args[3], (ReconciliationScope) args[4], (String) args[5], (String) args[6],
                (String) args[7], (String) args[8], (String) args[9], (String) args[10],
                (String) args[11], (String) args[12], (String) args[13],
                (OffsetDateTime) args[14], (OffsetDateTime) args[15]);
    }

    @Test
    void baselinePermitIsWellFormed() {
        ReconciliationPermit p = permit();
        assertEquals("bop-2026-0001", p.businessOperationId());
        assertEquals(ReconciliationScope.STATUS_QUERY, p.scope());
        assertEquals(16, ReconciliationPermit.class.getRecordComponents().length);
    }

    @Test
    void allStringFieldsRejectNullOrBlank() {
        // permitId(1)…bindingRef(11)、businessOperationId(12)、allowedProjection(13) 逐项
        List<Integer> stringFields = List.of(1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        for (int field : stringFields) {
            assertThrows(IllegalArgumentException.class, () -> permit(field, null),
                    "字段序 " + field + " null 必须紧致校验拒绝");
            assertThrows(IllegalArgumentException.class, () -> permit(field, "  "),
                    "字段序 " + field + " 空白必须紧致校验拒绝");
        }
    }

    @Test
    void scopeMustBeProvided() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> permit(SCOPE, null));
        assertEquals("scope 必选", e.getMessage());
    }

    @Test
    void timeWindowMustBeOrderedAndProvided() {
        assertThrows(IllegalArgumentException.class, () -> permit(NOT_BEFORE, null));
        assertThrows(IllegalArgumentException.class, () -> permit(EXPIRES_AT, null));
        // notBefore == expiresAt 与 notBefore > expiresAt 都不是「早于」→ 拒绝
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> permit(NOT_BEFORE, T1));
        assertEquals("notBefore 必须早于 expiresAt（结构校验；时刻有效性由验证器判定）", e.getMessage());
    }

    @Test
    void schemaVersionIsFrozen() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> permit(SCHEMA_VERSION, "0.2"));
        assertEquals("schemaVersion 不受支持：期望 0.1，实际 0.2", e.getMessage());
    }

    @Test
    void wellFormedPermitRoundTripsThroughEquality() {
        ReconciliationPermit a = permit();
        ReconciliationPermit b = permit();
        assertEquals(a, b);
        assertSame(ReconciliationScope.STATUS_QUERY, a.scope(),
                "scope 单值枚举：STATUS_QUERY 不可自外构造");
    }
}
