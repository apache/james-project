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

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.User;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.utils.ErrorResponder;
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
import spark.Response;
import spark.Service;

@Api(tags = "Deleted Messages Vault")
@Path(DeletedMessagesVaultRoutes.ROOT_PATH)
@Produces(Constants.JSON_CONTENT_TYPE)
public class DeletedMessagesVaultRoutes implements Routes {

    public static final String ROOT_PATH = "deletedMessages/user";
    private static final String USER_PATH_PARAM = "user";
    private static final String RESTORE_PATH = ROOT_PATH + SEPARATOR + ":" + USER_PATH_PARAM;

    private final RestoreService vaultRestore;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;

    @Inject
    @VisibleForTesting
    DeletedMessagesVaultRoutes(RestoreService vaultRestore, JsonTransformer jsonTransformer,
                               TaskManager taskManager) {
        this.vaultRestore = vaultRestore;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
    }

    @Override
    public String getBasePath() {
        return ROOT_PATH;
    }

    @Override
    public void define(Service service) {
        service.post(RESTORE_PATH, this::restore, jsonTransformer);
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
            value = "Compulsory. Needs to be a valid username represent for an user had requested to restore deleted emails")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - user param is invalid"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    private TaskIdDto restore(Request request, Response response) {
        User userToRestore = extractUser(request);
        TaskId taskId = taskManager.submit(new DeletedMessagesVaultRestoreTask(userToRestore, vaultRestore));
        return TaskIdDto.respond(response, taskId);
    }

    private User extractUser(Request request) {
        try {
            return User.fromUsername(request.params(USER_PATH_PARAM));
        } catch (IllegalArgumentException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(e.getMessage())
                .haltError();
        }
    }
}
