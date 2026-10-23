package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;

/**
 * 目录源端口（W3 设计 §2/W0-2 §1；feishu 实现 = W3-b）：只读分页读取
 * 部门/人员（任职关系由 {@link DirectoryPerson#departmentIds()} 派生）+
 * 同步范围描述。
 *
 * <p><b>契约要点</b>：</p>
 * <ul>
 *   <li><b>全部远程调用在事务外</b>（编排用例保证；临界区内禁止远程调用，W3 设计 §5.2）；</li>
 *   <li>错误分类（W0-4 §3）：权限不足（230002/41050/40004）→
 *       {@code DirectorySourceForbiddenException}（fail-fast，禁止当离职、禁止退避风暴）；
 *       超时/连接失败/5xx/限流（429/99991400）→
 *       {@code DirectorySourceUnavailableException}；<b>源适配器自有有界重试，
 *       耗尽后才抛出</b>——编排用例不重试分页（重试责任不叠加）；</li>
 *   <li>分页终止语义：{@link DirectoryPage#hasMore()} {@code == false} = 终页；
 *       空页/空目录是合法结果（"不存在"≠错误），缺失对象按事实层规则 B 处理
 *       （W3 设计 §6.3），不得在源侧推断删除；</li>
 *   <li>幂等性：同 {@link DirectoryPageQuery} 重复调用应返回等价结果（幂等 GET）；
 *       游标对端口不透明（原样透传，不解释）。</li>
 * </ul>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectorySource {

    /** 本源实例服务的同步范围（M1：每进程单范围）。 */
    DirectoryScope scope();

    /** 部门分页（部门流，先于人员流完整遍历）。 */
    DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query);

    /**
     * 指定部门下的人员分页（人员流，按部门逐个遍历）。
     * 部门不存在（被上游并发删除）按"空页 + hasMore=false"返回，不抛异常。
     */
    DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query);
}
