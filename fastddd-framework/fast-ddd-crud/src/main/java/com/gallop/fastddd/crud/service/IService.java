package com.gallop.fastddd.crud.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.model.po.BasePO;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通用服务接口，提供标准的CRUD操作。
 * 扩展了MyBatis Plus的基础功能，为业务层提供统一的数据访问接口。
 *
 * <p>（ID-004）类型参数 {@code ID} 把 {@code getById}/{@code removeById}/{@code listByIds} 等
 * 主键方法从 {@code Serializable} 收窄为强类型——错误 ID 类型在编译期失败。{@code ID} 透传自
 * {@code T extends BasePO<ID>}，实体与其 ID 类型在签名层绑定。
 *
 * @param <T> 实体类型，必须继承 {@code BasePO<ID>}
 * @param <ID> 主键类型（透传自实体）
 * @author gallop
 * @since 2025-01-02
 * @version 1.0.0-SNAPSHOT
 */
public interface IService<T extends BasePO<ID>, ID extends Serializable> {

    /**
     * 保存实体（插入）
     * 
     * @param entity 实体对象
     * @return 是否保存成功
     */
    boolean save(T entity);

    /**
     * 批量保存实体
     * 
     * @param entityList 实体列表
     * @return 是否保存成功
     */
    boolean saveBatch(Collection<T> entityList);

    /**
     * 批量保存实体（指定批次大小）
     * 
     * @param entityList 实体列表
     * @param batchSize 批次大小
     * @return 是否保存成功
     */
    boolean saveBatch(Collection<T> entityList, int batchSize);

    /**
     * 保存或更新实体
     * 
     * @param entity 实体对象
     * @return 是否操作成功
     */
    boolean saveOrUpdate(T entity);

    /**
     * 批量保存或更新实体
     * 
     * @param entityList 实体列表
     * @return 是否操作成功
     */
    boolean saveOrUpdateBatch(Collection<T> entityList);

    /**
     * 批量保存或更新实体（指定批次大小）
     * 
     * @param entityList 实体列表
     * @param batchSize 批次大小
     * @return 是否操作成功
     */
    boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize);

    /**
     * 根据ID删除
     *
     * @param id 主键ID（强类型 {@code ID}）
     * @return 是否删除成功
     */
    boolean removeById(ID id);

    /**
     * 根据条件删除
     *
     * @param queryWrapper 查询条件
     * @return 是否删除成功
     */
    boolean remove(Wrapper<T> queryWrapper);

    /**
     * 根据ID批量删除
     *
     * @param idList ID列表（强类型 {@code ID}）
     * @return 是否删除成功
     */
    boolean removeByIds(Collection<? extends ID> idList);

    /**
     * 根据ID更新
     * 
     * @param entity 实体对象
     * @return 是否更新成功
     */
    boolean updateById(T entity);

    /**
     * 根据条件更新
     * 
     * @param entity 实体对象
     * @param updateWrapper 更新条件
     * @return 是否更新成功
     */
    boolean update(T entity, Wrapper<T> updateWrapper);

    /**
     * 批量更新
     * 
     * @param entityList 实体列表
     * @return 是否更新成功
     */
    boolean updateBatchById(Collection<T> entityList);

    /**
     * 批量更新（指定批次大小）
     * 
     * @param entityList 实体列表
     * @param batchSize 批次大小
     * @return 是否更新成功
     */
    boolean updateBatchById(Collection<T> entityList, int batchSize);

    /**
     * 根据ID查询
     *
     * @param id 主键ID（强类型 {@code ID}）
     * @return 实体对象
     */
    Optional<T> getById(ID id);

    /**
     * 根据ID批量查询
     *
     * @param idList ID列表（强类型 {@code ID}）
     * @return 实体列表
     */
    List<T> listByIds(Collection<? extends ID> idList);

    /**
     * 根据条件查询一条记录
     * 
     * @param queryWrapper 查询条件
     * @return 实体对象
     */
    Optional<T> getOne(Wrapper<T> queryWrapper);

    /**
     * 根据条件查询一条记录（不抛出异常）
     * 
     * @param queryWrapper 查询条件
     * @param throwEx 是否抛出异常
     * @return 实体对象
     */
    Optional<T> getOne(Wrapper<T> queryWrapper, boolean throwEx);

    /**
     * 根据条件查询列表
     * 
     * @param queryWrapper 查询条件
     * @return 实体列表
     */
    List<T> list(Wrapper<T> queryWrapper);

    /**
     * 查询所有记录
     * 
     * @return 实体列表
     */
    List<T> list();

    /**
     * 分页查询
     * 
     * @param page 分页对象
     * @return 分页结果
     */
    IPage<T> page(IPage<T> page);

    /**
     * 分页查询
     * 
     * @param page 分页对象
     * @param queryWrapper 查询条件
     * @return 分页结果
     */
    IPage<T> page(IPage<T> page, Wrapper<T> queryWrapper);

    /**
     * 根据条件查询总记录数
     * 
     * @param queryWrapper 查询条件
     * @return 总记录数
     */
    long count(Wrapper<T> queryWrapper);

    /**
     * 查询总记录数
     * 
     * @return 总记录数
     */
    long count();

    /**
     * 根据字段查询一条记录
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 实体对象
     */
    Optional<T> getByField(String field, Object value);

    /**
     * 根据字段查询列表
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 实体列表
     */
    List<T> listByField(String field, Object value);

    /**
     * 根据多个字段查询列表
     * 
     * @param fieldValueMap 字段值映射
     * @return 实体列表
     */
    List<T> listByFields(Map<String, Object> fieldValueMap);

    /**
     * 根据字段值判断是否存在
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 是否存在
     */
    boolean existsByField(String field, Object value);

    /**
     * 根据字段统计数量
     * 
     * @param field 字段名
     * @param value 字段值
     * @return 记录数量
     */
    long countByField(String field, Object value);
}