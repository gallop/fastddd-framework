package com.gallop.fastddd.security.strategy;

import com.gallop.fastddd.security.code.VerificationCodePurpose;
import com.gallop.fastddd.security.code.VerificationCodeService;
import com.gallop.fastddd.security.code.VerificationCodeType;
import com.gallop.fastddd.security.spi.CodeLoginIdentityService;
import com.gallop.fastddd.security.spi.SecurityIdentityService;
import com.gallop.fastddd.security.spi.SecurityUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * 邮箱验证码认证策略。
 *
 * @author gallop
 */
@Slf4j
@RequiredArgsConstructor
public class EmailCodeAuthStrategy implements AuthStrategy {

    public static final String TYPE = "email_code";

    private final VerificationCodeService verificationCodeService;
    private final SecurityIdentityService identityService;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Authentication authenticate(AuthenticationRequest request) {
        String email = firstNonBlank(request.getExtraParam("email"), request.getUsername());
        String normalizedEmail = email != null ? email.trim().toLowerCase() : null;
        String code = request.getExtraParam("code");

        verificationCodeService.verifyAndConsume(VerificationCodeType.EMAIL, VerificationCodePurpose.LOGIN, normalizedEmail, code);

        SecurityUserDetails userDetails = identityService instanceof CodeLoginIdentityService codeLoginIdentityService
                ? codeLoginIdentityService.loadUserByEmail(normalizedEmail)
                : identityService.loadUserByUsername(normalizedEmail);
        if (userDetails == null) {
            throw new BadCredentialsException("该邮箱未注册: " + normalizedEmail);
        }
        if (!userDetails.isEnabled()) {
            throw new AuthenticationServiceException("用户已被禁用");
        }

        log.info("邮箱验证码登录成功: email={}", maskEmail(normalizedEmail));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String maskEmail(String email) {
        int at = email != null ? email.indexOf('@') : -1;
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
