package com.gallop.fastddd.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
//import io.swagger.v3.oas.annotations.media.Content;
//import io.swagger.v3.oas.annotations.media.Schema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通用控制器基类，提供标准的CRUD REST端点。
 * 具体的控制器可以继承此类来获得基础的HTTP接口功能。
 * 
 * @param <S> 服务类型，必须继承IService
 * @param <T> 实体类型，必须继承BasePO
 * @author gallop
 * @since 2025-01-02
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
@RestController
@Validated
public abstract class BaseController<S extends IService<T>, T extends BasePO> {

    @Autowired
    protected S service;

    /**
     * 创建单个实体
     * @param entity 实体对象
     * @return 创建后的实体对象
     */
    @Operation(summary = "创建单个实体", description = "创建并保存一个新的实体对象")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "实体创建成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<T>> create(@Validated @RequestBody T entity) {
        try {
            boolean success = service.save(entity);
            if (success) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(entity, "创建成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("创建失败"));
            }
        } catch (Exception e) {
            log.error("创建实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("创建失败: " + e.getMessage()));
        }
    }

    /**
     * 批量创建实体
     * 
     * @param entityList 实体列表
     * @return 创建结果
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<String>> createBatch(@Valid @RequestBody List<T> entityList) {
        try {
            boolean success = service.saveBatch(entityList);
            if (success) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success("成功创建 " + entityList.size() + " 条记录", "批量创建成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("批量创建失败"));
            }
        } catch (Exception e) {
            log.error("批量创建实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量创建失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID查询实体
     * 
     * @param id 主键ID
     * @return 实体对象
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> getById(@PathVariable("id") @NotNull Serializable id) {
        try {
            Optional<T> entityOpt = service.getById(id);
            if (entityOpt.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(entityOpt.get(), "查询成功"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("未找到指定记录"));
            }
        } catch (Exception e) {
            log.error("根据ID查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 查询所有实体
     * 
     * @return 实体列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<T>>> list() {
        try {
            List<T> entityList = service.list();
            return ResponseEntity.ok(ApiResponse.success(entityList, "查询成功"));
        } catch (Exception e) {
            log.error("查询实体列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 分页查询实体
     * 
     * @param current 当前页码
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/page")
    public ResponseEntity<ApiResponse<IPage<T>>> page(
            @RequestParam(name = "current", defaultValue = "1") Long current,
            @RequestParam(name = "size", defaultValue = "10") Long size) {
        try {
            Page<T> page = new Page<>(current, size);
            IPage<T> result = service.page(page);
            return ResponseEntity.ok(ApiResponse.success(result, "分页查询成功"));
        } catch (Exception e) {
            log.error("分页查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("分页查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据字段查询实体列表
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 实体列表
     */
    @GetMapping("/field/{field}/{value}")
    public ResponseEntity<ApiResponse<List<T>>> listByField(
            @PathVariable("field") String field,
            @PathVariable("value") String value) {
        try {
            List<T> entityList = service.listByField(field, value);
            return ResponseEntity.ok(ApiResponse.success(entityList, "查询成功"));
        } catch (Exception e) {
            log.error("根据字段查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据多个字段查询实体列表
     * 
     * @param fieldValueMap 字段值映射
     * @return 实体列表
     */
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<T>>> listByFields(@RequestBody Map<String, Object> fieldValueMap) {
        try {
            List<T> entityList = service.listByFields(fieldValueMap);
            return ResponseEntity.ok(ApiResponse.success(entityList, "查询成功"));
        } catch (Exception e) {
            log.error("根据多字段查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("查询失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID更新实体
     * 
     * @param id 主键ID
     * @param entity 实体对象
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> updateById(
            @PathVariable("id") @NotNull Serializable id,
            @Valid @RequestBody T entity) {
        try {
            // 注意：这里假设实体已经包含正确的ID，或者由具体实现来处理ID设置
            boolean success = service.updateById(entity);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(entity, "更新成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("更新失败"));
            }
        } catch (Exception e) {
            log.error("更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 更新实体
     * 
     * @param entity 实体对象
     * @return 更新结果
     */
    @Operation(summary = "更新实体", description = "更新已存在的实体对象")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "实体更新成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    @PutMapping
    public ResponseEntity<ApiResponse<Boolean>> update(@Validated @RequestBody T entity) {
        try {
            boolean success = service.updateById(entity);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(true, "更新成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("更新失败"));
            }
        } catch (Exception e) {
            log.error("更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("更新失败: " + e.getMessage()));
        }
    }

    /**
     * 批量更新实体
     * 
     * @param entityList 实体列表
     * @return 更新结果
     */
    @PutMapping("/batch")
    public ResponseEntity<ApiResponse<String>> updateBatch(@Valid @RequestBody List<T> entityList) {
        try {
            boolean success = service.updateBatchById(entityList);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("成功更新 " + entityList.size() + " 条记录", "批量更新成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("批量更新失败"));
            }
        } catch (Exception e) {
            log.error("批量更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量更新失败: " + e.getMessage()));
        }
    }

    /**
     * 根据ID删除实体
     * 
     * @param id 主键ID
     * @return 删除结果
     */
    @Operation(summary = "删除实体", description = "根据ID删除已存在的实体对象")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "实体删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "未找到指定实体")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") @NotNull Serializable id) {
        try {
            boolean success = service.removeById(id);
            if (success) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("删除实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 批量删除实体
     * 
     * @param ids ID列表
     * @return 删除结果
     */
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<String>> deleteBatch(@RequestBody List<? extends Serializable> ids) {
        try {
            boolean success = service.removeByIds(ids);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success("成功删除 " + ids.size() + " 条记录", "批量删除成功"));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("批量删除失败"));
            }
        } catch (Exception e) {
            log.error("批量删除实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("批量删除失败: " + e.getMessage()));
        }
    }

    /**
     * 统计记录总数
     * 
     * @return 记录总数
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> count() {
        try {
            long count = service.count();
            return ResponseEntity.ok(ApiResponse.success(count, "统计成功"));
        } catch (Exception e) {
            log.error("统计记录数失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }

    /**
     * 根据字段统计记录数
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 记录数
     */
    @GetMapping("/count/{field}/{value}")
    public ResponseEntity<ApiResponse<Long>> countByField(
            @PathVariable("field") String field,
            @PathVariable("value") String value) {
        try {
            long count = service.countByField(field, value);
            return ResponseEntity.ok(ApiResponse.success(count, "统计成功"));
        } catch (Exception e) {
            log.error("根据字段统计记录数失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("统计失败: " + e.getMessage()));
        }
    }

    /**
     * 检查记录是否存在
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 是否存在
     */
    @GetMapping("/exists/{field}/{value}")
    public ResponseEntity<ApiResponse<Boolean>> existsByField(
            @PathVariable("field") String field,
            @PathVariable("value") String value) {
        try {
            boolean exists = service.existsByField(field, value);
            return ResponseEntity.ok(ApiResponse.success(exists, "检查成功"));
        } catch (Exception e) {
            log.error("检查记录存在性失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("检查失败: " + e.getMessage()));
        }
    }
}