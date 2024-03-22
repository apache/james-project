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

import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.util.streams.Iterators;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.ForceDelivery;
import org.apache.james.webadmin.dto.MailQueueDTO;
import org.apache.james.webadmin.dto.MailQueueItemDTO;
import org.apache.james.webadmin.service.ClearMailQueueTask;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTask;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractException;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.james.webadmin.utils.ParametersExtractor;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;

import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;

public class MailQueueRoutes implements Routes {

    public static final String BASE_URL = "/mailQueues";
    static final String MAIL_QUEUE_NAME = ":mailQueueName";
    static final String MAILS = "/mails";
    
    private static final String DELAYED_QUERY_PARAM = "delayed";
    private static final String SENDER_QUERY_PARAM = "sender";
    private static final String NAME_QUERY_PARAM = "name";
    private static final String RECIPIENT_QUERY_PARAM = "recipient";
    
    private final MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory;
    private final JsonTransformer jsonTransformer;
    private final JsonExtractor<ForceDelivery> jsonExtractor;
    private final TaskManager taskManager;

    @Inject
    @SuppressWarnings("unchecked")
    @VisibleForTesting MailQueueRoutes(MailQueueFactory<? extends ManageableMailQueue> mailQueueFactory, JsonTransformer jsonTransformer,
                                       TaskManager taskManager) {
        this.mailQueueFactory = mailQueueFactory;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(ForceDelivery.class);
        this.taskManager = taskManager;
    }

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Override
    public void define(Service service) {
        defineListQueues(service);

        getMailQueue(service);

        listMails(service);

        deleteMails(service);

        forceDelayedMailsDelivery(service);
    }

    public void defineListQueues(Service service) {
        service.get(BASE_URL,
            (request, response) -> mailQueueFactory.listCreatedMailQueues()
                .stream()
                .map(MailQueueName::asString)
                .collect(ImmutableList.toImmutableList()),
            jsonTransformer);
    }

    public void getMailQueue(Service service) {
        service.get(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME,
            (request, response) -> getMailQueue(request),
            jsonTransformer);
    }

    private MailQueueDTO getMailQueue(Request request) {
        MailQueueName mailQueueName = MailQueueName.of(request.params(MAIL_QUEUE_NAME));
        return mailQueueFactory.getQueue(mailQueueName).map(this::toDTO)
            .orElseThrow(
                () -> ErrorResponder.builder()
                    .message("%s can not be found", mailQueueName)
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .haltError());
    }

    private MailQueueDTO toDTO(ManageableMailQueue q) {
        try (ManageableMailQueue queue = q) {
            return MailQueueDTO.from(queue);
        } catch (MailQueueException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for getting the mail queue %s", q.getName().asString())
                .cause(e)
                .haltError();
        } catch (IOException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
                .type(ErrorType.SERVER_ERROR)
                .message("Cannot close queue %s", q.getName().asString())
                .cause(e)
                .haltError();
        }
    }

    public void listMails(Service service) {
        service.get(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
                (request, response) -> listMails(request),
                jsonTransformer);
    }

    private List<MailQueueItemDTO> listMails(Request request) {
        MailQueueName mailQueueName = MailQueueName.of(request.params(MAIL_QUEUE_NAME));
        Optional<? extends ManageableMailQueue> queue = mailQueueFactory.getQueue(mailQueueName);
        try {
            return queue
                .map(q -> listMails(q, isDelayed(request.queryParams(DELAYED_QUERY_PARAM)), ParametersExtractor.extractLimit(request)))
                .orElseThrow(
                    () -> ErrorResponder.builder()
                        .message("%s can not be found", mailQueueName)
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .haltError());
        } finally {
            queue.ifPresent(Throwing.consumer(Closeable::close));
        }
    }

    @VisibleForTesting Optional<Boolean> isDelayed(String delayedAsString) {
        return Optional.ofNullable(delayedAsString)
                .map(Boolean::parseBoolean);
    }

