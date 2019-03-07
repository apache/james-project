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
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.User;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.vault.routes.query.QueryElement;
import org.apache.james.webadmin.vault.routes.query.QueryTranslator;
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

    enum UserVaultAction {
        RESTORE("restore");

        static Optional<UserVaultAction> getAction(String value) {
            Preconditions.checkNotNull(value, "action cannot be null");
            Preconditions.checkArgument(StringUtils.isNotBlank(value), "action cannot be empty or blank");

            return Stream.of(values())
                .filter(action -> action.value.equals(value))
                .findFirst();
        }

        private static List<String> plainValues() {
            return Stream.of(values())
                .map(UserVaultAction::getValue)
                .collect(Guavate.toImmutableList());
        }

        private final String value;

        UserVaultAction(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final String ROOT_PATH = "deletedMessages/user";
    private static final String USER_PATH_PARAM = "user";
    private static final String RESTORE_PATH = ROOT_PATH + SEPARATOR + ":" + USER_PATH_PARAM;
    private static final String ACTION_QUERY_PARAM = "action";

    private final RestoreService vaultRestore;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final JsonExtractor<QueryElement> jsonExtractor;
    private final QueryTranslator queryTranslator;

    @Inject
    @VisibleForTesting
    DeletedMessagesVaultRoutes(RestoreService vaultRestore, JsonTransformer jsonTransformer,
                               TaskManager taskManager, QueryTranslator queryTranslator) {
        this.vaultRestore = vaultRestore;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
        this.queryTranslator = queryTranslator;
        this.jsonExtractor = new JsonExtractor<>(QueryElement.class);
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(RESTORE_PATH, this::userActions, jsonTransformer);
    }

    @POST
    @Path(ROOT_PATH)
    @ApiOperation(value = "Restore deleted emails from a specified user to his new restore mailbox")
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
                "valid action should be in the list (restore)")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - user param is invalid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    private TaskIdDto userActions(Request request, Response response) throws JsonExtractException {
        UserVaultAction requestedAction = extractUserVaultAction(request);

        Task requestedTask = generateTask(requestedAction, request);
        TaskId taskId = taskManager.submit(requestedTask);
        return TaskIdDto.respond(response, taskId);
    }

    private Task generateTask(UserVaultAction requestedAction, Request request) throws JsonExtractException {
        User userToRestore = extractUser(request);
        Query query = translate(jsonExtractor.parse(request.body()));

        switch (requestedAction) {
            case RESTORE:
                return new DeletedMessagesVaultRestoreTask(vaultRestore, userToRestore, query);
            default:
                throw new NotImplementedException(requestedAction + " is not yet handled.");
        }
    }

    private Query translate(QueryElement queryElement) {
        try {
            return queryTranslator.translate(queryElement);
        } catch (QueryTranslator.QueryTranslatorException e) {
            throw badRequest("Invalid payload passing to the route", e);
        }
    }

    private User extractUser(Request request) {
        try {
            return User.fromUsername(request.params(USER_PATH_PARAM));
        } catch (IllegalArgumentException e) {
            throw badRequest("Invalid 'user' parameter", e);
        }
    }

    private HaltException badRequest(String message, Exception e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
            .message(message)
            .cause(e)
            .haltError();
    }

    private UserVaultAction extractUserVaultAction(Request request) {
        String actionParam = request.queryParams(ACTION_QUERY_PARAM);
        return Optional.ofNullable(actionParam)
            .map(this::getUserVaultAction)
            .orElseThrow(() -> new IllegalArgumentException("action parameter is missing"));
    }

    private UserVaultAction getUserVaultAction(String actionString) {
        return UserVaultAction.getAction(actionString)
            .orElseThrow(() -> new IllegalArgumentException(String.format("'%s' is not a valid action. Supported values are: (%s)",
                actionString,
                Joiner.on(",").join(UserVaultAction.plainValues()))));
    }
}
