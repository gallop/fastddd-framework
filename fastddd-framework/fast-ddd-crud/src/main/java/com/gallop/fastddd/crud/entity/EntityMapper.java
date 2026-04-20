package com.gallop.fastddd.crud.entity;

import com.gallop.fastddd.common.domain.BaseEntity;
import com.gallop.fastddd.model.po.BasePO;

import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Entity-PO 映射器接口
 *
 * 定义充血实体（Entity）与持久化对象（PO）之间的转换契约。
 * 通常使用 MapStruct 自动生成实现。
 *
 * <p>使用示例：
 * <pre>{@code
 * // 使用 MapStruct 生成实现
 * &#64;Mapper(componentModel = "spring")
 * public interface OrderMapper extends EntityMapper<Order, OrderPO> {
 *     // MapStruct 会自动生成 toEntity 和 toPo 方法的实现
 * }
 *
 * // 在 Repository 中使用
 * &#64;Service
 * public class OrderRepositoryImpl implements OrderRepository {
 *     private final OrderMapper mapper;
 *     private final OrderBaseMapper baseMapper;
 *
 *     public Order save(Order entity) {
 *         OrderPO po = mapper.toPo(entity);
 *         baseMapper.insert(po);
 *         return mapper.toEntity(po);
 *     }
 * }
 * }</pre>
 *
 * @param <E> 实体类型
 * @param <P> PO 类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
public interface EntityMapper<E extends BaseEntity<?>, P extends BasePO> {

    /**
     * 将 PO 转换为 Entity
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    E toEntity(P po);

    /**
     * 将 Entity 转换为 PO
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    P toPo(E entity);

    /**
     * 批量将 PO 列表转换为 Entity 列表
     *
     * @param pos 持久化对象列表
     * @return 领域实体列表
     */
    default List<E> toEntityList(List<P> pos) {
        if (pos == null) {
            return List.of();
        }
        return pos.stream()
                .map(this::toEntity)
                .toList();
    }

    /**
     * 批量将 PO 可迭代对象转换为 Entity 列表
     *
     * @param pos 持久化对象可迭代对象
     * @return 领域实体列表
     */
    default List<E> toEntityList(Iterable<P> pos) {
        if (pos == null) {
            return List.of();
        }
        return StreamSupport.stream(pos.spliterator(), false)
                .map(this::toEntity)
                .toList();
    }

    /**
     * 批量将 PO 流转换为 Entity 流
     *
     * @param pos 持久化对象流
     * @return 领域实体流
     */
    default Stream<E> toEntityStream(Stream<P> pos) {
        if (pos == null) {
            return Stream.empty();
        }
        return pos.map(this::toEntity);
    }

    /**
     * 批量将 Entity 列表转换为 PO 列表
     *
     * @param entities 领域实体列表
     * @return 持久化对象列表
     */
    default List<P> toPoList(List<E> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream()
                .map(this::toPo)
                .toList();
    }

    /**
     * 批量将 Entity 可迭代对象转换为 PO 列表
     *
     * @param entities 领域实体可迭代对象
     * @return 持久化对象列表
     */
    default List<P> toPoList(Iterable<E> entities) {
        if (entities == null) {
            return List.of();
        }
        return StreamSupport.stream(entities.spliterator(), false)
                .map(this::toPo)
                .toList();
    }

    /**
     * 批量将 Entity 流转换为 PO 流
     *
     * @param entities 领域实体流
     * @return 持久化对象流
     */
    default Stream<P> toPoStream(Stream<E> entities) {
        if (entities == null) {
            return Stream.empty();
        }
        return entities.map(this::toPo);
    }

    /**
     * 更新 PO 字段（不修改null值）
     * 用于部分更新场景
     *
     * @param source 源PO
     * @param target 目标PO
     */
    default void updatePoNotNullFields(P source, P target) {
        // 默认实现为空，子类可以覆盖
        // MapStruct 可以使用 @MappingTarget 和 @BeanMapping(nullValuePropertyMappingStrategy = IGNORE) 生成
    }
}
