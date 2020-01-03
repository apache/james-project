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

import java.util.Optional;

import javax.inject.Inject;
import javax.mail.internet.AddressException;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.dto.query.QueryElement;
import org.apache.james.vault.dto.query.QueryTranslator;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Route;
import spark.Service;

@Api(tags = "Deleted Messages Vault")
@Path(DeletedMessagesVaultRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class DeletedMessagesVaultRoutes implements Routes {
    private static final TaskRegistrationKey EXPORT_REGISTRATION_KEY = TaskRegistrationKey.of("export");
    private static final TaskRegistrationKey RESTORE_REGISTRATION_KEY = TaskRegistrationKey.of("restore");
    private static final TaskRegistrationKey EXPIRED_REGISTRATION_KEY = TaskRegistrationKey.of("expired");

    public static final String ROOT_PATH = "deletedMessages";
    public static final String USERS = "users";
    public static final String MESSAGE_PATH_PARAM = "messages";
    private static final String USER_PATH_PARAM = ":user";
    private static final String MESSAGE_ID_PARAM = ":messageId";
    static final String USER_PATH = ROOT_PATH + SEPARATOR + USERS + SEPARATOR + USER_PATH_PARAM;
    private static final String DELETE_PATH = ROOT_PATH + SEPARATOR + USERS + SEPARATOR + USER_PATH_PARAM + SEPARATOR + MESSAGE_PATH_PARAM + SEPARATOR + MESSAGE_ID_PARAM;
    private static final String SCOPE_QUERY_PARAM = "scope";
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
        service.post(USER_PATH, userActions(), jsonTransformer);
        service.delete(ROOT_PATH, deleteWithScope(), jsonTransformer);

        TaskFromRequest deleteTaskFromRequest = this::deleteMessage;
        service.delete(DELETE_PATH, deleteTaskFromRequest.asRoute(taskManager), jsonTransformer);
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
    private Route userActions() {
        return TaskFromRequestRegistry.builder()
            .register(EXPORT_REGISTRATION_KEY, this::export)
            .register(RESTORE_REGISTRATION_KEY, this::restore)
            .buildAsRoute(taskManager);
    }

    private Task export(Request request) throws JsonExtractException {
        Username username = extractUser(request);
        validateUserExist(username);
        return new DeletedMessagesVaultExportTask(vaultExport, username, extractQuery(request), extractMailAddress(request));
    }

    private Task restore(Request request) throws JsonExtractException {
        Username username = extractUser(request);
        validateUserExist(username);
        return new DeletedMessagesVaultRestoreTask(vaultRestore, username, extractQuery(request));
    }

    @DELETE
    @ApiOperation(value = "Purge all expired messages based on retentionPeriod of deletedMessageVault configuration")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "scope",
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
    private Route deleteWithScope() {
        return TaskFromRequestRegistry.builder()
            .parameterName(SCOPE_QUERY_PARAM)
            .register(EXPIRED_REGISTRATION_KEY, request -> deletedMessageVault.deleteExpiredMessagesTask())
            .buildAsRoute(taskManager);
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
    private Task deleteMessage(Request request) {
        Username username = extractUser(request);
        validateUserExist(username);
        MessageId messageId = parseMessageId(request);

        return new DeletedMessagesVaultDeleteTask(deletedMessageVault, username, messageId);
    }

    private void validateUserExist(Username username) {
        try {
            if (!usersRepository.contains(username)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .message("User '%s' does not exist in the system", username.asString())
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

    private Query extractQuery(Request request) throws JsonExtractException {
        try {
            QueryElement queryElement = jsonExtractor.parse(request.body());
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

    private MessageId parseMessageId(Request request) {
        String messageIdAsString = request.params(MESSAGE_ID_PARAM);
        try {
            return messageIdFactory.fromString(messageIdAsString);
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Can not deserialize the supplied messageId: %s", messageIdAsString)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        }
    }
}
