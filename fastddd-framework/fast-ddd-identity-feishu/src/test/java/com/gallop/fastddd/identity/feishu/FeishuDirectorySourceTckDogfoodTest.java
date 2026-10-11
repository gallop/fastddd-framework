package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.tck.DirectorySourceContractTck;
import com.gallop.fastddd.identity.core.tck.DirectorySourceHarness;

/**
 * W7-d TCK dogfood（D-7 = (A) test-jar classifier）：feishu 参考实现继承 core 契约基类
 * 自跑全套端口契约断言——P/S/F/R/G/C/X 七组（含 8 畸形形态 × 双流、3 瞬态形态参数化）。
 * 套件「能失败」判据见 W7-d 定点变异记录（执行记录 §变异）。
 */
class FeishuDirectorySourceTckDogfoodTest extends DirectorySourceContractTck {

    @Override
    protected DirectorySourceHarness newHarness() {
        return new TckFeishuHarness();
    }
}
