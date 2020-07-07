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
import static org.apache.james.webadmin.routes.MailQueueRoutes.BASE_URL;
import static org.apache.james.webadmin.routes.MailQueueRoutes.MAIL_QUEUE_NAME;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.RepublishNotprocessedMailsTask;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
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
import spark.Service;

@Api(tags = "MailQueues")
@Path(BASE_URL)
@Produces("application/json")
public class RabbitMQMailQueuesRoutes implements Routes {

    private static final TaskRegistrationKey REPUBLISH_NOT_PROCESSED_MAILS_REGISTRATION_KEY = TaskRegistrationKey.of("RepublishNotProcessedMails");

    private final MailQueueFactory<RabbitMQMailQueue> mailQueueFactory;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final Clock clock;

    @Inject
    @SuppressWarnings("unchecked")
    @VisibleForTesting
    RabbitMQMailQueuesRoutes(MailQueueFactory<RabbitMQMailQueue> mailQueueFactory,
                             Clock clock, JsonTransformer jsonTransformer, TaskManager taskManager) {
        this.mailQueueFactory = mailQueueFactory;
        this.clock = clock;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
    }

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Override
    public void define(Service service) {
        republishNotProcessedMails(service);
    }


    @POST
    @Path("/{mailQueueName}")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "mailQueueName", paramType = "path"),
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "action",
            paramType = "query",
            example = "?action=RepublishNotProcessedMails",
            value = "Specify the action to perform on a RabbitMQ mail queue."),
        @ApiImplicitParam(
            required = true,
            dataType = "String",
            name = "olderThan",
            paramType = "query",
            example = "?olderThan=1w",
            value = "Specify the messages minimum age to republish")
    })
    @ApiOperation(
        value = "republish the not processed mails of the RabbitMQ MailQueue using the cassandra mail queue view"
    )
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.CREATED_201, message = "OK, the task for rebuilding the queue is created"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid request for rebuilding the mail queue."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500, message = "Internal server error - Something went bad on the server side.")
    })
    public void republishNotProcessedMails(Service service) {
        TaskFromRequest taskFromRequest = this::republishNotProcessedMails;
        service.post(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME,
            TaskFromRequestRegistry.builder()
                .register(REPUBLISH_NOT_PROCESSED_MAILS_REGISTRATION_KEY, this::republishNotProcessedMails)
                .buildAsRoute(taskManager),
            jsonTransformer);
    }

    private Task republishNotProcessedMails(Request request) {
        RabbitMQMailQueue mailQueue = getMailQueue(MailQueueName.of(request.params(MAIL_QUEUE_NAME)));
        return new RepublishNotprocessedMailsTask(mailQueue, getOlderThan(request));
    }


    private RabbitMQMailQueue getMailQueue(MailQueueName mailQueueName) {
        return mailQueueFactory.getQueue(mailQueueName)
            .orElseThrow(
                () -> ErrorResponder.builder()
                    .message("%s can not be found", mailQueueName)
                    .statusCode(HttpStatus.NOT_FOUND_404)
                    .type(ErrorResponder.ErrorType.NOT_FOUND)
                    .haltError());
    }

    private Instant getOlderThan(Request req) {
        try {
            Duration olderThan =  Optional.ofNullable(req.queryParams("olderThan"))
                .filter(Predicate.not(String::isEmpty))
                .map(rawString -> DurationParser.parse(rawString, ChronoUnit.DAYS))
                .orElseThrow();

            return clock.instant().minus(olderThan);
        } catch (NoSuchElementException e) {
            throw ErrorResponder.builder()
                .message("Missing olderThan")
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .haltError();
        } catch (Exception e) {
            throw ErrorResponder.builder()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .cause(e)
                .type(ErrorResponder.ErrorType.INVALID_ARGUMENT)
                .message("Invalid olderThan")
                .haltError();
        }
    }
}
