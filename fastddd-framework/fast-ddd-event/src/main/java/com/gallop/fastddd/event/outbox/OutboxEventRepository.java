package com.gallop.fastddd.event.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Outbox事件数据访问接口
 * 
 * 提供Outbox事件的CRUD操作和查询功能。
 * 
 * @author gallop
 * @since 2025-01-09
 */
@Mapper
public interface OutboxEventRepository extends BaseMapper<OutboxEvent> {
    
    /**
     * 根据事件ID查找事件
     * 
     * @param eventId 事件ID
     * @return 事件实体
     */
    @Select("SELECT * FROM outbox_events WHERE event_id = #{eventId}")
    OutboxEvent selectByEventId(@Param("eventId") String eventId);
    
    /**
     * 查找指定状态的事件（分页）
     * 
     * @param status 事件状态
     * @param page 分页参数
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = #{status} ORDER BY created_at ASC")
    IPage<OutboxEvent> selectByStatusOrderByCreatedAtAsc(@Param("status") String status, Page<OutboxEvent> page);
    
    /**
     * 查找待处理的事件（包括失败但可重试的事件）
     * 
     * @param maxRetryTime 最大重试时间
     * @param page 分页参数
     * @return 待处理事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE " +
           "(status = 'PENDING') OR " +
           "(status = 'FAILED' AND retry_count < max_retry_count AND " +
           "(next_retry_at IS NULL OR next_retry_at <= #{maxRetryTime})) " +
           "ORDER BY created_at ASC")
    IPage<OutboxEvent> selectPendingEvents(@Param("maxRetryTime") LocalDateTime maxRetryTime, Page<OutboxEvent> page);
    
    /**
     * 使用悲观锁查找待处理的事件（批量处理）
     * 支持并发控制，防止多个实例处理同一事件
     * 
     * @param maxRetryTime 最大重试时间
     * @param limit 限制数量
     * @return 待处理事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE " +
           "(status = 'PENDING') OR " +
           "(status = 'FAILED' AND retry_count < max_retry_count AND " +
           "(next_retry_at IS NULL OR next_retry_at <= #{maxRetryTime})) " +
           "ORDER BY created_at ASC LIMIT #{limit} FOR UPDATE")
    List<OutboxEvent> selectPendingEventsWithLock(@Param("maxRetryTime") LocalDateTime maxRetryTime, @Param("limit") int limit);
    
    /**
     * 使用悲观锁查找失败的事件（批量重试）
     * 支持并发控制，防止多个实例重试同一事件
     * 
     * @param maxRetryTime 最大重试时间
     * @param limit 限制数量
     * @return 失败事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE " +
           "status = 'FAILED' AND retry_count < max_retry_count AND " +
           "(next_retry_at IS NULL OR next_retry_at <= #{maxRetryTime}) " +
           "ORDER BY updated_at ASC LIMIT #{limit} FOR UPDATE")
    List<OutboxEvent> selectFailedEventsWithLock(@Param("maxRetryTime") LocalDateTime maxRetryTime, @Param("limit") int limit);
    
    /**
     * 查找指定聚合根的事件
     * 
     * @param aggregateId 聚合根ID
     * @param aggregateType 聚合根类型
     * @param page 分页参数
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE aggregate_id = #{aggregateId} AND aggregate_type = #{aggregateType} " +
           "ORDER BY created_at DESC")
    IPage<OutboxEvent> selectByAggregateIdAndAggregateTypeOrderByCreatedAtDesc(
            @Param("aggregateId") String aggregateId, @Param("aggregateType") String aggregateType, Page<OutboxEvent> page);
    
    /**
     * 查找指定事件类型的事件
     * 
     * @param eventType 事件类型
     * @param page 分页参数
     * @return 事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE event_type = #{eventType} ORDER BY created_at DESC")
    IPage<OutboxEvent> selectByEventTypeOrderByCreatedAtDesc(@Param("eventType") String eventType, Page<OutboxEvent> page);
    
    /**
     * 统计指定状态的事件数量
     * 
     * @param status 事件状态
     * @return 事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_events WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
    
    /**
     * 统计待处理事件数量
     * 
     * @param maxRetryTime 最大重试时间
     * @return 待处理事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_events WHERE " +
           "(status = 'PENDING') OR " +
           "(status = 'FAILED' AND retry_count < max_retry_count AND " +
           "(next_retry_at IS NULL OR next_retry_at <= #{maxRetryTime}))")
    long countPendingEvents(@Param("maxRetryTime") LocalDateTime maxRetryTime);
    
    /**
     * 删除指定时间之前已发布的事件（清理历史数据）
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Delete("DELETE FROM outbox_events WHERE status = 'PUBLISHED' AND published_at < #{beforeTime}")
    int deletePublishedEventsBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 删除指定时间之前已取消的事件（清理历史数据）
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Delete("DELETE FROM outbox_events WHERE status = 'CANCELLED' AND updated_at < #{beforeTime}")
    int deleteCancelledEventsBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 查找超过最大重试次数的失败事件
     * 
     * @param page 分页参数
     * @return 失败事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = 'FAILED' AND retry_count >= max_retry_count " +
           "ORDER BY updated_at DESC")
    IPage<OutboxEvent> selectFailedEventsExceedingMaxRetries(Page<OutboxEvent> page);
    
    /**
     * 批量更新事件状态为处理中
     * 
     * @param eventIds 事件ID列表
     * @param processingTime 处理时间
     * @return 更新的记录数
     */
    @Update("<script>" +
           "UPDATE outbox_events SET status = 'PROCESSING', updated_at = #{processingTime} " +
           "WHERE id IN " +
           "<foreach collection='eventIds' item='id' open='(' separator=',' close=')'>" +
           "#{id}" +
           "</foreach>" +
           " AND status IN ('PENDING', 'FAILED')" +
           "</script>")
    int batchUpdateToProcessing(@Param("eventIds") List<Long> eventIds, 
                               @Param("processingTime") LocalDateTime processingTime);
    
