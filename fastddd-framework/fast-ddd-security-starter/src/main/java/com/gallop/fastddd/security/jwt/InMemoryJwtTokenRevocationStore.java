package com.gallop.fastddd.security.jwt;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Single-node revocation store. A shared {@link JwtTokenRevocationStore} bean
 * must be supplied by clustered applications.
 */
public class InMemoryJwtTokenRevocationStore implements JwtTokenRevocationStore {

    private final ConcurrentMap<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    @Override
    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return true;
        }
        Instant expiresAt = revokedTokens.get(tokenId);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(Instant.now())) {
            revokedTokens.remove(tokenId, expiresAt);
            return false;
        }
        return true;
    }

    @Override
    public boolean revoke(String tokenId, Instant expiresAt) {
        if (tokenId == null || tokenId.isBlank() || expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            return false;
        }
        return revokedTokens.putIfAbsent(tokenId, expiresAt) == null;
    }
}
