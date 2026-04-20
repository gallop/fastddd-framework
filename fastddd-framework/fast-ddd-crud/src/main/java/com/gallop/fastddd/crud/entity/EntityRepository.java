package com.gallop.fastddd.crud.entity;

import com.gallop.fastddd.common.domain.BaseEntity;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Entity Repository 接口
 *
 * 提供对充血实体（Rich Entity）的持久化操作。
 * 区别于 BaseRepository（操作 PO），此接口操作领域实体（Entity）。
 *
 * <p>职责：
 * <ul>
 *   <li>实体的持久化和检索</li>
 *   <li>实体与 PO 之间的转换</li>
 *   <li>领域事件的发布</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * public interface OrderEntityRepository extends EntityRepository<Order, Long> {
 *     // 继承所有 CRUD 方法
 *     // 可以添加自定义查询方法
 *     List<Order> findByCustomerId(String customerId);
 * }
 * }</pre>
 *
 * @param <E> 实体类型
 * @param <ID> 主键类型
 * @author FastDDD Framework Team
 * @since 1.1.0
 */
public interface EntityRepository<E extends BaseEntity<ID>, ID extends Serializable> {

    /**
     * 保存实体
     *
     * @param entity 要保存的实体
     * @return 保存后的实体
     */
    E save(E entity);

    /**
     * 保存实体并发布领域事件
     * 如果实体是 AggregateRoot，会自动收集并发布其领域事件
     *
     * @param entity 要保存的实体
     * @return 保存后的实体
     */
    E saveAndPublishEvents(E entity);

    /**
     * 根据ID查找实体
     *
     * @param id 主键
     * @return 实体的 Optional
     */
    Optional<E> findById(ID id);

    /**
     * 查找所有实体
     *
     * @return 实体列表
     */
    List<E> findAll();

    /**
     * 根据ID列表查找实体
     *
     * @param ids 主键列表
     * @return 实体列表
     */
    List<E> findAllById(Iterable<ID> ids);

    /**
     * 根据ID删除实体
     *
     * @param id 主键
     */
    void deleteById(ID id);

    /**
     * 删除实体
     *
     * @param entity 要删除的实体
     */
    void delete(E entity);

    /**
     * 批量删除实体
     *
     * @param entities 要删除的实体列表
     */
    void deleteAll(Iterable<? extends E> entities);

    /**
     * 删除所有实体
     */
    void deleteAll();

    /**
     * 判断指定ID的实体是否存在
     *
     * @param id 主键
     * @return 如果存在返回true，否则返回false
     */
    boolean existsById(ID id);

    /**
     * 统计实体数量
     *
     * @return 实体数量
     */
    long count();

    /**
     * 刷新实体状态
     * 从数据库重新加载实体数据
     *
     * @param entity 要刷新的实体
     */
    void refresh(E entity);

    /**
     * 刷新所有未保存的更改
     */
    void flush();
}
