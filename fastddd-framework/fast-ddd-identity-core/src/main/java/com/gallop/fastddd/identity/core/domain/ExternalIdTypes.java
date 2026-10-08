package com.gallop.fastddd.identity.core.domain;

/**
 * 外部身份 id 类型词表（W5 设计 §8.1(c)，W5-b 冻结）：常量锚定与防漂移，
 * 不引枚举——{@code ExternalIdentity.externalIdType} 维持自由字符串
 * （宿主/适配器可携带其他 id 类型，本类不收敛取值域）。
 *
 * <p><b>剔除规则仅 OPEN_ID 域生效（冻结）</b>：目录 PERSON 对象键 = open_id 域；
 * 登录五元组现用 union_id（FeishuExternalLoginProvider 包内常量）。
 * readEffective 的 rejection 剔除（§8.1(c) 键映射）只对 external_id_type =
 * {@link #OPEN_ID} 的活跃拒绝行生效；其他 id 类型 = 不剔除 + 结构化日志登记。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public final class ExternalIdTypes {

    /** 目录 PERSON 对象键域（剔除规则生效域）。 */
    public static final String OPEN_ID = "open_id";

    /** 登录五元组现用 id 域（不参与目录剔除键映射）。 */
    public static final String UNION_ID = "union_id";

    private ExternalIdTypes() {
    }
}
