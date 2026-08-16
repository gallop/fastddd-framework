package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActionOutcomeCode;
import com.gallop.fastddd.agentaccess.contract.BusinessActionDescriptor;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * StaticDescriptorRegistry AST-05 负向测试：未注册/disabled/版本漂移
 * （高于与低于已发布版本）统一 ACTION_NOT_EXPOSED；registeredCount=显式注册数
 * （无扫描增量——静态注册器不存在发现路径）；重复注册拒绝。
 */
class StaticDescriptorRegistryTest {

    private static BusinessActionDescriptor writeAction(String actionId, String actionVersion, boolean enabled) {
        return ReferenceDescriptorFixtures.variantOf(
                ReferenceDescriptorFixtures.referenceWriteAction(), actionId, actionVersion, enabled);
    }

    @Test
    void registeredEnabledActionResolvesToSameDescriptor() {
        BusinessActionDescriptor descriptor = writeAction("ar.reference.write-sample", "1.0", true);
        StaticDescriptorRegistry registry = StaticDescriptorRegistry.builder().register(descriptor).build();
        assertSame(descriptor, registry.resolve("ar.reference.write-sample", "1.0"));
        assertEquals(1, registry.registeredCount());
    }

    @Test
    void ast05AllRejectionInputsYieldActionNotExposed() {
        BusinessActionDescriptor v1 = writeAction("ar.reference.write-sample", "1.0", true);
        BusinessActionDescriptor v2 = writeAction("ar.reference.write-sample", "2.0", true);
        BusinessActionDescriptor disabled = writeAction("ar.reference.disabled-sample", "1.0", false);
        StaticDescriptorRegistry registry = StaticDescriptorRegistry.builder()
                .register(v1).register(v2).register(disabled).build();

        assertAll("未注册 / disabled / 版本高于与低于已发布 → 统一 ACTION_NOT_EXPOSED",
                () -> {
                    ActionNotExposedException unregistered =
                            assertThrows(ActionNotExposedException.class,
                                    () -> registry.resolve("ar.reference.never-registered", "1.0"));
                    assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, unregistered.outcomeCode());
                },
                () -> {
                    ActionNotExposedException off =
                            assertThrows(ActionNotExposedException.class,
                                    () -> registry.resolve("ar.reference.disabled-sample", "1.0"));
                    assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, off.outcomeCode());
                },
                () -> {
                    ActionNotExposedException higher =
                            assertThrows(ActionNotExposedException.class,
                                    () -> registry.resolve("ar.reference.write-sample", "3.0"));
                    assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, higher.outcomeCode());
                },
                () -> {
                    ActionNotExposedException lower =
                            assertThrows(ActionNotExposedException.class,
                                    () -> registry.resolve("ar.reference.write-sample", "0.9"));
                    assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, lower.outcomeCode());
                });
        // 外部码统一；内部细因区分仅供审计（descriptor.versionDrifted vs unregistered/disabled）
        assertEquals("descriptor.version.drifted",
                assertThrows(ActionNotExposedException.class,
                        () -> registry.resolve("ar.reference.write-sample", "3.0")).internalReason().code());
    }

    @Test
    void blankIdentityResolvesToNotRegistered() {
        StaticDescriptorRegistry registry = StaticDescriptorRegistry.builder()
                .register(writeAction("ar.reference.write-sample", "1.0", true)).build();
        assertAll("缺失标识/版本无法证明已显式发布",
                () -> assertThrows(ActionNotExposedException.class, () -> registry.resolve(null, "1.0")),
                () -> assertThrows(ActionNotExposedException.class, () -> registry.resolve(" ", "1.0")),
                () -> assertThrows(ActionNotExposedException.class,
                        () -> registry.resolve("ar.reference.write-sample", null)),
                () -> assertThrows(ActionNotExposedException.class,
                        () -> registry.resolve("ar.reference.write-sample", "  ")));
    }

    @Test
    void duplicateRegistrationIsRejectedNotOverwritten() {
        assertAll("重复 (actionId, actionVersion) 注册拒绝（禁覆盖式 put）",
                () -> assertThrows(IllegalArgumentException.class, () -> StaticDescriptorRegistry.builder()
                        .register(writeAction("ar.reference.write-sample", "1.0", true))
                        .register(writeAction("ar.reference.write-sample", "1.0", true))
                        .build()),
                // 同 actionId 不同 version 允许共存（多版本发布是显式行为）
                () -> StaticDescriptorRegistry.builder()
                        .register(writeAction("ar.reference.write-sample", "1.0", true))
                        .register(writeAction("ar.reference.write-sample", "2.0", true))
                        .build());
    }

    @Test
    void registeredCountEqualsExplicitRegistrationsWithoutScanDelta() {
        StaticDescriptorRegistry registry = StaticDescriptorRegistry.builder()
                .register(writeAction("ar.reference.a", "1.0", true))
                .register(writeAction("ar.reference.b", "1.0", true))
                .register(writeAction("ar.reference.c", "1.0", false))
                .build();
        // 行数=显式注册数（含 disabled 条目——注册数而非可用数）；
        // 类路径上不存在扫描入口，不存在运行时增量
        assertEquals(3, registry.registeredCount());
    }

    @Test
    void atSignKeyCollisionResolvesToNotRegisteredInsteadOfWrongDescriptor() {
        // exactKey("...a@b","c") 与 exactKey("...a","b@c") 拼出同串：lookup 会
        // 命中他人条目——命中后 (id, version) 一致性校验兜底为视同未命中，
        // 不得返回 actionId 不匹配的描述符（保守失败）
        StaticDescriptorRegistry registry = StaticDescriptorRegistry.builder()
                .register(writeAction("ar.reference.a@b", "c", true)).build();
        ActionNotExposedException collision = assertThrows(ActionNotExposedException.class,
                () -> registry.resolve("ar.reference.a", "b@c"));
        assertEquals(ActionOutcomeCode.ACTION_NOT_EXPOSED, collision.outcomeCode());
        assertEquals("descriptor.unregistered", collision.internalReason().code());
    }
}
