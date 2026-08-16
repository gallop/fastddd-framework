package com.gallop.fastddd.agentaccess;

import org.junit.jupiter.api.Test;

/**
 * reactor 骨架冒烟测试（FDD-AA-10 subtask 7.1）。
 *
 * <p>唯一目的：验证 JUnit5 + Surefire 接线在本模块可用（骨架期占位，
 * 协议 DTO 测试由 7.2 充实，负向语义测试由 7.5 充实）。</p>
 */
class ReactorSmokeTest {

    @Test
    void surefireWiringIsFunctional() {
        // 纯 JDK 模块（无 compile 依赖）——骨架期无可断言的领域类型
        org.junit.jupiter.api.Assertions.assertTrue(
                Runtime.version().feature() >= 21,
                "agent-access requires JDK 21+");
    }
}
