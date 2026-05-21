package com.gallop.fastddd.security.jwt;

import com.gallop.fastddd.security.channel.AuthChannel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Verify JwtTokenProvider correctly delegates to a custom JwtSigner
 * via the 2-argument constructor.
 */
class JwtTokenProviderSignerDelegationTest {

    private JwtProperties jwtProperties;
    private JwtSigner mockSigner;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm");
        jwtProperties.setExpiration(Duration.ofMinutes(30));
        jwtProperties.setRefreshExpiration(Duration.ofHours(24));

        mockSigner = mock(JwtSigner.class);
        provider = new JwtTokenProvider(jwtProperties, mockSigner);
    }

    @Test
    void generateAccessTokenDelegatesToSigner() {
        when(mockSigner.sign(any(JwtBuilder.class))).thenReturn("mock-access-token");

        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User("testuser", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = provider.generateAccessToken(auth);

        assertEquals("mock-access-token", token);
        verify(mockSigner, times(1)).sign(any(JwtBuilder.class));
    }

    @Test
    void generateRefreshTokenDelegatesToSigner() {
        when(mockSigner.sign(any(JwtBuilder.class))).thenReturn("mock-refresh-token");

        org.springframework.security.core.userdetails.User userDetails =
                new org.springframework.security.core.userdetails.User("testuser", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = provider.generateRefreshToken(auth);

        assertEquals("mock-refresh-token", token);
        verify(mockSigner, times(1)).sign(any(JwtBuilder.class));
    }

    @Test
    void validateTokenDelegatesToSignerParse() {
        Claims claims = Jwts.claims();
        claims.setSubject("testuser");
        claims.setExpiration(new Date(System.currentTimeMillis() + 60000));
        when(mockSigner.parse("valid-token")).thenReturn(claims);

        assertTrue(provider.validateToken("valid-token"));
        verify(mockSigner).parse("valid-token");
    }

    @Test
    void validateTokenReturnsFalseOnException() {
        when(mockSigner.parse(anyString())).thenThrow(new io.jsonwebtoken.JwtException("invalid"));

        assertFalse(provider.validateToken("bad-token"));
    }

    @Test
    void getUsernameFromTokenDelegatesToSignerParse() {
        Claims claims = Jwts.claims();
        claims.setSubject("alice");
        when(mockSigner.parse("token")).thenReturn(claims);

        assertEquals("alice", provider.getUsernameFromToken("token"));
        verify(mockSigner).parse("token");
    }

    @Test
    void getAuthoritiesFromTokenDelegatesToSignerParse() {
        Claims claims = Jwts.claims();
        claims.put("authorities", Arrays.asList("ROLE_USER", "ROLE_ADMIN"));
        when(mockSigner.parse("token")).thenReturn(claims);

        List<String> authorities = provider.getAuthoritiesFromToken("token");
        assertEquals(2, authorities.size());
        assertTrue(authorities.contains("ROLE_ADMIN"));
    }

    @Test
    void getChannelFromTokenDelegatesToSignerParse() {
        Claims claims = Jwts.claims();
        claims.put("channel", "admin");
        when(mockSigner.parse("token")).thenReturn(claims);

        assertEquals(AuthChannel.ADMIN, provider.getChannelFromToken("token"));
    }

    @Test
    void getChannelFromTokenReturnsDefaultWhenNoChannelClaim() {
        Claims claims = Jwts.claims();
        when(mockSigner.parse("token")).thenReturn(claims);

        assertEquals(AuthChannel.DEFAULT, provider.getChannelFromToken("token"));
    }
}
