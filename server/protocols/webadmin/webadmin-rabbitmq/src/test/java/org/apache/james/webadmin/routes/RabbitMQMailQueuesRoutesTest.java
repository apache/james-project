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

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.json.DTOConverter;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueue;
import org.apache.james.queue.rabbitmq.RabbitMQMailQueueFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

class RabbitMQMailQueuesRoutesTest {
    static final ZonedDateTime DATE = ZonedDateTime.parse("2015-10-30T14:12:00Z");

    WebAdminServer webAdminServer;
    MailQueueFactory mailQueueFactory;
    Clock clock;

    WebAdminServer createServer(MailQueueFactory mailQueueFactory) {
        TaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();
        clock = UpdatableTickingClock.fixed(DATE.toInstant(), ZoneOffset.UTC);
        return WebAdminUtils.createWebAdminServer(
                new RabbitMQMailQueuesRoutes(mailQueueFactory, clock, jsonTransformer, taskManager,
                    ImmutableSet.of()),
                new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of()))
            .start();
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setBasePath("/")
            .setPort(server.getPort().getValue())
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .build();
    }

    @BeforeEach
    void setUp() {
        mailQueueFactory = mock(RabbitMQMailQueueFactory.class);
        webAdminServer = createServer(mailQueueFactory);
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void triggeringARepublishNotProcessedMailsShouldCreateATask() {
        when(mailQueueFactory.getQueue(any())).thenReturn(Optional.of(mock(RabbitMQMailQueue.class)));
        given()
            .queryParam("action", "RepublishNotProcessedMails")
            .queryParam("olderThan", "1d")
        .when()
            .post(MailQueueRoutes.BASE_URL + "/spooler")
        .then()
            .statusCode(HttpStatus.CREATED_201);

        given()
        .when()
            .get("/tasks")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("", hasSize(1));
    }

    @Test
    void triggeringARepublishNotProcessedMailsWhenTheQueueHasNotBeenInitializedShouldFail() {
        when(mailQueueFactory.getQueue(any())).thenReturn(Optional.empty());
        given()
            .queryParam("action", "RepublishNotProcessedMails")
            .queryParam("olderThan", "1d")
        .when()
            .post(MailQueueRoutes.BASE_URL + "/spooler")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .body("message", containsString("MailQueueName{value=spooler} can not be found"));
    }

    @Test
    void triggeringARepublishNotProcessedMailsWithAnInvalidOlderThanShouldFail() {
        when(mailQueueFactory.getQueue(any())).thenReturn(Optional.of(mock(RabbitMQMailQueue.class)));
        given()
            .queryParam("action", "RepublishNotProcessedMails")
            .queryParam("olderThan", "invalidValue")
        .when()
            .post(MailQueueRoutes.BASE_URL + "/spooler")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("Invalid olderThan"))
            .body("details", containsString("Supplied value do not follow the unit format (number optionally suffixed with a string representing the unit"));
    }

}
