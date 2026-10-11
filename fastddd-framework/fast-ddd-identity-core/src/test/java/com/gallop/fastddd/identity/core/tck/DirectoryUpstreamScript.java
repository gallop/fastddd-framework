package com.gallop.fastddd.identity.core.tck;

import java.util.List;

/**
 * TCK 上游脚本（W7-d）：预排上游行为 + 消费 + 观测三面一体的测试替身协议。
 *
 * <p>三个使用面：
 * <ul>
 *   <li><b>编程面</b>（契约基类调用）：测试前预排响应序列；</li>
 *   <li><b>消费面</b>（宿主 harness 调用）：每次上游外呼发生时 poll 下一个响应，harness 负责
 *       把响应渲染为真实线协议形态；</li>
 *   <li><b>观测面</b>（断言用）：上游外呼计数、观测到的 page_size 与游标序列。</li>
 * </ul>
 *
 * <p>消费语义（契约基类依赖的不变量）：
 * <ul>
 *   <li>departments 队列 FIFO 逐位消费；<b>队列耗尽 → 回放合法空终页</b>（不回放最后响应——
 *       OK + hasMore=true 的粘性回放会造成死循环）；</li>
 *   <li>{@link #failDepartmentsWith} 为粘性失败：此后每次 departments 外呼恒返回该失败
 *       （重试击数观测用）；粘性优先于队列；</li>
 *   <li>persons 队列按 departmentId 键控、各自 FIFO、耗尽同样回放合法空终页。</li>
 * </ul>
 */
public interface DirectoryUpstreamScript {

    // ---- 编程面（契约基类 / 测试侧） ----

    /** 预排 departments 流响应序列（FIFO 消费）。 */
    void playDepartments(UpstreamResponse... responses);

    /** departments 流粘性失败（此后每次部门外呼恒返回该响应；优先于队列）。 */
    void failDepartmentsWith(UpstreamResponse stickyFailure);

    /** 预排指定部门的 persons 流响应序列（FIFO 消费；未编程部门走耗尽语义）。 */
    void playPersons(String departmentId, UpstreamResponse... responses);

    /** 凭据建立（如 tenant token）粘性失败。 */
    void failAuthEstablishmentWith(UpstreamResponse stickyFailure);

    // ---- 消费面（宿主 harness 侧） ----

    /**
     * 消费下一次 departments 外呼的响应（记录观测：pageSize / cursor）。
     * 队列耗尽（且无粘性失败）→ 合法空终页。
     */
    UpstreamResponse pollDepartment(int pageSize, String cursor);

    /** 消费指定部门下一次 persons 外呼的响应（记录观测）。队列耗尽 → 合法空终页。 */
    UpstreamResponse pollPerson(String departmentId, int pageSize, String cursor);

    /** 查询凭据建立粘性失败（无则空 = 正常成功路径）。不计数。 */
    UpstreamResponse pollAuthEstablishmentFailure();

    /** 记录一次凭据建立外呼（计数用；harness 在 auth 端点被调时调用）。 */
    void recordAuthEstablishment();

    // ---- 观测面（断言侧） ----

    /** departments 流上游外呼总数。 */
    int departmentCallCount();

    /** departments 流观测到的每次外呼 page_size（外呼序）。 */
    List<Integer> observedDepartmentPageSizes();

    /** departments 流观测到的每次外呼游标（外呼序；首页为 null/空，形态自由）。 */
    List<String> observedDepartmentCursors();

    /** 指定部门 persons 流外呼总数。 */
    int personCallCount(String departmentId);

    /** 指定部门 persons 流观测到的每次外呼 page_size。 */
    List<Integer> observedPersonPageSizes(String departmentId);

    /** 指定部门 persons 流观测到的每次外呼游标。 */
    List<String> observedPersonCursors(String departmentId);

    /** 凭据建立外呼总数。 */
    int authEstablishmentCallCount();

    /** 全部上游外呼总数（auth + departments + persons）——fail-closed 零外呼断言用。 */
    int totalUpstreamCallCount();
}
