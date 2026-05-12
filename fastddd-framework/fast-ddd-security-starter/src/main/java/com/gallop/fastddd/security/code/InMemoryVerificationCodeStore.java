package com.gallop.fastddd.security.code;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 默认内存验证码存储，适合单体应用和开发环境。
 *
 * @author gallop
 */
public class InMemoryVerificationCodeStore implements VerificationCodeStore {

    private final ConcurrentMap<String, VerificationCodeEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<VerificationCodeEntry> get(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        String key = key(type, purpose, target);
        VerificationCodeEntry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.getExpiresAt() != null && entry.getExpiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public void put(VerificationCodeType type, VerificationCodePurpose purpose, String target, VerificationCodeEntry entry) {
        entries.put(key(type, purpose, target), entry);
    }

    @Override
    public void remove(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        entries.remove(key(type, purpose, target));
    }

    private String key(VerificationCodeType type, VerificationCodePurpose purpose, String target) {
        return type.getCode() + ":" + purpose.getCode() + ":" + target;
    }
}
