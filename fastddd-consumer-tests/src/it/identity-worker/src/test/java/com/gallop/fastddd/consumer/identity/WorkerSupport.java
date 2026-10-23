package com.gallop.fastddd.consumer.identity;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/** Worker 消费者测试夹具：zonky 一次性 PG（§五 桩边界——仅测试源码）。 */
@Configuration(proxyBeanMethods = false)
class WorkerSupport {

    private static final EmbeddedPostgres PG;

    static {
        try {
            PG = EmbeddedPostgres.builder().start();
        } catch (Exception e) {
            throw new IllegalStateException("embedded PostgreSQL 启动失败", e);
        }
    }

    @Bean
    DataSource dataSource() {
        return PG.getDatabase("postgres", "postgres");
    }
}
