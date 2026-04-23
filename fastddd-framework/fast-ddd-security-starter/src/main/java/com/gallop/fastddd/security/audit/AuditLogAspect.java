package com.gallop.fastddd.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 审计日志切面
 *
 * @author gallop
 * @date 2025-01-10 15:30:00
 */
@Slf4j
@Aspect
public class AuditLogAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.objectMapper = new ObjectMapper();
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        AuditLogEntity logEntity = new AuditLogEntity();
        
        try {
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
            
            // 获取当前用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : "anonymous";
            
            // 获取方法信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            
            // 填充基本信息
            logEntity.setUsername(username);
            logEntity.setOperation(auditLog.value().isEmpty() ? method.getName() : auditLog.value());
            logEntity.setOperationType(auditLog.type().name());
            logEntity.setMethod(method.getDeclaringClass().getSimpleName() + "." + method.getName());
            logEntity.setCreateTime(LocalDateTime.now());
            
            // 填充请求信息
            if (request != null) {
                logEntity.setRequestUrl(request.getRequestURI());
                logEntity.setClientIp(getClientIp(request));
                logEntity.setUserAgent(request.getHeader("User-Agent"));
            }
            
            // 记录请求参数（跳过 Servlet 等不可序列化类型）
            if (auditLog.logParams()) {
                try {
                    Object[] args = joinPoint.getArgs();
                    if (args != null && args.length > 0) {
                        Object[] safeArgs = Arrays.stream(args)
                                .filter(arg -> !(arg instanceof ServletRequest)
                                        && !(arg instanceof ServletResponse))
                                .collect(Collectors.toList())
                                .toArray();
                        if (safeArgs.length > 0) {
                            String paramsJson = objectMapper.writeValueAsString(safeArgs);
                            // 脱敏: 遮盖 password/secret/token 字段
                            paramsJson = paramsJson.replaceAll(
                                    "\"(password|secret|token|accessToken|refreshToken)\"\\s*:\\s*\"[^\"]*\"",
                                    "\"$1\":\"***\"");
                            logEntity.setRequestParams(paramsJson);
                        }
                    }
                } catch (Throwable e) {
                    log.warn("Failed to serialize request parameters: {}", e.getMessage());
                    logEntity.setRequestParams("Failed to serialize parameters");
                }
            }
            
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 记录返回结果
            if (auditLog.logResult() && result != null) {
                try {
                    logEntity.setResult(objectMapper.writeValueAsString(result));
                } catch (Throwable e) {
                    log.warn("Failed to serialize result: {}", e.getMessage());
                    logEntity.setResult("Failed to serialize result");
                }
            }
            
            logEntity.setSuccess(true);
            return result;
            
        } catch (Exception e) {
            logEntity.setSuccess(false);
            logEntity.setErrorMessage(e.getMessage());
            throw e;
            
        } finally {
            // 计算执行时间
            long executionTime = System.currentTimeMillis() - startTime;
            logEntity.setExecutionTime(executionTime);
            
            // 保存审计日志
            try {
                if (auditLog.async()) {
                    auditLogService.saveAuditLogAsync(logEntity);
                } else {
                    auditLogService.saveAuditLog(logEntity);
                }
            } catch (Exception e) {
                log.error("Failed to save audit log", e);
            }
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况，取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}