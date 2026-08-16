package com.gallop.fastddd.agentaccess.spi;

import com.gallop.fastddd.agentaccess.contract.CallerContext;
import com.gallop.fastddd.agentaccess.gateway.DefaultAgentAccessGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AAC-12：FastAgent 不可用时，普通人工入口按 FastDDD 自身授权工作——
 * 人工/系统作业路径经 {@link CallerIdentityBridge} 从宿主已验证上下文桥接，
 * <b>结构上不依赖任何 FastAgent 治理类型</b>（Permit/Verifier/Gateway）。
 *
 * <p>骨架期以结构断言承载（桥接运行时行为归宿主装配；本仓不实现宿主
 * 安全上下文适配）。断言面：桥接接口签名与治理网关编排依赖均无
 * Permit 类型耦合——人工入口断供 FastAgent 时仍可独立装配。</p>
 */
class Aac12HumanEntranceIndependenceTest {

    @Test
    void bridgeContractCarriesNoPermitCoupling() {
        for (Method method : CallerIdentityBridge.class.getDeclaredMethods()) {
            assertEquals(CallerContext.class, method.getReturnType(),
                    "桥接产出是宿主 CallerContext，不是治理载体");
            for (Parameter parameter : method.getParameters()) {
                assertNoPermitType(parameter.getType());
            }
            for (Class<?> exceptionType : method.getExceptionTypes()) {
                assertNoPermitType(exceptionType);
            }
        }
    }

    @Test
    void gatewayOrchestrationDoesNotDependOnTheBridge() {
        // 治理网关编排依赖（verifier/registry/admissionPort）不含桥接：
        // 人工入口不被迫经过 Agent 治理链，也不被网关反向依赖形成环。
        // （网关自身依赖 Permit 治理类型是设计使然——那是 Agent 路径；此处只断言桥接缺席）
        List<Class<?>> dependencies = Arrays.stream(
                        DefaultAgentAccessGateway.class.getDeclaredConstructors()[0].getParameterTypes())
                .toList();
        assertTrue(dependencies.stream().noneMatch(t -> t == CallerIdentityBridge.class),
                "网关编排不得依赖人工桥接（两类入口结构分离）");
    }

    private static void assertNoPermitType(Class<?> type) {
        assertTrue(!type.getName().contains("Permit"),
                "人工入口结构面不得出现 Permit 治理类型：" + type.getName());
    }
}
