package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BusinessActionEnvelope §5.5 紧致校验：九字段组必选、permit/permitRef 二选一恰好一个、
 * 可选项统一「提供时非空白」、preconditions 提供时非空；跨字段一致性比对（信封↔许可）
 * 归网关编排层（FDD-AA-12/13 编排），本类只测字段级 fail-fast。
 */
class BusinessActionEnvelopeTest {

    private static final OffsetDateTime SENT_AT = OffsetDateTime.parse("2026-08-16T04:00:00Z");

    // 组件索引（与 record 组件序一致；修订 2026-08-16 增列 PRECONDITIONS_HASH/EXPECTED_VERSIONS）
    private static final int SCHEMA = 0, REQUEST_ID = 1, SENT_AT_I = 2, TENANT = 3, ENV = 4,
            ACTION_ID = 5, ACTION_VERSION = 6, EFFECT_I = 7, PERMIT = 8, PERMIT_REF = 9,
            AUTHZ_DIGEST = 10, EVIDENCE_REF = 11, OPERATION_ID = 12, ATTEMPT = 13, DISPATCH = 14,
            IDEMPOTENCY_KEY = 15, RESOURCE_REF = 16, PRECONDITIONS = 17, PRECONDITIONS_HASH = 18,
            EXPECTED_VERSIONS = 19, PURPOSE = 20,
            PAYLOAD = 21, PAYLOAD_SCHEMA = 22, EFFECT_DIGEST = 23, BINDING_DIGEST = 24,
            TRACE = 25, CORRELATION = 26, CAUSATION = 27, ORIGINAL_BINDING = 28;

    /**
     * 变长参数信封工厂：无参 = §13 样板形态合法基线；
     * (index, value) 成对替换做单字段变异——构造即校验，负向断言直接包工厂调用。
     */
    @SuppressWarnings("unchecked")
    static BusinessActionEnvelope envelope(Object... overrides) {
        ExecutionPermitCarrier carrier = new ExecutionPermitCarrier() {
            @Override
            public CallerContext caller() {
                return CallerContextTest.validAgentCaller();
            }
        };
        Object[] a = {
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION, "req-0001", SENT_AT, "acme", "dev",
                "ar.createCrmFollowup", "1", Effect.WRITE, carrier, null,
                "authz-digest-0", null, "op-2026-0001", null, null, null,
                "INV-2026-0001", null, null, null, "collection.followup",
                "{\"amount\":100}", "1",
                "effect-digest-0", "binding-digest-0", null, null, null, null};
        for (int i = 0; i < overrides.length; i += 2) {
            a[(Integer) overrides[i]] = overrides[i + 1];
        }
        return new BusinessActionEnvelope((String) a[0], (String) a[1], (OffsetDateTime) a[2],
                (String) a[3], (String) a[4], (String) a[5], (String) a[6], (Effect) a[7],
                (ExecutionPermitCarrier) a[8], (String) a[9], (String) a[10], (String) a[11],
                (String) a[12], (String) a[13], (String) a[14], (String) a[15], (String) a[16],
                (List<ResourcePrecondition>) a[17], (String) a[18],
                (java.util.Map<String, String>) a[19],
                (String) a[20], (String) a[21], (String) a[22],
                (String) a[23], (String) a[24], (String) a[25], (String) a[26], (String) a[27],
                (String) a[28]);
    }

    @Test
    void validEnvelopeWithCarrierPasses() {
        BusinessActionEnvelope e = assertDoesNotThrow(() -> envelope());
        assertEquals("op-2026-0001", e.businessOperationId());
        assertEquals(Effect.WRITE, e.effect());
    }

    @Test
    void permitRefPathPassesWithoutCarrier() {
        BusinessActionEnvelope e = assertDoesNotThrow(() -> envelope(PERMIT, null, PERMIT_REF, "permit-ref-0001"));
        assertEquals("permit-ref-0001", e.permitRef());
    }

    @Test
    void permitAndPermitRefMustBeExactlyOne() {
        assertThrows(IllegalArgumentException.class, () -> envelope(PERMIT, null));
        assertThrows(IllegalArgumentException.class,
                () -> envelope(PERMIT_REF, "permit-ref-0001"));
        assertThrows(IllegalArgumentException.class, () -> envelope(PERMIT_REF, " "));
    }

    @Test
    void missingRequiredScalarFailsFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> envelope(SCHEMA, "9.9"));
        invalid.add(() -> envelope(REQUEST_ID, " "));
        invalid.add(() -> envelope(SENT_AT_I, null));
        invalid.add(() -> envelope(TENANT, null));
        invalid.add(() -> envelope(ENV, ""));
        invalid.add(() -> envelope(ACTION_ID, " "));
        invalid.add(() -> envelope(ACTION_VERSION, null));
        invalid.add(() -> envelope(EFFECT_I, null));
        invalid.add(() -> envelope(AUTHZ_DIGEST, " "));
        invalid.add(() -> envelope(OPERATION_ID, null));
        invalid.add(() -> envelope(RESOURCE_REF, ""));
        invalid.add(() -> envelope(PURPOSE, " "));
        invalid.add(() -> envelope(PAYLOAD, null));
        invalid.add(() -> envelope(PAYLOAD_SCHEMA, " "));
        invalid.add(() -> envelope(EFFECT_DIGEST, ""));
        invalid.add(() -> envelope(BINDING_DIGEST, null));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run);
        }
    }

    @Test
    void optionalFieldsRejectBlankButAcceptNull() {
        int[] optional = {ATTEMPT, DISPATCH, IDEMPOTENCY_KEY, EVIDENCE_REF, PRECONDITIONS_HASH,
                TRACE, CORRELATION, CAUSATION, ORIGINAL_BINDING};
        for (int i : optional) {
            assertThrows(IllegalArgumentException.class, () -> envelope(i, " "),
                    "可选组件索引 " + i + " 提供空白必须拒绝");
        }
    }

    @Test
    void preconditionsAcceptNullAndNonEmptyButRejectEmptyList() {
        ResourcePrecondition p = new ResourcePrecondition("ar.overdueInvoice", "INV-2026-0001",
                "7", null, null, "OVERDUE", SENT_AT);
        assertDoesNotThrow(() -> envelope(PRECONDITIONS, List.of(p)));
        assertThrows(IllegalArgumentException.class, () -> envelope(PRECONDITIONS, List.of()));
        // null 元素必须以本组统一紧致校验口径 IAE 拒绝，而非 List.copyOf 的 NPE
        assertThrows(IllegalArgumentException.class,
                () -> envelope(PRECONDITIONS, java.util.Arrays.asList(p, null)));
    }

    /** 修订 2026-08-16 增列组件：null=缺省合法；空 Map 是非法值（与 preconditions 列表同口径）。 */
    @Test
    void expectedResourceVersionsAcceptNullAndNonEmptyButRejectEmptyMap() {
        assertDoesNotThrow(() -> envelope(EXPECTED_VERSIONS, java.util.Map.of("INV-2026-0001", "7")));
        assertThrows(IllegalArgumentException.class,
                () -> envelope(EXPECTED_VERSIONS, java.util.Map.of()),
                "空 Map 是非法值（无版本重检语义用 null 表达）");
        // null 值必须以本组统一紧致校验口径 IAE 拒绝，而非 Map.copyOf 的 NPE
        assertThrows(IllegalArgumentException.class, () -> envelope(EXPECTED_VERSIONS,
                java.util.Collections.singletonMap("INV-2026-0001", null)));
    }
}
