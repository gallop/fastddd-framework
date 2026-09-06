package com.gallop.fastddd.consumer.applicationminimal;

import com.gallop.fastddd.application.command.Command;
import com.gallop.fastddd.application.command.CommandHandler;
import com.gallop.fastddd.application.command.CommandResult;
import com.gallop.fastddd.application.exception.CommandValidationException;
import com.gallop.fastddd.application.exception.CommandValidationException.ValidationError;
import com.gallop.fastddd.application.query.Query;
import com.gallop.fastddd.application.query.QueryHandler;
import com.gallop.fastddd.consumer.applicationminimal.command.RegisterUserCommand;
import com.gallop.fastddd.consumer.applicationminimal.command.UserCommandHandler;
import com.gallop.fastddd.consumer.applicationminimal.query.ListUsersQuery;
import com.gallop.fastddd.consumer.applicationminimal.query.UserQueryHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * application 模块最小消费者契约验证：
 * 通过公开 API（Command/Query/Handler/CommandResult/验证异常）完成真实命令与查询行为。
 */
@SpringBootTest(classes = ApplicationMinimalApp.class)
class ApplicationContractTest {

    @Autowired
    private UserCommandHandler userCommandHandler;

    @Autowired
    private UserQueryHandler userQueryHandler;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 1. 命令成功路径：CommandResult 信封（成功态 + 数据）、命令类型、元数据驱动的稳定 commandId。
     */
    @Test
    void commandSuccessReturnsEnvelopeWithDataAndStableCommandId() {
        RegisterUserCommand command = new RegisterUserCommand("u-001", "u001@example.com");
        Command.CommandMetadata metadata = new Command.CommandMetadata("corr-001", null);
        command.setMetadata(metadata);

        assertThat(command.getCommandType()).isEqualTo("RegisterUserCommand");

        CommandResult<String> result = userCommandHandler.handle(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
        assertThat(result.getData()).contains("u-001");
        assertThat(result.getMessage()).isNotBlank();
        assertThat(result.getError()).isNull();
        assertThat(userCommandHandler.getUsers()).containsEntry("u-001", "u001@example.com");

        // commandId 契约：设置元数据后，getCommandId 稳定且等于元数据中的命令ID
        assertThat(command.getCommandId())
                .isEqualTo(metadata.getCommandId())
                .isEqualTo(command.getCommandId());
        assertThat(command.getMetadata().getCorrelationId()).isEqualTo("corr-001");
    }

    /**
     * 2. 命令验证失败路径：CommandValidationException 携带结构化错误，可映射为 CommandResult 失败信封。
     */
    @Test
    void invalidCommandThrowsStructuredValidationErrorConvertibleToFailure() {
        RegisterUserCommand badEmail = new RegisterUserCommand("u-002", "not-an-email");
        assertThatThrownBy(() -> userCommandHandler.handle(badEmail))
                .isInstanceOf(CommandValidationException.class)
                .hasMessageContaining("1 errors")
                .satisfies(ex -> {
                    List<ValidationError> errors = ((CommandValidationException) ex).getErrors();
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getField()).isEqualTo("email");
                    assertThat(errors.get(0).getMessage()).contains("email");
                });

        RegisterUserCommand blankUser = new RegisterUserCommand(" ", "x@example.com");
        assertThatThrownBy(() -> userCommandHandler.handle(blankUser))
                .isInstanceOf(CommandValidationException.class)
                .satisfies(ex -> assertThat(((CommandValidationException) ex).getErrors())
                        .singleElement()
                        .extracting(ValidationError::getField)
                        .isEqualTo("userId"));

        // 异常 → 失败信封映射（公开 API 行为）
        CommandValidationException ex = new CommandValidationException(List.of(
                new ValidationError("email", "must be a valid email address")));
        CommandResult<String> failure = CommandResult.fromException(ex);
        assertThat(failure.isFailure()).isTrue();
        assertThat(failure.getData()).isEmpty();
        assertThat(failure.getError()).isEqualTo(ex.getMessage());

        // 错误列表不可变
        assertThatThrownBy(() -> ex.getErrors().add(new ValidationError("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * 3. 查询路径：过滤行为、查询类型、QueryMetadata 扩展属性回环。
     */
    @Test
    void queryFiltersReadModelAndCarriesMetadata() {
        userCommandHandler.handle(new RegisterUserCommand("q-001", "alice@example.com"));
        userCommandHandler.handle(new RegisterUserCommand("q-002", "bob@other.org"));

        ListUsersQuery query = ListUsersQuery.all();
        assertThat(query.getQueryType()).isEqualTo("ListUsersQuery");

        Query.QueryMetadata queryMetadata = new Query.QueryMetadata();
        queryMetadata.putAttribute("channel", "portal");
        query.setMetadata(queryMetadata);
        assertThat(query.getMetadata().getAttribute("channel")).isEqualTo("portal");
        assertThat(query.getMetadata().getAttribute("absent")).isNull();

        List<ListUsersQuery.UserItem> all = userQueryHandler.handle(query);
        assertThat(all)
                .extracting(ListUsersQuery.UserItem::userId)
                .containsExactly("q-001", "q-002");

        List<ListUsersQuery.UserItem> filtered =
                userQueryHandler.handle(new ListUsersQuery("example.com"));
        assertThat(filtered)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.userId()).isEqualTo("q-001");
                    assertThat(item.email()).isEqualTo("alice@example.com");
                });
    }

    /**
     * 4. @ApplicationService 装配：消费者自有包扫描将命令/查询处理器注册为 Spring Bean。
     */
    @Test
    void applicationServiceHandlersAreRegisteredAsBeans() {
        assertThat(applicationContext.getBeansOfType(CommandHandler.class))
                .containsKey("userCommandHandler");
        assertThat(applicationContext.getBeansOfType(QueryHandler.class))
                .containsKey("userQueryHandler");
    }
}
