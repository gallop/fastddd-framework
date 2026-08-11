package com.gallop.fastddd.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.common.i18n.Messages;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * 只读 Controller 基类，仅暴露 GET 查询端点。
 * 适合字典查询、后台数据展示等只读场景。
 *
 * @param <S> 服务类型
 * @param <T> 实体类型
 * @author gallop
 * @since 1.1
 */
@Slf4j
@Tag(name = "Read-Only API")
public abstract class BaseReadOnlyController<S extends IService<T, Long>, T extends BasePO<Long>>
        extends AbstractBaseController<S, T> {

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<T>> getById(@PathVariable("id") @NotNull String idStr) {
        try {
            Serializable id = resolveId(idStr);
            // ID-004：service.getById 收窄为 Long（控制器侧主键恒 Long）；ID-005 以 IdCodec 替换 resolveId 后强转消失。
            Optional<T> entityOpt = service.getById((Long) id);
            if (entityOpt.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success(entityOpt.get(), Messages.get("web.crud.query.success")));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(Messages.get("web.crud.query.not-found")));
            }
        } catch (Exception e) {
            log.error("根据ID查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.query.failed", e.getMessage())));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<T>>> list() {
        try {
            List<T> entityList = service.list();
            return ResponseEntity.ok(ApiResponse.success(entityList, Messages.get("web.crud.query.success")));
        } catch (Exception e) {
            log.error("查询实体列表失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.query.failed", e.getMessage())));
        }
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<IPage<T>>> page(
            @RequestParam(name = "current", defaultValue = "1") Long current,
            @RequestParam(name = "size", defaultValue = "10") Long size) {
        try {
            Page<T> page = new Page<>(current, size);
            IPage<T> result = service.page(page);
            return ResponseEntity.ok(ApiResponse.success(result, Messages.get("web.crud.page.success")));
        } catch (Exception e) {
            log.error("分页查询实体失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(Messages.get("web.crud.page.failed", e.getMessage())));
        }
    }
}
