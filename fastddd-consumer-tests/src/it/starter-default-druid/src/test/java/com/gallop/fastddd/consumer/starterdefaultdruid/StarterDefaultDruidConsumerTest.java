package com.gallop.fastddd.consumer.starterdefaultdruid;

import com.alibaba.druid.pool.DruidDataSource;
import com.gallop.fastddd.consumer.starterdefaultdruid.entity.StockPO;
import com.gallop.fastddd.consumer.starterdefaultdruid.mapper.StockMapper;
import com.gallop.fastddd.web.exception.CqrsExceptionAdvice;
import com.gallop.fastddd.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ECO-006C4 验证点③：聚合 starter + 数据库——默认池仍为 Druid，既有功能不退化。
 * 消费者未显式选池：Druid 必须来自 fast-ddd-starter 聚合自身（C4 后为显式依赖）。
 * 不要求聚合 starter 无库启动成功（有库场景即本场景形态）。
 */
@SpringBootTest(classes = StarterDefaultDruidApp.class)
class StarterDefaultDruidConsumerTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void defaultPoolIsDruidWithoutExplicitSelection() {
        // 若聚合丢失 Druid 且 classpath 无 Hikari，这里将直接启动失败；
        // 若意外引入 Hikari 且顺序反转，这里会暴露错误池型——两种回归都逃不过此断言
        assertThat(dataSource).isInstanceOf(DruidDataSource.class);
    }

    @Test
    void mapperCrudSmokeStillWorks() {
        StockPO po = new StockPO();
        po.setSku("SKU-C4");
        po.setQuantity(100);
        stockMapper.insert(po);

        assertThat(po.getId()).isNotNull();

        StockPO loaded = stockMapper.selectById(po.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSku()).isEqualTo("SKU-C4");
        assertThat(loaded.getCreateTime()).isNotNull();

        loaded.setQuantity(80);
        stockMapper.updateById(loaded);

        StockPO reloaded = stockMapper.selectById(po.getId());
        assertThat(reloaded.getQuantity()).isEqualTo(80);
    }

    @Test
    void existingWebSurfaceNotRegressed() {
        // 既有功能不退化：Web 异常处理表面仍在（对齐 web-minimal 的验收断言）
        assertThat(applicationContext.getBean(GlobalExceptionHandler.class)).isNotNull();
        assertThat(applicationContext.getBean(CqrsExceptionAdvice.class)).isNotNull();
    }
}
