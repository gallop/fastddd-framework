package com.gallop.fastddd.consumer.webcruddruid.service;

import com.gallop.fastddd.common.exception.BusinessException;
import com.gallop.fastddd.consumer.webcruddruid.entity.OrderPO;
import com.gallop.fastddd.consumer.webcruddruid.mapper.OrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 事务行为探针服务：insertThenFail 在同一事务内插入两条后抛业务异常，
 * 用于断言真实回滚（DataSourceTransactionManager 经显式 Druid 池生效）。
 */
@Service
public class OrderService {

    private final OrderMapper orderMapper;

    public OrderService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public void insertThenFail(String nameA, String nameB) {
        OrderPO a = new OrderPO();
        a.setName(nameA);
        a.setAmount(1);
        orderMapper.insert(a);

        OrderPO b = new OrderPO();
        b.setName(nameB);
        b.setAmount(2);
        orderMapper.insert(b);

        throw new BusinessException("C4_ROLLBACK_PROBE", "deliberate failure to prove rollback");
    }
}
