package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 目录同步 Worker 调度器（W3 设计 v3 §7，W3-a 骨架；W3-a 补修 W3A-02）：单守护
 * 线程串行执行 {@link DirectorySyncUseCase#runFullSync}，scope 取自
 * {@link DirectorySource#scope()}（源拥有 scope 定义——应用范围变更即 scope
 * 变更，不在此缓存）。
 *
 * <p><b>调度语义（最小可用决策，设计未规定处）</b>：</p>
 * <ul>
 *   <li>cron 模式：首轮在下一个 cron 匹配时刻（不立即抢跑）；每轮结束后重算
 *       下一时刻，错过的时刻不补跑（cron 语义）。cron 表达式是<b>日历语义</b>，
 *       下一时刻以 {@link Clock} 时区下的 {@link ZonedDateTime} 计算后再转延时
 *       ——生产缺省<b>宿主默认时区</b>（{@link Clock#systemDefaultZone()}），
 *       测试可注入固定/异区时钟（W3A-02：对 {@code Instant} 直接求 next 会因
 *       缺 DayOfWeek 等日历字段抛 {@code UnsupportedTemporalTypeException}，
 *       合法 cron 曾致 Worker 启动失败）；</li>
 *   <li>interval 模式：首轮在一个完整间隔之后（保守起步），此后
 *       scheduleWithFixedDelay（上一轮<b>结束</b>到下一轮开始的间隔）——
 *       慢源不会堆叠轮次；缺省间隔 6h；</li>
 *   <li>启动失败恢复（W3A-02）：start() 中解析/首排失败时恢复 running=false
 *       并回收执行器线程（shutdownNow），不呈现"正在运行"假象、不泄漏线程，
 *       原异常照常抛出（非法 cron 仍 fail-fast）；</li>
 *   <li>单轮失败只 WARN 不中断调度（代已按契约 abandon，下轮重新开始——
 *       W3 设计 §5.2 重试责任在源，调度层不叠加重试）；单次调度链上的意外
 *       同样只告警（runOnce 已兜底所有失败）；</li>
 *   <li>非 Worker 形态不存在本 Bean（装配条件见
 *       {@code IdentityDirectoryAutoConfiguration}），web 形态零调度线程。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectorySyncScheduler implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(DirectorySyncScheduler.class);

    private static final String THREAD_NAME = "fastddd-directory-sync";

    private final DirectorySyncUseCase useCase;
    private final DirectorySource source;
    private final Duration interval;
    private final String cron;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public DirectorySyncScheduler(DirectorySyncUseCase useCase, DirectorySource source,
                                  Duration interval, String cron) {
        this(useCase, source, interval, cron, Clock.systemDefaultZone());
    }

    /**
     * 时钟注入形态（测试/高级用法）：cron 下一时刻按 {@code clock} 的时区计算。
     */
    public DirectorySyncScheduler(DirectorySyncUseCase useCase, DirectorySource source,
                                  Duration interval, String cron, Clock clock) {
        this.useCase = useCase;
        this.source = source;
        this.interval = interval;
        this.cron = cron;
        this.clock = clock;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, THREAD_NAME);
                thread.setDaemon(true);
                return thread;
            });
            if (cron != null && !cron.isBlank()) {
                scheduleNextCronRun(CronExpression.parse(cron));
                LOG.info("[directory] 目录同步调度启动（cron={}，时区 {}，守护线程 {}）",
                        cron, clock.getZone(), THREAD_NAME);
            } else {
                long millis = interval.toMillis();
                executor.scheduleWithFixedDelay(this::runOnce, millis, millis, TimeUnit.MILLISECONDS);
                LOG.info("[directory] 目录同步调度启动（间隔 {}，守护线程 {}）", interval, THREAD_NAME);
            }
        } catch (RuntimeException failure) {
            // 启动失败：恢复 running 状态并回收执行器线程（W3A-02），
            // 不呈现"已运行"假象；原异常照常抛出（非法 cron fail-fast）
            running.set(false);
            ScheduledExecutorService failed = this.executor;
            this.executor = null;
            if (failed != null) {
                failed.shutdownNow();
            }
            throw failure;
        }
    }

    private void scheduleNextCronRun(CronExpression expression) {
        // cron 是日历语义：以带时区的 ZonedDateTime 求下一时刻（W3A-02），
        // 再转延时——不能对 Instant 直接求 next（缺日历字段）
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = expression.next(now);
        if (next == null || !running.get()) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(now, next)
                .truncatedTo(ChronoUnit.MILLIS).toMillis());
        executor.schedule(() -> {
            try {
                runOnce();
                if (running.get()) {
                    scheduleNextCronRun(expression);
                }
            } catch (RuntimeException schedulingFailure) {
                // 调度链兜底：轮次失败已由 runOnce 记日志，这里只兜住重排本身的意外
                LOG.warn("[directory] 目录同步 cron 调度链意外终止（{}）——调度停止",
                        schedulingFailure.getMessage(), schedulingFailure);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    /** 单轮同步：任何失败只记日志（代已整代废弃），调度继续。 */
    private void runOnce() {
        try {
            useCase.runFullSync(source.scope());
        } catch (Throwable failure) {
            LOG.warn("[directory] 目录同步轮次失败（整代已按契约废弃，等待下轮调度）：{}",
                    failure.getMessage(), failure);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
