package com.gallop.fastddd.security.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gallop.fastddd.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

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

    /** identity 模式 Actor 桥（可空：A/B/D 与目录单开为 null，走旧路径）。 */
    private final AuditActorProvider actorProvider;

    public AuditLogAspect(AuditLogService auditLogService) {
        this(auditLogService, null);
    }

    public AuditLogAspect(AuditLogService auditLogService, AuditActorProvider actorProvider) {
        this.auditLogService = auditLogService;
        this.actorProvider = actorProvider;
        this.objectMapper = new ObjectMapper();
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        AuditLogEntity logEntity = new AuditLogEntity();

        try {
            // identity 模式（W21-04）：进入业务前解析统一 Actor——缺失身份在此抛出，
            // 业务不执行（不降级 anonymous）；解析结果随实体捕获，异步落库不延后读取
            AuditActorProvider.AuditActor actor = null;
            if (actorProvider != null) {
                actor = actorProvider.resolveActor();
            }

            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes != null ? attributes.getRequest() : null;

            // 获取当前用户（identity 模式下 actor.principalId 与会话 getName() 一致）
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = actor != null ? actor.principalId()
                    : authentication != null ? authentication.getName() : "anonymous";
            if (actor != null) {
                logEntity.setActorTenantId(actor.tenantId());
                logEntity.setActorPrincipalId(actor.principalId());
                logEntity.setActorType(actor.actorType());
            }
            
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
            
            // v1.0.1 判型守卫：按返回值信封判定 success（守卫自身异常兜底恒真，绝不污染被审计请求）
            applyResultSuccessSemantics(logEntity, result);
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

    /**
     * fast-ddd-web 在场探针：web 系 starter 的 optional 依赖（消费者可不携带），
     * 判型守卫引用 {@link ApiResponse} 前必须先探在场，缺席时整体退回 1.0.0
     * 恒 success=true 语义（探针用本类类加载器，不依赖 TCCL）。
     */
    private static final boolean API_RESPONSE_PRESENT = ClassUtils.isPresent(
            "com.gallop.fastddd.web.response.ApiResponse", AuditLogAspect.class.getClassLoader());

    /**
     * v1.0.1 判型守卫开关：true（缺省）=判型语义生效；false=整体回退 1.0.0 恒 true 语义。
     * 经 @Value setter 注入（键须用 kebab-case 全字面量——@Value 无宽松绑定，
     * camelCase 变体会静默落缺省值 true）。
     */
    private volatile boolean resultJudgingEnabled = true;

    @Value("${fastddd.security.audit.result-judging.enabled:true}")
    public void setResultJudgingEnabled(boolean resultJudgingEnabled) {
        this.resultJudgingEnabled = resultJudgingEnabled;
    }

    /**
     * v1.0.1 判型守卫：proceed() 正常返回后按返回值信封判定审计 success。
     *
     * <p>1.0.0 语义在 controller 承接业务异常转失败信封（HTTP 200/400 正常返回、
     * 异常不出抛）时恒记 success=1，对失败请求失真（最显性=框架自带
     * {@code /api/auth/login} 失败登录曾被记成功）。本守卫仅判两类显式信封形：
     * 方法直返 {@link ApiResponse}，或 {@code ResponseEntity} 一层包裹 ApiResponse
     * （取 body）；其余形态（非信封对象、ResponseEntity 空 body 或 body 非信封、
     * 异步返回体在 proceed 时刻未定形）维持 success=true——与 1.0.0 一致。
     * code != 200（含 null，沿 ApiResponse#isSuccess 200 硬判定契约）即记失败，
     * 失败时 errorMessage 承载信封 message（空白不覆写）。
     *
     * <p>守卫自身的任何异常（含信封子类覆写 isFailure/getMessage 抛出的 Exception 或
     * Error——try 块仅调信封只读检查方法、无资源语义，吞 Throwable 安全）兜底记
     * success=true 并降级 debug 日志——审计守卫绝不改变被审计请求的成败与路径。
     * 开关 {@code fastddd.security.audit.result-judging.enabled=false} 可整体回退
     * 1.0.0 语义；fast-ddd-web 缺席时守卫恒休眠（在场探针短路）。
     */
    private void applyResultSuccessSemantics(AuditLogEntity logEntity, Object result) {
        if (!resultJudgingEnabled || !API_RESPONSE_PRESENT) {
            logEntity.setSuccess(true);
            return;
        }
        try {
            // 强转在方法体内（懒解析，守卫+try 兜底之内）；extract 返回型保持 Object——见其 javadoc W2-1 注
            ApiResponse<?> envelope = (ApiResponse<?>) extractApiResponse(result);
            if (envelope == null) {
                logEntity.setSuccess(true);
                return;
            }
            if (envelope.isFailure()) {
                logEntity.setSuccess(false);
                if (StringUtils.hasText(envelope.getMessage())) {
                    logEntity.setErrorMessage(envelope.getMessage());
                }
            } else {
                logEntity.setSuccess(true);
            }
        } catch (Throwable e) {
            // 判型链路任何异常（含信封子类覆写方法抛出的 Error）不改变 1.0.0 语义与请求路径——
            // try 块仅调信封只读检查方法、无资源语义，吞 Throwable 安全
            log.debug("Audit result-judging fell back to legacy success=true: {}", e.getMessage());
            logEntity.setSuccess(true);
        }
    }

    /**
     * 独立私有方法承载 ApiResponse 引用（懒解析隔离）：仅当 API_RESPONSE_PRESENT
     * 为真时才被调用——方法体内 instanceof/强转涉及的 ApiResponse 常量池解析随首次
     * 执行触发，web 缺席消费者永不调用本方法即永不解析。
     *
     * <p><b>返回型必须保持 {@code Object}（W2-1 修复，2026-10-09）</b>：方法
     * <b>签名</b>（方法描述符）参与 {@code Class#getDeclaredMethods} 急切解析，
     * 而 AspectJ advisor 构建（{@code AspectMetadata} → {@code AjTypeSystem.getAjType}）
     * 即走此路径——签名出现可选依赖类型时，security-starter + AOP 代理 + classpath
     * 无 fast-ddd-web 的宿主在启动期即 {@code NoClassDefFoundError}（原
     * {@code ApiResponse<?>} 返回型签名即此缺陷，v1.0.1 懒守卫只隔离了调用路径
     * 未覆盖签名解析面）。调用方局部变量声明不进方法描述符，体内强转安全。
     */
    private Object extractApiResponse(Object result) {
        Object payload = result;
        if (payload instanceof ResponseEntity<?> entity) {
            payload = entity.getBody();
        }
        return payload instanceof ApiResponse<?> api ? api : null;
    }
}