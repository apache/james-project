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

package org.apache.james.webadmin.routes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ExtendedMailRepositoryResponse;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Strings;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Service;

@Api(tags = "MailRepositories")
@Path("/mailRepositories")
@Produces("application/json")
public class MailRepositoriesRoutes implements Routes {

    public static final String MAIL_REPOSITORIES = "mailRepositories";

    private final JsonTransformer jsonTransformer;
    private final MailRepositoryStoreService repositoryStoreService;
    private final TaskManager taskManager;
    private Service service;

    @Inject
    public MailRepositoriesRoutes(MailRepositoryStoreService repositoryStoreService, JsonTransformer jsonTransformer, TaskManager taskManager) {
        this.repositoryStoreService = repositoryStoreService;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineGetMailRepositories();

        defineListMails();

        defineGetMailRepository();

        defineGetMail();

        defineDeleteMail();

        defineDeleteAll();
    }

    @GET
    @Path("/{encodedUrl}/mails")
    @ApiOperation(value = "Listing all mails in a repository")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = false,
            name = "offset",
            paramType = "query parameter",
            dataType = "Integer",
            defaultValue = "0",
            example = "?offset=100",
            value = "If present, skips the given number of key in the output."),
        @ApiImplicitParam(
            required = false,
            paramType = "query parameter",
            name = "limit",
            dataType = "Integer",
            defaultValue = "absent",
            example = "?limit=100",
            value = "If present, fixes the maximal number of key returned in that call. Must be more than zero if specified.")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The list of all mails in a repository", response = List.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - invalid parameter"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The repository does not exist", response = ErrorResponder.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineListMails() {
        service.get(MAIL_REPOSITORIES + "/:encodedUrl/mails", (request, response) -> {
            Offset offset = Offset.from(assertPositiveInteger(request, "offset"));
            Limit limit = Limit.from(assertPositiveInteger(request, "limit")
                .map(value -> assertNotZero(value, "limit")));
            String encodedUrl = request.params("encodedUrl");
            String url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.displayName());
            try {
                return repositoryStoreService.listMails(url, offset, limit)
                    .orElseThrow(() -> ErrorResponder.builder()
                            .statusCode(HttpStatus.NOT_FOUND_404)
                            .type(ErrorType.NOT_FOUND)
                            .message("The repository " + encodedUrl + "(decoded value: '" + url + "') does not exist")
                            .haltError());

            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while listing keys")
                    .haltError();
            }
        }, jsonTransformer);
    }

    @GET
    @ApiOperation(value = "Listing all mail repositories URLs")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "Listing all mail repositories URLs", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineGetMailRepositories() {
        service.get(MAIL_REPOSITORIES,
            (request, response) -> repositoryStoreService.listMailRepositories(),
            jsonTransformer);
    }

    @Path("/{encodedUrl}/mails/{mailKey}")
    @ApiOperation(value = "Retrieving a specific mail details")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The list of all mails in a repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "Not found - Could not retrieve the given mail.")
    })
    public void defineGetMail() {
        service.get(MAIL_REPOSITORIES + "/:encodedUrl/mails/:mailKey", (request, response) -> {
            String url = URLDecoder.decode(request.params("encodedUrl"), StandardCharsets.UTF_8.displayName());
            String mailKey = request.params("mailKey");
            try {
                return repositoryStoreService.retrieveMail(url, mailKey)
                    .orElseThrow(() -> ErrorResponder.builder()
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .message("Could not retrieve " + mailKey)
                        .haltError());
            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while retrieving mail")
                    .haltError();
            }
        }, jsonTransformer);
    }

    @GET
    @Path("/{encodedUrl}")
    @ApiOperation(value = "Reading the information of a repository, such as size (can take some time to compute)")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The repository information", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The repository does not exist", response = ErrorResponder.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void defineGetMailRepository() {
        service.get(MAIL_REPOSITORIES + "/:encodedUrl", (request, response) -> {
            String encodedUrl = request.params("encodedUrl");
            String url = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.displayName());
            try {
                long size = repositoryStoreService.size(url)
                    .orElseThrow(() -> ErrorResponder.builder()
                            .statusCode(HttpStatus.NOT_FOUND_404)
                            .type(ErrorType.NOT_FOUND)
                            .message("The repository " + encodedUrl + "(decoded value: '" + url + "') does not exist")
                            .haltError());
                return new ExtendedMailRepositoryResponse(url, size);
            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while retrieving mail repository information")
                    .haltError();
            }
        }, jsonTransformer);
    }

    @DELETE
    @Path("/{encodedUrl}/mails/{mailKey}")
    @ApiOperation(value = "Deleting a specific mail from that mailRepository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "Mail is no more stored in the repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void defineDeleteMail() {
        service.delete(MAIL_REPOSITORIES + "/:encodedUrl/mails/:mailKey", (request, response) -> {
            String url = URLDecoder.decode(request.params("encodedUrl"), StandardCharsets.UTF_8.displayName());
            String mailKey = request.params("mailKey");
            try {
                response.status(HttpStatus.NO_CONTENT_204);
                repositoryStoreService.deleteMail(url, mailKey);
                return Constants.EMPTY_BODY;
            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while deleting mail")
                    .haltError();
            }
        });
    }

    @DELETE
    @Path("/{encodedUrl}/mails")
    @ApiOperation(value = "Deleting all mails in that mailRepository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "All mails are deleted", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - unknown action")
    })
    public void defineDeleteAll() {
        service.delete(MAIL_REPOSITORIES + "/:encodedUrl/mails", (request, response) -> {
            String url = URLDecoder.decode(request.params("encodedUrl"), StandardCharsets.UTF_8.displayName());
            try {
                Task task = repositoryStoreService.createClearMailRepositoryTask(url);
                TaskId taskId = taskManager.submit(task);
                return TaskIdDto.respond(response, taskId);
            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while deleting all mails")
                    .haltError();
            }
        }, jsonTransformer);
    }

    private Optional<Integer> assertPositiveInteger(Request request, String parameterName) {
        try {
            return Optional.ofNullable(request.queryParams(parameterName))
                .filter(s -> !Strings.isNullOrEmpty(s))
                .map(Integer::valueOf)
                .map(value -> assertPositive(value, parameterName));
        } catch (NumberFormatException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .cause(e)
                .message("Can not parse " + parameterName)
                .haltError();
        }
    }

    private int assertPositive(int value, String parameterName) {
        if (value < 0) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(parameterName + " can not be negative")
                .haltError();
        }
        return value;
    }

    private int assertNotZero(int value, String parameterName) {
        if (value == 0) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message(parameterName + " can not be equal to zero")
                .haltError();
        }
        return value;
    }
}
