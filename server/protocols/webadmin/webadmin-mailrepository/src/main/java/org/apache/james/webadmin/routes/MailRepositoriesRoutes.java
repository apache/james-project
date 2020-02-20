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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepositoryPath;
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ExtendedMailRepositoryResponse;
import org.apache.james.webadmin.dto.InaccessibleFieldException;
import org.apache.james.webadmin.dto.MailDto;
import org.apache.james.webadmin.dto.MailDto.AdditionalField;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.service.ReprocessingAllMailsTask;
import org.apache.james.webadmin.service.ReprocessingOneMailTask;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskIdDto;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Splitter;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.jaxrs.PATCH;
import spark.HaltException;
import spark.Request;
import spark.Service;

@Api(tags = "MailRepositories", consumes = "application/json")
@Path("/mailRepositories")
@Produces("application/json")
public class MailRepositoriesRoutes implements Routes {

    public static final String MAIL_REPOSITORIES = "mailRepositories";
    private static final TaskRegistrationKey REPROCESS_ACTION = TaskRegistrationKey.of("reprocess");
    private static final String ACTION_PARAMETER = "action";

    private final JsonTransformer jsonTransformer;
    private final MailRepositoryStoreService repositoryStoreService;
    private final ReprocessingService reprocessingService;
    private final TaskManager taskManager;
    private Service service;

    @Inject
    public MailRepositoriesRoutes(MailRepositoryStoreService repositoryStoreService, JsonTransformer jsonTransformer, ReprocessingService reprocessingService, TaskManager taskManager) {
        this.repositoryStoreService = repositoryStoreService;
        this.jsonTransformer = jsonTransformer;
        this.reprocessingService = reprocessingService;
        this.taskManager = taskManager;
    }

    @Override
    public String getBasePath() {
        return MAIL_REPOSITORIES;
    }

    @Override
    public void define(Service service) {
        this.service = service;

        definePutMailRepository();

        defineGetMailRepositories();

        defineListMails();

        defineGetMailRepository();

        defineGetMail();

        defineDeleteMail();

        defineDeleteAll();

        defineReprocessAll();

        defineReprocessOne();
    }

    @PUT
    @Path("/{encodedPath}")
    @ApiOperation(value = "Create a repository")
    @ApiImplicitParams({
        @ApiImplicitParam(
                required = true, 
                dataType = "String", 
                name = "protocol", 
                paramType = "query",
                example = "?protocol=file",
                value = "Specify the storage protocol to use"),
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The repository is created"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void definePutMailRepository() {
        service.put(MAIL_REPOSITORIES + "/:encodedPath", (request, response) -> {
            MailRepositoryPath path = decodedRepositoryPath(request);
            String protocol = request.queryParams("protocol");
            try {
                repositoryStoreService.createMailRepository(path, protocol);
                return Responses.returnNoContent(response);
            } catch (MailRepositoryStore.UnsupportedRepositoryStoreException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .cause(e)
                    .message("'%s' is an unsupported protocol", protocol)
                    .haltError();
            } catch (MailRepositoryStore.MailRepositoryStoreException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while creating a mail repository with path '%s' and protocol '%s'", path.asString(), protocol)
                    .haltError();
            }
        }, jsonTransformer);
    }

    @GET
    @Path("/{encodedPath}/mails")
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
        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails", (request, response) -> {
            Offset offset = ParametersExtractor.extractOffset(request);
            Limit limit = ParametersExtractor.extractLimit(request);
            MailRepositoryPath path = decodedRepositoryPath(request);
            try {
                return repositoryStoreService.listMails(path, offset, limit)
                    .orElseThrow(() -> repositoryNotFound(request.params("encodedPath"), path));

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
            (request, response) -> repositoryStoreService.listMailRepositories().collect(Guavate.toImmutableList()),
            jsonTransformer);
    }

