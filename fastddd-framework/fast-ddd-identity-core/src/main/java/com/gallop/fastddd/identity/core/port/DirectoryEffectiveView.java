package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.domain.EffectiveSnapshot;

/**
 * 有效视图端口（W3 设计 §6.6 M2 演进——<b>签名与语义冻结，实现 M2</b>）：
 * {@code raw ⊕ 受保护事实}的授权语义当前状态（墓碑/拒绝/撤销生效）。
 *
 * <p><b>为什么是独立接口（复评钉住事项 ①）</b>：M2-only 能力不得成为 M1 必须实现
 * 的抽象方法——它不在 {@link DirectoryMirrorStore} 上，M1 装配不要求其实现，
 * 也不存在返回空集合冒充有效视图的默认实现。{@code readObservation} 语义保持
 * 不回退、两接口并存（M1 调用方无感）。</p>
 *
 * <p>M2 引入时的已冻结约束：可变有效视图的读取协议须重新验证（不复用不可变代
 * 读取结论，W3 设计 §4 末条）；观察快照禁止冒充本层。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryEffectiveView {

    /**
     * 读有效视图（授权语义；墓碑生效的对象不出现）。
     * M2 实现前无任何生产实现方——本方法在 M1 不可达。
     */
    EffectiveSnapshot readEffective(DirectoryScope scope);
}
