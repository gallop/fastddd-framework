package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ResourcePrecondition §5.4 紧致校验：三必选字段 + capturedAt；
 * 可选项提供状态由验证器比对（本类型不预判提供语义）。
 */
class ResourcePreconditionTest {

    private static ResourcePrecondition reference() {
        return new ResourcePrecondition("ar.overdueInvoice", "INV-2026-0001",
                "7", "snap-001", "d41d8cd9", "OVERDUE", OffsetDateTime.parse("2026-08-16T00:00:00Z"));
    }

    @Test
    void validPreconditionPassesWithAllOptionalFields() {
        ResourcePrecondition p = reference();
        assertEquals("ar.overdueInvoice", p.resourceType());
        assertEquals("INV-2026-0001", p.resourceId());
        assertEquals("7", p.expectedVersion());
        assertEquals("OVERDUE", p.requiredState());
    }

    @Test
    void minimalPreconditionWithoutOptionalFieldsPasses() {
        // 按 Descriptor：expectedVersion/factSnapshotRef/factDigest/requiredState 全可选
        assertDoesNotThrow(() -> new ResourcePrecondition(
                "ar.overdueInvoice", "INV-2026-0001", null, null, null, null,
                OffsetDateTime.parse("2026-08-16T00:00:00Z")));
    }

    @Test
    void missingRequiredFieldsFailFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> new ResourcePrecondition(null, "INV-2026-0001", null, null, null, null,
                OffsetDateTime.parse("2026-08-16T00:00:00Z")));
        invalid.add(() -> new ResourcePrecondition(" ", "INV-2026-0001", null, null, null, null,
                OffsetDateTime.parse("2026-08-16T00:00:00Z")));
        invalid.add(() -> new ResourcePrecondition("ar.overdueInvoice", null, null, null, null, null,
                OffsetDateTime.parse("2026-08-16T00:00:00Z")));
        invalid.add(() -> new ResourcePrecondition("ar.overdueInvoice", "  ", null, null, null, null,
                OffsetDateTime.parse("2026-08-16T00:00:00Z")));
        invalid.add(() -> new ResourcePrecondition("ar.overdueInvoice", "INV-2026-0001",
                null, null, null, null, null));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run);
        }
    }
}
