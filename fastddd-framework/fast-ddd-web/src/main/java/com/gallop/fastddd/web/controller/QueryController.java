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
 * Query Controller 基类（CQRS 读端）
 *
 * <p>异常由 {@link com.gallop.fastddd.web.exception.GlobalExceptionHandler} 统一处理，
 * 子类无需 try-catch。
 *
 * @param <Q> Query 类型
 * @param <R> 返回结果类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
@Slf4j
@Validated
public abstract class QueryController<Q extends Query, R> {

    protected abstract QueryHandler<Q, R> getQueryHandler();

    @PostMapping("/query")
    public ResponseEntity<ApiResponse<R>> executeQuery(@Valid @RequestBody Q query) {
        log.debug("执行查询: {}", query.getQueryType());
        R result = getQueryHandler().handle(query);
        log.debug("查询执行成功: {}", query.getQueryType());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<R>> search(@Valid Q query) {
        log.debug("执行查询: {}", query.getQueryType());
        R result = getQueryHandler().handle(query);
        log.debug("查询执行成功: {}", query.getQueryType());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/find-one")
    public ResponseEntity<ApiResponse<R>> findOne(@Valid @RequestBody Q query) {
        log.debug("执行查询: {}", query.getQueryType());
        R result = getQueryHandler().handle(query);
        if (result == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "查询结果为空"));
        }
        log.debug("查询执行成功: {}", query.getQueryType());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
