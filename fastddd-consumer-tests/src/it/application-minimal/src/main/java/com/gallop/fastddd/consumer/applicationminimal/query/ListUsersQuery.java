package com.gallop.fastddd.consumer.applicationminimal.query;

import com.gallop.fastddd.application.query.Query;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 消费者侧查询：列出用户（可按 email 域过滤）。
 */
@Getter
@RequiredArgsConstructor
public class ListUsersQuery extends Query {

    private final String emailDomainFilter;

    public static ListUsersQuery all() {
        return new ListUsersQuery(null);
    }

    /**
     * 查询结果条目（读模型 DTO）。
     */
    public record UserItem(String userId, String email) {

        public static List<UserItem> of(List<String> ids, java.util.function.Function<String, String> emailOf) {
            return ids.stream().map(id -> new UserItem(id, emailOf.apply(id))).toList();
        }
    }
}
