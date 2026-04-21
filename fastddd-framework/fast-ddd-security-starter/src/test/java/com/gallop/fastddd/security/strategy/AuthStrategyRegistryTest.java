package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.channel.AuthChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证策略注册中心 + 多策略登录测试
 *
 * @author gallop
 * @date 2026-04-21
 */
class AuthStrategyRegistryTest {

    private AuthStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        List<AuthStrategy> strategies = Arrays.asList(
                new MockPasswordStrategy(),
                new MockSmsStrategy(),
                new MockWechatStrategy()
        );
        registry = new AuthStrategyRegistry(strategies);
    }

    // ---- AuthStrategyRegistry 测试 ----

    @Test
    void testGetStrategyByType() {
        assertNotNull(registry.getStrategy("password"));
        assertNotNull(registry.getStrategy("sms"));
        assertNotNull(registry.getStrategy("wechat-mini"));
        assertNull(registry.getStrategy("unknown"));
    }

    @Test
    void testRegistryWithNullStrategies() {
        AuthStrategyRegistry emptyRegistry = new AuthStrategyRegistry(null);
        assertNull(emptyRegistry.getStrategy("password"));
    }

    @Test
    void testRegistryWithEmptyList() {
        AuthStrategyRegistry emptyRegistry = new AuthStrategyRegistry(Collections.emptyList());
        assertNull(emptyRegistry.getStrategy("password"));
    }

    @Test
    void testGetStrategiesForChannel() {
        // wechat-mini 不支持 ADMIN 通道
        List<AuthStrategy> adminStrategies = registry.getStrategiesForChannel(AuthChannel.ADMIN);
        assertEquals(2, adminStrategies.size()); // password + sms

        List<AuthStrategy> customerStrategies = registry.getStrategiesForChannel(AuthChannel.CUSTOMER);
        assertEquals(3, customerStrategies.size()); // password + sms + wechat-mini
    }

    // ---- AuthenticationRequest 测试 ----

    @Test
    void testAuthenticationRequestExtraParams() {
        AuthenticationRequest request = new AuthenticationRequest();
        assertNull(request.getExtraParam("smsCode"));

        Map<String, String> extra = new HashMap<>();
        extra.put("smsCode", "123456");
        request.setExtra(extra);

        assertEquals("123456", request.getExtraParam("smsCode"));
        assertNull(request.getExtraParam("wechatCode"));
    }

    // ---- 多策略认证执行测试 ----

    @Test
    void testPasswordStrategyAuthenticate() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        AuthStrategy strategy = registry.getStrategy("password");
        Authentication auth = strategy.authenticate(request);

        assertEquals("admin", auth.getName());
    }

    @Test
    void testSmsStrategyAuthenticate() {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("13800138000");
        Map<String, String> extra = new HashMap<>();
        extra.put("smsCode", "123456");
        request.setExtra(extra);

        AuthStrategy strategy = registry.getStrategy("sms");
        Authentication auth = strategy.authenticate(request);

        assertEquals("13800138000", auth.getName());
    }

    @Test
    void testWechatStrategyAuthenticate() {
        AuthenticationRequest request = new AuthenticationRequest();
        Map<String, String> extra = new HashMap<>();
        extra.put("wechatCode", "0a1b2c3d");
        request.setExtra(extra);

        AuthStrategy strategy = registry.getStrategy("wechat-mini");
        Authentication auth = strategy.authenticate(request);

        assertEquals("wechat_user_001", auth.getName());
    }

    @Test
    void testWechatStrategyNotSupportAdmin() {
        MockWechatStrategy wechat = new MockWechatStrategy();
        assertFalse(wechat.supports(AuthChannel.ADMIN));
        assertTrue(wechat.supports(AuthChannel.CUSTOMER));
        assertTrue(wechat.supports(AuthChannel.MERCHANT));
        assertTrue(wechat.supports(AuthChannel.DEFAULT));
    }

    // ---- Mock 策略实现 ----

    static class MockPasswordStrategy implements AuthStrategy {
        @Override
        public String getType() { return "password"; }

        @Override
        public Authentication authenticate(AuthenticationRequest request) {
            return new TestingAuthenticationToken(request.getUsername(), null, "ROLE_USER");
        }
    }

    static class MockSmsStrategy implements AuthStrategy {
        @Override
        public String getType() { return "sms"; }

        @Override
        public Authentication authenticate(AuthenticationRequest request) {
            return new TestingAuthenticationToken(request.getUsername(), null, "ROLE_USER");
        }
    }

    static class MockWechatStrategy implements AuthStrategy {
        @Override
        public String getType() { return "wechat-mini"; }

        @Override
        public Authentication authenticate(AuthenticationRequest request) {
            return new TestingAuthenticationToken("wechat_user_001", null, "ROLE_USER");
        }

        @Override
        public boolean supports(AuthChannel channel) {
            return channel != AuthChannel.ADMIN;
        }
    }
}
