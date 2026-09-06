package com.gallop.fastddd.consumer.applicationminimal.query;

import com.gallop.fastddd.application.annotation.ApplicationService;
import com.gallop.fastddd.application.query.QueryHandler;
import com.gallop.fastddd.consumer.applicationminimal.command.UserCommandHandler;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 消费者侧查询处理器：从命令侧内存存储读取（读写分离的最小形态）。
 */
@ApplicationService
@RequiredArgsConstructor
public class UserQueryHandler implements QueryHandler<ListUsersQuery, List<ListUsersQuery.UserItem>> {

    private final UserCommandHandler userCommandHandler;

    @Override
    public List<ListUsersQuery.UserItem> handle(ListUsersQuery query) {
        return userCommandHandler.getUsers().entrySet().stream()
                .filter(e -> query.getEmailDomainFilter() == null
                        || e.getValue().endsWith("@" + query.getEmailDomainFilter()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ListUsersQuery.UserItem(e.getKey(), e.getValue()))
                .toList();
    }
}
