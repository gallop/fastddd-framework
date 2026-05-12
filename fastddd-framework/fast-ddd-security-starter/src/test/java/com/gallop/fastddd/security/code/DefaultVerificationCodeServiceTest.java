package com.gallop.fastddd.security.code;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultVerificationCodeServiceTest {

    private VerificationCodeProperties properties;
    private InMemoryVerificationCodeStore store;
    private CapturingSender sender;
    private DefaultVerificationCodeService service;

    @BeforeEach
    void setUp() {
        properties = new VerificationCodeProperties();
        properties.setTtl(Duration.ofMinutes(5));
        properties.setResendInterval(Duration.ZERO);
        properties.setMaxAttempts(2);
        store = new InMemoryVerificationCodeStore();
        sender = new CapturingSender(VerificationCodeType.EMAIL);
        service = new DefaultVerificationCodeService(
                properties,
                store,
                new BCryptPasswordEncoder(),
                new NoopVerificationCodeRateLimiter(),
                List.of(sender)
        );
    }

    @Test
    void sendStoresHashedCodeAndCallsSender() {
        service.send(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, " USER@Example.COM ");

        assertEquals("user@example.com", sender.target);
        assertNotNull(sender.code);
        assertEquals(6, sender.code.length());

        VerificationCodeEntry entry = store.get(
                VerificationCodeType.EMAIL,
                VerificationCodePurpose.LOGIN,
                "user@example.com"
        ).orElseThrow();
        assertNotEquals(sender.code, entry.getCodeHash());
    }

    @Test
    void verifyConsumesCode() {
        service.send(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com");

        service.verifyAndConsume(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "USER@example.com", sender.code);

        assertTrue(store.get(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com").isEmpty());
        assertThrows(BadCredentialsException.class,
                () -> service.verifyAndConsume(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com", sender.code));
    }

    @Test
    void wrongCodeRemovesEntryAfterMaxAttempts() {
        service.send(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com");

        assertThrows(BadCredentialsException.class,
                () -> service.verifyAndConsume(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com", "000000"));
        assertTrue(store.get(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com").isPresent());

        assertThrows(BadCredentialsException.class,
                () -> service.verifyAndConsume(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com", "111111"));
        assertTrue(store.get(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com").isEmpty());
    }

    @Test
    void sendFailsWhenSenderMissing() {
        DefaultVerificationCodeService smsService = new DefaultVerificationCodeService(
                properties,
                store,
                new BCryptPasswordEncoder(),
                new NoopVerificationCodeRateLimiter(),
                List.of(sender)
        );

        assertThrows(AuthenticationServiceException.class,
                () -> smsService.send(VerificationCodeType.SMS, VerificationCodePurpose.LOGIN, "13800138000"));
    }

    @Test
    void sendFailureRemovesStoredCode() {
        DefaultVerificationCodeService failingService = new DefaultVerificationCodeService(
                properties,
                store,
                new BCryptPasswordEncoder(),
                new NoopVerificationCodeRateLimiter(),
                List.of(new FailingSender(VerificationCodeType.EMAIL))
        );

        assertThrows(IllegalStateException.class,
                () -> failingService.send(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com"));
        assertTrue(store.get(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, "user@example.com").isEmpty());
    }

    static class CapturingSender implements VerificationCodeSender {

        private final VerificationCodeType type;
        private String target;
        private String code;

        CapturingSender(VerificationCodeType type) {
            this.type = type;
        }

        @Override
        public VerificationCodeType getType() {
            return type;
        }

        @Override
        public void send(String target, String code, VerificationCodePurpose purpose) {
            this.target = target;
            this.code = code;
        }
    }

    static class FailingSender implements VerificationCodeSender {

        private final VerificationCodeType type;

        FailingSender(VerificationCodeType type) {
            this.type = type;
        }

        @Override
        public VerificationCodeType getType() {
            return type;
        }

        @Override
        public void send(String target, String code, VerificationCodePurpose purpose) {
            throw new IllegalStateException("send failed");
        }
    }
}
