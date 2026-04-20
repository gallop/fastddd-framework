package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.application.query.Query;
import com.gallop.fastddd.application.query.QueryHandler;
import com.gallop.fastddd.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Query Controller 基类
 *
 * 提供 CQRS 模式中 Query 端的标准 REST 接口实现。
 * 子类只需提供 QueryHandler，自动获得标准的查询端点。
 *
 * <p>功能：
 * <ul>
 *   <li>自动处理 Query 验证</li>
 *   <li>统一异常处理</li>
 *   <li>标准 ApiResponse 响应格式</li>
 *   <li>日志记录</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * &#64;RestController
 * &#64;RequestMapping("/api/orders")
 * public class OrderQueryController extends QueryController<GetOrderByIdQuery, OrderDTO> {
 *
 *     private final GetOrderByIdQueryHandler handler;
 *
 *     public OrderQueryController(GetOrderByIdQueryHandler handler) {
 *         this.handler = handler;
 *     }
 *
 *     &#64;Override
 *     protected QueryHandler<GetOrderByIdQuery, OrderDTO> getQueryHandler() {
 *         return handler;
 *     }
 * }
 * }</pre>
 *
 * <p>HTTP 方法说明：
 * <ul>
 *   <li>POST /api/resource/query - 执行查询并返回结果</li>
 *   <li>POST /api/resource/search - 执行查询并返回单个结果</li>
 * </ul>
 *
 * @param <Q> Query 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Slf4j
@Validated
public abstract class QueryController<Q extends Query, R> {

    /**
     * 获取 Query Handler
     * 子类必须实现此方法以提供具体的 QueryHandler
     *
     * @return QueryHandler 实例
     */
    protected abstract QueryHandler<Q, R> getQueryHandler();

    /**
     * 执行查询
     * 接收 Query 并通过 Handler 执行，返回标准 ApiResponse 格式
     *
     * @param query 要执行的查询
     * @return 查询结果
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<R>> executeQuery(@Valid @RequestBody Q query) {
        try {
            log.debug("执行查询: {}", query.getQueryType());

            R result = getQueryHandler().handle(query);

            log.debug("查询执行成功: {}", query.getQueryType());
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));

        } catch (Exception e) {
            log.error("执行查询失败: {} - {}", query.getQueryType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 执行查询（GET 方法）
     * 用于简单的查询场景，Query 参数通过请求参数传递
     *
     * @param query 要执行的查询
     * @return 查询结果
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<R>> search(@Valid Q query) {
        try {
            log.debug("执行查询: {}", query.getQueryType());

            R result = getQueryHandler().handle(query);

            log.debug("查询执行成功: {}", query.getQueryType());
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));

        } catch (Exception e) {
            log.error("执行查询失败: {} - {}", query.getQueryType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 执行查询并返回单个结果
     * 用于期望返回单个结果的查询场景
     *
     * @param query 要执行的查询
     * @return 查询结果
     */
    @PostMapping("/find-one")
    public ResponseEntity<ApiResponse<R>> findOne(@Valid @RequestBody Q query) {
        try {
            log.debug("执行查询: {}", query.getQueryType());

            R result = getQueryHandler().handle(query);

            if (result == null) {
                log.debug("查询结果为空: {}", query.getQueryType());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "未找到匹配的数据"));
            }

            log.debug("查询执行成功: {}", query.getQueryType());
            return ResponseEntity.ok(ApiResponse.success(result, "查询成功"));

        } catch (Exception e) {
            log.error("执行查询失败: {} - {}", query.getQueryType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }
}
