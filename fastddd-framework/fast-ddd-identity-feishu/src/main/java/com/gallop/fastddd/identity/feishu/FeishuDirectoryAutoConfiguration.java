package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.port.DirectorySource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * 飞书目录源自动配置（W3-b）：{@code fastddd.identity.enabled=true} 且
 * {@code fastddd.identity.directory.enabled=true} 时装配真实
 * {@link DirectorySource}（{@link FeishuDirectorySource}）。
 *
 * <p><b>条件独立于 login.mode</b>：目录单开（{@code login.mode=none}）不要求
 * 登录链配置（redirect-uri 等）——登录适配器仍由
 * {@code FeishuIdentityAutoConfiguration} 按 {@code feishu-web} 单独门控，
 * 两者可独立成立（目录单开 / 仅登录 / 同时开启）。凭据键复用
 * {@code fastddd.identity.feishu.*}（目录消费 app-id/app-secret/
 * expected-tenant-key 三键；缺失在 Bean 构造期启动失败并逐项列出，
 * fail-closed 不留"能启动但目录必败"形态）。</p>
 *
 * <p><b>顺序与衔接（W3-a）</b>：先于 starter {@code IdentityAutoConfiguration}
 * 注册（后者及其后处理的 {@code IdentityDirectoryAutoConfiguration} 以
 * {@code @ConditionalOnBean(DirectorySource.class)} 引用本 Bean——用户配置与
 * 本自动配置先注册，判定顺序安全；与 reference-pg/登录自动配置同模式）。
 * 宿主使用其他目录源时提供自己的 {@code DirectorySource} Bean，本类以
 * {@code @ConditionalOnMissingBean} 退让；重试配额绑定框架键
 * {@code fastddd.identity.directory.retry.*}（值合法性由 starter
 * {@code IdentityModeGuard} fail-fast 校验；缺省 2 次/500ms 与 starter 一致）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
@AutoConfiguration(beforeName = "com.gallop.fastddd.identity.spring.IdentityAutoConfiguration")
@Conditional(FeishuDirectoryAutoConfiguration.FeishuDirectoryConditions.class)
@EnableConfigurationProperties({FeishuLoginProperties.class, FeishuDirectoryProperties.class,
        FeishuDirectoryRetryProperties.class})
public class FeishuDirectoryAutoConfiguration {

    /** 真实飞书目录源（部门 BFS + 人员分页 + tenant token 缓存 + 有界重试）。 */
    @Bean
    @ConditionalOnMissingBean(DirectorySource.class)
    FeishuDirectorySource feishuDirectorySource(FeishuLoginProperties loginProperties,
                                                FeishuDirectoryProperties directoryProperties,
                                                FeishuDirectoryRetryProperties retryProperties) {
        return new FeishuDirectorySource(loginProperties, directoryProperties,
                retryProperties.getMaxAttempts(), retryProperties.getBackoff());
    }

    /** identity.enabled=true AND directory.enabled=true（与 login.mode 无关）。 */
    static class FeishuDirectoryConditions extends AllNestedConditions {

        FeishuDirectoryConditions() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "fastddd.identity", name = "enabled", havingValue = "true")
        static class IdentityEnabled {
        }

        @ConditionalOnProperty(prefix = "fastddd.identity.directory", name = "enabled",
                havingValue = "true")
        static class DirectoryEnabled {
        }
    }
}
