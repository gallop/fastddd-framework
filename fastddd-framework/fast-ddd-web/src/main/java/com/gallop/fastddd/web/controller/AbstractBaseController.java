package com.gallop.fastddd.web.controller;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Controller 基础设施层：持有 service、ID 解析、实体类推断。
 * 不声明任何 HTTP endpoint，仅供子类复用。
 *
 * @param <S> 服务类型
 * @param <T> 实体类型
 * @author gallop
 * @since 1.1
 */
@Validated
public abstract class AbstractBaseController<S extends IService<T>, T extends BasePO> {

    @Autowired
    protected S service;

    private final Class<?> entityClass;

    protected AbstractBaseController() {
        this.entityClass = resolveEntityClass();
    }

    private Class<?> resolveEntityClass() {
        Class<?> clazz = this.getClass();
        while (clazz != Object.class) {
            Type type = clazz.getGenericSuperclass();
            if (type instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > 1 && args[1] instanceof Class<?> c) {
                    return c;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 根据实体 MyBatis-Plus TableInfo 的 ID 策略，将 URL 路径字符串转换为正确的 ID 类型。
     */
    protected Serializable resolveId(String idStr) {
        if (entityClass != null) {
            var tableInfo = TableInfoHelper.getTableInfo(entityClass);
            if (tableInfo != null) {
                IdType idType = tableInfo.getIdType();
                if (idType == IdType.ASSIGN_ID || idType == IdType.AUTO) {
                    try {
                        return Long.parseLong(idStr);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid numeric ID: " + idStr);
                    }
                }
                return idStr;
            }
        }
        try {
            return Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            return idStr;
        }
    }
}
