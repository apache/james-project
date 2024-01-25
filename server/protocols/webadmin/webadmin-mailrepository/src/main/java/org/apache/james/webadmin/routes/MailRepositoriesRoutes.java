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

import java.io.IOException;
import java.io.OutputStream;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.output.CountingOutputStream;
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
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import jakarta.servlet.http.HttpServletResponse;
import spark.HaltException;
import spark.Request;
import spark.Service;

public class MailRepositoriesRoutes implements Routes {

    public static final String MAIL_REPOSITORIES = "mailRepositories";
    private static final TaskRegistrationKey REPROCESS_ACTION = TaskRegistrationKey.of("reprocess");

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

    public void definePutMailRepository() {
        service.put(MAIL_REPOSITORIES + "/:encodedPath", (request, response) -> {
            MailRepositoryPath path = getRepositoryPath(request);
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

    public void defineListMails() {
        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails", (request, response) -> {
            Offset offset = ParametersExtractor.extractOffset(request);
            Limit limit = ParametersExtractor.extractLimit(request);
            MailRepositoryPath path = getRepositoryPath(request);
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

    public void defineGetMailRepositories() {
        service.get(MAIL_REPOSITORIES,
            (request, response) -> repositoryStoreService.listMailRepositories().collect(ImmutableList.toImmutableList()),
            jsonTransformer);
    }

    public void defineGetMail() {
        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", Constants.JSON_CONTENT_TYPE,
            (request, response) ->
                getMailAsJson(getRepositoryPath(request), new MailKey(request.params("mailKey")), request),
            jsonTransformer);

        service.get(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", Constants.RFC822_CONTENT_TYPE,
            (request, response) -> writeMimeMessage(
                getMailAsMimeMessage(
                    getRepositoryPath(request),
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
        CountingOutputStream countingOutputStream = new CountingOutputStream(OutputStream.nullOutputStream());
        mimeMessage.writeTo(countingOutputStream);
        return countingOutputStream.getByteCount();
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

    public void defineGetMailRepository() {
        service.get(MAIL_REPOSITORIES + "/:encodedPath", (request, response) -> {
            MailRepositoryPath path = getRepositoryPath(request);
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

    public void defineDeleteMail() {
        service.delete(MAIL_REPOSITORIES + "/:encodedPath/mails/:mailKey", (request, response) -> {
            MailRepositoryPath path = getRepositoryPath(request);
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

    public void defineDeleteAll() {
        TaskFromRequest taskFromRequest = request -> {
            MailRepositoryPath path = getRepositoryPath(request);
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

    public void defineReprocessAll() {
        service.patch(MAIL_REPOSITORIES + "/:encodedPath/mails",
            TaskFromRequestRegistry.of(REPROCESS_ACTION, this::reprocessAll)
                .asRoute(taskManager),
            jsonTransformer);
    }

    private Task reprocessAll(Request request) throws MailRepositoryStore.MailRepositoryStoreException {
        MailRepositoryPath path = getRepositoryPath(request);

        Long repositorySize = repositoryStoreService.size(path).orElse(0L);
        return new ReprocessingAllMailsTask(reprocessingService, repositorySize, path, extractConfiguration(request));
    }

    private ReprocessingService.Configuration extractConfiguration(Request request) {
        return new ReprocessingService.Configuration(parseTargetQueue(request),
            parseTargetProcessor(request),
            parseMaxRetries(request),
            parseConsume(request).orElse(true),
            parseLimit(request));
    }

    public void defineReprocessOne() {
        service.patch(MAIL_REPOSITORIES + "/:encodedPath/mails/:key",
            TaskFromRequestRegistry.of(REPROCESS_ACTION, this::reprocessOne)
                .asRoute(taskManager),
            jsonTransformer);
    }

    private Task reprocessOne(Request request) {
        MailRepositoryPath path = getRepositoryPath(request);
        MailKey key = new MailKey(request.params("key"));

        return new ReprocessingOneMailTask(reprocessingService, path, extractConfiguration(request), key, Clock.systemUTC());
    }

    private Set<AdditionalField> extractAdditionalFields(String additionalFieldsParam) throws IllegalArgumentException {
        return Splitter
            .on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToStream(additionalFieldsParam)
            .map(field -> AdditionalField.find(field).orElseThrow(() -> new IllegalArgumentException(field)))
            .collect(ImmutableSet.toImmutableSet());
    }

    private Optional<String> parseTargetProcessor(Request request) {
        return Optional.ofNullable(request.queryParams("processor"));
    }

    private Optional<Boolean> parseConsume(Request request) {
        return Optional.ofNullable(request.queryParams("consume"))
            .map(Boolean::parseBoolean);
    }

    private MailQueueName parseTargetQueue(Request request) {
        return Optional.ofNullable(request.queryParams("queue"))
            .map(MailQueueName::of)
            .orElse(MailQueueFactory.SPOOL);
    }

    private MailRepositoryPath getRepositoryPath(Request request) {
        return MailRepositoryPath.from(request.params("encodedPath"));
    }

    private Limit parseLimit(Request request) {
        return Limit.from(ParametersExtractor.extractPositiveInteger(request, "limit"));
    }

    private Optional<Integer> parseMaxRetries(Request request) {
        return ParametersExtractor.extractPositiveInteger(request, "maxRetries");
    }
}
