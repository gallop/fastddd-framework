package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 两段式授权 Port 入参 record 紧致校验测试（TM-19：事务外预检 + 事务内终检的
 * 入参缺失必须 fail-fast，不得以缺省值进入判定）。
 */
class TwoPhaseAuthorizationRecordsTest {

    private static CallerContext caller() {
        return new CallerContext(
                "0.1", "subject-alice", com.gallop.fastddd.agentaccess.contract.SubjectType.HUMAN,
                "agent-ar-collector", com.gallop.fastddd.agentaccess.contract.ActorType.AGENT,
                "workload-egress-1", "acme", "dev", null,
                OffsetDateTime.parse("2026-08-16T03:00:00Z"), null);
    }

    @Test
    void validAdmissionQueryPasses() {
        assertDoesNotThrow(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), "ar.dunning.send-reminder", "1.0", Effect.WRITE));
    }

    @Test
    void admissionQueryMissingRequiredFieldsFailsFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                null, "ar.dunning.send-reminder", "1.0", Effect.WRITE));
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), null, "1.0", Effect.WRITE));
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), " ", "1.0", Effect.WRITE));
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), "ar.dunning.send-reminder", null, Effect.WRITE));
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), "ar.dunning.send-reminder", "\t", Effect.WRITE));
        invalid.add(() -> new TargetAdmissionAuthorizationPort.AdmissionQuery(
                caller(), "ar.dunning.send-reminder", "1.0", null));
        assertAll(invalid.stream().map(r -> () -> assertThrows(IllegalArgumentException.class, r::run)));
    }

    @Test
    void validResourceCheckPasses() {
        assertDoesNotThrow(() -> new ResourceAuthorizationPort.ResourceCheck(
                "acme", "ArInvoice", "INV-001"));
    }

    @Test
    void resourceCheckMissingRequiredFieldsFailsFast() {
        List<Runnable> invalid = new ArrayList<>();
        invalid.add(() -> new ResourceAuthorizationPort.ResourceCheck(null, "ArInvoice", "INV-001"));
        invalid.add(() -> new ResourceAuthorizationPort.ResourceCheck(" ", "ArInvoice", "INV-001"));
        invalid.add(() -> new ResourceAuthorizationPort.ResourceCheck("acme", null, "INV-001"));
        invalid.add(() -> new ResourceAuthorizationPort.ResourceCheck("acme", "ArInvoice", null));
        invalid.add(() -> new ResourceAuthorizationPort.ResourceCheck("acme", "ArInvoice", "  "));
        assertAll(invalid.stream().map(r -> () -> assertThrows(IllegalArgumentException.class, r::run)));
    }
}
