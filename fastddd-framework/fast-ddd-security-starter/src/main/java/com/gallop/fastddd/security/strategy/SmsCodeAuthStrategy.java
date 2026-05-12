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
 * 手机短信验证码认证策略。
 *
 * @author gallop
 */
@Slf4j
@RequiredArgsConstructor
public class SmsCodeAuthStrategy implements AuthStrategy {

    public static final String TYPE = "sms_code";

    private final VerificationCodeService verificationCodeService;
    private final SecurityIdentityService identityService;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Authentication authenticate(AuthenticationRequest request) {
        String phone = firstNonBlank(request.getExtraParam("phone"), request.getUsername());
        String normalizedPhone = phone != null ? phone.trim() : null;
        String code = request.getExtraParam("code");

        verificationCodeService.verifyAndConsume(VerificationCodeType.SMS, VerificationCodePurpose.LOGIN, normalizedPhone, code);

        SecurityUserDetails userDetails = identityService instanceof CodeLoginIdentityService codeLoginIdentityService
                ? codeLoginIdentityService.loadUserByPhone(normalizedPhone)
                : identityService.loadUserByUsername(normalizedPhone);
        if (userDetails == null) {
            throw new BadCredentialsException("该手机号未注册: " + normalizedPhone);
        }
        if (!userDetails.isEnabled()) {
            throw new AuthenticationServiceException("用户已被禁用");
        }

        log.info("短信验证码登录成功: phone={}", maskPhone(normalizedPhone));
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
