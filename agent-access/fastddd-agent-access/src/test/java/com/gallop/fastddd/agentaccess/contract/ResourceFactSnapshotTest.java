package com.gallop.fastddd.agentaccess.contract;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ResourceFactSnapshot §13 样板八字段紧致校验：全必选（六非空白 + version/amount 非 null）；
 * 快照仅授权输入、不替代事务重读由 javadoc 表达（类型层无行为）。
 */
class ResourceFactSnapshotTest {

    private static ResourceFactSnapshot reference() {
        return new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "OVERDUE",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", 100L);
    }

    @Test
    void referenceSampleShapePasses() {
        ResourceFactSnapshot s = reference();
        assertEquals("ar.overdueInvoice", s.resourceType());
        assertEquals(Long.valueOf(7L), s.version());
        assertEquals("acme", s.tenantId());
        assertEquals(Long.valueOf(100L), s.amount());
    }

    @Test
    void missingAnyRequiredFieldFailsFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> new ResourceFactSnapshot(null, "INV-2026-0001", 7L, "OVERDUE",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", " ", 7L, "OVERDUE",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", null, "OVERDUE",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "OVERDUE",
                null, "CUS-10423", "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "OVERDUE",
                "acme", null, "CUSTOMER_FINANCIAL", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "OVERDUE",
                "acme", "CUS-10423", "  ", 100L));
        invalid.add(() -> new ResourceFactSnapshot("ar.overdueInvoice", "INV-2026-0001", 7L, "OVERDUE",
                "acme", "CUS-10423", "CUSTOMER_FINANCIAL", null));
        for (Runnable r : invalid) {
            assertThrows(IllegalArgumentException.class, r::run);
        }
    }
}
