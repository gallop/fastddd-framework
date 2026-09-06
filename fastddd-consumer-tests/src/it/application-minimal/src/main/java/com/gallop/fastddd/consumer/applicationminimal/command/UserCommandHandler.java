package com.gallop.fastddd.consumer.applicationminimal.command;

import com.gallop.fastddd.application.annotation.ApplicationService;
import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.application.command.CommandResult;
import com.gallop.fastddd.application.exception.CommandValidationException;
import com.gallop.fastddd.application.exception.CommandValidationException.ValidationError;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消费者侧命令处理器：真实业务行为（校验 + 内存存储），返回 CommandResult 信封。
 */
@ApplicationService(aggregate = Object.class, description = "user registration")
public class UserCommandHandler implements CommandHandler<RegisterUserCommand, CommandResult<String>> {

    @Getter
    private final Map<String, String> users = new ConcurrentHashMap<>();

    @Override
    public CommandResult<String> handle(RegisterUserCommand command) {
        if (command.getEmail() == null || !command.getEmail().contains("@")) {
            throw new CommandValidationException(List.of(
                    new ValidationError("email", "must be a valid email address")));
        }
        if (command.getUserId() == null || command.getUserId().isBlank()) {
            throw new CommandValidationException(List.of(
                    new ValidationError("userId", "must not be blank")));
        }
        users.put(command.getUserId(), command.getEmail());
        return CommandResult.success(command.getUserId());
    }
}
