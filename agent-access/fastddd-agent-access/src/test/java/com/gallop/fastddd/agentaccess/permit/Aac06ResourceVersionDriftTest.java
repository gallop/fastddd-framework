package com.gallop.fastddd.agentaccess.permit;

import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.Effect;
import com.gallop.fastddd.agentaccess.contract.ExecutionPermit;
import com.gallop.fastddd.agentaccess.contract.ReferenceDescriptorFixtures;
import com.gallop.fastddd.agentaccess.contract.ResourceConstraints;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import com.gallop.fastddd.agentaccess.spi.PermitBindingView;
import com.gallop.fastddd.agentaccess.spi.PermitVerifierConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AAC-06 本仓半边：审批后资源版本漂移在出口前的防线——许可声明的
 * {@code expectedResourceVersions} 与执行时绑定投影逐项一致，值漂移即
 * {@code PERMIT_BINDING_MISMATCH} 拒绝（旧批准不覆盖新版本的事实）。
 *
 * <p><b>验收边界如实标注</b>：AAC-06 完整收敛形态
 * （{@code PRECONDITION_CHANGED}/{@code RESOURCE_VERSION_CONFLICT} +
 * 业务表前后快照 0 变更）依赖事务内资源终检编排（Contract §7 步骤 9/11），
 * 归宿主 conformance 与 V04-09；本测试证明的是骨架期唯一可用防线——
 * 版本声明进入许可绑定面，漂移不可静默通过。</p>
 */
class Aac06ResourceVersionDriftTest {

    private static final Instant NOW = Instant.parse("2026-08-16T03:05:00Z");
    private static final String TRUSTED_ISSUER = "https://issuer.fastagent.example";
    private static final String AUDIENCE = "fastddd-ar-service";

    private static CallerContext caller() {
        return new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "subject-alice", SubjectType.HUMAN,
                "agent-ar-collector", ActorType.AGENT,
                "workload-egress-1", "acme", "dev", null,
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(5)), ZoneOffset.UTC),
                null);
    }

    private static ExecutionPermit permitWithExpectedVersion(String version) {
        return new ExecutionPermit(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "permit-001", TRUSTED_ISSUER, AUDIENCE, caller(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionId(),
                ReferenceDescriptorFixtures.referenceWriteAction().actionVersion(),
                Effect.WRITE, new ResourceConstraints(Set.of("ReferenceThing"), null, null),
                "reference.testing", "bop-2026-0001",
                "sha256:auth", "evidence://fastagent/grant-1", "sha256:effect", "sha256:binding",
                OffsetDateTime.ofInstant(NOW.minus(Duration.ofMinutes(1)), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(NOW.plus(Duration.ofMinutes(10)), ZoneOffset.UTC),
                "idem-1", null, Map.of("ReferenceThing/REF-001", version),
                null, null, null, null);
    }

    private static PermitBindingView bindingWithExpectedVersion(ExecutionPermit p, String version) {
        return new PermitBindingView(
                p.caller().tenantId(), p.caller().environment(), p.actionId(), p.actionVersion(),
                p.effect(), p.purpose(), p.businessOperationId(),
                p.externalIdempotencyKey(), p.preconditionsHash(),
                version == null ? null : Map.of("ReferenceThing/REF-001", version),
                p.effectDigest(), p.authorizationDigest(), p.authorizationBindingDigest());
    }

    private static DefaultExecutionPermitVerifier verifier() {
        return new DefaultExecutionPermitVerifier(new PermitVerifierConfig(
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of(TRUSTED_ISSUER), AUDIENCE,
                Duration.ofMinutes(1), false));
    }

    @Test
    void resourceVersionValueDriftIsRejectedAsBindingMismatch() {
        // 审批时声明版本 42；执行时资源已演进为 43 —— 旧批准不覆盖新事实
        assertThrows(com.gallop.fastddd.agentaccess.spi.PermitVerificationException.class,
                () -> verifier().verify(permitWithExpectedVersion("42"), bindingWithExpectedVersion(
                        permitWithExpectedVersion("42"), "43")));
    }

    @Test
    void resourceVersionKeyRemovalIsRejectedAndMatchingVersionPasses() {
        ExecutionPermit permit = permitWithExpectedVersion("42");
        // 投影丢弃版本声明（单边缺失）→ 拒绝（presence-sensitive，不得默认通过）
        assertThrows(com.gallop.fastddd.agentaccess.spi.PermitVerificationException.class,
                () -> verifier().verify(permit, bindingWithExpectedVersion(permit, null)));
        // 一致版本通过
        verifier().verify(permit, bindingWithExpectedVersion(permit, "42"));
    }
}
