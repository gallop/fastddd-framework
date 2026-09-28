package com.gallop.fastddd.identity.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code fastddd.identity.directory.retry.*} 目录源有界重试配额（W3 设计 §7
 * 框架键：缺省 2 次 / 500ms 固定退避——与 starter
 * {@code FastDddIdentityProperties.Directory.Retry} 绑定**同一组键**、缺省一致；
 * 值合法性由 starter {@code IdentityModeGuard} fail-fast 校验，本模块只消费）。
 *
 * <p>feishu 模块不依赖 starter（消费方向为宿主同时引入两者），故此处独立绑定
 * 同键；两处缺省值必须保持同步（W3-b 执行记录登记该耦合）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@ConfigurationProperties(prefix = "fastddd.identity.directory.retry")
public class FeishuDirectoryRetryProperties {

    /** 幂等 GET 附加重试次数（传输异常/429/5xx/99991400 后；0 = 不重试）。 */
    private int maxAttempts = 2;

    /** 固定退避（复用登录客户端退避口径：不指数放大）。 */
    private Duration backoff = Duration.ofMillis(500);

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoff() {
        return backoff;
    }

    public void setBackoff(Duration backoff) {
        this.backoff = backoff;
    }
}
