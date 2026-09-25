package com.gallop.fastddd.consumer.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-1 反例（§4.3 条 3，防"以为有会话防护实际没有"）：宿主自建 SecurityFilterChain
 * 使 identity 链 @CoM 退让，但未声明 {@code fastddd.identity.security.host-owned=true}
 * ——identity 终检（SmartInitializingSingleton）使启动失败并指明须显式声明。
 */
class UndeclaredHostChainFailsConsumerTest {

    @SpringBootApplication
    @Import({HostSecuritySupport.class, HostChainSupport.class})
    static class App {
    }

    @Test
    void startupFailsWhenHostChainExistsWithoutDeclaration() {
        assertThatThrownBy(() -> SpringApplication.run(App.class, "--server.port=0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("host-owned");
    }
}
