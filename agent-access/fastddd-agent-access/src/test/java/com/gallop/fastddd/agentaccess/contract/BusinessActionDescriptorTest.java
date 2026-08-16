package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BusinessActionDescriptor 紧致校验测试（Contract §5.3 字段表 + 跨字段
 * WRITE/TASK 必选约束 + AST-05 结构前置）+ 十二结果码/InternalReason 承载。
 */
class BusinessActionDescriptorTest {

    /** 无业务语义的 reference 样板（委托公共 fixture；同包测试复用）。 */
    static BusinessActionDescriptor referenceWriteAction() {
        return ReferenceDescriptorFixtures.referenceWriteAction();
    }

    /** 无业务语义的 reference 样板（READ：idempotency/reconciliation 可选）。 */
    static BusinessActionDescriptor referenceReadAction() {
        return ReferenceDescriptorFixtures.referenceReadAction();
    }

    @Test
    void validWriteAndReadDescriptorsPass() {
        assertDoesNotThrow(BusinessActionDescriptorTest::referenceWriteAction);
        assertDoesNotThrow(BusinessActionDescriptorTest::referenceReadAction);
    }

    @Test
    void writeAndTaskRequireIdempotencyAndReconciliationModes() {
        BusinessActionDescriptor write = referenceWriteAction();
        assertAll("§5.3：idempotencyMode/reconciliationMode 在 WRITE/TASK 必选",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(write.actionId(), write.actionVersion(), write.effect(),
                                write.resourceTypes(), write.inputSchemaRef(), write.outputSchemaRef(),
                                write.requiredPurposes(), write.dataClassification(),
                                null, write.preconditionMode(), write.reconciliationMode(),
                                write.timeoutClass(), write.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(write.actionId(), write.actionVersion(), write.effect(),
                                write.resourceTypes(), write.inputSchemaRef(), write.outputSchemaRef(),
                                write.requiredPurposes(), write.dataClassification(),
                                write.idempotencyMode(), write.preconditionMode(), null,
                                write.timeoutClass(), write.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(write.actionId(), write.actionVersion(), Effect.TASK,
                                write.resourceTypes(), write.inputSchemaRef(), write.outputSchemaRef(),
                                write.requiredPurposes(), write.dataClassification(),
                                null, write.preconditionMode(), write.reconciliationMode(),
                                write.timeoutClass(), write.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(write.actionId(), write.actionVersion(), Effect.TASK,
                                write.resourceTypes(), write.inputSchemaRef(), write.outputSchemaRef(),
                                write.requiredPurposes(), write.dataClassification(),
                                write.idempotencyMode(), write.preconditionMode(), null,
                                write.timeoutClass(), write.enabled())));
        // READ 允许两者缺省（referenceReadAction 构造成功已证）
    }

    @Test
    void missingRequiredFieldFailsFast() {
        BusinessActionDescriptor base = referenceWriteAction();
        assertAll("§5.3 必选字段逐项 null/blank/空集 fail-fast",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(null, base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(" ", base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), null, base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), null, base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), Set.of(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                null, base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), null, base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), Set.of(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                null, base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), null,
                                base.reconciliationMode(), base.timeoutClass(), base.enabled())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> descriptor(base.actionId(), base.actionVersion(), base.effect(), base.resourceTypes(),
                                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                                base.reconciliationMode(), null, base.enabled())));
    }

    @Test
    void outcomeCodesAreExactlyTheTwelveFrozenValues() {
        assertEquals(12, ActionOutcomeCode.values().length);
        assertTrue(java.util.Arrays.asList(ActionOutcomeCode.values()).containsAll(
                java.util.List.of(ActionOutcomeCode.TARGET_AUTHENTICATION_FAILED,
                        ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED, ActionOutcomeCode.ACTION_NOT_EXPOSED,
                        ActionOutcomeCode.PERMIT_INVALID_OR_EXPIRED, ActionOutcomeCode.PERMIT_BINDING_MISMATCH,
                        ActionOutcomeCode.PRECONDITION_CHANGED, ActionOutcomeCode.RESOURCE_VERSION_CONFLICT,
                        ActionOutcomeCode.BUSINESS_RULE_VIOLATION, ActionOutcomeCode.IDEMPOTENCY_CONFLICT,
                        ActionOutcomeCode.ALREADY_COMPLETED, ActionOutcomeCode.TARGET_TEMPORARILY_UNAVAILABLE,
                        ActionOutcomeCode.UNKNOWN)));
    }

    @Test
    void internalReasonEnforcesLowerDotCodeAndUniqueOutcome() {
        assertDoesNotThrow(() -> new InternalReason("target.authorization.denied",
                ActionOutcomeCode.TARGET_AUTHORIZATION_DENIED));
        assertAll("格式与映射约束",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason("Target.Authorization", ActionOutcomeCode.UNKNOWN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason("target..denied", ActionOutcomeCode.UNKNOWN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason("target-denied", ActionOutcomeCode.UNKNOWN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason("target.denied#", ActionOutcomeCode.UNKNOWN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason(null, ActionOutcomeCode.UNKNOWN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new InternalReason("target.denied", null)));
        // 多内→一外合法（AA-02 冻结规则的正向面）
        assertEquals(new InternalReason("descriptor.disabled", ActionOutcomeCode.ACTION_NOT_EXPOSED).outcome(),
                new InternalReason("descriptor.unregistered", ActionOutcomeCode.ACTION_NOT_EXPOSED).outcome());
    }

    private static BusinessActionDescriptor descriptor(String actionId, String actionVersion, Effect effect,
                                                        Set<String> resourceTypes, String inputSchemaRef,
                                                        String outputSchemaRef, Set<String> requiredPurposes,
                                                        String dataClassification, IdempotencyMode idempotencyMode,
                                                        PreconditionMode preconditionMode,
                                                        ReconciliationMode reconciliationMode,
                                                        String timeoutClass, boolean enabled) {
        return new BusinessActionDescriptor(actionId, actionVersion, effect, resourceTypes, inputSchemaRef,
                outputSchemaRef, requiredPurposes, dataClassification, idempotencyMode, preconditionMode,
                reconciliationMode, timeoutClass, enabled);
    }
}
