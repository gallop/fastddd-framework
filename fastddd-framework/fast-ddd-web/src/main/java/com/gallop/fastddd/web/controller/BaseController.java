package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用控制器基类，提供标准的CRUD REST端点。
 *
 * @deprecated since 1.1. Use {@link BaseAdminCrudController} for admin CRUD APIs,
 *             or {@link BaseReadOnlyController} for read-only APIs.
 * @param <S> 服务类型，必须继承IService
 * @param <T> 实体类型，必须继承BasePO
 * @author gallop
 * @since 2025-01-02
 */
@Deprecated(since = "1.1", forRemoval = false)
@RestController
public abstract class BaseController<S extends IService<T, Long>, T extends BasePO<Long>>
        extends BaseAdminCrudController<S, T> {
}
