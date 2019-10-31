/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin.vault.routes;

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.mail.internet.AddressException;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.dto.query.QueryElement;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

@Api(tags = "Deleted Messages Vault")
@Path(DeletedMessagesVaultRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class DeletedMessagesVaultRoutes implements Routes {

    enum VaultAction {
        RESTORE("restore"),
        EXPORT("export");

        static Optional<VaultAction> getAction(String value) {
            Preconditions.checkNotNull(value, "action cannot be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(value), "action cannot be empty or blank");

            return Stream.of(values())
                .filter(action -> action.value.equals(value))
                .findFirst();
        }

        @VisibleForTesting
        static List<String> plainValues() {
            return Stream.of(values())
                .map(VaultAction::getValue)
                .collect(Guavate.toImmutableList());
        }

        private final String value;

        VaultAction(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    enum VaultScope {
        EXPIRED("expired");

        static Optional<VaultScope> getScope(String value) {
            Preconditions.checkNotNull(value, "scope cannot be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(value), "scope cannot be empty or blank");

            return Stream.of(values())
                .filter(action -> action.value.equals(value))
                .findFirst();
        }

        @VisibleForTesting
        static List<String> plainValues() {
            return Stream.of(values())
                .map(VaultScope::getValue)
                .collect(Guavate.toImmutableList());
        }

        private final String value;

        VaultScope(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final String ROOT_PATH = "deletedMessages";
    public static final String USERS = "users";
    public static final String MESSAGE_PATH_PARAM = "messages";
    private static final String USER_PATH_PARAM = ":user";
    private static final String MESSAGE_ID_PARAM = ":messageId";
    static final String USER_PATH = ROOT_PATH + SEPARATOR + USERS + SEPARATOR + USER_PATH_PARAM;
    private static final String DELETE_PATH = ROOT_PATH + SEPARATOR + USERS + SEPARATOR + USER_PATH_PARAM + SEPARATOR + MESSAGE_PATH_PARAM + SEPARATOR + MESSAGE_ID_PARAM;
    private static final String SCOPE_QUERY_PARAM = "scope";
    private static final String ACTION_QUERY_PARAM = "action";
    private static final String EXPORT_TO_QUERY_PARAM = "exportTo";

    private final RestoreService vaultRestore;
    private final ExportService vaultExport;
    private final DeletedMessageVault deletedMessageVault;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final JsonExtractor<QueryElement> jsonExtractor;
    private final QueryTranslator queryTranslator;
    private final UsersRepository usersRepository;
    private final MessageId.Factory messageIdFactory;

    @Inject
    @VisibleForTesting
    DeletedMessagesVaultRoutes(DeletedMessageVault deletedMessageVault, RestoreService vaultRestore, ExportService vaultExport,
                               JsonTransformer jsonTransformer, TaskManager taskManager, QueryTranslator queryTranslator,
                               UsersRepository usersRepository, MessageId.Factory messageIdFactory) {
        this.deletedMessageVault = deletedMessageVault;
        this.vaultRestore = vaultRestore;
        this.vaultExport = vaultExport;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
        this.queryTranslator = queryTranslator;
        this.usersRepository = usersRepository;
        this.jsonExtractor = new JsonExtractor<>(QueryElement.class);
        this.messageIdFactory = messageIdFactory;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(USER_PATH, this::userActions, jsonTransformer);
        service.delete(ROOT_PATH, this::deleteWithScope, jsonTransformer);
        service.delete(DELETE_PATH, this::deleteMessage, jsonTransformer);
    }

    @POST
    @Path("users/{user}")
    @ApiOperation(value = "Restore deleted emails from a specified user to his new restore mailbox" +
        " or export their content to a destination mail address")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "user",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            example = "user@james.org",
            value = "Compulsory. Needs to be a valid username represent for an user had requested to restore deleted emails"),
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=restore",
            value = "Compulsory. Needs to be a valid action represent for an operation to perform on the Deleted Message Vault, " +
                "valid action should be in the list (restore, export)"),
        @ApiImplicitParam(
            dataType = "String",
            name = "exportTo",
            paramType = "query",
            example = "?exportTo=user@james.org",
            value = "Compulsory if action is export. Needs to be a valid mail address. The content of the vault matching the query will be sent to that address")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - user param is invalid"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "Not found - requested user does not exist"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    private TaskIdDto userActions(Request request, Response response) throws JsonExtractException {
        VaultAction requestedAction = extractParam(request, ACTION_QUERY_PARAM, this::getVaultAction);
        validateAction(requestedAction, VaultAction.RESTORE, VaultAction.EXPORT);

        Task requestedTask = generateUserTask(requestedAction, request);
        TaskId taskId = taskManager.submit(requestedTask);
        return TaskIdDto.respond(response, taskId);
    }

    @DELETE
    @ApiOperation(value = "Purge all expired messages based on retentionPeriod of deletedMessageVault configuration")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "action",
            dataType = "String",
            paramType = "query",
            example = "?scope=expired",
            value = "Compulsory. Needs to be a purge action")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - action is invalid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    private TaskIdDto deleteWithScope(Request request, Response response) {
        Task vaultTask = generateVaultScopeTask(request);
        TaskId taskId = taskManager.submit(vaultTask);
        return TaskIdDto.respond(response, taskId);
    }

    @DELETE
    @Path("users/{user}/messages/{messageId}")
    @ApiOperation(value = "Delete message with messageId")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "user",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            example = "user0@james.org",
            value = "Compulsory. Needs to be a valid username represent for an user had requested to restore deleted emails"),
        @ApiImplicitParam(
            required = true,
            name = "messageId",
            paramType = "path parameter",
            dataType = "String",
            defaultValue = "none",
            value = "Compulsory, Need to be a valid messageId")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - user param is invalid"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - messageId param is invalid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    private TaskIdDto deleteMessage(Request request, Response response) {
        Username username = extractUser(request);
        validateUserExist(username);
        MessageId messageId = parseMessageId(request);

        TaskId taskId = taskManager.submit(new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId));
        return TaskIdDto.respond(response, taskId);
    }

    private Task generateVaultScopeTask(Request request) {
        VaultScope scope = extractParam(request, SCOPE_QUERY_PARAM, this::getVaultScope);
        if (!scope.equals(VaultScope.EXPIRED)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(String.format("'%s' is not a valid scope. Supported values are: %s",
                    scope.value,
                    VaultScope.plainValues()))
                .haltError();
        }
        return deletedMessageVault.deleteExpiredMessagesTask();
    }

    private Task generateUserTask(VaultAction requestedAction, Request request) throws JsonExtractException {
        Username username = extractUser(request);
        validateUserExist(username);
        Query query = translate(jsonExtractor.parse(request.body()));

        switch (requestedAction) {
            case RESTORE:
                return new DeletedMessagesVaultRestoreTask(vaultRestore, username, query);
            case EXPORT:
                return new DeletedMessagesVaultExportTask(vaultExport, username, query, extractMailAddress(request));
            default:
                throw new NotImplementedException(requestedAction + " is not yet handled.");
        }
    }

    private void validateUserExist(Username username) {
        try {
            if (!usersRepository.contains(username)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("User '" + username.asString() + "' does not exist in the system")
                    .haltError();
            }
        } catch (UsersRepositoryException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorResponder.ErrorType.SERVER_ERROR)
                .message("Unable to validate 'user' parameter")
                .cause(e)
                .haltError();
        }
    }

    private MailAddress extractMailAddress(Request request) {
        return Optional.ofNullable(request.queryParams(EXPORT_TO_QUERY_PARAM))
            .filter(StringUtils::isNotBlank)
            .map(this::parseToMailAddress)
            .orElseThrow(() -> ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid 'exportTo' parameter, null or blank value is not accepted")
                .haltError());
    }

    private MailAddress parseToMailAddress(String addressString) {
        try {
            return new MailAddress(addressString);
        } catch (AddressException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid 'exportTo' parameter")
                .cause(e)
                .haltError();
        }
    }

    private Query translate(QueryElement queryElement) {
        try {
            return queryTranslator.translate(queryElement);
        } catch (QueryTranslator.QueryTranslatorException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid payload passing to the route")
                .cause(e)
                .haltError();
        }
    }

    private Username extractUser(Request request) {
        try {
            return Username.of(request.params(USER_PATH_PARAM));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid 'user' parameter")
                .cause(e)
                .haltError();
        }
    }

    private <T> T extractParam(Request request, String queryParam, Function<String, T> mapper) {
        String param = request.queryParams(queryParam);
        return Optional.ofNullable(param)
            .map(mapper)
            .orElseThrow(() -> new IllegalArgumentException("parameter is missing"));
    }

    private VaultAction getVaultAction(String actionString) {
        return VaultAction.getAction(actionString)
            .orElseThrow(() -> new IllegalArgumentException(String.format("'%s' is not a valid action. Supported values are: (%s)",
                actionString,
                Joiner.on(",").join(VaultAction.plainValues()))));
    }

    private VaultScope getVaultScope(String scopeString) {
        return VaultScope.getScope(scopeString)
            .orElseThrow(() -> new IllegalArgumentException(String.format("'%s' is not a valid scope. Supported values are: (%s)",
                scopeString,
                Joiner.on(",").join(VaultScope.plainValues()))));
    }

    private void validateAction(VaultAction requestedAction, VaultAction... validActions) {
        Stream.of(validActions)
            .filter(action -> action.equals(requestedAction))
            .findFirst()
            .orElseThrow(() -> throwNotSupportedAction(requestedAction, validActions));
    }

    private HaltException throwNotSupportedAction(VaultAction requestAction, VaultAction... validActions) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(String.format("'%s' is not a valid action. Supported values are: %s",
                requestAction.getValue(),
                Joiner.on(", ").join(Stream.of(validActions)
                    .map(action -> String.format("'%s'", action.getValue()))
                    .collect(Guavate.toImmutableList()))))
            .haltError();
    }

    private MessageId parseMessageId(Request request) {
        String messageIdAsString = request.params(MESSAGE_ID_PARAM);
        try {
            return messageIdFactory.fromString(messageIdAsString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied messageId: " + messageIdAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }
}
