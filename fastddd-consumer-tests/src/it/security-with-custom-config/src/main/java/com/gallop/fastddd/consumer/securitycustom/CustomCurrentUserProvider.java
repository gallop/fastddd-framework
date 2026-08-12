package com.gallop.fastddd.consumer.securitycustom;

import com.gallop.fastddd.crud.provider.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * 业务自定义 CurrentUserProvider（SEC-003 退让验证：框架不应覆盖此 bean）。
 */
@Component
public class CustomCurrentUserProvider implements CurrentUserProvider {

    static final String CUSTOM_USER_ID = "business-user-001";

    @Override
    public String getCurrentUserId() {
        return CUSTOM_USER_ID;
    }
}
