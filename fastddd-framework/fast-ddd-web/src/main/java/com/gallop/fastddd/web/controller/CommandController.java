package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.application.command.Command;
import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Command Controller 基类（CQRS 写端）
 *
 * <p>异常由 {@link com.gallop.fastddd.web.exception.GlobalExceptionHandler} 统一处理，
 * 子类无需 try-catch。
 *
 * @param <C> Command 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Slf4j
@Validated
public abstract class CommandController<C extends Command, R> {

    protected abstract CommandHandler<C, R> getCommandHandler();

    @PostMapping
    public ResponseEntity<ApiResponse<R>> executeCommand(@Valid @RequestBody C command) {
        log.debug("执行命令: {}", command.getCommandType());
        R result = getCommandHandler().handle(command);
        log.debug("命令执行成功: {}", command.getCommandType());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Void>> executeCommandNoResult(@Valid @RequestBody C command) {
        log.debug("执行命令: {}", command.getCommandType());
        getCommandHandler().handle(command);
        log.debug("命令执行成功: {}", command.getCommandType());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
