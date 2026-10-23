package com.gallop.fastddd.identity.feishu;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryObjectType;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.ObservedDirectoryObject;
import com.gallop.fastddd.identity.core.port.DirectoryRecheckQuery;

import java.time.Duration;
import java.util.Optional;

/**
 * 飞书回查单查适配器（W5 设计 §7.3 第②步权威单查，W5-d）：事件触发回查的
 * 事实来源——users/get（人员单查）+ departments/get（部门单查），经
 * {@link FeishuDirectoryClient} 复用信封判读/错误分类/有界重试。
 *
 * <p><b>停用派生（W0-6 §2 冻结口径）</b>：人员 = is_frozen ∨ is_resigned ∨
 * is_exited ∨ is_unjoin（is_activated=false 单独不构成停用）；部门 =
 * {@code status.is_deleted}。status 布尔在客户端原样透传，派生集中本层
 * （单一处所，不散落）。</p>
 *
 * <p><b>ABSENT 可达性不对称（W0-6 结论，W5-d 存活残余登记）</b>：
 * departments/get 40015 显式"部门不存在" → {@code Optional.empty()}（权威
 * 确证 ABSENT）；users/get <b>无对应显式码</b>——已删用户以权限族/未核对码
 * 形态出现时客户端 fail-safe 上抛（Unavailable/Forbidden → worker
 * markFailed），人员 ABSENT 经单查不可达（删除事件的落点依赖停用结论或
 * 人工介入，非 ABSENT 幻觉）。</p>
 *
 * <p><b>根部门归化</b>：{@code parent_department_id="0"}（飞书根哨兵）→
 * {@code null}（无父，不虚构）——与 {@link FeishuDirectorySource} 合成根
 * 部门同口径。</p>
 *
 * <p><b>独立客户端实例</b>：与目录源各持 {@link FeishuDirectoryClient}
 * （token 缓存独立——官方有效期内重复获取幂等，无正确性影响；W5-d 差异
 * 登记项）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class FeishuRecheckQuery implements DirectoryRecheckQuery {

    private final FeishuDirectoryClient client;

    /**
     * @param loginProperties 凭据与范围键（与目录源同键）
     * @param properties      目录端点/超时配置
     * @param maxRetries      幂等单查附加重试次数（{@code directory.retry.max-attempts}）
     * @param retryBackoff    固定退避（{@code directory.retry.backoff}）
     */
    public FeishuRecheckQuery(FeishuLoginProperties loginProperties,
                              FeishuDirectoryProperties properties,
                              int maxRetries, Duration retryBackoff) {
        this.client = new FeishuDirectoryClient(loginProperties, properties, maxRetries, retryBackoff);
    }

    @Override
    public Optional<ObservedDirectoryObject> fetchObserved(DirectoryObjectType objectType,
                                                           String externalId) {
        return switch (objectType) {
            case PERSON -> fetchPerson(externalId);
            case DEPARTMENT -> fetchDepartment(externalId);
        };
    }

    private Optional<ObservedDirectoryObject> fetchPerson(String openId) {
        FeishuDirectoryClient.PersonDetail detail = client.singlePerson(openId);
        boolean deactivated = detail.frozen() || detail.resigned()
                || detail.exited() || detail.unjoin();
        DirectoryPerson person = new DirectoryPerson(detail.openId(), detail.name(),
                detail.enName(), detail.departmentIds());
        return Optional.of(ObservedDirectoryObject.of(person, deactivated));
    }

    private Optional<ObservedDirectoryObject> fetchDepartment(String openDepartmentId) {
        return client.singleDepartment(openDepartmentId)
                .map(FeishuRecheckQuery::toObservedDepartment);
    }

    private static ObservedDirectoryObject toObservedDepartment(
            FeishuDirectoryClient.DepartmentDetail detail) {
        String parent = FeishuDirectorySource.ROOT_DEPARTMENT_ID.equals(detail.parentDepartmentId())
                ? null : detail.parentDepartmentId();
        DirectoryDepartment department = new DirectoryDepartment(
                detail.openDepartmentId(), parent, detail.name());
        return ObservedDirectoryObject.of(department, detail.deleted());
    }
}
