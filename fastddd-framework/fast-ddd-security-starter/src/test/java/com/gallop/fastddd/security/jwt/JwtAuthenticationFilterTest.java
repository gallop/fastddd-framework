package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import com.gallop.fastddd.security.channel.AuthChannelContext;
import com.gallop.fastddd.security.config.SecurityHttpProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtAuthenticationFilter 通道校验测试
 *
 * @author gallop
 * @date 2026-04-21
 */
class JwtAuthenticationFilterTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;
    private SecurityHttpProperties httpProperties;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm");
        jwtProperties.setExpiration(Duration.ofMinutes(30));
        jwtProperties.setRefreshExpiration(Duration.ofHours(24));
        jwtProperties.setTokenPrefix("Bearer ");
        jwtProperties.setHeaderName("Authorization");

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);

        httpProperties = new SecurityHttpProperties();
        httpProperties.setPermitAllPaths(Collections.emptyList());

        filter = new JwtAuthenticationFilter(jwtTokenProvider, jwtProperties, httpProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AuthChannelContext.clear();
    }

    @Test
    void testDefaultChannelAcceptsAllTokens() throws Exception {
        // 应用配置 DEFAULT，Token 也是 ADMIN → 应通过
        jwtProperties.setChannel(AuthChannel.DEFAULT);

        String token = generateToken("admin", AuthChannel.ADMIN);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testAdminAppRejectsCustomerToken() throws Exception {
        // 应用配置 ADMIN，Token 是 CUSTOMER → 应拒绝
        jwtProperties.setChannel(AuthChannel.ADMIN);

        String token = generateToken("customer1", AuthChannel.CUSTOMER);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        // 认证应被清除（拒绝）
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void testAdminAppAcceptsDefaultToken() throws Exception {
        // 应用配置 ADMIN，Token 是 DEFAULT → 应通过（向后兼容旧 Token）
        jwtProperties.setChannel(AuthChannel.ADMIN);

        String token = generateToken("admin", AuthChannel.DEFAULT);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testAdminAppAcceptsAdminToken() throws Exception {
        // 应用配置 ADMIN，Token 也是 ADMIN → 应通过
        jwtProperties.setChannel(AuthChannel.ADMIN);

        String token = generateToken("admin", AuthChannel.ADMIN);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("admin", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testChannelContextSetOnAcceptedRequest() throws Exception {
        jwtProperties.setChannel(AuthChannel.CUSTOMER);

        String token = generateToken("user1", AuthChannel.CUSTOMER);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 在 filter 执行期间检查 context
        MockFilterChain filterChain = new MockFilterChain(() -> {
            assertEquals(AuthChannel.CUSTOMER, AuthChannelContext.get());
        });

        filter.doFilterInternal(request, response, filterChain);

        // filter 执行完毕后 context 应被清理
        assertNull(AuthChannelContext.get());
    }

    @Test
    void testChannelContextClearedAfterRejectedRequest() throws Exception {
        jwtProperties.setChannel(AuthChannel.ADMIN);

        String token = generateToken("user1", AuthChannel.CUSTOMER);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        // 被拒绝的请求，context 也应被清理
        assertNull(AuthChannelContext.get());
    }

    @Test
    void testChannelContextNotSetOnRejectedRequest() throws Exception {
        jwtProperties.setChannel(AuthChannel.ADMIN);

        String token = generateToken("user1", AuthChannel.CUSTOMER);
        MockHttpServletRequest request = createRequestWithToken(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 在 filter chain 中检查 — 被拒绝时不应该设置 context
        MockFilterChain filterChain = new MockFilterChain(() -> {
            assertNull(AuthChannelContext.get());
        });

        filter.doFilterInternal(request, response, filterChain);
    }

    @Test
    void testNoTokenPassesThrough() throws Exception {
        jwtProperties.setChannel(AuthChannel.ADMIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain filterChain = new MockFilterChain();
        filter.doFilterInternal(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(AuthChannelContext.get());
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequest() throws Exception {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new User("user1", "password", authorities), null, authorities);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication, AuthChannel.DEFAULT);

        MockHttpServletRequest request = createRequestWithToken(refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // ---- helpers ----

    private String generateToken(String username, AuthChannel channel) {
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER")
        );
        UserDetails userDetails = new User(username, "password", authorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
        return jwtTokenProvider.generateAccessToken(authentication, channel);
    }

    private MockHttpServletRequest createRequestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    /**
     * 简易 MockFilterChain，支持在 doFilter 时执行回调
     */
    private static class MockFilterChain implements jakarta.servlet.FilterChain {
        private final Runnable callback;

        MockFilterChain() {
            this.callback = () -> {};
        }

        MockFilterChain(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            callback.run();
        }
    }
}
