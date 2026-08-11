package com.gallop.fastddd.web.exception;

import com.gallop.fastddd.application.exception.CommandValidationException;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CQRS（fast-ddd-application）异常映射 advice（WEB-001 自 GlobalExceptionHandler 抽离）。
 *
 * <p>仅当 classpath 存在 {@link CommandValidationException}（即消费者引入了可选的
 * fast-ddd-application）时装配。fast-ddd-web 对 fast-ddd-application 是 optional 依赖，
 * 缺席时本类整体被 Spring 的 {@code @ConditionalOnClass} 跳过——条件经 ASM 元数据求值，
 * 不触发 {@code CommandValidationException} 的类加载，故 fast-ddd-web 可在无 application 时启动。
 *
 * <p>装配后与 {@link GlobalExceptionHandler} 共存：Spring MVC 的异常解析器在所有
 * {@code @ControllerAdvice} 中选取最具体的 {@code @ExceptionHandler}，故
 * {@code CommandValidationException}（具体）优先于 {@code BusinessException}（父类）命中本类。
 *
 * @author gallop
 * @since 1.0.0-jdk21
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass(CommandValidationException.class)
public class CqrsExceptionAdvice {

    /**
     * 处理 Command 验证异常。
     *
     * <p>此异常包含字段级别的验证错误，需特殊格式化处理。
     *
     * @param e       Command 验证异常
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
}
