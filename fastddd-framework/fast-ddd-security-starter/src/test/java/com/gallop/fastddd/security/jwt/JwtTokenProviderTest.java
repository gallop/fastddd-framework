package com.gallop.fastddd.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT Token Provider测试类
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        // HS512 requires at least 512 bits = 64 characters
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm");
        jwtProperties.setExpiration(Duration.ofMinutes(30));
        jwtProperties.setRefreshExpiration(Duration.ofHours(24));
        jwtProperties.setTokenPrefix("Bearer ");
        jwtProperties.setHeaderName("Authorization");

        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    void testGenerateAccessToken() {
        // 准备测试数据
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        );
        UserDetails userDetails = new User("testuser", "password", authorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

        // 生成Token
        String token = jwtTokenProvider.generateAccessToken(authentication);

        // 验证Token
        assertNotNull(token);
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals("testuser", jwtTokenProvider.getUsernameFromToken(token));
        
        List<String> tokenAuthorities = jwtTokenProvider.getAuthoritiesFromToken(token);
        assertEquals(2, tokenAuthorities.size());
        assertTrue(tokenAuthorities.contains("ROLE_USER"));
        assertTrue(tokenAuthorities.contains("ROLE_ADMIN"));
    }

    @Test
    void testGenerateRefreshToken() {
        // 准备测试数据
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER")
        );
        UserDetails userDetails = new User("testuser", "password", authorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

        // 生成刷新Token
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        // 验证Token
        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(refreshToken));
        assertEquals("testuser", jwtTokenProvider.getUsernameFromToken(refreshToken));
    }

    @Test
    void testValidateToken() {
        // 准备测试数据
        List<SimpleGrantedAuthority> authorities = Arrays.asList(
                new SimpleGrantedAuthority("ROLE_USER")
        );
        UserDetails userDetails = new User("testuser", "password", authorities);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);

        // 生成有效Token
        String validToken = jwtTokenProvider.generateAccessToken(authentication);
        assertTrue(jwtTokenProvider.validateToken(validToken));

        // 测试无效Token
        String invalidToken = "invalid.token.here";
        assertFalse(jwtTokenProvider.validateToken(invalidToken));

        // 测试空Token
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    void testExtractTokenFromHeader() {
        String token = "eyJhbGciOiJIUzUxMiJ9.test.token";
        String authHeader = "Bearer " + token;

        String extractedToken = jwtTokenProvider.extractTokenFromHeader(authHeader);
        assertEquals(token, extractedToken);

        // 测试无效格式
        assertNull(jwtTokenProvider.extractTokenFromHeader("InvalidHeader"));
        assertNull(jwtTokenProvider.extractTokenFromHeader(null));
        assertNull(jwtTokenProvider.extractTokenFromHeader(""));
    }

    @Test
    void testTokenExpiration() {
        // 创建一个已过期的token（通过直接设置过期时间为过去的时间）
        Date now = new Date();
        Date pastExpiry = new Date(now.getTime() - 1000); // 1秒前过期

        String expiredToken = Jwts.builder()
                .setSubject("testuser")
                .claim("authorities", Arrays.asList("ROLE_USER"))
                .setIssuedAt(new Date(now.getTime() - 60000)) // 1分钟前签发
                .setExpiration(pastExpiry)
                .signWith(Keys.hmacShaKeyFor(
                        jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.SignatureAlgorithm.HS512)
                .compact();

        // 过期的token不应该通过验证
        assertFalse(jwtTokenProvider.validateToken(expiredToken));
        // isTokenExpired应该对过期token返回true
        assertTrue(jwtTokenProvider.isTokenExpired(expiredToken));
    }
}