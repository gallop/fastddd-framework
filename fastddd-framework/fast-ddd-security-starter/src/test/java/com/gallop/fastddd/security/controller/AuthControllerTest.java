package com.gallop.fastddd.security.controller;

import com.gallop.fastddd.security.channel.AuthChannel;
import com.gallop.fastddd.security.dto.LoginResponse;
import com.gallop.fastddd.security.jwt.JwtProperties;
import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityPermissionService;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import com.gallop.fastddd.security.spi.SecurityUserInfo;
import com.gallop.fastddd.security.strategy.AuthStrategy;
import com.gallop.fastddd.security.strategy.AuthStrategyRegistry;
import com.gallop.fastddd.security.strategy.AuthenticationRequest;
import com.gallop.fastddd.web.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    @Test
    void loginUsesSecurityUserDetailsPrincipalWithoutUsernameReload() {
        SecurityUserDetails details = SecurityUserDetails.builder()
                .userId("42")
                .username("user@example.com")
                .enabled(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        ThrowingLoadIdentityService identityService = new ThrowingLoadIdentityService();
        AuthController controller = new AuthController(
                new JwtTokenProvider(jwtProperties()),
                jwtProperties(),
                identityService,
                new TestPermissionService(),
                new AuthStrategyRegistry(List.of(new PrincipalStrategy(details)))
        );

        AuthenticationRequest request = new AuthenticationRequest();
        request.setType("email_code");
        request.setChannel(AuthChannel.DEFAULT);

        ApiResponse<LoginResponse> response = controller.login(request, null);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData().getAccessToken());
        assertEquals("42", identityService.userInfoId);
    }

    private JwtProperties jwtProperties() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm");
        properties.setExpiration(Duration.ofMinutes(30));
        properties.setRefreshExpiration(Duration.ofHours(24));
        return properties;
    }

    static class PrincipalStrategy implements AuthStrategy {

        private final SecurityUserDetails details;

        PrincipalStrategy(SecurityUserDetails details) {
            this.details = details;
        }

        @Override
        public String getType() {
            return "email_code";
        }

        @Override
        public Authentication authenticate(AuthenticationRequest request) {
            return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        }
    }

    static class ThrowingLoadIdentityService implements SecurityIdentityService {

        private String userInfoId;

        @Override
        public SecurityUserDetails loadUserByUsername(String username) {
            throw new AssertionError("loadUserByUsername should not be called when principal is SecurityUserDetails");
        }

        @Override
        public SecurityUserInfo getUserInfo(String userId) {
            this.userInfoId = userId;
            return SecurityUserInfo.builder()
                    .id(userId)
                    .username("user@example.com")
                    .enabled(true)
                    .build();
        }
    }

    static class TestPermissionService implements SecurityPermissionService {

        @Override
        public List<String> getPermissionsByUserId(String userId) {
            return List.of("user:read");
        }

        @Override
        public List<String> getRolesByUserId(String userId) {
            return List.of("USER");
        }
    }
}