    private List<MailQueueItemDTO> listMails(ManageableMailQueue queue, Optional<Boolean> isDelayed, Limit limit) {
        try (MailQueue closeable = queue) {
            return limit.applyOnStream(Iterators.toStream(queue.browse()))
                    .map(Throwing.function(MailQueueItemDTO::from).sneakyThrow())
                    .filter(item -> filter(item, isDelayed))
                    .collect(ImmutableList.toImmutableList());
        } catch (MailQueueException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for listing the mails from the mail queue %s", queue)
                .cause(e)
                .haltError();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean filter(MailQueueItemDTO item, Optional<Boolean> isDelayed) {
        boolean mailIsDelayed = item.getNextDelivery().map(date -> date.isAfter(ZonedDateTime.now())).orElse(false);
        return isDelayed
            .map(delayed -> delayed == mailIsDelayed)
            .orElse(true);
    }

    public void deleteMails(Service service) {
        TaskFromRequest taskFromRequest = this::deleteMails;
        service.delete(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
                taskFromRequest.asRoute(taskManager),
                jsonTransformer);
    }

    private Task deleteMails(Request request) {
        MailQueueName mailQueueName = MailQueueName.of(request.params(MAIL_QUEUE_NAME));
        checkQueueExists(mailQueueName);
        return deleteMailsTask(mailQueueName,
                    sender(request.queryParams(SENDER_QUERY_PARAM)),
                    name(request.queryParams(NAME_QUERY_PARAM)),
                    recipient(request.queryParams(RECIPIENT_QUERY_PARAM)));
    }

    private Optional<MailAddress> sender(String senderAsString) throws HaltException {
        try {
            return Optional.ofNullable(senderAsString)
                    .map(Throwing.function((String sender) -> new MailAddress(sender)).sneakyThrow());
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("'sender' should be a mail address (i.e. sender@james.org)")
                .cause(e)
                .haltError();
        }
    }

    private Optional<String> name(String nameAsString) {
        return Optional.ofNullable(nameAsString);
    }

    private Optional<MailAddress> recipient(String recipientAsString) throws HaltException {
        try {
            return Optional.ofNullable(recipientAsString)
                    .map(Throwing.function((String recipient) -> new MailAddress(recipient)).sneakyThrow());
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("'recipient' should be a mail address (i.e. recipient@james.org)")
                .cause(e)
                .haltError();
        }
    }

    public void forceDelayedMailsDelivery(Service service) {
        service.patch(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
            this::forceDelayedMailsDelivery,
            jsonTransformer);
    }

    private String forceDelayedMailsDelivery(Request request, Response response) throws MailQueueException {
        assertDelayedParamIsTrue(request);
        assertPayloadContainsDelayedEntry(request);
        try (ManageableMailQueue mailQueue = assertMailQueueExists(request)) {
            mailQueue.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Responses.returnNoContent(response);
    }

    private ManageableMailQueue assertMailQueueExists(Request request) {
        MailQueueName mailQueueName = MailQueueName.of(request.params(MAIL_QUEUE_NAME));
        return mailQueueFactory.getQueue(mailQueueName)
            .orElseThrow(() -> ErrorResponder.builder()
                .message("%s can not be found", mailQueueName)
                .statusCode(HttpStatus.NOT_FOUND_404)
                .type(ErrorType.NOT_FOUND)
                .haltError());
    }

    private void assertPayloadContainsDelayedEntry(Request request) {
        try {
            if (jsonExtractor.parse(request.body())
                .getDelayed()
                .orElse(true)) {
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("This request requires payload to contain delayed attribute set to false")
                    .haltError();
            }
        } catch (JsonExtractException e) {
            throw ErrorResponder.builder()
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .message("Invalid JSON document: " + e.getMessage())
                .cause(e)
                .haltError();
        }
    }

    private Task deleteMailsTask(MailQueueName queueName, Optional<MailAddress> maybeSender, Optional<String> maybeName, Optional<MailAddress> maybeRecipient) {
        int paramCount = Booleans.countTrue(maybeSender.isPresent(), maybeName.isPresent(), maybeRecipient.isPresent());
        switch (paramCount) {
            case 0:
                return new ClearMailQueueTask(queueName, this::getQueue);
            case 1:
                return new DeleteMailsFromMailQueueTask(queueName, this::getQueue, maybeSender, maybeName, maybeRecipient);
            default:
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("You should provide only one of the query parameters 'sender', 'name', 'recipient' " +
                            "for deleting mails by condition or no parameter for deleting all mails in the mail queue.")
                    .haltError();
        }
    }

    private ManageableMailQueue getQueue(MailQueueName queueName) throws MailQueueException {
        return mailQueueFactory.getQueue(queueName).orElseThrow(() -> new MailQueueException("unable to find queue " + queueName.asString()));
    }

    private ManageableMailQueue checkQueueExists(MailQueueName queueName) {
        Optional<? extends ManageableMailQueue> queue = mailQueueFactory.getQueue(queueName);
        try {
            return queue
                .orElseThrow(
                    () -> ErrorResponder.builder()
                        .message("%s can not be found", queueName)
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .haltError());
        } finally {
            queue.ifPresent(Throwing.consumer(Closeable::close));
        }
    }

    private void assertDelayedParamIsTrue(Request request) {
        if (!isDelayed(request.queryParams(DELAYED_QUERY_PARAM)).orElse(false)) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("This request requires delayed param to be set to true")
                .haltError();
        }
    }
}
