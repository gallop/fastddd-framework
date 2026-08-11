package com.gallop.fastddd.security.jwt;

import java.util.Arrays;

/** Distinguishes tokens that may access APIs from tokens used only to refresh them. */
public enum JwtTokenType {

    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;

    JwtTokenType(String claimValue) {
        this.claimValue = claimValue;
    }

    public String getClaimValue() {
        return claimValue;
    }

    public static JwtTokenType fromClaim(Object claim) {
        if (claim == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.claimValue.equals(claim.toString()))
                .findFirst()
                .orElse(null);
    }
}
