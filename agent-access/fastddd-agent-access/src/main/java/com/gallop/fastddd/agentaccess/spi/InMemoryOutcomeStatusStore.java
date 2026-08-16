package com.gallop.fastddd.agentaccess.spi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link OutcomeStatusPort} 进程内参考实现（测试与 conformance 基底；
 * 与 {@code InMemoryDedupStore} 同定位）。
 *
 * <p><b>如实标注</b>：进程内 Map 不是权威 attempt/receipt/tombstone 表，
 * kill/restart 后不可恢复；真实实现由宿主对自有表提供。写入为覆盖式
 * {@code put}——测试组装状态演进序列（Absent→InFlight→Recorded 等）用，
 * 不表达生产记录的追加写语义。</p>
 */
public final class InMemoryOutcomeStatusStore implements OutcomeStatusPort {

    private final Map<DedupKey, OutcomeRecordView> records = new ConcurrentHashMap<>();

    /** 覆盖式写入（测试组装用；生产记录是追加写，见类注）。 */
    public void put(DedupKey key, OutcomeRecordView view) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        if (view == null) {
            throw new IllegalArgumentException("view 必选");
        }
        records.put(key, view);
    }

    @Override
    public OutcomeRecordView find(DedupKey key, String allowedProjection) {
        if (key == null) {
            throw new IllegalArgumentException("key 必选");
        }
        if (allowedProjection == null || allowedProjection.isBlank()) {
            throw new IllegalArgumentException("allowedProjection 必选且不得为空白（§5.7 许可绑定面透传）");
        }
        // 如实标注：参考实现不执行字段级投影（形态未冻结），原样返回已存视图；
        // 真实宿主实现必须按 allowedProjection 施加数据最小化（见 OutcomeStatusPort）
        OutcomeRecordView view = records.get(key);
        return view != null ? view : new OutcomeRecordView.Absent();
    }

    /** 当前记录数（测试/观测用）。 */
    public int size() {
        return records.size();
    }
}
