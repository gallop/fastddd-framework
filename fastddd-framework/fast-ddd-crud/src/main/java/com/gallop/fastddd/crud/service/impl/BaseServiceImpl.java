package com.gallop.fastddd.crud.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gallop.fastddd.crud.repository.BaseRepository;
import com.gallop.fastddd.crud.service.IService;
import com.gallop.fastddd.model.po.BasePO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 通用服务实现基类，提供标准CRUD操作的默认实现。
 * 业务服务类可以继承此类来获得基础的数据访问功能。
 * 
 * @param <M> Repository类型，必须继承BaseRepository
 * @param <T> 实体类型，必须继承BasePO
 * @author gallop
 * @since 2025-01-02
 * @version 1.0.0-SNAPSHOT
 */
@Slf4j
public abstract class BaseServiceImpl<M extends BaseRepository<T>, T extends BasePO> implements IService<T> {

    @Autowired
    protected M baseMapper;

    /**
     * 默认批次大小
     */
    protected static final int DEFAULT_BATCH_SIZE = 1000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(T entity) {
        return baseMapper.insert(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(Collection<T> entityList) {
        return saveBatch(entityList, DEFAULT_BATCH_SIZE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList)) {
            return false;
        }
        
        try {
            return baseMapper.batchInsert(entityList.stream().toList()) > 0;
        } catch (Exception e) {
            log.error("批量保存失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdate(T entity) {
        if (entity.getId() == null) {
            return save(entity);
        } else {
            return updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBatch(Collection<T> entityList) {
        return saveOrUpdateBatch(entityList, DEFAULT_BATCH_SIZE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList)) {
            return false;
        }
        
        try {
            List<T> insertList = entityList.stream()
                    .filter(entity -> entity.getId() == null)
                    .toList();
            List<T> updateList = entityList.stream()
                    .filter(entity -> entity.getId() != null)
                    .toList();
            
            boolean insertResult = true;
            boolean updateResult = true;
            
            if (!insertList.isEmpty()) {
                insertResult = baseMapper.batchInsert(insertList) > 0;
            }
            
            if (!updateList.isEmpty()) {
                updateResult = baseMapper.batchUpdateById(updateList) > 0;
            }
            
            return insertResult && updateResult;
        } catch (Exception e) {
            log.error("批量保存或更新失败", e);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        return baseMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Wrapper<T> queryWrapper) {
        return baseMapper.delete(queryWrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeByIds(Collection<? extends Serializable> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return false;
        }
        return baseMapper.deleteBatchIds(idList) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(T entity) {
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(T entity, Wrapper<T> updateWrapper) {
        return baseMapper.update(entity, updateWrapper) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchById(Collection<T> entityList) {
        return updateBatchById(entityList, DEFAULT_BATCH_SIZE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchById(Collection<T> entityList, int batchSize) {
        if (CollectionUtils.isEmpty(entityList)) {
            return false;
        }
        
        try {
            return baseMapper.batchUpdateById(entityList.stream().toList()) > 0;
        } catch (Exception e) {
            log.error("批量更新失败", e);
            return false;
        }
    }

    @Override
    public Optional<T> getById(Serializable id) {
        return Optional.ofNullable(baseMapper.selectById(id));
    }

    @Override
    public List<T> listByIds(Collection<? extends Serializable> idList) {
        if (CollectionUtils.isEmpty(idList)) {
            return List.of();
        }
        return baseMapper.selectBatchIds(idList);
    }

    @Override
    public Optional<T> getOne(Wrapper<T> queryWrapper) {
        return getOne(queryWrapper, true);
    }

    @Override
    public Optional<T> getOne(Wrapper<T> queryWrapper, boolean throwEx) {
        try {
            T result = baseMapper.selectOne(queryWrapper);
            return Optional.ofNullable(result);
        } catch (Exception e) {
            if (throwEx) {
                throw e;
            }
            log.warn("查询单条记录时发生异常: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<T> list(Wrapper<T> queryWrapper) {
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public List<T> list() {
        return baseMapper.selectList(null);
    }

    @Override
    public IPage<T> page(IPage<T> page) {
        return baseMapper.selectPage(page, null);
    }

    @Override
    public IPage<T> page(IPage<T> page, Wrapper<T> queryWrapper) {
        return baseMapper.selectPage(page, queryWrapper);
    }

    @Override
    public long count(Wrapper<T> queryWrapper) {
        return baseMapper.selectCount(queryWrapper);
    }

    @Override
    public long count() {
        return baseMapper.selectCount(null);
    }

    @Override
    public Optional<T> getByField(String field, Object value) {
        return baseMapper.selectOneByField(field, value);
    }

    @Override
    public List<T> listByField(String field, Object value) {
        return baseMapper.selectListByField(field, value);
    }

    @Override
    public List<T> listByFields(Map<String, Object> fieldValueMap) {
        if (fieldValueMap == null || fieldValueMap.isEmpty()) {
            return List.of();
        }
        
        Wrapper<T> queryWrapper = Wrappers.<T>query().allEq(fieldValueMap);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public boolean existsByField(String field, Object value) {
        return baseMapper.existsByField(field, value);
    }

    @Override
    public long countByField(String field, Object value) {
        return baseMapper.countByField(field, value);
    }
}