package com.gallop.fastddd.consumer.webcruddruid;

import com.alibaba.druid.pool.DruidDataSource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gallop.fastddd.common.exception.BusinessException;
import com.gallop.fastddd.consumer.webcruddruid.entity.OrderPO;
import com.gallop.fastddd.consumer.webcruddruid.mapper.OrderMapper;
import com.gallop.fastddd.consumer.webcruddruid.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ECO-006C4 验证点 2：fast-ddd-web（传递 crud/model）+ 显式 Druid 的行为验证——
 * 池类型、真实 Mapper CRUD + 审计自动填充、真实分页（消费者装配的分页拦截器）、真实事务回滚。
 */
@SpringBootTest(classes = WebCrudDruidApp.class)
class WebCrudDruidConsumerTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Test
    void poolTypeIsDruidWhenExplicitlySelected() {
        assertThat(dataSource).isInstanceOf(DruidDataSource.class);
    }

    @Test
    void mapperCrudRoundtripWithAuditFill() {
        OrderPO po = new OrderPO();
        po.setName("roundtrip");
        po.setAmount(7);
        orderMapper.insert(po);

        assertThat(po.getId()).isNotNull();

        OrderPO loaded = orderMapper.selectById(po.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("roundtrip");
        // crud 自动装配的 AutoFillMetaObjectHandler 负责审计填充
        assertThat(loaded.getCreateTime()).isNotNull();

        loaded.setAmount(9);
        orderMapper.updateById(loaded);

        OrderPO reloaded = orderMapper.selectById(po.getId());
        assertThat(reloaded.getAmount()).isEqualTo(9);
        assertThat(reloaded.getUpdateTime()).isNotNull();
    }

    @Test
    void paginationWorksViaConsumerInterceptor() {
        for (int i = 0; i < 5; i++) {
            OrderPO po = new OrderPO();
            po.setName("page-" + i);
            po.setAmount(i);
            orderMapper.insert(po);
        }

        LambdaQueryWrapper<OrderPO> scoped = new LambdaQueryWrapper<OrderPO>()
                .likeRight(OrderPO::getName, "page-");
        Page<OrderPO> page = orderMapper.selectPage(Page.of(1, 2), scoped);

        // 无分页拦截器时 getRecords 会返回全部 5 条而非 LIMIT 2——此处同时证明 SQL 级分页生效
        assertThat(page.getRecords()).hasSize(2);
        assertThat(page.getTotal()).isEqualTo(5);
    }

    @Test
    void transactionalInsertRollsBack() {
        Long before = orderMapper.selectCount(
                new LambdaQueryWrapper<OrderPO>().likeRight(OrderPO::getName, "tx-"));

        assertThatThrownBy(() -> orderService.insertThenFail("tx-a", "tx-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deliberate failure");

        Long after = orderMapper.selectCount(
                new LambdaQueryWrapper<OrderPO>().likeRight(OrderPO::getName, "tx-"));
        assertThat(after).as("两条插入必须随事务一起回滚").isEqualTo(before);
    }
}
