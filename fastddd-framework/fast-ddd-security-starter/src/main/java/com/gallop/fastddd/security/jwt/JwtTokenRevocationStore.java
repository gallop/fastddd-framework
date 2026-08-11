package com.gallop.fastddd.security.jwt;

import java.time.Instant;

/**
 * Stores revoked token identifiers.
 *
 * <p>Applications running more than one instance should provide a shared
 * implementation (for example backed by Redis) as a bean.</p>
 */
public interface JwtTokenRevocationStore {

    boolean isRevoked(String tokenId);

    /**
     * Marks a token as revoked. Returns {@code true} only for the first caller,
     * enabling one-time refresh-token rotation.
     */
    boolean revoke(String tokenId, Instant expiresAt);
}
