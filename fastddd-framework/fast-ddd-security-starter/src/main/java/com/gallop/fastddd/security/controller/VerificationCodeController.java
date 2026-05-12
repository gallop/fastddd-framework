package com.gallop.fastddd.security.controller;

import com.gallop.fastddd.security.audit.AuditLog;
import com.gallop.fastddd.security.code.VerificationCodePurpose;
import com.gallop.fastddd.security.code.VerificationCodeService;
import com.gallop.fastddd.security.code.VerificationCodeType;
import com.gallop.fastddd.security.dto.SendVerificationCodeRequest;
import com.gallop.fastddd.web.response.ApiResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码接口。
 *
 * @author gallop
 */
@RestController
@RequestMapping("/api/auth/code")
public class VerificationCodeController {

    private final VerificationCodeService verificationCodeService;

    public VerificationCodeController(VerificationCodeService verificationCodeService) {
        this.verificationCodeService = verificationCodeService;
    }

    @PostMapping("/send")
    @AuditLog(value = "发送验证码", type = AuditLog.OperationType.OTHER, logParams = false)
    public ApiResponse<Void> send(@RequestBody SendVerificationCodeRequest request) {
        VerificationCodeType type = VerificationCodeType.fromCode(request.getType());
        if (type == null) {
            throw new AuthenticationServiceException("不支持的验证码类型: " + request.getType());
        }
        VerificationCodePurpose purpose = VerificationCodePurpose.fromCode(request.getPurpose());
        if (purpose == null) {
            throw new AuthenticationServiceException("不支持的验证码用途: " + request.getPurpose());
        }
        verificationCodeService.send(type, purpose, request.getTarget());
        return ApiResponse.success(null);
    }
}
