package com.gallop.fastddd.identity.core.domain;

/**
 * 目录对象类型（W5 设计 §3.2，W5-b 词表冻结）：人员 / 部门两域。
 *
 * <p>供回查证据对象定位（{@code RecheckEvidence}）与变化事件对象域判别
 * （{@code DirectoryChangeType#objectType()}）使用。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public enum DirectoryObjectType {

    /** 人员（对象键 = open_id 域）。 */
    PERSON,

    /** 部门。 */
    DEPARTMENT
}
