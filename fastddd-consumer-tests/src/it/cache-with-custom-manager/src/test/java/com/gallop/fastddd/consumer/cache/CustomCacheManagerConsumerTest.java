package com.gallop.fastddd.consumer.cache;

import com.gallop.fastddd.cache.spring.MultiLevelCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** CACHE-002 独立消费者：业务 CacheManager 是唯一默认选择。 */
@SpringBootTest(classes = CustomCacheManagerConsumerTest.App.class)
class CustomCacheManagerConsumerTest {

    @org.springframework.beans.factory.annotation.Autowired
    CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Autowired
    CachedService cachedService;

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.context.ApplicationContext applicationContext;

    @Test
    void customManagerIsSelectedWithoutFrameworkAmbiguity() {
        CachedService.resetCallCount();
        assertSame(applicationContext.getBean("businessCacheManager"), cacheManager);
        assertFalse(applicationContext.containsBean("multiLevelCacheManager"));
        assertFalse(applicationContext.getBeansOfType(MultiLevelCacheManager.class).size() > 0);

        assertEquals("value-1", cachedService.value("key"));
        assertEquals("value-1", cachedService.value("key"));
        assertEquals(1, cachedService.callCount(), "@Cacheable 应只调用一次业务方法");
    }

    @SpringBootApplication
    static class App {
        @Bean
        CacheManager businessCacheManager() {
            return new ConcurrentMapCacheManager("consumer-values");
        }

        @Bean
        CachedService cachedService() {
            return new CachedService();
        }
    }

    @Service
    static class CachedService {
        private static final AtomicInteger CALLS = new AtomicInteger();

        @Cacheable("consumer-values")
        String value(String key) {
            return "value-" + CALLS.incrementAndGet();
        }

        int callCount() {
            return CALLS.get();
        }

        static void resetCallCount() {
            CALLS.set(0);
        }
    }
}
