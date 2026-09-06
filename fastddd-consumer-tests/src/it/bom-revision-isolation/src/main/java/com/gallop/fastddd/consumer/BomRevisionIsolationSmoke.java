package com.gallop.fastddd.consumer;

import com.gallop.fastddd.common.util.StringUtils;

/** Compile-only proof that the Framework module is resolved from the imported BOM version. */
public final class BomRevisionIsolationSmoke {

    private BomRevisionIsolationSmoke() {
    }

    public static boolean hasText(String value) {
        return StringUtils.isNotBlank(value);
    }
}
