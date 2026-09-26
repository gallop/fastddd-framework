package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.LoginStateEntry;

import java.util.Optional;

/**
 * 登录授权上下文（state）端口（v0.2 §6/§7.3，W2-2 启用）：授权发起时保存
 * state + PKCE verifier + 浏览器绑定 + 回跳地址；回调时一次性原子消费。
 *
 * <p>事务责任（§8.3a）：save/consume 均为 MANDATORY——经 runner 以独立短事务
 * 执行（先于 T2 签发事务；两事务不合并，回调处理中途失败不因 T2 回滚而复活已消费
 * 的 state）。consume 的原子性由适配器以条件更新保证（如 PG
 * {@code UPDATE … WHERE consumed_at IS NULL AND expires_at > now RETURNING …}）：
 * 并发重复回调恰有一个成功，其余返回 empty——不存在"先读后写"窗口。</p>
 *
 * <p>安全契约：state 一次性（消费后不可再用）、有有效期；适配器不得将
 * code_verifier/browser_binding 记入日志。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface LoginStateStore {

    /** 保存新签发的授权上下文（T-state 短事务）。同 state 重复保存由主键/唯一约束拒绝。 */
    void save(LoginStateEntry entry);

    /**
     * 原子一次性消费：state 未过期且未消费 → 标记消费并返回上下文；否则 empty。
     * 并发下同一 state 至多一个调用方拿到非 empty 结果。
     */
    Optional<LoginStateEntry> consume(String state);
}
