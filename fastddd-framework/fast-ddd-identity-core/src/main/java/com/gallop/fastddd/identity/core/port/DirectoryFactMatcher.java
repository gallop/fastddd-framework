package com.gallop.fastddd.identity.core.port;

import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;

/**
 * 事实内容结构等值端口（W5 设计 §7.3 结论派生，W5-d）：存储事实文本与
 * 权威观察对象的内容比对——IDENTICAL（零 H、仅复位 pending）与
 * CONTENT_DIFFERS（rev+1 内容替换）的分界裁决。
 *
 * <p><b>为什么是端口而不是字符串比较</b>：事实列以 jsonb 承载，读取文本
 * 已经 PG 规范化（键序/空白重排）——与写入侧序列化字面不等；等值必须
 * <b>结构级</b>。PG 参考实现以 {@code ?::jsonb = ?::jsonb}（jsonb 语义等值，
 * 键序无关）承载，真源单一且零解析依赖；内存替身以串等值承载。</p>
 *
 * <p>比对面 = 事实列锚点字段（人员 name/en_name/department_ids；部门
 * name/parent_department_id——与规则 A 写入面同源，W3 §6.3）；状态布尔组
 * <b>不进</b>比对面（停用走 DEACTIVATED 结论，非内容差异）。</p>
 *
 * @author gallop
 * @since 1.1.0
 */
public interface DirectoryFactMatcher {

    /** 存储事实文本与观察人员内容结构等值（true → IDENTICAL）。 */
    boolean matches(String storedFactJson, DirectoryPerson observed);

    /** 存储事实文本与观察部门内容结构等值（true → IDENTICAL）。 */
    boolean matches(String storedFactJson, DirectoryDepartment observed);
}
