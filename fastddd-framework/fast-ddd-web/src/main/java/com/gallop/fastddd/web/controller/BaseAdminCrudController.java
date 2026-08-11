package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 管理端完整 CRUD Controller 基类。
 * <p>
 * 命名带 Admin，使用时应将路径放在 /api/admin/** 下。
 * 包含 lifecycle hooks 供子类覆写。
 *
 * @param <S>  服务类型
 * @param <T>  实体类型
 * @param <ID> 主键类型
 * @author gallop
 * @since 1.1
 */
@Slf4j
@Tag(name = "Admin CRUD")
public abstract class BaseAdminCrudController<S extends IService<T, ID>, T extends BasePO<ID>, ID extends Serializable>
        extends BaseReadOnlyController<S, T, ID> {

    // ==================== Lifecycle Hooks ====================

    protected void beforeCreate(T entity) {}

    protected void afterCreate(T entity) {}

    protected void beforeUpdate(ID id, T entity) {}

    protected void afterUpdate(ID id, T entity) {}

    protected void beforeDelete(ID id) {}

    protected void afterDelete(ID id) {}

    protected void beforeCreateBatch(List<T> entityList) {}

    protected void beforeUpdateBatch(List<T> entityList) {}

    // ==================== Write Operations ====================

    @Operation(summary = "[Admin CRUD] 创建单个实体", description = "创建并保存一个新的实体对象")
    @PostMapping
    public ResponseEntity<ApiResponse<T>> create(@Validated @RequestBody T entity) {
        try {
            beforeCreate(entity);
            boolean success = service.save(entity);
            if (success) {
                afterCreate(entity);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(entity, Messages.get("web.crud.create.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.create.failed")));
            }
        } catch (Exception e) {
            log.error("创建实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.create.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 批量创建实体")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Boolean>> createBatch(@Valid @RequestBody List<T> entityList) {
        try {
            beforeCreateBatch(entityList);
            boolean success = service.saveBatch(entityList);
            if (success) {
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(true, Messages.get("web.crud.batch-create.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.batch-create.failed")));
            }
        } catch (Exception e) {
            log.error("批量创建实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.batch-create.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 根据ID更新实体")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> updateById(
            @PathVariable("id") @NotNull String idStr,
            @Valid @RequestBody T entity) {
        try {
            // ID-005：parseId 经 IdCodec<ID> 解析为强类型；entity.setId(ID) 类型匹配，无强转。
            ID id = parseId(idStr);
            entity.setId(id);
            beforeUpdate(id, entity);
            boolean success = service.updateById(entity);
            if (success) {
                afterUpdate(id, entity);
                return ResponseEntity.ok(ApiResponse.success(entity, Messages.get("web.crud.update.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.update.failed")));
            }
        } catch (Exception e) {
            log.error("更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.update.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 更新实体")
    @PutMapping
    public ResponseEntity<ApiResponse<Boolean>> update(@Validated @RequestBody T entity) {
        try {
            beforeUpdate(entity.getId(), entity);
            boolean success = service.updateById(entity);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(true, Messages.get("web.crud.update.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.update.failed")));
            }
        } catch (Exception e) {
            log.error("更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.update.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 批量更新实体")
    @PutMapping("/batch")
    public ResponseEntity<ApiResponse<Boolean>> updateBatch(@Valid @RequestBody List<T> entityList) {
        try {
            beforeUpdateBatch(entityList);
            boolean success = service.updateBatchById(entityList);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(true, Messages.get("web.crud.batch-update.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.batch-update.failed")));
            }
        } catch (Exception e) {
            log.error("批量更新实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.batch-update.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 删除实体")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "实体删除成功"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "未找到指定实体")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable("id") @NotNull String idStr) {
        try {
            // ID-005：parseId 经 IdCodec<ID> 解析为强类型；service.removeById(ID) 无强转。
            ID id = parseId(idStr);
            beforeDelete(id);
            boolean success = service.removeById(id);
            if (success) {
                afterDelete(id);
                return ResponseEntity.ok(ApiResponse.success(true, Messages.get("web.crud.delete.success")));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, Messages.get("web.crud.delete.not-found")));
            }
        } catch (Exception e) {
            log.error("删除实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.delete.error", e.getMessage())));
        }
    }

    @Operation(summary = "[Admin CRUD] 批量删除实体")
    @DeleteMapping("/batch")
    public ResponseEntity<ApiResponse<Boolean>> deleteBatch(@RequestBody List<ID> ids) {
        try {
            boolean success = service.removeByIds(ids);
            if (success) {
                return ResponseEntity.ok(ApiResponse.success(true, Messages.get("web.crud.batch-delete.success")));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(Messages.get("web.crud.batch-delete.failed")));
            }
        } catch (Exception e) {
            log.error("批量删除实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.batch-delete.error", e.getMessage())));
        }
    }
}
