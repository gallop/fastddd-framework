package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermitCarrier;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Agent 路径唯一 Caller 构造入口的拒绝语义（AST-04 骨架期负向：无匿名兜底）。
 */
class VerifiedCallerSpiTest {

    private static CallerContext agentCaller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice",
                SubjectType.HUMAN,
                "agent-ar-collector",
                ActorType.AGENT,
                "workload-egress-1",
                "acme",
                "dev",
                null,
                OffsetDateTime.parse("2026-08-16T03:00:00Z"),
                null);
    }

    @Test
    void verifiedPermitYieldsPermitCarriedCaller() {
        CallerContext caller = agentCaller();
        ExecutionPermitCarrier permit = () -> caller;
        CallerContext result = new PermitBackedVerifiedCaller().fromVerifiedPermit(permit);
        assertSame(caller, result);
    }

    @Test
    void missingPermitIsRejectedWithoutFallback() {
        CallerAuthenticationException ex = assertThrows(CallerAuthenticationException.class,
                () -> new PermitBackedVerifiedCaller().fromVerifiedPermit(null));
        assertEquals("Agent 路径拒绝：许可缺失，无已验证 caller 来源", ex.getMessage());
    }

    @Test
    void permitWithoutCallerIsRejectedWithoutFallback() {
        ExecutionPermitCarrier permit = () -> null;
        assertThrows(CallerAuthenticationException.class,
                () -> new PermitBackedVerifiedCaller().fromVerifiedPermit(permit));
    }
}
