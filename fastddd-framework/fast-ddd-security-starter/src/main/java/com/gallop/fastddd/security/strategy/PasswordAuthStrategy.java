package com.gallop.fastddd.security.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 内置密码认证策略
 *
 * @author gallop
 * @date 2026-04-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordAuthStrategy implements AuthStrategy {

    private final AuthenticationManager authenticationManager;

    @Override
    public String getType() {
        return "password";
    }

    @Override
    public Authentication authenticate(AuthenticationRequest request) {
        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );
    }
}
