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
import org.apache.james.mailrepository.api.MailRepositoryStore;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.task.Task;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.util.streams.Limit;
import org.apache.james.util.streams.Offset;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ExtendedMailRepositoryResponse;
import org.apache.james.webadmin.dto.InaccessibleFieldException;
import org.apache.james.webadmin.dto.MailDto;
import org.apache.james.webadmin.dto.MailDto.AdditionalField;
import org.apache.james.webadmin.dto.TaskIdDto;
import org.apache.james.webadmin.service.MailRepositoryStoreService;
import org.apache.james.webadmin.service.ReprocessingAllMailsTask;
import org.apache.james.webadmin.service.ReprocessingOneMailTask;
import org.apache.james.webadmin.service.ReprocessingService;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
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
    @Path("/{encodedUrl}")
    @ApiOperation(value = "Create a repository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "The repository is created"),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void definePutMailRepository() {
        service.put(MAIL_REPOSITORIES + "/:encodedUrl", (request, response) -> {
            MailRepositoryUrl url = decodedRepositoryUrl(request);
            try {
                repositoryStoreService.createMailRepository(url);
                response.status(HttpStatus.NO_CONTENT_204);
                return Constants.EMPTY_BODY;
            } catch (MailRepositoryStore.MailRepositoryStoreException e) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                    .type(ErrorResponder.ErrorType.SERVER_ERROR)
                    .cause(e)
                    .message(String.format("Error while creating a mail repository with url '%s'", url.asString()))
                    .haltError();
            }
        }, jsonTransformer);
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
            Offset offset = ParametersExtractor.extractOffset(request);
            Limit limit = ParametersExtractor.extractLimit(request);
            String encodedUrl = request.params("encodedUrl");
            MailRepositoryUrl url = MailRepositoryUrl.fromEncoded(encodedUrl);
            try {
                return repositoryStoreService.listMails(url, offset, limit)
                    .orElseThrow(() -> repositoryNotFound(encodedUrl, url));

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

    @GET
    @Produces("application/json, message/rfc822")
    @Path("/{encodedUrl}/mails/{mailKey}")
    @ApiOperation(value = "Retrieving a specific mail details (this endpoint can accept both \"application/json\" or \"message/rfc822\")")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "The list of all mails in a repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "Not found - Could not retrieve the given mail.")
    })
    public void defineGetMail() {
        service.get(MAIL_REPOSITORIES + "/:encodedUrl/mails/:mailKey", Constants.JSON_CONTENT_TYPE,
            (request, response) ->
                getMailAsJson(decodedRepositoryUrl(request), new MailKey(request.params("mailKey")), request),
            jsonTransformer);

        service.get(MAIL_REPOSITORIES + "/:encodedUrl/mails/:mailKey", Constants.RFC822_CONTENT_TYPE,
            (request, response) -> writeMimeMessage(
                getMailAsMimeMessage(
                    decodedRepositoryUrl(request),
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

    private MimeMessage getMailAsMimeMessage(MailRepositoryUrl url, MailKey mailKey) {
        try {
            return repositoryStoreService.retrieveMessage(url, mailKey)
                .orElseThrow(mailNotFoundError(mailKey));
        } catch (MailRepositoryStore.MailRepositoryStoreException | MessagingException e) {
            throw internalServerError(e);
        }
    }

    private MailDto getMailAsJson(MailRepositoryUrl url, MailKey mailKey, Request request) {
        try {
            return repositoryStoreService.retrieveMail(url, mailKey, extractAdditionalFields(request.queryParamOrDefault("additionalFields", "")))
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
            .message("The field '" + e.getField().getName() + "' requested in additionalFields parameter can't be accessed")
            .haltError();
    }

    private HaltException invalidField(IllegalArgumentException e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .cause(e)
            .message("The field '" + e.getMessage() + "' can't be requested in additionalFields parameter")
            .haltError();
    }

    private Supplier<HaltException> mailNotFoundError(MailKey mailKey) {
        return () -> ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorResponder.ErrorType.NOT_FOUND)
            .message("Could not retrieve " + mailKey.asString())
            .haltError();
    }

    private HaltException repositoryNotFound(String encodedUrl, MailRepositoryUrl url) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.NOT_FOUND)
            .message("The repository '" + encodedUrl + "' (decoded value: '" + url.asString() + "') does not exist")
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
            MailRepositoryUrl url = MailRepositoryUrl.fromEncoded(encodedUrl);
            try {
                long size = repositoryStoreService.size(url)
                    .orElseThrow(() -> repositoryNotFound(encodedUrl, url));
                return new ExtendedMailRepositoryResponse(url, size);
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
    @Path("/{encodedUrl}/mails/{mailKey}")
    @ApiOperation(value = "Deleting a specific mail from that mailRepository")
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "Mail is no more stored in the repository", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side."),
    })
    public void defineDeleteMail() {
        service.delete(MAIL_REPOSITORIES + "/:encodedUrl/mails/:mailKey", (request, response) -> {
            MailRepositoryUrl url = decodedRepositoryUrl(request);
            MailKey mailKey = new MailKey(request.params("mailKey"));
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
            MailRepositoryUrl url = decodedRepositoryUrl(request);
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

    @PATCH
    @Path("/{encodedUrl}/mails")
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
        service.patch(MAIL_REPOSITORIES + "/:encodedUrl/mails", (request, response) -> {
            Task task = toAllMailReprocessingTask(request);
            TaskId taskId = taskManager.submit(task);
            return TaskIdDto.respond(response, taskId);
        }, jsonTransformer);
    }

    private Task toAllMailReprocessingTask(Request request) throws UnsupportedEncodingException, MailRepositoryStore.MailRepositoryStoreException, MessagingException {
        MailRepositoryUrl url = decodedRepositoryUrl(request);
        enforceActionParameter(request);
        Optional<String> targetProcessor = Optional.ofNullable(request.queryParams("processor"));
        String targetQueue = Optional.ofNullable(request.queryParams("queue")).orElse(MailQueueFactory.SPOOL);

        Long repositorySize = repositoryStoreService.size(url).orElse(0L);
        return new ReprocessingAllMailsTask(reprocessingService, repositorySize, url, targetQueue, targetProcessor);
    }

    @PATCH
    @Path("/{encodedUrl}/mails/{key}")
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
        service.patch(MAIL_REPOSITORIES + "/:encodedUrl/mails/:key", (request, response) -> {
            Task task = toOneMailReprocessingTask(request);
            TaskId taskId = taskManager.submit(task);
            return TaskIdDto.respond(response, taskId);
        }, jsonTransformer);
    }

    private Task toOneMailReprocessingTask(Request request) throws UnsupportedEncodingException {
        MailRepositoryUrl url = decodedRepositoryUrl(request);
        MailKey key = new MailKey(request.params("key"));
        enforceActionParameter(request);
        Optional<String> targetProcessor = Optional.ofNullable(request.queryParams("processor"));
        String targetQueue = Optional.ofNullable(request.queryParams("queue")).orElse(MailQueueFactory.SPOOL);

        return new ReprocessingOneMailTask(reprocessingService, url, targetQueue, key, targetProcessor);
    }

    private void enforceActionParameter(Request request) {
        String action = request.queryParams("action");
        if (!"reprocess".equals(action)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("action query parameter is mandatory. The only supported value is `reprocess`")
                .haltError();
        }
    }

    private MailRepositoryUrl decodedRepositoryUrl(Request request) throws UnsupportedEncodingException {
        return MailRepositoryUrl.fromEncoded(request.params("encodedUrl"));
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

}