    /**
     * 查找待发布的事件（限制数量）
     * 
     * @param limit 限制数量
     * @return 待发布事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = 'PENDING' " +
           "ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);
    
    /**
     * 查找失败的事件（限制数量）
     * 
     * @param limit 限制数量
     * @return 失败事件列表
     */
    @Select("SELECT * FROM outbox_events WHERE status = 'FAILED' " +
           "ORDER BY updated_at ASC LIMIT #{limit}")
    List<OutboxEvent> findFailedEvents(@Param("limit") int limit);
    
    /**
     * 统计待发布事件数量
     * 
     * @return 待发布事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'")
    long countPendingEvents();
    
    /**
     * 统计失败的事件数量
     * @return 失败事件数量
     */
    @Select("SELECT COUNT(*) FROM outbox_events WHERE status = 'FAILED'")
    Long countFailedEvents();
    
    /**
     * 更新事件状态
     * @param eventId 事件ID
     * @param status 新状态
     * @param updatedAt 更新时间
     */
    @Update("UPDATE outbox_events SET status = #{status}, updated_at = #{updatedAt} WHERE event_id = #{eventId}")
    void updateEventStatus(@Param("eventId") String eventId, 
                          @Param("status") OutboxEventStatus status, 
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新事件状态并记录错误信息和重试次数
     * @param eventId 事件ID
     * @param status 新状态
     * @param updatedAt 更新时间
     * @param errorMessage 错误信息
     * @param retryCount 重试次数
     */
    @Update("UPDATE outbox_events SET status = #{status}, updated_at = #{updatedAt}, error_message = #{errorMessage}, retry_count = #{retryCount} WHERE event_id = #{eventId}")
    void updateEventStatusWithError(@Param("eventId") String eventId, 
                                   @Param("status") OutboxEventStatus status, 
                                   @Param("updatedAt") LocalDateTime updatedAt,
                                   @Param("errorMessage") String errorMessage,
                                   @Param("retryCount") Integer retryCount);
}