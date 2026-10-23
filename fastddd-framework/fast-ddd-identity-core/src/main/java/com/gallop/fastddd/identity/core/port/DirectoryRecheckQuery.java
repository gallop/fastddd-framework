package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;

import java.util.Optional;

/**
 * 目录单对象权威回查端口（W5 设计 §7.1/§7.2，W5-d）：事件触发后取权威侧
 * 单点观察。设计 §3 模块表未列本端口（W5-d 差异登记：全量源
 * {@link DirectorySource} 之外的单查消费面）。
 *
 * <p><b>语义铁律（表 7-1⑥ / W0-6 P-8）</b>：</p>
 * <ul>
 *   <li><b>empty = 权威端确证不存在</b>——仅显式「不存在」信号码可返回空
 *       （部门侧 40015「department not exist」，W0-6 §2.3）；</li>
 *   <li>瞬态/不可判定形态（超时/5xx/429/未知码/信封异常）一律抛
 *       {@link DirectorySourceUnavailableException}——<b>故障 ≠ 删除</b>，
 *       调用方不得据此写墓碑；</li>
 *   <li>权限类抛 {@link DirectorySourceForbiddenException}（配置类 fail-fast，
 *       同 {@link DirectorySource} 错误分类）；</li>
 *   <li>有界重试责任在本适配器（幂等 GET，配额内；同 W3-b 源适配纪律）。</li>
 * </ul>
 *
 * <p><b>飞书侧已知不对称（W0-6 P-8 live 残留，如实登记）</b>：users/:user_id
 * 单查错误码表（41050/40001/41012/40003）<b>无「用户不存在」显式码</b>——
 * 人员侧 empty 在文档级不可达（未知形态 fail-safe 走不可用），人员墓碑的
 * 权威确证等待 live 校准；部门侧 40015 显式、部门墓碑链路文档级完整。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryRecheckQuery {

    /**
     * 单对象权威观察（事务外调用——外部 GET 不得入事务，W3A-01 同源纪律）。
     *
     * @throws DirectorySourceUnavailableException 瞬态/不可判定（不确证任何结论）
     * @throws DirectorySourceForbiddenException   权限/配置类（fail-fast）
     */
    Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType, String externalId);
}
