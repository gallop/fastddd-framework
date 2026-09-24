package com.gallop.fastddd.identity.spring.tx;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import javax.sql.DataSource;
import java.util.Arrays;

/**
 * 业务 DataSource 按名解析（W0-2 §2：不靠 @Primary/单候选猜资源）。
 *
 * <p>bean 名不存在即启动失败并列出全部 DataSource 候选；多 DataSource 时必须显式
 * 配置 {@code fastddd.identity.transaction.data-source-bean} 指名。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class IdentityDataSourceResolver {

    private IdentityDataSourceResolver() {
    }

    public static DataSource requireByName(ConfigurableListableBeanFactory beanFactory, String beanName) {
        String[] candidates = beanFactory.getBeanNamesForType(DataSource.class, true, false);
        if (candidates == null || candidates.length == 0) {
            throw new IllegalStateException(String.format(
                    "FastDDD identity 需要业务 DataSource：上下文中没有任何 DataSource bean。"
                            + "请提供名为 '%s' 的 bean 或用 fastddd.identity.transaction.data-source-bean 指名。",
                    beanName));
        }
        for (String candidate : candidates) {
            if (candidate.equals(beanName)) {
                return beanFactory.getBean(beanName, DataSource.class);
            }
        }
        throw new IllegalStateException(String.format(
                "FastDDD identity 业务 DataSource '%s' 不存在（fastddd.identity.transaction.data-source-bean）。"
                        + "当前 DataSource 候选：%s。按名显式绑定，不猜 @Primary/单候选。",
                beanName, Arrays.toString(candidates)));
    }
}
