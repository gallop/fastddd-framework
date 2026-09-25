package com.gallop.fastddd.identity.pg.support;

import com.gallop.fastddd.identity.core.error.BindingConcurrentModificationException;
import com.gallop.fastddd.identity.core.error.TransientTransactionConflictException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Objects;

/**
 * PG 适配器公共支撑：MANDATORY 事务门禁 + 核心异常翻译。
 *
 * <p>写端口只在外层事务内执行（不各自提交）；绑定唯一键竞争与死锁/序列化失败
 * 翻译为 core 的可重试冲突异常，由用例层完整回滚后新事务重试（§8.3a）。</p>
 *
 * <p><b>支持的事务模型（W21-03 补修显式化）</b>：本门禁只验证 <b>JDBC 本地事务</b>
 * ——由 Spring {@code DataSourceTransactionManager}（或等价的、基于同一
 * {@link DataSource} 实例开启本地事务的管理器）在适配器 {@link DataSource} 上
 * 开启的事务。该模型的 {@code doBegin} 为事务绑定 autoCommit=false 的
 * {@code ConnectionHolder} 连接，门禁据此核验真实参与。</p>
 *
 * <p><b>门禁三重判据（W21-03，绑定适配器数据源）</b>：每个适配器以自己的
 * {@link DataSource} 实例构造门禁；写入前依次核验，任一不满足即在首次写入前拒绝：</p>
 * <ul>
 *   <li>线程有实际事务（{@code TransactionSynchronizationManager.isActualTransactionActive()}，
 *       仅有事务同步不算）；</li>
 *   <li>适配器 {@link DataSource} 上有线程绑定的连接资源（{@code ConnectionHolder}，
 *       即 {@code JdbcTemplate} 参与外层事务的同一判据）——事务开在另一个
 *       DataSource 上（即使两个 DataSource 指向同一数据库）时缺绑定，直接拒绝；</li>
 *   <li><b>绑定连接真实参与事务</b>：{@code holder.getConnection().getAutoCommit()}
 *       必须为 false。autoCommit=true 的绑定连接不在任何受支持事务内——典型形态是
 *       事务开在另一数据源上时，先行公开读取本适配器经事务同步自然绑定了非事务
 *       连接（第三轮反例：全局有实际事务 + 目标源有 holder 双条件同时满足仍会
 *       自动提交）；写入将被拒绝而非静默自动提交。无法核验（{@link SQLException}
 *       或非受检异常，如死连接）时同样拒绝（fail-closed，不以"无法判断"放行，
 *       原始异常保留为 cause）。</li>
 * </ul>
 *
 * <p><b>不支持/未验证边界</b>：JTA/XA、{@code TransactionAwareDataSourceProxy}
 * 等代理/包装 DataSource 形态未经本门禁验证、不宣称支持——autoCommit=false 判据
 * 仅在上述 JDBC 本地事务模型内构成"参与事务"的证明，不外推为对所有事务管理器
 * （JTA/XA 等）的通用证明。需要这些形态的宿主须自行验证装配（保证适配器解析的
 * 事务连接真实参与其事务），或使用默认 {@code SpringIdentityTransactionRunner}。</p>
 *
 * <p><b>池预置 autoCommit=false（W21c，复评 F1）</b>：连接池预置
 * {@code autoCommit=false} 时，第三判据失去区分力——绑定连接的 autoCommit=false
 * 既可能来自 {@code doBegin} 的事务开始，也可能来自池预置（{@code doBegin} 对
 * 预置 false 的连接不再调用 {@code setAutoCommit(false)}），公开 API 无法分辨
 * （{@code isSynchronizedWithTransaction()} 在 {@code doBegin} 与普通读取同步
 * 绑定两条路径上都被置 true；{@code doBegin} 的 mustRestoreAutoCommit 标志位于
 * 事务管理器私有对象上）。该配置下受支持模型本身仍正常（事务开在适配器数据源
 * 上即真实参与），但"异源事务 + 目标源预读取"形态会放行写入并随池回滚<b>静默
 * 丢失</b>（含 {@code revokeAllSessions}）。故该配置列为不受支持边界，并由
 * {@code FastDddIdentityPgAutoConfiguration} 在装配期对 Hikari 池直接拒绝启动
 * （其他池/手工装配该形态同样自担风险，不宣称支持）。</p>
 *
 * <p><b>自定义 runner 契约</b>：宿主替换 {@code IdentityTransactionRunner} 时，
 * 必须在 {@code fastddd.identity.transaction.data-source-bean} 指定的同一
 * {@link DataSource} <b>Bean 实例</b>上以受支持模型（JDBC 本地事务）开启并提交
 * 事务（与默认 {@code SpringIdentityTransactionRunner} 一致）；否则适配器首次
 * 写入前即失败。判定基于 DataSource 实例同一性——宿主若包装/代理了该 Bean，
 * 须保证事务管理器与适配器解析到同一实例。注意 {@code DataSourceTransactionManager}
 * 对 {@code TransactionAwareDataSourceProxy} 会自动解包到目标源（holder 绑定在
 * 裸源键上）：宿主使用该代理时"两侧都传代理"无法满足实例同一性（将被判据二
 * 拒绝），事务管理器应持目标（裸）实例；包装形态整体不宣称支持（实测各装配
 * 形态要么被拒绝、要么真实参与，无写入泄漏）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class PgTransactionGate {

    private final DataSource dataSource;

    public PgTransactionGate(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * MANDATORY 门禁：无实际事务、适配器数据源上无线程绑定的连接资源
     * （{@code ConnectionHolder}）、或绑定连接未参与事务（autoCommit=true，
     * 含无法核验的 fail-closed 形态），均在首次写入前拒绝（本适配器不自行
     * 开事务、不提交、不改连接自动提交状态来"修复"装配）。
     */
    public void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）必须在身份事务 runner 的外层事务内执行（MANDATORY）："
                            + "当前线程无实际事务");
        }
        Object resource = TransactionSynchronizationManager.getResource(dataSource);
        if (!(resource instanceof ConnectionHolder holder)) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）要求事务绑定在本适配器数据源上（MANDATORY）："
                            + "当前线程事务未绑定到适配器 DataSource（可能开在另一个数据源上）。自定义"
                            + " IdentityTransactionRunner 必须与 fastddd.identity.transaction.data-source-bean"
                            + " 指定的同一 DataSource 实例开事务");
        }
        // W21-03 补修：绑定连接必须真实参与事务。受支持模型（DataSourceTransactionManager
        // JDBC 本地事务）下事务连接 autoCommit=false；同步绑定的非事务连接（异源事务中先行
        // 公开读取自然产生）保持 autoCommit=true——写入会自动提交、外层回滚无法带走。
        // W21c（F3）：核验调用只此一处，SQLException 与非受检异常（如死连接代理抛出的
        // IllegalStateException）同样以契约消息拒绝并保留原始 cause——两种形态的结局
        // 本就都是拒绝，扩大 catch 不改变放行面，只让失败带上可诊断的契约文本。
        boolean boundConnectionAutoCommit;
        try {
            boundConnectionAutoCommit = holder.getConnection().getAutoCommit();
        } catch (SQLException | RuntimeException e) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）无法核验适配器数据源绑定连接的事务参与状态"
                            + "（MANDATORY，fail-closed）：" + e.getMessage(), e);
        }
        if (boundConnectionAutoCommit) {
            throw new IllegalStateException(
                    "identity PG 写端口（" + operation + "）要求适配器数据源的绑定连接参与外层事务（MANDATORY）："
                            + "当前绑定连接 autoCommit=true，未参与任何受支持事务（典型形态：事务开在另一"
                            + "数据源上时，先行公开读取本适配器经事务同步绑定了非事务连接），写入会自动"
                            + "提交、外层回滚无法带走。事务必须以 JDBC 本地事务开在"
                            + " fastddd.identity.transaction.data-source-bean 指定的同一 DataSource 实例上");
        }
    }

    /** 唯一约束冲突（绑定五元组）→ 绑定并发修改。 */
    public static RuntimeException translateBindingConflict(String operation, RuntimeException e) {
        if (e instanceof DuplicateKeyException) {
            return new BindingConcurrentModificationException(operation + "：绑定唯一键竞争", e);
        }
        return e;
    }

    /** 死锁/序列化失败 → 瞬时冲突（可重试）。 */
    public static RuntimeException translateTransient(String operation, RuntimeException e) {
        if (e instanceof DeadlockLoserDataAccessException || e instanceof CannotSerializeTransactionException) {
            return new TransientTransactionConflictException(operation + "：死锁/序列化冲突", e);
        }
        return e;
    }
}
