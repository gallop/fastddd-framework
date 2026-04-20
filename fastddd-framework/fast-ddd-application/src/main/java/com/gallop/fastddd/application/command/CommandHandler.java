package com.gallop.fastddd.application.command;

/**
 * Command 处理器接口
 *
 * 定义处理 Command 的契约。每个具体的 Command 都应有对应的 Handler。
 * 这是函数式接口，可以通过 Lambda 表达式实现。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Component
 * public class CreateOrderCommandHandler implements CommandHandler<CreateOrderCommand, OrderDTO> {
 *     private final OrderRepository orderRepository;
 *
 *     @Override
 *     public OrderDTO handle(CreateOrderCommand command) {
 *         Order order = Order.create(command.getCustomerId(), command.getAmount());
 *         orderRepository.save(order);
 *         return OrderDTO.from(order);
 *     }
 * }
 * }</pre>
 *
 * @param <C> Command 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@FunctionalInterface
public interface CommandHandler<C extends Command, R> {

    /**
     * 处理命令
     *
     * @param command 要处理的命令
     * @return 处理结果
     * @throws CommandValidationException 命令验证失败
     */
    R handle(C command);
}
