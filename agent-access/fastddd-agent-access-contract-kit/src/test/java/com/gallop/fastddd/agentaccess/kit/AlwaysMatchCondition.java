package com.gallop.fastddd.agentaccess.kit;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 恒真条件：让被测 Bean 实际注册（存在），从而验证「条件化声明」检查独立于
 * 「Bean 缺失」检查生效。
 */
class AlwaysMatchCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return true;
    }
}
