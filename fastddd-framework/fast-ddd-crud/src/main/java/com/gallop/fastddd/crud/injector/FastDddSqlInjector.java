package com.gallop.fastddd.crud.injector;

import com.baomidou.mybatisplus.core.injector.AbstractMethod;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.gallop.fastddd.crud.injector.methods.BatchInsert;
import com.gallop.fastddd.crud.injector.methods.BatchUpdateById;

import java.util.List;

/**
 * FastDDD CRUD操作的自定义SQL注入器。
 * 扩展DefaultSqlInjector以添加自定义批量操作方法。
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
public class FastDddSqlInjector extends DefaultSqlInjector {

    /**
     * Get the list of methods to be injected into MyBatis mappers.
     * This includes all default MyBatis Plus methods plus our custom batch operations.
     * 
     * @param mapperClass the mapper class
     * @param tableInfo the table information
     * @return list of methods to inject
     */
    @Override
    public List<AbstractMethod> getMethodList(Class<?> mapperClass, TableInfo tableInfo) {
        // Get all default MyBatis Plus methods
        List<AbstractMethod> methodList = super.getMethodList(mapperClass, tableInfo);
        
        // Add our custom batch insert method
        methodList.add(new BatchInsert());
        
        // Add our custom batch update method
        methodList.add(new BatchUpdateById());
        
        return methodList;
    }
}