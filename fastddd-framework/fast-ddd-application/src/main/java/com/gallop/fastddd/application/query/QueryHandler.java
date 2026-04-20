package com.gallop.fastddd.application.query;

/**
 * Query 处理器接口
 *
 * 定义处理 Query 的契约。每个具体的 Query 都应有对应的 Handler。
 * 这是函数式接口，可以通过 Lambda 表达式实现。
 *
 * <p>使用示例：
 * <pre>{@code
 * @Component
 * public class GetOrderByIdQueryHandler implements QueryHandler<GetOrderByIdQuery, OrderDTO> {
 *     private final OrderRepository orderRepository;
 *
 *     @Override
 *     public OrderDTO handle(GetOrderByIdQuery query) {
 *         Order order = orderRepository.findById(query.getOrderId())
 *             .orElseThrow(() -> new NotFoundException("订单不存在"));
 *         return OrderDTO.from(order);
 *     }
 * }
 * }</pre>
 *
 * @param <Q> Query 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@FunctionalInterface
public interface QueryHandler<Q extends Query, R> {

    /**
     * 处理查询
     *
     * @param query 要处理的查询
     * @return 查询结果
     */
    R handle(Q query);
}
