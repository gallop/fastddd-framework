package com.gallop.fastddd.identity.spring;

import com.gallop.fastddd.identity.core.port.DirectoryMirrorStore;
import com.gallop.fastddd.identity.core.usecase.DirectorySyncUseCase;
import com.gallop.fastddd.identity.spring.directory.DirectorySyncScheduler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W3A-02 调度器单元测试（W3-a 实现独立复评意见）：启动失败必须恢复 running
 * 状态并回收执行器线程；cron 下一时刻按注入时钟的<b>时区</b>计算（可控时钟
 * 验证时区语义）；真实调度回调至少触发一次（非仅断言 cron 解析成功）。
 * 装配级 Worker+合法 cron 生命周期反例见 {@code IdentityDirectoryAssemblyTest}。
 */
class DirectorySyncSchedulerTest {

    private static DirectorySyncScheduler schedulerWithCron(String cron) {
        DirectoryAssemblyDoubles.PagedSource source = new DirectoryAssemblyDoubles.PagedSource();
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source,
                new DirectoryAssemblyDoubles.RecordingMirrorStore(),
                DirectoryAssemblyDoubles.txRunner(), 100, 100);
        return new DirectorySyncScheduler(useCase, source, Duration.ofHours(6), cron);
    }

    private static DirectorySyncScheduler scheduler(Clock clock, DirectoryMirrorStore store,
                                                   String cron) {
        DirectoryAssemblyDoubles.PagedSource source = new DirectoryAssemblyDoubles.PagedSource();
        DirectorySyncUseCase useCase = new DirectorySyncUseCase(source, store,
                DirectoryAssemblyDoubles.txRunner(), 100, 100);
        return new DirectorySyncScheduler(useCase, source, Duration.ofHours(6), cron, clock);
    }

    @Test
    void startupFailureRestoresRunningStateAndReclaimsExecutor() throws Exception {
        DirectorySyncScheduler scheduler = schedulerWithCron("not-a-cron");

        // 非法 cron 仍 fail-fast（解析失败即抛）
        assertThatThrownBy(scheduler::start).isInstanceOf(IllegalArgumentException.class);

        // 旧实现缺陷：running 残留 true、执行器线程泄漏——两者都必须恢复
        assertThat(scheduler.isRunning()).isFalse();
        assertThat(noDirectorySyncThread()).isTrue();
    }

    /**
     * W3A-02 验收：同一时刻、两个时区的时钟（UTC 18:59:59.500，cron = 每日
     * 19:00 整点）——UTC 时区的调度器约 500ms 后真实触发一次同步回调；
     * +08 时区（Asia/Shanghai）的下一时刻是当地 19:00 = 11:00Z，约 16 小时
     * 后，测试窗口内不触发。证明下一时刻按时钟时区的日历语义计算且回调真实
     * 执行（存储观测到 begin，非仅 cron 解析成功）。
     */
    @Test
    void cronNextOccurrenceFollowsClockZoneAndFiresRealCallback() throws Exception {
        Instant base = Instant.parse("2026-09-28T18:59:59.500Z");
        DirectoryAssemblyDoubles.RecordingMirrorStore utcStore =
                new DirectoryAssemblyDoubles.RecordingMirrorStore();
        DirectorySyncScheduler utc = scheduler(Clock.fixed(base, ZoneOffset.UTC), utcStore,
                "0 0 19 * * *");
        DirectoryAssemblyDoubles.RecordingMirrorStore beijingStore =
                new DirectoryAssemblyDoubles.RecordingMirrorStore();
        DirectorySyncScheduler beijing = scheduler(Clock.fixed(base, ZoneId.of("Asia/Shanghai")),
                beijingStore, "0 0 19 * * *");

        utc.start();
        beijing.start();
        try {
            assertThat(waitFor(() -> !utcStore.calls.isEmpty(), 5_000))
                    .as("UTC 时区：19:00 整点应真实触发调度回调").isTrue();
            // 真实回调执行了完整一轮同步（begin → … → publish）
            assertThat(utcStore.calls).contains("begin", "completePaging", "publish");
            // +08 时区同一时刻：下一匹配在约 16 小时后——窗口内零触发
            assertThat(beijingStore.calls).isEmpty();
        } finally {
            utc.stop();
            beijing.stop();
        }
    }

    /** 轮询等待条件成立（真实调度回调走真实执行器线程，需等待真实时间）。 */
    private static boolean waitFor(java.util.function.BooleanSupplier condition,
                                   long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

    private static boolean noDirectorySyncThread() throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.getName().equals("fastddd-directory-sync"));
            if (!alive) {
                return true;
            }
            Thread.sleep(20);
        }
        return Thread.getAllStackTraces().keySet().stream()
                .noneMatch(thread -> thread.getName().equals("fastddd-directory-sync"));
    }
}
