package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 目录同步 Worker 调度器（W3 设计 v3 §7，W3-a 骨架）：单守护线程串行执行
 * {@link DirectorySyncUseCase#runFullSync}，scope 取自 {@link DirectorySource#scope()}
 * （源拥有 scope 定义——应用范围变更即 scope 变更，不在此缓存）。
 *
 * <p><b>调度语义（最小可用决策，设计未规定处）</b>：</p>
 * <ul>
 *   <li>cron 模式：首轮在下一个 cron 匹配时刻（不立即抢跑）；每轮结束后重算
 *       下一时刻，错过的时刻不补跑（cron 语义）；</li>
 *   <li>interval 模式：首轮在一个完整间隔之后（保守起步），此后
 *       scheduleWithFixedDelay（上一轮<b>结束</b>到下一轮开始的间隔）——
 *       慢源不会堆叠轮次；缺省间隔 6h；</li>
 *   <li>单轮失败只 WARN 不中断调度（代已按契约 abandon，下轮重新开始——
 *       W3 设计 §5.2 重试责任在源，调度层不叠加重试）；</li>
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

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public DirectorySyncScheduler(DirectorySyncUseCase useCase, DirectorySource source,
                                  Duration interval, String cron) {
        this.useCase = useCase;
        this.source = source;
        this.interval = interval;
        this.cron = cron;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        });
        if (cron != null && !cron.isBlank()) {
            scheduleNextCronRun(CronExpression.parse(cron));
            LOG.info("[directory] 目录同步调度启动（cron={}，守护线程 {}）", cron, THREAD_NAME);
        } else {
            long millis = interval.toMillis();
            executor.scheduleWithFixedDelay(this::runOnce, millis, millis, TimeUnit.MILLISECONDS);
            LOG.info("[directory] 目录同步调度启动（间隔 {}，守护线程 {}）", interval, THREAD_NAME);
        }
    }

    private void scheduleNextCronRun(CronExpression expression) {
        Instant next = expression.next(Instant.now());
        if (next == null || !running.get()) {
            return;
        }
        long delayMillis = Math.max(0, Duration.between(Instant.now(), next)
                .truncatedTo(ChronoUnit.MILLIS).toMillis());
        executor.schedule(() -> {
            runOnce();
            if (running.get()) {
                scheduleNextCronRun(expression);
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
