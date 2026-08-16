package com.gallop.fastddd.agentaccess.contract;

import java.util.Set;

/**
 * 无业务语义的 reference Descriptor 样板（FDD-AA-12 落点 1：reference fixture）。
 *
 * <p>命名空间 {@code ar.reference.*}：刻意避免伪装业务语义；跨测试包
 * （.contract/.spi/.gateway）复用的公共 fixture。生产动作描述符必须由宿主
 * 显式注册，不得引用本 fixture。</p>
 */
public final class ReferenceDescriptorFixtures {

    private ReferenceDescriptorFixtures() {
    }

    /** WRITE 样板（全字段齐备，已启用）。 */
    public static BusinessActionDescriptor referenceWriteAction() {
        return new BusinessActionDescriptor(
                "ar.reference.write-sample",
                "1.0",
                Effect.WRITE,
                Set.of("ReferenceThing"),
                "schema://reference/write-input@1",
                "schema://reference/write-output@1",
                Set.of("reference.testing"),
                "INTERNAL",
                IdempotencyMode.REQUIRED,
                PreconditionMode.REQUIRED,
                ReconciliationMode.QUERYABLE,
                "SHORT",
                true);
    }

    /** READ 样板（idempotency/reconciliation 可缺省，已启用）。 */
    public static BusinessActionDescriptor referenceReadAction() {
        return new BusinessActionDescriptor(
                "ar.reference.read-sample",
                "1.0",
                Effect.READ,
                Set.of("ReferenceThing"),
                "schema://reference/read-input@1",
                "schema://reference/read-output@1",
                Set.of("reference.testing"),
                "INTERNAL",
                null,
                PreconditionMode.NOT_APPLICABLE,
                null,
                "SHORT",
                true);
    }

    /** 按样板派生（覆盖标识/版本/启用位）。 */
    public static BusinessActionDescriptor variantOf(BusinessActionDescriptor base, String actionId,
                                                     String actionVersion, boolean enabled) {
        return new BusinessActionDescriptor(actionId, actionVersion, base.effect(), base.resourceTypes(),
                base.inputSchemaRef(), base.outputSchemaRef(), base.requiredPurposes(),
                base.dataClassification(), base.idempotencyMode(), base.preconditionMode(),
                base.reconciliationMode(), base.timeoutClass(), enabled);
    }
}
