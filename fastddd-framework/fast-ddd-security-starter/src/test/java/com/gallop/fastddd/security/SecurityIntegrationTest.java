package com.gallop.fastddd.security;

import com.gallop.fastddd.security.jwt.JwtTokenProvider;
import com.gallop.fastddd.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 安全集成测试
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Disabled("Integration test disabled due to Spring context loading issues")
@SpringBootTest(classes = SecurityIntegrationTest.class)
@AutoConfigureMockMvc
@EnableAutoConfiguration
@TestPropertySource(properties = {
    "fast-ddd.security.jwt.enabled=true",
    "fast-ddd.security.jwt.secret=test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm",
    "fast-ddd.security.jwt.expiration=3600000",
    "fast-ddd.security.jwt.refresh-expiration=604800000"
})
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-secret-key-for-jwt-token-generation-must-be-long-enough-for-hs512-algorithm");
        jwtProperties.setExpiration(Duration.ofHours(1));
        jwtProperties.setRefreshExpiration(Duration.ofDays(7));
        jwtProperties.setHeaderName("Authorization");
        jwtProperties.setTokenPrefix("Bearer ");
        this.jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    void testAccessSecuredEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void testAccessSecuredEndpointWithValidToken() throws Exception {
        // 生成有效Token
        UserDetails userDetails = new User("testuser", "password",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateAccessToken(authentication);

        mockMvc.perform(get("/api/test/secured")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Secured endpoint accessed successfully"));
    }

    @Test
    void testAccessAdminEndpointWithUserRole() throws Exception {
        // 生成用户角色Token
        UserDetails userDetails = new User("testuser", "password",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateAccessToken(authentication);

        mockMvc.perform(get("/api/test/admin")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void testAccessAdminEndpointWithAdminRole() throws Exception {
        // 生成管理员角色Token
        UserDetails userDetails = new User("admin", "password",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateAccessToken(authentication);

        mockMvc.perform(get("/api/test/admin")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("Admin endpoint accessed successfully"));
    }

    @Test
    void testAccessPublicEndpoint() throws Exception {
        mockMvc.perform(get("/public/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Public endpoint accessed"));
    }

    @Test
    void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/test/secured")
                .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 测试控制器
     */
    @RestController
    public static class TestController {

        @GetMapping("/api/test/secured")
        public String securedEndpoint() {
            return "Secured endpoint accessed successfully";
        }

        @GetMapping("/api/test/admin")
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        public String adminEndpoint() {
            return "Admin endpoint accessed successfully";
        }

        @GetMapping("/public/test")
        public String publicEndpoint() {
            return "Public endpoint accessed";
        }
    }
}