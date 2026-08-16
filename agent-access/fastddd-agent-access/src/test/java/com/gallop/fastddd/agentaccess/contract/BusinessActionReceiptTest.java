package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §5.6 十八字段紧致校验 + §9「业务提交」列跨字段形式化：
 * 仅 ALREADY_COMPLETED 可携带三提交字段（成功时必选、未提交态禁携）。
 */
class BusinessActionReceiptTest {

    private static final int SCHEMA = 0, TENANT = 1, OPERATION = 2, ACTION_ID = 3, ACTION_VERSION = 4,
            EFFECT_DIGEST = 5, ISSUER = 6, AUDIENCE = 7, BINDING_REF = 8, INTEGRITY_PROOF = 9,
            OUTCOME_CODE = 10, COMMAND_REF = 11, AGGREGATE_REF = 12, RESOURCE_VERSION_AFTER = 13,
            COMMITTED_AT = 14, RETRY_DISPOSITION = 15, TRACE_ID = 16, RESPONSE_PROJECTION = 17;

    private static final OffsetDateTime COMMITTED =
            OffsetDateTime.of(2026, 8, 16, 3, 5, 0, 0, ZoneOffset.UTC);

    /** 变长参数变异工厂：18 个基线值（已提交态）按 (index, value) 成对替换。 */
    static BusinessActionReceipt receipt(Object... overrides) {
        Object[] a = {
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "acme",
                "bop-2026-0001",
                "ar.createCrmFollowup",
                "1",
                "sha256:effect",
                "https://fastddd.target.example",
                "fastddd-ar-service",
                "binding-2026-08-16-001",
                "hmac-sha256:proof",
                ActionOutcomeCode.ALREADY_COMPLETED,
                "cmd-0f1e2d3c",
                "arFollowup/FOLLOW-001",
                "8",
                COMMITTED,
                RetryDisposition.DO_NOT_RETRY,
                "trace-77",
                null};
        for (int i = 0; i < overrides.length; i += 2) {
            a[(Integer) overrides[i]] = overrides[i + 1];
        }
        return new BusinessActionReceipt(
                (String) a[SCHEMA], (String) a[TENANT], (String) a[OPERATION], (String) a[ACTION_ID],
                (String) a[ACTION_VERSION], (String) a[EFFECT_DIGEST], (String) a[ISSUER], (String) a[AUDIENCE],
                (String) a[BINDING_REF], (String) a[INTEGRITY_PROOF], (ActionOutcomeCode) a[OUTCOME_CODE],
                (String) a[COMMAND_REF], (String) a[AGGREGATE_REF], (String) a[RESOURCE_VERSION_AFTER],
                (OffsetDateTime) a[COMMITTED_AT], (RetryDisposition) a[RETRY_DISPOSITION],
                (String) a[TRACE_ID], (Map<String, String>) a[RESPONSE_PROJECTION]);
    }

    @Test
    void committedPathAcceptsFullReceipt() {
        assertDoesNotThrow(() -> receipt());
        // LOCAL_OBJECT 同 JVM：integrityProof 可省（跨信任边界必选由验证层强制，javadoc 冻结）
        assertDoesNotThrow(() -> receipt(INTEGRITY_PROOF, null));
    }

    @Test
    void committedOutcomeRequiresAllCommitFields() {
        List<Runnable> missing = List.of(
                () -> receipt(COMMAND_REF, null),
                () -> receipt(AGGREGATE_REF, " "),
                () -> receipt(COMMITTED_AT, null));
        for (Runnable r : missing) {
            assertThrows(IllegalArgumentException.class, r::run, "已提交态三字段必选");
        }
    }

    @Test
    void nonCommittedOutcomeForbidsCommitFields() {
        // §9 业务提交列：TARGET_TEMPORARILY_UNAVAILABLE=可证明未提交，其余拒绝码=否，UNKNOWN=不定
        List<ActionOutcomeCode> nonCommitted = List.of(
                ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED,
                ActionOutcomeCode.IDEMPOTENCY_CONFLICT,
                ActionOutcomeCode.TARGET_TEMPORARILY_UNAVAILABLE,
                ActionOutcomeCode.UNKNOWN);
        for (ActionOutcomeCode code : nonCommitted) {
            assertThrows(IllegalArgumentException.class,
                    () -> receipt(OUTCOME_CODE, code), code + " 禁携提交字段（基线含三提交字段）");
        }
        // 未提交态去三提交字段后合法
        assertDoesNotThrow(() -> receipt(OUTCOME_CODE, ActionOutcomeCode.TARGET_TEMPORARILY_UNAVAILABLE,
                COMMAND_REF, null, AGGREGATE_REF, null, COMMITTED_AT, null,
                RETRY_DISPOSITION, RetryDisposition.RETRY_SAME_ATTEMPT));
    }

    @Test
    void requiredFieldsFailClosed() {
        List<Runnable> invalid = List.of(
                () -> receipt(SCHEMA, "0.2"),
                () -> receipt(SCHEMA, null),
                () -> receipt(TENANT, " "),
                () -> receipt(OPERATION, null),
                () -> receipt(EFFECT_DIGEST, ""),
                () -> receipt(ISSUER, null),
                () -> receipt(AUDIENCE, " "),
                () -> receipt(BINDING_REF, null),
                () -> receipt(OUTCOME_CODE, null),
                () -> receipt(RETRY_DISPOSITION, null),
                () -> receipt(TRACE_ID, " "));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run);
        }
    }

    @Test
    void optionalFieldsRejectBlankButAcceptNull() {
        assertThrows(IllegalArgumentException.class, () -> receipt(INTEGRITY_PROOF, " "));
        assertThrows(IllegalArgumentException.class, () -> receipt(RESOURCE_VERSION_AFTER, ""));
        assertDoesNotThrow(() -> receipt(RESOURCE_VERSION_AFTER, null));
    }

    @Test
    void responseProjectionIsDefensiveCopyWithNullDistinctFromEmpty() {
        Map<String, String> original = new HashMap<>();
        original.put("followupId", "FOLLOW-001");
        BusinessActionReceipt withProjection = receipt(RESPONSE_PROJECTION, original);
        original.put("leaked", "secret");
        assertEquals(Map.of("followupId", "FOLLOW-001"), withProjection.responseProjection(),
                "构造后外部修改不得影响投影（copyOf 防御拷贝）");
        assertThrows(UnsupportedOperationException.class,
                () -> withProjection.responseProjection().put("x", "y"));
        // null=不提供 与 空映射=显式空投影 语义不同（§5.5 规则 2 先例），两者都合法
        // （空映射的 copyOf 由 JDK 归一共享单例，实例等同性不是本类型承诺，不断言）
        assertDoesNotThrow(() -> receipt(RESPONSE_PROJECTION, null));
        BusinessActionReceipt empty = receipt(RESPONSE_PROJECTION, Map.of());
        assertTrue(empty.responseProjection().isEmpty());
    }
}
