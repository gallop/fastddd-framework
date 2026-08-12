package com.gallop.fastddd.cache;

import com.gallop.fastddd.cache.spring.MultiLevelCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CACHE-002：框架 CacheManager 必须对消费者已有的 Spring CacheManager 退让，
 * 不能因返回类型是具体实现而遗漏 {@link CacheManager} 接口 Bean。
 */
class CacheManagerBackOffTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FastDddCacheAutoConfiguration.class));

    @Test
    void createsFrameworkManagerWhenConsumerDoesNotProvideOne() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(MultiLevelCacheManager.class);
        });
    }

    @Test
    void backsOffWhenConsumerProvidesCacheManager() {
        runner.withBean("businessCacheManager", CacheManager.class, ConcurrentMapCacheManager::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasBean("businessCacheManager");
                    assertThat(context).doesNotHaveBean(MultiLevelCacheManager.class);
                });
    }
}
