package com.gallop.fastddd.consumer.applicationminimal.command;

import com.gallop.fastddd.application.command.Command;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消费者侧命令：注册用户。
 */
@Getter
@RequiredArgsConstructor
public class RegisterUserCommand extends Command {

    private final String userId;
    private final String email;
}
