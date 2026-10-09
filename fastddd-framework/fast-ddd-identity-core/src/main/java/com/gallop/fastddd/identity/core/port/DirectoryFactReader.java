package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.LocalFactView;

import java.util.Optional;

/**
 * 单键本地事实读取端口（W5 设计 §7.3 三步之首，W5-d）：worker 事务外取
 * CAS 基线（expectedRevision）与内容比较输入。
 *
 * <p>设计 §3 模块表未列本端口（W5-d 差异登记）：{@code DirectoryMirrorStore.
 * readProtectedFacts}（W3 冻结签名）为<b>范围全量、无内容</b>的管理面——
 * 单键带内容读取是 worker 专属消费面，冻结签名不动、新端口承载。</p>
 *
 * <p>只读无副作用；调用方不在事务内调用亦无碍（无 MANDATORY 门禁需求）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryFactReader {

    /** 单键事实视图（含内容文本）；无行 = empty（expectedRevision 基线取 0）。 */
    Optional<LocalFactView> readFact(DirectoryScope scope, DirectoryObjectType objectType,
                                     String externalId);
}
