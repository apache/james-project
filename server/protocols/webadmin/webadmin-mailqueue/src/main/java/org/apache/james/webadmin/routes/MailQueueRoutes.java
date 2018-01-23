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

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.util.streams.Iterators;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.MailQueueDTO;
import org.apache.james.webadmin.dto.MailQueueItemDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Request;
import spark.Service;


@Api(tags = "MailQueues")
@Path(MailQueueRoutes.BASE_URL)
@Produces("application/json")
public class MailQueueRoutes implements Routes {

    @VisibleForTesting static final String BASE_URL = "/mailQueues";
    @VisibleForTesting static final String MAIL_QUEUE_NAME = ":mailQueueName";
    @VisibleForTesting static final String MESSAGES = "/messages";
    
    private static final String DELAYED_QUERY_PARAM = "delayed";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MailQueueRoutes.class);

    private final MailQueueFactory<ManageableMailQueue> mailQueueFactory;
    private final JsonTransformer jsonTransformer;

    @Inject
    @VisibleForTesting MailQueueRoutes(MailQueueFactory<ManageableMailQueue> mailQueueFactory, JsonTransformer jsonTransformer) {
        this.mailQueueFactory = mailQueueFactory;
        this.jsonTransformer = jsonTransformer;
    }

    @Override
    public void define(Service service) {
        defineListQueues(service);

        getMailQueue(service);
        
        listMessages(service);
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
    @Path("/{mailQueueName}/messages")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "mailQueueName", paramType = "path"),
        @ApiImplicitParam(required = false, dataType = "boolean", name = DELAYED_QUERY_PARAM, paramType = "query")
    })
    @ApiOperation(
        value = "List the messages of the MailQueue"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK", response = List.class),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The MailQueue does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void listMessages(Service service) {
        service.get(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME + MESSAGES, 
                (request, response) -> listMessages(request), 
                jsonTransformer);
    }

    private List<MailQueueItemDTO> listMessages(Request request) {
        String mailQueueName = request.params(MAIL_QUEUE_NAME);
        return mailQueueFactory.getQueue(mailQueueName)
                .map(name -> listMessages(name, isDelayed(request.queryParams(DELAYED_QUERY_PARAM))))
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

    private List<MailQueueItemDTO> listMessages(ManageableMailQueue queue, Optional<Boolean> isDelayed) {
        try {
            return Iterators.toStream(queue.browse())
                    .map(Throwing.function(MailQueueItemDTO::from))
                    .filter(item -> filter(item, isDelayed))
                    .collect(Guavate.toImmutableList());
        } catch (MailQueueException e) {
            LOGGER.info("Invalid request for getting the mail queue " + queue, e);
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorType.INVALID_ARGUMENT)
                .message("Invalid request for getting the mail queue " + queue)
                .cause(e)
                .haltError();
        }
    }

    private boolean filter(MailQueueItemDTO item, Optional<Boolean> isDelayed) {
        return isDelayed.map(delayed -> delayed == item.isDelayed())
            .orElse(true);
    }
}
