package com.gallop.fastddd.identity.spring.directory;

import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase;
import com.gallop.fastddd.identity.core.usecase.DirectoryEventWorkerUseCase.InboxRunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 目录增量事件 worker 调度器（W5 设计 §7/§10，W5-d）：单守护线程串行执行
 * {@link DirectoryEventWorkerUseCase#runOnce()}——镜像 {@link DirectorySyncScheduler}
 * 形态（interval-only，无 cron 面：inbox 消费是短轮询任务非日历任务）。
 *
 * <p><b>调度语义（最小可用决策，设计未规定处——同步调度器同款）</b>：</p>
 * <ul>
 *   <li>scheduleWithFixedDelay（上一轮<b>结束</b>到下一轮开始的间隔）——慢批
 *       不堆叠轮次；首轮在一个完整间隔之后（保守起步）；</li>
 *   <li>单轮失败只 WARN 不中断调度（单事件故障已由用例 markFailed 兜底、
 *       表 7-1⑥ 零墓碑；调度层不叠加重试）；</li>
 *   <li>空轮静默（M-29 零写零推零通知——零噪声），仅非零活动轮次记一条
 *       INFO 结构化日志（计数面 = {@link InboxRunResult}）；</li>
 *   <li>FAILED 终态不走本调度重试（§4.1：管理端显式重放入口）；租约回收在
 *       每轮 runOnce 内部先行。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class DirectoryEventWorkerScheduler implements SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryEventWorkerScheduler.class);

    private static final String THREAD_NAME = "fastddd-directory-event-worker";

    private final DirectoryEventWorkerUseCase useCase;
    private final Duration pollInterval;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService executor;

    public DirectoryEventWorkerScheduler(DirectoryEventWorkerUseCase useCase, Duration pollInterval) {
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException("pollInterval 必须为正时长（收到：" + pollInterval + "）");
        }
        this.useCase = useCase;
        this.pollInterval = pollInterval;
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
        long millis = pollInterval.toMillis();
        executor.scheduleWithFixedDelay(this::runOnce, millis, millis, TimeUnit.MILLISECONDS);
        LOG.info("[directory-events] 增量事件 worker 调度启动（间隔 {}，守护线程 {}）",
                pollInterval, THREAD_NAME);
    }

    /** 单轮：回收过期租约 + claim 批 + 逐事件处置。任何失败只记日志，调度继续。 */
    private void runOnce() {
        try {
            InboxRunResult result = useCase.runOnce();
            if (result.reclaimed() > 0 || result.claimed() > 0 || result.failed() > 0
                    || result.deferred() > 0 || result.processed() > 0 || result.processedStale() > 0) {
                LOG.info("[directory-events] worker 轮次：reclaimed={}, claimed={}, processed={}, "
                                + "processedStale={}, failed={}, deferred={}",
                        result.reclaimed(), result.claimed(), result.processed(),
                        result.processedStale(), result.failed(), result.deferred());
            }
        } catch (Throwable failure) {
            LOG.warn("[directory-events] 增量事件 worker 轮次失败（单事件故障已由用例兜底，"
                    + "等待下轮调度）：{}", failure.getMessage(), failure);
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
