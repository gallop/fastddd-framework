package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.application.command.Command;
import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Command Controller 基类
 *
 * 提供 CQRS 模式中 Command 端的标准 REST 接口实现。
 * 子类只需提供 CommandHandler，自动获得标准的 POST 端点。
 *
 * <p>功能：
 * <ul>
 *   <li>自动处理 Command 验证</li>
 *   <li>统一异常处理</li>
 *   <li>标准 ApiResponse 响应格式</li>
 *   <li>日志记录</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * &#64;RestController
 * &#64;RequestMapping("/api/orders")
 * public class OrderCommandController extends CommandController<CreateOrderCommand, OrderDTO> {
 *
 *     private final CreateOrderCommandHandler handler;
 *
 *     public OrderCommandController(CreateOrderCommandHandler handler) {
 *         this.handler = handler;
 *     }
 *
 *     &#64;Override
 *     protected CommandHandler<CreateOrderCommand, OrderDTO> getCommandHandler() {
 *         return handler;
 *     }
 * }
 * }</pre>
 *
 * @param <C> Command 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Slf4j
@Validated
public abstract class CommandController<C extends Command, R> {

    /**
     * 获取 Command Handler
     * 子类必须实现此方法以提供具体的 CommandHandler
     *
     * @return CommandHandler 实例
     */
    protected abstract CommandHandler<C, R> getCommandHandler();

    /**
     * 执行命令
     * 接收 Command 并通过 Handler 执行，返回标准 ApiResponse 格式
     *
     * @param command 要执行的命令
     * @return 执行结果
     */
    @PostMapping
    public ResponseEntity<ApiResponse<R>> executeCommand(@Valid @RequestBody C command) {
        try {
            log.debug("执行命令: {}", command.getCommandType());

            R result = getCommandHandler().handle(command);

            log.debug("命令执行成功: {}", command.getCommandType());
            return ResponseEntity.ok(ApiResponse.success(result, Messages.get("web.command.success")));

        } catch (Exception e) {
            log.error("执行命令失败: {} - {}", command.getCommandType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.command.failed", e.getMessage())));
        }
    }

    /**
     * 执行命令并返回简化的成功响应
     * 用于不需要返回数据的命令
     *
     * @param command 要执行的命令
     * @return 执行结果
     */
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<Void>> executeCommandNoResult(@Valid @RequestBody C command) {
        try {
            log.debug("执行命令: {}", command.getCommandType());

            getCommandHandler().handle(command);

            log.debug("命令执行成功: {}", command.getCommandType());
            return ResponseEntity.ok(ApiResponse.success(null, Messages.get("web.command.success")));

        } catch (Exception e) {
            log.error("执行命令失败: {} - {}", command.getCommandType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.command.failed", e.getMessage())));
        }
    }
}
