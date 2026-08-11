package com.gallop.fastddd.web.controller;

import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import com.gallop.fastddd.web.idcodec.IdCodec;
import com.gallop.fastddd.web.idcodec.IdCodecs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Controller 基础设施层：持有 service、ID 编解码、实体类推断。
 * 不声明任何 HTTP endpoint，仅供子类复用。
 *
 * <p>ID-005：以 {@link IdCodec} 取代历史上的 {@code resolveId(String)}（基于 MyBatis-Plus
 * {@code IdType} 的"DB 生成策略 → Java 值类型"猜测，ADR-001 §2.6）。主键类型现由声明的
 * {@code <ID>} 泛型参数显式决定，{@link #parseId(String)} 经 {@link #idCodec()} 解析为强类型，
 * {@code service.getById(ID)} 等调用全链路类型安全、无需强转。业务自定义主键类型（Hashid 混淆 /
 * 领域值对象）可覆写 {@link #idCodec()} 注入。</p>
 *
 * @param <S>  服务类型
 * @param <T>  实体类型
 * @param <ID> 主键类型（透传自实体 {@code BasePO<ID>}）
 * @author gallop
 * @since 1.1
 */
@Validated
public abstract class AbstractBaseController<S extends IService<T, ID>, T extends BasePO<ID>, ID extends Serializable> {

    @Autowired
    protected S service;

    private final Class<?> entityClass;
    private final Class<ID> idClass;
    private final IdCodec<ID> codec;

    protected AbstractBaseController() {
        this.entityClass = resolveTypeArgument(1);
        this.idClass = resolveTypeArgument(2);
        this.codec = initCodec();
    }

    /**
     * 默认经 {@link IdCodecs#forClass(Class)} 按主键类型解析内置 codec；业务可覆写注入自定义实现。
     */
    protected IdCodec<ID> idCodec() {
        return codec;
    }

    private IdCodec<ID> initCodec() {
        return IdCodecs.forClass(idClass);
    }

    /**
     * 将 URL 路径字符串解析为强类型主键（经 {@link #idCodec()}）。
     *
     * @throws IllegalArgumentException 字符串不是该主键类型的合法表示
     */
    protected ID parseId(String idStr) {
        return idCodec().parse(idStr);
    }

    /**
     * @return 推断的实体 {@code Class}（来自继承链首个参数化父类的第 2 个类型实参）
     */
    protected Class<?> entityClass() {
        return entityClass;
    }

    @SuppressWarnings("unchecked")
    private <R> Class<R> resolveTypeArgument(int index) {
        Class<?> clazz = this.getClass();
        while (clazz != Object.class) {
            Type type = clazz.getGenericSuperclass();
            if (type instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > index && args[index] instanceof Class<?> c) {
                    return (Class<R>) c;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
