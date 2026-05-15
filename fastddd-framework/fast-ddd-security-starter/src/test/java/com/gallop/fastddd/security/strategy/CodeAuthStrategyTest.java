package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.code.VerificationCodePurpose;
import com.gallop.fastddd.security.code.VerificationCodeService;
import com.gallop.fastddd.security.code.VerificationCodeType;
import com.gallop.fastddd.security.spi.CodeLoginIdentityService;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import com.gallop.fastddd.security.spi.SecurityUserInfo;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodeAuthStrategyTest {

    @Test
    void emailCodeStrategyReturnsUserDetailsPrincipal() {
        RecordingVerificationCodeService codeService = new RecordingVerificationCodeService();
        TestIdentityService identityService = new TestIdentityService();
        EmailCodeAuthStrategy strategy = new EmailCodeAuthStrategy(codeService, identityService);

        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername(" USER@Example.COM ");
        request.setExtra(Map.of("code", "123456"));

        Authentication authentication = strategy.authenticate(request);

        assertEquals(VerificationCodeType.EMAIL, codeService.type);
        assertEquals("user@example.com", codeService.target);
        assertInstanceOf(SecurityUserDetails.class, authentication.getPrincipal());
        assertEquals("user@example.com", authentication.getName());
    }

    @Test
    void smsCodeStrategyUsesPhoneLookup() {
        RecordingVerificationCodeService codeService = new RecordingVerificationCodeService();
        TestIdentityService identityService = new TestIdentityService();
        SmsCodeAuthStrategy strategy = new SmsCodeAuthStrategy(codeService, identityService);

        AuthenticationRequest request = new AuthenticationRequest();
        request.setExtra(Map.of("phone", " 13800138000 ", "code", "123456"));

        Authentication authentication = strategy.authenticate(request);

        assertEquals(VerificationCodeType.SMS, codeService.type);
        assertEquals("13800138000", codeService.target);
        assertInstanceOf(SecurityUserDetails.class, authentication.getPrincipal());
        assertEquals("13800138000", authentication.getName());
    }

    @Test
    void emailCodeStrategyRejectsMissingUser() {
        RecordingVerificationCodeService codeService = new RecordingVerificationCodeService();
        TestIdentityService identityService = new TestIdentityService();
        identityService.returnUser = false;
        EmailCodeAuthStrategy strategy = new EmailCodeAuthStrategy(codeService, identityService);

        AuthenticationRequest request = new AuthenticationRequest();
        request.setExtra(Map.of("email", "missing@example.com", "code", "123456"));

        assertThrows(BadCredentialsException.class, () -> strategy.authenticate(request));
    }

    static class RecordingVerificationCodeService implements VerificationCodeService {

        private VerificationCodeType type;
        private String target;

        @Override
        public void send(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        }

        @Override
        public void verifyAndConsume(VerificationCodeType type, VerificationCodePurpose purpose, String target, String code) {
            this.type = type;
            this.target = target;
            assertEquals(VerificationCodePurpose.LOGIN, purpose);
            assertEquals("123456", code);
        }
    }

    static class TestIdentityService implements CodeLoginIdentityService {

        private boolean returnUser = true;

        @Override
        public SecurityUserDetails loadUserByUsername(String username) {
            return user(username);
        }

        @Override
        public SecurityUserDetails loadUserByPhone(String phone) {
            return user(phone);
        }

        @Override
        public SecurityUserDetails loadUserByEmail(String email) {
            return user(email);
        }

        @Override
        public SecurityUserInfo getUserInfo(String userId) {
            return null;
        }

        private SecurityUserDetails user(String username) {
            if (!returnUser) {
                return null;
            }
            return SecurityUserDetails.builder()
                    .userId("1")
                    .username(username)
                    .enabled(true)
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                    .build();
        }
    }
}
