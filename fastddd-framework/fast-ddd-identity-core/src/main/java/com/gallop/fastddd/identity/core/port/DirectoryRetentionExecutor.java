package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.RetentionPolicy;
import com.gallop.fastddd.identity.core.domain.RetentionReport;

/**
 * 目录事实回收执行器端口（W5 设计 §8.3 M-26；W5-f 设计 §3——core 纯接口，
 * reference-pg 实现，沿既有端口先例）。
 *
 * <p><b>回收四面与判据（W5-f 设计 §3.1 四分面）</b>：</p>
 * <ol>
 *   <li><b>已终结代镜像 raw</b>（三镜像表各一语句）：可回收 = 代 state ∈ 终结态
 *       （PUBLISHED/ABANDONED）∧ 代 id ≠ scope 活动代指针。非 active 判定
 *       <b>必须由 DELETE 语句自身在同一语句快照内联读取
 *       directory_publish_metadata.active_generation（JOIN/相关子查询联读），
 *       明文禁止语句外预读 active 后以绑定参数注入</b>（C-1：参数形态存在
 *       TOCTOU 窗口——预读后、DELETE 快照前并发发布切指针 → 活动代镜像被
 *       整删）。active 为 NULL（从未发布 scope）时终结代镜像可清（无读者面，
 *       O-6 取舍）。非终态代（BUILDING/VALIDATING/VALIDATED）与活动代镜像
 *       恒不回收。</li>
 *   <li><b>Inbox PROCESSED 行</b>：state='PROCESSED' ∧ processed_at 越过
 *       {@link RetentionPolicy#inboxProcessedTtl()}；RECEIVED/PROCESSING（在途）
 *       与 FAILED（管理端重放数据）永不回收。</li>
 *   <li><b>变更日志条目</b>：仅当 {@link RetentionPolicy#changeLogRetention()}
 *       非 null；且 committed_seq 低于水位 W = min(start_seq) OVER（非终态代 ∪
 *       {活动代}）——活动代锚定（P3-1）保证活动代激活条目与其发布前预合并条目
 *       恒保留、迟到同代重复发布（B-5）判据不失守。无非终态代且无活动代时
 *       W 无界（保留期是唯一约束）。三种 kind 同判据。</li>
 *   <li><b>事实层</b>：无操作数（F1）——directory_fact 为当前值表，执行器零触碰。</li>
 * </ol>
 *
 * <p><b>事务形态（O-1：批内原子 + 跨批幂等收敛）</b>：分面独立短事务——面①
 * 三镜像语句同批事务（批内三面原子），面②③各自独立事务互不牵连；各面带批
 * 上限循环至零行、<b>批间提交</b>（非整面单长事务），跨批崩溃由「判据纯谓词、
 * 重跑收敛同值」的幂等重跑兜底。{@link #execute} 入口<b>拒绝外层事务</b>——
 * 批间提交形态不允许汇入调用方长事务。</p>
 *
 * <p><b>幂等与只读面</b>：判据纯谓词（无游标/无副作用标记），对已收敛状态重跑
 * 恒返回零行报告；{@link #preview} 只读（同判据 SELECT count），不开启任何
 * 删除。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryRetentionExecutor {

    /** 只读预估：与 execute 同判据的各面计数（零删除）。 */
    RetentionReport preview(DirectoryScope scope, RetentionPolicy policy);

    /** 分面执行回收，返回各面实际行数。 */
    RetentionReport execute(DirectoryScope scope, RetentionPolicy policy);
}
