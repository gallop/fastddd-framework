package com.gallop.fastddd.agentaccess.arch.violation;

import com.gallop.fastagent.FastAgentSideStub;

/**
 * 违例样例：agent-access 包内类型引用 FastAgent 侧类型（AST-06 反例；仅测试导入用）。
 */
public final class ReferringToFastAgent {

    private ReferringToFastAgent() {
    }

    /** 违例依赖：主制品不得依赖 com.gallop.fastagent..（ADR-007 L3 边界）。 */
    @SuppressWarnings("unused")
    static FastAgentSideStub illegalReference() {
        throw new UnsupportedOperationException("违例样例");
    }
}
