package com.gallop.fastddd.security.code;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * 验证码存储记录。
 *
 * @author gallop
 */
@Data
@Builder
public class VerificationCodeEntry {

    private String codeHash;
    private Instant expiresAt;
    private Instant lastSentAt;
    private int attempts;
}