    @GET
    @Produces("application/json, message/rfc822")
    @Path("/{encodedPath}/mails/{mailKey}")
    @ApiOperation(value = "Retrieving a specific mail details (this endpoint can accept both \"application/json\" or \"message/rfc822\")")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The list of all mails in a repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "Not found - Could not retrieve the given mail.")
    })
    public void defineGetMail() {
        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", Constants.JSON_CONTENT_TYPE,
            (request, response) ->
                getMailAsJson(decodedRepositoryPath(request), new MailKey(request.params("mailKey")), request),
            jsonTransformer);

        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", Constants.RFC822_CONTENT_TYPE,
            (request, response) -> writeMimeMessage(
                getMailAsMimeMessage(
                    decodedRepositoryPath(request),
                    new MailKey(request.params("mailKey"))),
                response.raw()));
    }

    private Object writeMimeMessage(MimeMessage mimeMessage, HttpServletResponse rawResponse) throws MessagingException, IOException {
        rawResponse.setContentType(Constants.RFC822_CONTENT_TYPE);
        rawResponse.setHeader("Content-Length", String.valueOf(computeExactSize(mimeMessage)));
        mimeMessage.writeTo(rawResponse.getOutputStream());
        return rawResponse;
    }

    private long computeExactSize(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteArrayOutputStream);
        return byteArrayOutputStream.size();
    }

    private MimeMessage getMailAsMimeMessage(MailRepositoryPath path, MailKey mailKey) {
        try {
            return repositoryStoreService.retrieveMessage(path, mailKey)
                .orElseThrow(mailNotFoundError(mailKey));
        } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
            throw internalServerError(e);
        }
    }

    private MailDto getMailAsJson(MailRepositoryPath path, MailKey mailKey, Request request) {
        try {
            return repositoryStoreService.retrieveMail(path, mailKey, extractAdditionalFields(request.queryParamOrDefault("additionalFields", "")))
                .orElseThrow(mailNotFoundError(mailKey));
        } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
            throw internalServerError(e);
        } catch (IllegalArgumentException e) {
            throw invalidField(e);
        } catch (InaccessibleFieldException e) {
            throw inaccessibleField(e);
        }
    }

    private HaltException inaccessibleField(InaccessibleFieldException e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
            .type(ErrorType.SERVER_ERROR)
            .cause(e)
            .message("The field '%s' requested in additionalFields parameter can't be accessed", e.getField().getName())
            .haltError();
    }

    private HaltException invalidField(IllegalArgumentException e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .cause(e)
            .message("The field '%s' can't be requested in additionalFields parameter", e.getMessage())
            .haltError();
    }

    private Supplier<HaltException> mailNotFoundError(MailKey mailKey) {
        return () -> ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message("Could not retrieve %s", mailKey.asString())
            .haltError();
    }

    private HaltException repositoryNotFound(String encodedPath, MailRepositoryPath path) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.NOT_FOUND)
            .message("The repository '%s' (decoded value: '%s') does not exist", encodedPath, path.asString())
            .haltError();
    }

    private HaltException internalServerError(Exception e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
            .type(ErrorResponder.ErrorType.SERVER_ERROR)
            .cause(e)
            .message("Error while retrieving mail")
            .haltError();
    }

    @GET
    @Path("/{encodedPath}")
    @ApiOperation(value = "Reading the information of a repository, such as size (can take some time to compute)")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The repository information", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The repository does not exist", response = ErrorResponder.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void defineGetMailRepository() {
        service.get(MAIL_REPOSITORIES + "/:encodedPath", (request, response) -> {
            MailRepositoryPath path = decodedRepositoryPath(request);
            try {
                long size = repositoryStoreService.size(path)
                    .orElseThrow(() -> repositoryNotFound(request.params("encodedPath"), path));
                return new ExtendedMailRepositoryResponse(path, size);
            } catch (MailRepositoryStore.MailRepositoryStoreException e) {
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
    @Path("/{encodedPath}/mails/{mailKey}")
    @ApiOperation(value = "Deleting a specific mail from that mailRepository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "Mail is no more stored in the repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void defineDeleteMail() {
        service.delete(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", (request, response) -> {
            MailRepositoryPath path = decodedRepositoryPath(request);
            MailKey mailKey = new MailKey(request.params("mailKey"));
            try {
                repositoryStoreService.deleteMail(path, mailKey);
                return Responses.returnNoContent(response);
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
    @Path("/{encodedPath}/mails")
    @ApiOperation(value = "Deleting all mails in that mailRepository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "All mails are deleted", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - unknown action")
    })
    public void defineDeleteAll() {
        TaskFromRequest taskFromRequest = request -> {
            MailRepositoryPath path = decodedRepositoryPath(request);
            try {
                return repositoryStoreService.createClearMailRepositoryTask(path);
            } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message("Error while deleting all mails")
                    .haltError();
            }
        };
        service.delete(MAIL_REPOSITORIES + "/:encodedPath/mails", taskFromRequest.asRoute(taskManager), jsonTransformer);
    }

    @PATCH
    @Path("/{encodedPath}/mails")
    @ApiOperation(value = "Reprocessing all mails in that mailRepository")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "action",
            paramType = "query parameter",
            dataType = "String",
            defaultValue = "none",
            example = "?action=reprocess",
            value = "Compulsory. Only supported value is `reprocess`"),
        @ApiImplicitParam(
            required = false,
            name = "queue",
            paramType = "query parameter",
            dataType = "String",
            defaultValue = "spool",
            example = "?queue=outgoing",
            value = "Indicates in which queue the mails stored in the repository should be re-enqueued"),
        @ApiImplicitParam(
            required = false,
            paramType = "query parameter",
            name = "processor",
            dataType = "String",
            defaultValue = "absent",
            example = "?processor=transport",
            value = "If present, modifies the state property of the mail to allow their processing by a specific mail container processor.")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - unknown action")
    })
    public void defineReprocessAll() {
        service.patch(MAIL_REPOSITORIES + "/:encodedPath/mails",
            TaskFromRequestRegistry.of(REPROCESS_ACTION, this::reprocessAll)
                .asRoute(taskManager),
            jsonTransformer);
    }

    private Task reprocessAll(Request request) throws UnsupportedEncodingException, MailRepositoryStore.MailRepositoryStoreException {
        MailRepositoryPath path = decodedRepositoryPath(request);
        Optional<String> targetProcessor = parseTargetProcessor(request);
        MailQueueName targetQueue = parseTargetQueue(request);

        Long repositorySize = repositoryStoreService.size(path).orElse(0L);
        return new ReprocessingAllMailsTask(reprocessingService, repositorySize, path, targetQueue, targetProcessor);
    }

    @PATCH
    @Path("/{encodedPath}/mails/{key}")
    @ApiOperation(value = "Reprocessing a single mail in that mailRepository")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            name = "action",
            paramType = "query parameter",
            dataType = "String",
            defaultValue = "none",
            example = "?action=reprocess",
            value = "Compulsory. Only supported value is `reprocess`"),
        @ApiImplicitParam(
            required = false,
            name = "queue",
            paramType = "query parameter",
            dataType = "String",
            defaultValue = "spool",
            example = "?queue=outgoing",
            value = "Indicates in which queue the mails stored in the repository should be re-enqueued"),
        @ApiImplicitParam(
            required = false,
            paramType = "query parameter",
            name = "processor",
            dataType = "String",
            defaultValue = "absent",
            example = "?processor=transport",
            value = "If present, modifies the state property of the mail to allow their processing by a specific mail container processor.")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "Task is created", response = TaskIdDto.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Bad request - unknown action")
    })
    public void defineReprocessOne() {
        service.patch(MAIL_REPOSITORIES + "/:encodedPath/mails/:key",
            TaskFromRequestRegistry.of(REPROCESS_ACTION, this::reprocessOne)
                .asRoute(taskManager),
            jsonTransformer);
    }

    private Task reprocessOne(Request request) throws UnsupportedEncodingException {
        MailRepositoryPath path = decodedRepositoryPath(request);
        MailKey key = new MailKey(request.params("key"));

        Optional<String> targetProcessor = parseTargetProcessor(request);
        MailQueueName targetQueue = parseTargetQueue(request);

        return new ReprocessingOneMailTask(reprocessingService, path, targetQueue, key, targetProcessor, Clock.systemUTC());
    }

    private Set<AdditionalField> extractAdditionalFields(String additionalFieldsParam) throws IllegalArgumentException {
        return Splitter
            .on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(additionalFieldsParam)
            .stream()
            .map((field) -> AdditionalField.find(field).orElseThrow(() -> new IllegalArgumentException(field)))
            .collect(Guavate.toImmutableSet());
    }

    private Optional<String> parseTargetProcessor(Request request) {
        return Optional.ofNullable(request.queryParams("processor"));
    }

    private MailQueueName parseTargetQueue(Request request) {
        return Optional.ofNullable(request.queryParams("queue"))
            .map(MailQueueName::of)
            .orElse(MailQueueFactory.SPOOL);
    }

    private MailRepositoryPath decodedRepositoryPath(Request request) throws UnsupportedEncodingException {
        return MailRepositoryPath.fromEncoded(request.params("encodedPath"));
    }
}
