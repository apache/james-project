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
import static org.apache.james.webadmin.tasks.TaskFromRequestRegistry.builder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.util.DurationParser;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.service.ClearMailQueueTask;
import org.apache.james.webadmin.service.RepublishNotprocessedMailsTask;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.tasks.TaskRegistrationKey;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.annotations.VisibleForTesting;

import spark.Request;
import spark.Service;

public class RabbitMQMailQueuesRoutes implements Routes {

    private static final TaskRegistrationKey REPUBLISH_NOT_PROCESSED_MAILS_REGISTRATION_KEY = TaskRegistrationKey.of("RepublishNotProcessedMails");

    private final MailQueueFactory<RabbitMQMailQueue> mailQueueFactory;
    private final JsonTransformer jsonTransformer;
    private final TaskManager taskManager;
    private final Clock clock;
    private final Set<TaskRegistration> extraTasks;

    @Inject
    @SuppressWarnings("unchecked")
    @VisibleForTesting
    RabbitMQMailQueuesRoutes(MailQueueFactory<RabbitMQMailQueue> mailQueueFactory,
                             Clock clock, JsonTransformer jsonTransformer, TaskManager taskManager,
                             @Named("RabbitMQMailQueuesRoutes") Set<TaskRegistration> extraTasks) {
        this.mailQueueFactory = mailQueueFactory;
        this.clock = clock;
        this.jsonTransformer = jsonTransformer;
        this.taskManager = taskManager;
        this.extraTasks = extraTasks;
    }

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Override
    public void define(Service service) {
        republishNotProcessedMails(service);
    }

    public void republishNotProcessedMails(Service service) {
        service.post(BASE_URL + SEPARATOR + MAIL_QUEUE_NAME,
            builder()
                .register(REPUBLISH_NOT_PROCESSED_MAILS_REGISTRATION_KEY, this::republishNotProcessedMails)
                .registrations(extraTasks)
                .buildAsRoute(taskManager),
            jsonTransformer);
    }

    private Task republishNotProcessedMails(Request request) {
        MailQueueName mailQueue = MailQueueName.of(request.params(MAIL_QUEUE_NAME));
        getMailQueue(mailQueue).close();
        return new RepublishNotprocessedMailsTask(mailQueue,
            name -> mailQueueFactory
                .getQueue(name)
                .orElseThrow(() -> new ClearMailQueueTask.UnknownSerializedQueue(name.asString())),
            getOlderThan(request));
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
