package com.gallop.fastddd.application.command;

import lombok.Getter;

import java.util.Optional;

/**
 * Command 执行结果
 *
 * 封装 Command 执行后的结果，支持成功和失败两种状态。
 * 提供类型安全的结果访问。
 *
 * <p>使用示例：
 * <pre>{@code
 * public CommandResult<OrderDTO> createOrder(CreateOrderCommand command) {
 *     try {
 *         Order order = Order.create(command.getCustomerId(), command.getAmount());
 *         orderRepository.save(order);
 *         return CommandResult.success(OrderDTO.from(order));
 *     } catch (Exception e) {
 *         return CommandResult.failure("创建订单失败: " + e.getMessage());
 *     }
 * }
 *
 * // 使用结果
 * CommandResult<OrderDTO> result = service.createOrder(command);
 * if (result.isSuccess()) {
 *     result.getData().ifPresent(order -> System.out.println(order));
 * } else {
 *     System.err.println(result.getError());
 * }
 * }</pre>
 *
 * @param <T> 结果数据类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Getter
public class CommandResult<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final String error;

    private CommandResult(boolean success, String message, T data, String error) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.error = error;
    }

    /**
     * 创建成功结果（无数据）
     *
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> CommandResult<T> success() {
        return new CommandResult<>(true, "操作成功", null, null);
    }

    /**
     * 创建成功结果（带数据）
     *
     * @param data 结果数据
     * @param <T>  数据类型
     * @return 成功结果
     */
    public static <T> CommandResult<T> success(T data) {
        return new CommandResult<>(true, "操作成功", data, null);
    }

    /**
     * 创建成功结果（带消息和数据）
     *
     * @param message 成功消息
     * @param data    结果数据
     * @param <T>     数据类型
     * @return 成功结果
     */
    public static <T> CommandResult<T> success(String message, T data) {
        return new CommandResult<>(true, message, data, null);
    }

    /**
     * 创建失败结果
     *
     * @param error 错误信息
     * @param <T>   数据类型
     * @return 失败结果
     */
    public static <T> CommandResult<T> failure(String error) {
        return new CommandResult<>(false, null, null, error);
    }

    /**
     * 创建失败结果（带消息）
     *
     * @param message 失败消息
     * @param error   错误详情
     * @param <T>     数据类型
     * @return 失败结果
     */
    public static <T> CommandResult<T> failure(String message, String error) {
        return new CommandResult<>(false, message, null, error);
    }

    /**
     * 从异常创建失败结果
     *
     * @param e 异常
     * @param <T> 数据类型
     * @return 失败结果
     */
    public static <T> CommandResult<T> fromException(Exception e) {
        return failure(e.getMessage());
    }

    /**
     * 获取数据（Optional 包装）
     *
     * @return 数据的 Optional
     */
    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    /**
     * 是否成功
     *
     * @return 如果成功返回true，否则返回false
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * 是否失败
     *
     * @return 如果失败返回true，否则返回false
     */
    public boolean isFailure() {
        return !success;
    }
}
