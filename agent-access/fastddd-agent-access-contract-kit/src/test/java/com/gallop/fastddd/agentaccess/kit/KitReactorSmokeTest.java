package com.gallop.fastddd.agentaccess.kit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * kit 模块骨架冒烟测试（FDD-AA-10 subtask 7.1）。
 *
 * <p>验证 kit → 主制品 compile 依赖与 AssertJ/Spring 类型接线；
 * AgentAccessAssemblyValidator 测试由 7.4 充实。</p>
 */
class KitReactorSmokeTest {

    @Test
    void kitConsumsCoreArtifactAndSpringContext() {
        // spring-context（Validator 载体）可达
        assertThat(org.springframework.beans.factory.SmartInitializingSingleton.class)
                .isInterface();
        // 主制品类型可达（当前仅有顶层命名空间占位断言；7.2 起替换为协议类型）
        assertThat("com.gallop.fastddd.agentaccess").startsWith("com.gallop.fastddd.");
    }
}
