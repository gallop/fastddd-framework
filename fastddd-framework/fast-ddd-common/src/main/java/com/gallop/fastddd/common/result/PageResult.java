package com.gallop.fastddd.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果类，用于处理分页查询的响应
 * 
 * @author gallop
 * @since 2025-08-31
 * @version 1.0.0-SNAPSHOT
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 当前页码（从1开始）
     */
    private Integer pageNum;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 总记录数
     */
    private Long total;
    
    /**
     * 总页数
     */
    private Integer pages;
    
    /**
     * 当前页数据
     */
    private List<T> records;
    
    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;
    
    /**
     * 是否有下一页
     */
    private Boolean hasNext;
    
    /**
     * 构造函数
     * 
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param total 总记录数
     * @param records 当前页数据
     */
    public PageResult(Integer pageNum, Integer pageSize, Long total, List<T> records) {
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
        this.records = records;
        
        // 计算总页数
        this.pages = (int) Math.ceil((double) total / pageSize);
        
        // 计算是否有上一页和下一页
        this.hasPrevious = pageNum > 1;
        this.hasNext = pageNum < pages;
    }
    
    /**
     * 创建空的分页结果
     * 
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param <T> 数据类型
     * @return 空的分页结果
     */
    public static <T> PageResult<T> empty(Integer pageNum, Integer pageSize) {
        return new PageResult<>(pageNum, pageSize, 0L, List.of());
    }
    
    /**
     * 创建分页结果
     * 
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param total 总记录数
     * @param records 当前页数据
     * @param <T> 数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(Integer pageNum, Integer pageSize, Long total, List<T> records) {
        return new PageResult<>(pageNum, pageSize, total, records);
    }
    
    /**
     * 判断是否为空
     * 
     * @return 是否为空
     */
    public boolean isEmpty() {
        return records == null || records.isEmpty();
    }
    
    /**
     * 获取当前页记录数
     * 
     * @return 当前页记录数
     */
    public int getSize() {
        return records != null ? records.size() : 0;
    }
}