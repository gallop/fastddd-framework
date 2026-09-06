package com.gallop.fastddd.consumer.webcruddruid.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.gallop.fastddd.model.po.BasePO;
import lombok.Getter;
import lombok.Setter;

/**
 * 消费者实体：继承 BasePO<Long>（强类型主键 + 审计字段填充 + 逻辑删除契约）。
 */
@Getter
@Setter
@TableName("c4_order")
public class OrderPO extends BasePO<Long> {

    private static final long serialVersionUID = 1L;

    private String name;

    private Integer amount;
}
