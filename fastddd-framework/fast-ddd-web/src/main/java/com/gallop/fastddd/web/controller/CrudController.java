package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通用CRUD控制器，提供标准的REST API端点。
 * 可以通过继承此类快速创建具有完整CRUD功能的控制器。
 * 
 * @param <S> 服务类型，必须继承IService
 * @param <T> 实体类型，必须继承BasePO
 * @author gallop
 * @since 2025-01-02
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@RestController
public abstract class CrudController<S extends IService<T>, T extends BasePO> extends BaseController<S, T> {

    /**
     * 获取实体类型名称，用于日志和错误消息
     * 
     * @return 实体类型名称
     */
    protected abstract String getEntityName();

    /**
     * 自定义查询条件构建
     * 子类可以重写此方法来实现特定的查询逻辑
     * 
     * @param params 查询参数
     * @return 查询结果
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<Object>> customQuery(@RequestBody Map<String, Object> params) {
        try {
            // 默认实现：根据参数进行字段查询
            if (params.isEmpty()) {
                return ResponseEntity.ok(ApiResponse.success(service.list(), "查询成功"));
            }
            
            Object result = service.listByFields(params);
            return ResponseEntity.ok(ApiResponse.success(result, "自定义查询成功"));
        } catch (Exception e) {
            log.error("{}自定义查询失败", getEntityName(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("自定义查询失败: " + e.getMessage()));
        }
    }

    /**
     * 健康检查端点
     * 
     * @return 健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        try {
            long count = service.count();
            String message = String.format("%s控制器运行正常，当前记录数: %d", getEntityName(), count);
            return ResponseEntity.ok(ApiResponse.success(message, "健康检查通过"));
        } catch (Exception e) {
            log.error("{}控制器健康检查失败", getEntityName(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("健康检查失败: " + e.getMessage()));
        }
    }

    /**
     * 获取实体统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        try {
            long totalCount = service.count();
            
            Map<String, Object> stats = Map.of(
                "entityName", getEntityName(),
                "totalCount", totalCount,
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(ApiResponse.success(stats, "统计信息获取成功"));
        } catch (Exception e) {
            log.error("{}统计信息获取失败", getEntityName(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("统计信息获取失败: " + e.getMessage()));
        }
    }
}