package com.gallop.fastddd.security.code;

import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 默认验证码服务。
 *
 * @author gallop
 */
public class DefaultVerificationCodeService implements VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeProperties properties;
    private final VerificationCodeStore store;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeRateLimiter rateLimiter;
    private final Map<VerificationCodeType, VerificationCodeSender> senderMap;

    public DefaultVerificationCodeService(VerificationCodeProperties properties,
                                          VerificationCodeStore store,
                                          PasswordEncoder passwordEncoder,
                                          VerificationCodeRateLimiter rateLimiter,
                                          List<VerificationCodeSender> senders) {
        this.properties = properties;
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.senderMap = new EnumMap<>(VerificationCodeType.class);
        if (senders != null) {
            for (VerificationCodeSender sender : senders) {
                VerificationCodeSender existing = this.senderMap.get(sender.getType());
                if (existing == null || existing instanceof NoopVerificationCodeSender) {
                    this.senderMap.put(sender.getType(), sender);
                }
            }
        }
    }

    @Override
    public void send(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        assertEnabled();
        String normalizedTarget = normalizeTarget(type, target);
        rateLimiter.checkSendAllowed(type, purpose, normalizedTarget);
        Instant now = Instant.now();
        store.get(type, purpose, normalizedTarget).ifPresent(existing -> {
            Instant lastSentAt = existing.getLastSentAt();
            if (lastSentAt != null && lastSentAt.plus(properties.getResendInterval()).isAfter(now)) {
                throw new AuthenticationServiceException("验证码发送过于频繁");
            }
        });

        VerificationCodeSender sender = senderMap.get(type);
        if (sender == null) {
            throw new AuthenticationServiceException("未配置验证码发送器: " + type.getCode());
        }

        String code = generateCode();
        VerificationCodeEntry entry = VerificationCodeEntry.builder()
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plus(properties.getTtl()))
                .lastSentAt(now)
                .attempts(0)
                .build();
        store.put(type, purpose, normalizedTarget, entry);
        try {
            sender.send(normalizedTarget, code, purpose);
        } catch (RuntimeException ex) {
            store.remove(type, purpose, normalizedTarget);
            throw ex;
        }
    }

    @Override
    public void verifyAndConsume(VerificationCodeType type, VerificationCodePurpose purpose, String target, String code) {
        assertEnabled();
        if (code == null || code.isBlank()) {
            throw new BadCredentialsException("验证码不能为空");
        }

        String normalizedTarget = normalizeTarget(type, target);
        VerificationCodeEntry entry = store.get(type, purpose, normalizedTarget)
                .orElseThrow(() -> new BadCredentialsException("验证码不存在或已过期"));

        if (entry.getAttempts() >= properties.getMaxAttempts()) {
            store.remove(type, purpose, normalizedTarget);
            throw new BadCredentialsException("验证码错误次数过多");
        }

        entry.setAttempts(entry.getAttempts() + 1);

        if (!passwordEncoder.matches(code, entry.getCodeHash())) {
            if (entry.getAttempts() >= properties.getMaxAttempts()) {
                store.remove(type, purpose, normalizedTarget);
            } else {
                store.put(type, purpose, normalizedTarget, entry);
            }
            throw new BadCredentialsException("验证码错误");
        }

        store.remove(type, purpose, normalizedTarget);
    }

    public String normalizeTarget(VerificationCodeType type, String target) {
        if (target == null || target.isBlank()) {
            throw new AuthenticationServiceException("验证码接收目标不能为空");
        }
        String normalized = target.trim();
        if (type == VerificationCodeType.EMAIL) {
            normalized = normalized.toLowerCase();
        }
        return normalized;
    }

    private void assertEnabled() {
        if (!properties.isEnabled()) {
            throw new AuthenticationServiceException("验证码功能未启用");
        }
    }

    private String generateCode() {
        int length = Math.max(4, properties.getLength());
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
