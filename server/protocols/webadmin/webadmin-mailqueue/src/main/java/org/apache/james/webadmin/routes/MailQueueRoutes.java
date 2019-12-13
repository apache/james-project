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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.MailAddress;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
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
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Booleans;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.jaxrs.PATCH;
import spark.HaltException;
import spark.Request;
import spark.Response;
import spark.Service;


@Api(tags = "MailQueues")
@Path(MailQueueRoutes.BASE_URL)
@Produces("application/json")
public class MailQueueRoutes implements Routes {

    public static final String BASE_URL = "/mailQueues";
    @VisibleForTesting static final String MAIL_QUEUE_NAME = ":mailQueueName";
    @VisibleForTesting static final String MAILS = "/mails";
    
    private static final String DELAYED_QUERY_PARAM = "delayed";
    private static final String LIMIT_QUERY_PARAM = "limit";
    @VisibleForTesting static final int DEFAULT_LIMIT_VALUE = 100;
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

    @GET
    @ApiOperation(
        value = "Listing existing MailQueues"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void defineListQueues(Service service) {
        service.get(BASE_URL,
            (request, response) ->
                mailQueueFactory
                    .listCreatedMailQueues()
                    .stream()
                    .map(ManageableMailQueue::getName)
                    .collect(Guavate.toImmutableList()),
            jsonTransformer);
    }

    @GET
    @Path("/{mailQueueName}")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "mailQueueName", paramType = "path")
    })
    @ApiOperation(
        value = "Get a MailQueue details"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = MailQueueDTO.class),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for getting the mail queue."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The MailQueue does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void getMailQueue(Service service) {
        service.get(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME,
            (request, response) -> getMailQueue(request),
            jsonTransformer);
    }

    private MailQueueDTO getMailQueue(Request request) {
        String mailQueueName = request.params(MAIL_QUEUE_NAME);
        return mailQueueFactory.getQueue(mailQueueName).map(this::toDTO)
            .orElseThrow(
                () -> ErrorResponder.builder()
                    .message(String.format("%s can not be found", mailQueueName))
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .haltError());
    }

    private MailQueueDTO toDTO(ManageableMailQueue queue) {
        try {
            return MailQueueDTO.from(queue);
        } catch (MailQueueException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for getting the mail queue " + queue)
                .cause(e)
                .haltError();
        }
    }

    @GET
    @Path("/{mailQueueName}/mails")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "mailQueueName", paramType = "path"),
        @ApiImplicitParam(
                required = false,
                dataType = "boolean",
                name = DELAYED_QUERY_PARAM,
                paramType = "query",
                example = "?delayed=true",
                value = "Whether the mails are delayed in the mail queue or not (already sent)."),
        @ApiImplicitParam(
                required = false,
                dataType = "int",
                name = LIMIT_QUERY_PARAM,
                paramType = "query",
                example = "?limit=100",
                defaultValue = "100",
                value = "Limits the maximum number of mails returned by this endpoint")
    })
    @ApiOperation(
        value = "List the mails of the MailQueue"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The MailQueue does not exist."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for listing the mails from the mail queue."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void listMails(Service service) {
        service.get(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
                (request, response) -> listMails(request),
                jsonTransformer);
    }

    private List<MailQueueItemDTO> listMails(Request request) {
        String mailQueueName = request.params(MAIL_QUEUE_NAME);
        return mailQueueFactory.getQueue(mailQueueName)
                .map(name -> listMails(name, isDelayed(request.queryParams(DELAYED_QUERY_PARAM)), ParametersExtractor.extractLimit(request)))
                .orElseThrow(
                    () -> ErrorResponder.builder()
                        .message(String.format("%s can not be found", mailQueueName))
                        .statusCode(HttpStatus.NOT_FOUND_404)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .haltError());
    }

    @VisibleForTesting Optional<Boolean> isDelayed(String delayedAsString) {
        return Optional.ofNullable(delayedAsString)
                .map(Boolean::parseBoolean);
    }

    private List<MailQueueItemDTO> listMails(ManageableMailQueue queue, Optional<Boolean> isDelayed, Limit limit) {
        try {
            return limit.applyOnStream(Iterators.toStream(queue.browse()))
                    .map(Throwing.function(MailQueueItemDTO::from).sneakyThrow())
                    .filter(item -> filter(item, isDelayed))
                    .collect(Guavate.toImmutableList());
        } catch (MailQueueException e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for listing the mails from the mail queue " + queue)
                .cause(e)
                .haltError();
        }
    }

    private boolean filter(MailQueueItemDTO item, Optional<Boolean> isDelayed) {
        boolean mailIsDelayed = item.getNextDelivery().map(date -> date.isAfter(ZonedDateTime.now())).orElse(false);
        return isDelayed
            .map(delayed -> delayed == mailIsDelayed)
            .orElse(true);
    }

    @DELETE
    @Path("/{mailQueueName}/mails")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "mailQueueName", paramType = "path"),
        @ApiImplicitParam(
                required = false, 
                dataTypeClass = MailAddress.class,
                name = SENDER_QUERY_PARAM, 
                paramType = "query",
                example = "?sender=sender@james.org",
                value = "The sender of the mails to be deleted should be equals to this query parameter."),
        @ApiImplicitParam(
                required = false, 
                dataType = "String", 
                name = NAME_QUERY_PARAM,
                paramType = "query",
                example = "?name=mailName",
                value = "The name of the mails to be deleted should be equals to this query parameter."),
        @ApiImplicitParam(
                required = false, 
                dataType = "MailAddress", 
                name = RECIPIENT_QUERY_PARAM, 
                paramType = "query",
                example = "?recipient=recipient@james.org",
                value = "The recipients of the mails to be deleted should contain this query parameter."),
    })
    @ApiOperation(
        value = "Delete mails from the MailQueue"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "OK, the task for deleting mails is created"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The MailQueue does not exist."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for deleting mails from the mail queue."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void deleteMails(Service service) {
        TaskFromRequest taskFromRequest = this::deleteMails;
        service.delete(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
                taskFromRequest.asRoute(taskManager),
                jsonTransformer);
    }

    private Task deleteMails(Request request) {
        String mailQueueName = request.params(MAIL_QUEUE_NAME);
        return mailQueueFactory.getQueue(mailQueueName)
            .map(name -> deleteMailsTask(name,
                    sender(request.queryParams(SENDER_QUERY_PARAM)),
                    name(request.queryParams(NAME_QUERY_PARAM)),
                    recipient(request.queryParams(RECIPIENT_QUERY_PARAM))))
            .orElseThrow(
                () -> ErrorResponder.builder()
                    .message(String.format("%s can not be found", mailQueueName))
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .haltError());
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

    @PATCH
    @Path("/{mailQueueName}/mails")
    @ApiImplicitParams({
        @ApiImplicitParam(
            required = true,
            dataType = "string",
            name = "mailQueueName",
            paramType = "path"),
        @ApiImplicitParam(
            required = false,
            dataType = "boolean",
            name = DELAYED_QUERY_PARAM,
            paramType = "query",
            example = "?delayed=true",
            value = "Whether the mails are delayed in the mail queue or not (already sent).")
    })
    @ApiOperation(
        value = "Force delayed mails delivery"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for getting the mail queue."),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The MailQueue does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void forceDelayedMailsDelivery(Service service) {
        service.patch(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MAILS,
            this::forceDelayedMailsDelivery,
            jsonTransformer);
    }

    private String forceDelayedMailsDelivery(Request request, Response response) throws JsonExtractException, MailQueueException {
        assertDelayedParamIsTrue(request);
        assertPayloadContainsDelayedEntry(request);
        ManageableMailQueue mailQueue = assertMailQueueExists(request);

        mailQueue.flush();

        return Responses.returnNoContent(response);
    }

    private ManageableMailQueue assertMailQueueExists(Request request) {
        String mailQueueName = request.params(MAIL_QUEUE_NAME);
        return mailQueueFactory.getQueue(mailQueueName)
            .orElseThrow(() -> ErrorResponder.builder()
                .message(String.format("%s can not be found", mailQueueName))
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

    private Task deleteMailsTask(ManageableMailQueue queue, Optional<MailAddress> maybeSender, Optional<String> maybeName, Optional<MailAddress> maybeRecipient) {
        int paramCount = Booleans.countTrue(maybeSender.isPresent(), maybeName.isPresent(), maybeRecipient.isPresent());
        switch (paramCount) {
            case 0:
                return new ClearMailQueueTask(queue);
            case 1:
                return new DeleteMailsFromMailQueueTask(queue, maybeSender, maybeName, maybeRecipient);
            default:
                throw ErrorResponder.builder()
                    .statusCode(HttpStatus.BAD_REQUEST_400)
                    .type(ErrorType.INVALID_ARGUMENT)
                    .message("You should provide only one of the query parameters 'sender', 'name', 'recipient' " +
                            "for deleting mails by condition or no parameter for deleting all mails in the mail queue.")
                    .haltError();
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
