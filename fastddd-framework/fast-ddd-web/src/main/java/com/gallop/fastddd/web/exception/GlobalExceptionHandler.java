package com.gallop.fastddd.web.exception;

import com.gallop.fastddd.application.exception.CommandValidationException;
import com.gallop.fastddd.common.exception.BusinessException;
import com.gallop.fastddd.common.exception.SystemException;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用程序中的各种异常，返回统一的响应格式
 *
 * @author FastDDD Framework Team
 * @version 1.1.0 - 添加 CommandValidationException 专门处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理 Command 验证异常
     *
     * 此异常包含字段级别的验证错误，需要特殊格式化处理。
     *
     * @param e Command 验证异常
     * @param request HTTP 请求
     * @return 包含字段错误详情的响应
     */
    @ExceptionHandler(CommandValidationException.class)
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> handleCommandValidationException(
            CommandValidationException e, HttpServletRequest request) {
        log.warn("Command validation exception occurred at {}: {} errors",
                request.getRequestURI(), e.getErrors().size());

        // 将验证错误按字段分组
        Map<String, List<String>> fieldErrors = e.getErrors().stream()
                .collect(Collectors.groupingBy(
                        CommandValidationException.ValidationError::getField,
                        Collectors.mapping(
                                CommandValidationException.ValidationError::getMessage,
                                Collectors.toList())
                ));

        // 构建错误消息
        String errorSummary = e.getErrors().stream()
                .map(error -> error.getField() + ": " + error.getMessage())
                .collect(Collectors.joining("; "));

        ApiResponse<Map<String, List<String>>> response = ApiResponse.error(
                400,
                Messages.get("web.error.command-validation", errorSummary),
                fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 处理业务异常
     *
     * <p>{@link BusinessException} 有两类构造器：
     * <ul>
     *   <li>{@code (Integer, String)} 等 —— {@code errorCode} 有值，{@code code} 为 null；</li>
     *   <li>{@code (String code, String message)} —— {@code code} 有值（语义化字符串错误码），
     *       <strong>{@code errorCode 为 null</strong>。</li>
     * </ul>
     * 历史实现假定 {@code errorCode} 恒非空并直接 {@code e.getErrorCode().toString()}，对后者会 NPE，
     * 导致原始业务码丢失、异常漏成 500/401。此处 null-safe 兜底，并透传 {@code code} 到响应 {@code errCode}。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("Business exception occurred: {} at {}", e.getMessage(), request.getRequestURI());
        // errorCode 可能为 null（(String,String) 构造器）；null 时业务异常默认 400 BAD_REQUEST。
        HttpStatus httpStatus = resolveHttpStatus(e.getErrorCode());
        // errCode 透传语义化字符串 code（前端可据此精确分支）；(Integer,String) 构造器 code=null → 字段不输出。
        ApiResponse<Void> response = ApiResponse.failure(httpStatus.value(), e.getCode(), e.getMessage());
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * 由 BusinessException.errorCode 推导 HTTP 状态码（null-safe）。
     * <p>null 或非 404/500 的值一律归 400（业务异常本质是客户端可处理）。
     */
    private HttpStatus resolveHttpStatus(Integer errorCode) {
        if (errorCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        switch (errorCode) {
            case 404:
                return HttpStatus.NOT_FOUND;
            case 500:
                return HttpStatus.INTERNAL_SERVER_ERROR;
            case 400:
            default:
                return HttpStatus.BAD_REQUEST;
        }
    }
    
    /**
     * 处理系统异常
     */
    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ApiResponse<Void>> handleSystemException(SystemException e, HttpServletRequest request) {
        log.error("System exception occurred: {} at {}", e.getMessage(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(e.getErrorCode(), e.getMessage()));
    }
    
    /**
     * 处理参数校验异常（@Valid注解）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        log.warn("Validation exception occurred at {}", request.getRequestURI());
        
        List<String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        
        String message = String.join("; ", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, Messages.get("web.error.param-validation", message)));
    }
    
    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("Bind exception occurred at {}", request.getRequestURI());
        
        List<String> errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.toList());
        
        String message = String.join("; ", errors);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, Messages.get("web.error.param-bind", message)));
    }
    
    /**
     * 处理参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("Type mismatch exception occurred at {}: {}", request.getRequestURI(), e.getMessage());
        
        String message = Messages.get("web.error.param-type-mismatch",
                e.getName(),
                e.getRequiredType() != null ? e.getRequiredType().getSimpleName() : Messages.get("web.error.param-type-unknown"));
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, message));
    }
    
    /**
     * 处理IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("Illegal argument exception occurred at {}: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(400, Messages.get("web.error.illegal-argument", e.getMessage())));
    }
    
    /**
     * 处理IllegalStateException
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        log.error("Illegal state exception occurred at {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(500, Messages.get("web.error.illegal-state", e.getMessage())));
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        log.error("Runtime exception occurred at {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(500, Messages.get("web.error.runtime")));
    }
    
    /**
     * 处理HTTP媒体类型不可接受异常
     */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptableException(HttpMediaTypeNotAcceptableException e, HttpServletRequest request) {
        log.warn("Media type not acceptable exception occurred at {}: {}", request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.failure(406, Messages.get("web.error.media-type", e.getMessage())));
    }
    
    /**
     * 处理其他未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e, HttpServletRequest request) {
        log.error("Unexpected exception occurred at {}: {}", request.getRequestURI(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(500, Messages.get("web.error.internal")));
    }
}