package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.ActorType;
import com.gallop.fastddd.agentaccess.contract.AgentAccessProtocolVersions;
import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.contract.SubjectType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Caller 入口治理负向（AST-04 骨架期形态）。
 *
 * <p>完整运行时负向（Agent 请求经桥接取得人工归因后在编排层被拒绝）属
 * FDD-AA-12 编排与 .arch 装配规则；本测试固定接口层的结构防线。</p>
 */
class CallerEntranceGovernanceTest {

    @Test
    void identityBridgeIsNotACallerEntrance() {
        // 桥接不得实现/继承 Agent 路径唯一构造入口——类型分离是「桥接不作
        // Agent Caller 来源」的结构前提，运行时拒绝由编排层叠加（AST-04 补充负向）
        assertFalse(VerifiedCaller.class.isAssignableFrom(CallerIdentityBridge.class),
                "CallerIdentityBridge 不得成为 VerifiedCaller 的子类型");
        assertFalse(CallerIdentityBridge.class.isAssignableFrom(VerifiedCaller.class),
                "VerifiedCaller 不得成为 CallerIdentityBridge 的子类型");
    }

    @Test
    void systemJobCallerIsExplicitAndNonAnonymous() {
        // SYSTEM_JOB 不变量 4 正测样板：责任链字段全部显式提供、身份证据可引用。
        // 「不存在省略身份的构造形态」的负向防线在 .contract 批 CallerContextTest
        // （紧致校验逐字段拒绝 null/blank），此处不重复。
        CallerContext systemJob = new CallerContext(
                AgentAccessProtocolVersions.WIRE_SCHEMA_VERSION,
                "svc-billing", SubjectType.SERVICE,
                "job-nightly-dunning", ActorType.SYSTEM_JOB,
                "workload-batch-1", "acme", "prod",
                null, OffsetDateTime.parse("2026-08-16T01:00:00Z"), "idp-ev-#4102");

        assertEquals(ActorType.SYSTEM_JOB, systemJob.actorType());
        assertEquals("job-nightly-dunning", systemJob.actorId());
        assertEquals("workload-batch-1", systemJob.workloadId());
        assertEquals("idp-ev-#4102", systemJob.identityEvidenceRef());
    }
}
