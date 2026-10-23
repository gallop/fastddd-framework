package com.gallop.fastddd.identity.core.directory;

import com.gallop.fastddd.identity.core.domain.Actor;
import com.gallop.fastddd.identity.core.domain.DirectoryDepartment;
import com.gallop.fastddd.identity.core.domain.DirectoryPage;
import com.gallop.fastddd.identity.core.domain.DirectoryPageQuery;
import com.gallop.fastddd.identity.core.domain.DirectoryPerson;
import com.gallop.fastddd.identity.core.domain.DirectoryScope;
import com.gallop.fastddd.identity.core.error.DirectoryContractViolationException;
import com.gallop.fastddd.identity.core.error.DirectorySourceForbiddenException;
import com.gallop.fastddd.identity.core.error.DirectorySourceUnavailableException;
import com.gallop.fastddd.identity.core.port.DirectorySource;
import com.gallop.fastddd.identity.core.system.SystemJobPrincipalContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * W3-a 编排测试用脚本化目录源（<b>测试替身，非生产实现</b>）：
 * 按 pageSize 切页；支持首页即拒（Forbidden）、指定页瞬时故障（Unavailable）、
 * 游标不前进（契约违反形态）、无限页（护栏形态）与调用顺序/系统作业身份记录。
 */
public final class ScriptedDirectorySource implements DirectorySource {

    private final DirectoryScope scope;
    private final List<DirectoryDepartment> departments;
    private final Map<String, List<DirectoryPerson>> personsByDepartment;

    /** 行为注入： departments(pageOrdinal) 可抛异常或返回定制页。 */
    public Consumer<Integer> departmentsHook;
    public PageCustomizer departmentsCustomizer;
    /** 行为注入： persons(departmentId, pageOrdinal)。 */
    public Consumer<String> personsHook;

    /** 调用记录（顺序断言用）。 */
    public final List<String> calls = new ArrayList<>();
    /** 每次调用观察到的系统作业 Actor（身份断言用）。 */
    public final List<Optional<Actor>> observedActors = new ArrayList<>();

    public ScriptedDirectorySource(DirectoryScope scope,
                                   List<DirectoryDepartment> departments,
                                   Map<String, List<DirectoryPerson>> personsByDepartment) {
        this.scope = scope;
        this.departments = List.copyOf(departments);
        this.personsByDepartment = Map.copyOf(personsByDepartment);
    }

    /** 定制页形态（契约违反注入：同游标/无游标等）。 */
    public interface PageCustomizer {
        DirectoryPage<DirectoryDepartment> customize(int pageOrdinal, DirectoryPage<DirectoryDepartment> natural);
    }

    @Override
    public DirectoryScope scope() {
        return scope;
    }

    @Override
    public DirectoryPage<DirectoryDepartment> departments(DirectoryPageQuery query) {
        observedActors.add(SystemJobPrincipalContext.currentActor());
        int pageOrdinal = cursorToOrdinal(query.cursor());
        calls.add("departments:" + pageOrdinal);
        if (departmentsHook != null) {
            departmentsHook.accept(pageOrdinal);
        }
        int from = pageOrdinal * query.pageSize();
        int to = Math.min(from + query.pageSize(), departments.size());
        List<DirectoryDepartment> slice = departments.subList(from, to);
        boolean hasMore = to < departments.size();
        DirectoryPage<DirectoryDepartment> page =
                new DirectoryPage<>(slice, hasMore ? "c" + (pageOrdinal + 1) : null, hasMore);
        if (departmentsCustomizer != null && hasMore) {
            return departmentsCustomizer.customize(pageOrdinal, page);
        }
        return page;
    }

    @Override
    public DirectoryPage<DirectoryPerson> persons(String departmentId, DirectoryPageQuery query) {
        observedActors.add(SystemJobPrincipalContext.currentActor());
        int pageOrdinal = cursorToOrdinal(query.cursor());
        calls.add("persons:" + departmentId + ":" + pageOrdinal);
        if (personsHook != null) {
            personsHook.accept(departmentId);
        }
        List<DirectoryPerson> list = personsByDepartment.getOrDefault(departmentId, List.of());
        int from = pageOrdinal * query.pageSize();
        if (from >= list.size()) {
            return DirectoryPage.last(List.of());
        }
        int to = Math.min(from + query.pageSize(), list.size());
        boolean hasMore = to < list.size();
        return new DirectoryPage<>(list.subList(from, to),
                hasMore ? "c" + (pageOrdinal + 1) : null, hasMore);
    }

    private static int cursorToOrdinal(String cursor) {
        return cursor == null ? 0 : Integer.parseInt(cursor.substring(1));
    }

    /** 便捷注入：第 {@code pageOrdinal} 页抛"瞬时不可用"（源重试已耗尽形态）。 */
    public static Consumer<Integer> unavailableAt(int pageOrdinal) {
        return ordinal -> {
            if (ordinal == pageOrdinal) {
                throw new DirectorySourceUnavailableException("transport",
                        "脚本注入：第 " + ordinal + " 页瞬时不可用（源重试已耗尽）");
            }
        };
    }

    /** 便捷注入：首页即权限不足（fail-fast 形态）。 */
    public static Consumer<Integer> forbiddenAt(int pageOrdinal) {
        return ordinal -> {
            if (ordinal == pageOrdinal) {
                throw new DirectorySourceForbiddenException("upstream-forbidden",
                        "脚本注入：第 " + ordinal + " 页无权限");
            }
        };
    }

    /** 便捷注入：hasMore=true 但游标与进入游标相同（契约违反形态）。 */
    public static PageCustomizer cursorNotAdvancing() {
        return (ordinal, natural) -> new DirectoryPage<>(natural.items(),
                ordinal == 0 ? "c0" : "c" + (ordinal - 1), true);
    }

    /** 便捷注入：hasMore=true 但无游标（契约违反形态）。 */
    public static PageCustomizer hasMoreWithoutCursor() {
        return (ordinal, natural) -> new DirectoryPage<>(natural.items(), null, true);
    }
}
