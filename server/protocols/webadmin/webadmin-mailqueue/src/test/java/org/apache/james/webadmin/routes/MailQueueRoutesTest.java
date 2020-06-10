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
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.MailAddress;
import org.apache.james.json.DTOConverter;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.Mails;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.service.ClearMailQueueTask;
import org.apache.james.webadmin.service.ClearMailQueueTaskAdditionalInformationDTO;
import org.apache.james.webadmin.service.DeleteMailsFromMailQueueTask;
import org.apache.james.webadmin.service.WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.steveash.guavate.Guavate;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

class MailQueueRoutesTest {

    static final MailQueueName FIRST_QUEUE = MailQueueName.of("first one");
    static final MailQueueName SECOND_QUEUE = MailQueueName.of("second one");
    static final MailQueueName THIRD_QUEUE = MailQueueName.of("third one");
    static final MailQueueName FOURTH_QUEUE = MailQueueName.of("fourth one");
    static final String SENDER_1_JAMES_ORG = "sender1@james.org";
    static final String SENDER_2_JAMES_ORG = "sender2@james.org";
    static final String RECIPIENT_JAMES_ORG = "recipient@james.org";
    static final String RECIPIENT_1_JAMES_ORG = "recipient1@james.org";
    static final String RECIPIENT_2_JAMES_ORG = "recipient2@james.org";
    static final String FAKE_MAIL_NAME_1 = "fake mail name 1";
    static final String FAKE_MAIL_NAME_2 = "fake mail name 2";
    static final String FAKE_MAIL_NAME_3 = "fake mail name 3";

    WebAdminServer webAdminServer;
    MemoryMailQueueFactory mailQueueFactory;


    WebAdminServer createServer(MemoryMailQueueFactory mailQueueFactory) {
        TaskManager taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();

        return WebAdminUtils.createWebAdminServer(
                new MailQueueRoutes(mailQueueFactory, jsonTransformer, taskManager),
                new TasksRoutes(taskManager, jsonTransformer,
                    DTOConverter.of(WebAdminDeleteMailsFromMailQueueTaskAdditionalInformationDTO.module(),
                        ClearMailQueueTaskAdditionalInformationDTO.module())))
            .start();
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setBasePath(MailQueueRoutes.BASE_URL)
            .setPort(server.getPort().getValue())
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .build();
    }

    @BeforeEach
    void setUp() {
        mailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        webAdminServer = createServer(mailQueueFactory);
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class ListMail {

        @Nested
        class DataValidation {
            @Test
            void listMailsShouldReturnBadRequestWhenLimitIsLessThanZero() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .param("limit", "-1")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void listMailsShouldReturnBadRequestWhenLimitEqualsToZero() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .param("limit", "0")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void listMailsShouldReturnBadRequestWhenLimitIsInvalid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .param("limit", "abc")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }
        }

        @Nested
        class HttpBodies {

            @Test
            void listAllMailQueuesShouldReturnEmptyWhenNone() {
                List<String> actual = when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

                assertThat(actual).isEmpty();
            }

            @Test
            void listAllMailQueuesShouldReturnSingleElementListWhenOnlyOneMailQueue() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                List<String> actual = when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

                assertThat(actual).containsOnly(FIRST_QUEUE.asString());
            }

            @Test
            void listAllMailQueuesShouldReturnListWhenSeveralMailQueues() {
                mailQueueFactory.createQueue(FIRST_QUEUE);
                mailQueueFactory.createQueue(SECOND_QUEUE);
                mailQueueFactory.createQueue(THIRD_QUEUE);
                mailQueueFactory.createQueue(FOURTH_QUEUE);

                List<String> actual = when()
                    .get()
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                .extract()
                    .body()
                    .jsonPath()
                    .getList(".");

                assertThat(actual)
                    .containsOnly(
                        FIRST_QUEUE.asString(),
                        SECOND_QUEUE.asString(),
                        THIRD_QUEUE.asString(),
                        FOURTH_QUEUE.asString());
            }

            @Test
            void listMailsShouldReturnEmptyListWhenNoMails() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", empty());
            }

            @Test
            public void listMailsShouldReturnMailsWhenSome() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                queue.enQueue(Mails.defaultMail().name("name").build());
                queue.enQueue(Mails.defaultMail().name("name").build());

                when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", hasSize(2));
            }

            @Test
            public void listMailsShouldReturnMailDetailsWhenSome() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail);

                String firstMail = "[0]";
                List<String> expectedRecipients = mail.getRecipients().stream()
                        .map(MailAddress::asString)
                        .collect(Guavate.toImmutableList());

                when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", hasSize(1))
                    .body(firstMail + ".name", equalTo(mail.getName()))
                    .body(firstMail + ".sender", equalTo(SENDER.asString()))
                    .body(firstMail + ".recipients", equalTo(expectedRecipients));
            }

            @Test
            public void listMailsShouldReturnEmptyWhenNoDelayedMailsAndAskFor() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail);

                given()
                    .param("delayed", "true")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", empty());
            }

            @Test
            public void listMailsShouldReturnCurrentMailsWhenMailsAndAskForNotDelayed() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail);

                given()
                    .param("delayed", "false")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", hasSize(1));
            }

            @Test
            public void listMailsShouldReturnDelayedMailsWhenAskFor() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail, 10, TimeUnit.MINUTES);

                given()
                    .param("delayed", "true")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", hasSize(1));
            }

            @Test
            public void listMailsShouldReturnOneMailWhenMailsAndAskForALimitOfOne() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail);
                queue.enQueue(mail);
                queue.enQueue(mail);

                given()
                    .param("limit", "1")
                .when()
                    .get(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .body(".", hasSize(1));
            }
        }
    }

    @Nested
    class GetMailQueue {

        @Test
        public void getMailQueueShouldReturnTheMailQueueDataWhenMailQueueExists() throws Exception {
            MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
            queue.enQueue(Mails.defaultMail().name("name").build());

            when()
                .get(FIRST_QUEUE.asString())
            .then()
                .statusCode(HttpStatus.OK_200)
                .body("name", equalTo(FIRST_QUEUE.asString()))
                .body("size", equalTo(1));
        }

        @Test
        void getMailQueueShouldReturnNotFoundWhenMailQueueDoesntExist() {
            when()
                .get(FIRST_QUEUE.asString())
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }
    }

    @Nested
    class ForceDelayedMailsDelivery {

        @Nested
        class DataValidation {

            @Test
            void forcingDelayedMailsDeliveryShouldReturnNoContent() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "true")
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                    .then()
                    .statusCode(HttpStatus.NO_CONTENT_204);
            }

            @Test
            void forcingDelayedMailsDeliveryForUnknownQueueShouldReturnNotFound() {
                given()
                    .queryParam("delayed", "true")
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch("unknown queue" + "/mails")
                    .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            void forcingDelayedMailsDeliveryRequiresDelayedParameter() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectFalseDelayedParam() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "false")
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectNonBooleanDelayedParam() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "wrong")
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectRequestWithoutBody() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "true")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectRequestWithoutDelayedParameter() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "true")
                    .body("{\"xx\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldAcceptRequestWithUnknownFields() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "true")
                    .body("{" +
                        "\"xx\": \"false\"," +
                        "\"delayed\": \"false\"" +
                        "}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectMalformedJsonPayload() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "true")
                    .body("{\"xx\":")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectTrueDelayedAttribute() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "false")
                    .body("{\"delayed\": \"true\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void forcingDelayedMailsDeliveryShouldRejectStringDelayedAttribute() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .queryParam("delayed", "false")
                    .body("{\"delayed\": \"string\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }
        }

        @Nested
        class SideEffects {
            @Test
            public void forcingDelayedMailsDeliveryShouldActuallyChangePropertyOnMails() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                FakeMail mail = Mails.defaultMail().name("name").build();
                queue.enQueue(mail, 10L, TimeUnit.MINUTES);
                queue.enQueue(mail, 10L, TimeUnit.MINUTES);
                queue.enQueue(mail);

                with()
                    .queryParam("delayed", "true")
                    .body("{\"delayed\": \"false\"}")
                .when()
                    .patch(FIRST_QUEUE.asString() + "/mails");

                assertThat(queue.browse())
                    .toIterable()
                    .extracting(ManageableMailQueue.MailQueueItemView::getNextDelivery)
                    .hasSize(3)
                    .allSatisfy((delivery) -> {
                        assertThat(delivery).isNotEmpty();
                        assertThat(delivery.get()).isBefore(ZonedDateTime.now());
                    });
            }
        }
    }

    @Nested
    class DeleteMail {

        @Nested
        class DataValidation {

            @Test
            void deleteMailsShouldReturnNotFoundWhenMailQueueDoesntExist() {
                when()
                    .delete(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.NOT_FOUND_404);
            }

            @Test
            void deleteMailsShouldReturnBadRequestWhenSenderIsInvalid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .param("sender", "123")
                .when()
                    .delete(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void deleteMailsShouldReturnBadRequestWhenRecipientIsInvalid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                given()
                    .param("recipient", "123")
                .when()
                    .delete(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void deleteMailsShouldReturnBadRequestWhenAllParametersAreGiven() {
                mailQueueFactory.createQueue(FIRST_QUEUE);
                given()
                    .param("sender", "sender@james.org")
                    .param("name", "mailName")
                    .param("recipient", "recipient@james.org")
                .when()
                    .delete(FIRST_QUEUE.asString() + "/mails")
                 .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }

            @Test
            void deleteMailsShouldReturnBadRequestWhenTwoParametersAreGiven() {
                mailQueueFactory.createQueue(FIRST_QUEUE);
                given()
                    .param("sender", "sender@james.org")
                    .param("name", "mailName")
                .when()
                    .delete(FIRST_QUEUE.asString() + "/mails")
                .then()
                    .statusCode(HttpStatus.BAD_REQUEST_400);
            }
        }

        @Nested
        class HttpBodies {

            @Test
            void deleteMailsTasksShouldCompleteWhenSenderIsValid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                String taskId = with()
                    .param("sender", SENDER_1_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));
            }

            @Test
            void deleteMailsShouldCompleteWhenNameIsValid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                String taskId = with()
                    .param("name", "mailName")
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));
            }

            @Test
            void deleteMailsShouldCompleteWhenRecipientIsValid() {
                mailQueueFactory.createQueue(FIRST_QUEUE);

                String taskId = with()
                    .param("recipient", RECIPIENT_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));
            }

            @Test
            public void deleteMailsTasksShouldHaveDetailsWhenSenderIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .sender(SENDER_1_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .sender(SENDER_2_JAMES_ORG)
                    .build());

                String taskId = with()
                    .param("sender", SENDER_1_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(DeleteMailsFromMailQueueTask.TYPE.asString()))
                    .body("additionalInformation.mailQueueName", is(FIRST_QUEUE.asString()))
                    .body("additionalInformation.initialCount", is(2))
                    .body("additionalInformation.remainingCount", is(1))
                    .body("additionalInformation.sender", is(SENDER_1_JAMES_ORG))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            public void deleteMailsTasksShouldHaveDetailsWhenNameIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .build());

                String taskId = with()
                    .param("name", FAKE_MAIL_NAME_1)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(DeleteMailsFromMailQueueTask.TYPE.asString()))
                    .body("additionalInformation.mailQueueName", is(FIRST_QUEUE.asString()))
                    .body("additionalInformation.initialCount", is(2))
                    .body("additionalInformation.remainingCount", is(1))
                    .body("additionalInformation.name", is(FAKE_MAIL_NAME_1))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }

            @Test
            public void deleteMailsTasksShouldHaveDetailsWhenRecipientIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .recipient(RECIPIENT_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .recipient(RECIPIENT_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .recipient(RECIPIENT_1_JAMES_ORG)
                    .build());

                String taskId = with()
                    .param("recipient", RECIPIENT_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"))
                    .body("taskId", is(notNullValue()))
                    .body("type", is(DeleteMailsFromMailQueueTask.TYPE.asString()))
                    .body("additionalInformation.mailQueueName", is(FIRST_QUEUE.asString()))
                    .body("additionalInformation.initialCount", is(3))
                    .body("additionalInformation.remainingCount", is(1))
                    .body("additionalInformation.recipient", is(RECIPIENT_JAMES_ORG))
                    .body("startedDate", is(notNullValue()))
                    .body("submitDate", is(notNullValue()))
                    .body("completedDate", is(notNullValue()));
            }
        }

        @Nested
        class SideEffects {

            @Test
            public void deleteMailsShouldDeleteMailsWhenSenderIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .sender(SENDER_1_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .sender(SENDER_2_JAMES_ORG)
                    .build());

                String taskId = with()
                    .param("sender", SENDER_1_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(queue.browse())
                    .toIterable()
                    .hasSize(1)
                    .first()
                    .satisfies(mailView -> assertThat(mailView.getMail().getName()).isEqualTo(FAKE_MAIL_NAME_2));
            }

            @Test
            public void deleteMailsShouldDeleteMailsWhenNameIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .build());

                String taskId = with()
                    .param("name", FAKE_MAIL_NAME_1)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(queue.browse())
                    .toIterable()
                    .hasSize(1)
                    .first()
                    .satisfies(mailView -> assertThat(mailView.getMail().getName()).isEqualTo(FAKE_MAIL_NAME_2));
            }

            @Test
            public void deleteMailsShouldDeleteMailsWhenRecipientIsGiven() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_1)
                    .recipient(RECIPIENT_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_2)
                    .recipient(RECIPIENT_1_JAMES_ORG)
                    .build());

                queue.enQueue(FakeMail.builder()
                    .name(FAKE_MAIL_NAME_3)
                    .recipient(RECIPIENT_2_JAMES_ORG)
                    .build());

                String taskId = with()
                    .param("recipient", RECIPIENT_JAMES_ORG)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                assertThat(queue.browse())
                    .toIterable()
                    .hasSize(2)
                    .extracting(ManageableMailQueue.MailQueueItemView::getMail)
                    .extracting(Mail::getName)
                    .contains(FAKE_MAIL_NAME_2, FAKE_MAIL_NAME_3);
            }

            @Test
            public void deleteMailsShouldDeleteMailsWhenTheyAreMatching() throws Exception {
                MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
                String recipient = "recipient@james.org";
                queue.enQueue(Mails.defaultMail()
                        .name("name")
                        .recipient(recipient)
                        .build());
                queue.enQueue(Mails.defaultMail().name("name").build());
                queue.enQueue(Mails.defaultMail().name("name").build());

                String taskId = with()
                    .param("recipient", recipient)
                .delete(FIRST_QUEUE.asString() + "/mails")
                    .jsonPath()
                    .getString("taskId");

                given()
                    .basePath(TasksRoutes.BASE)
                .when()
                    .get(taskId + "/await")
                .then()
                    .body("status", is("completed"));

                MailAddress deletedRecipientMailAddress = new MailAddress(recipient);
                assertThat(queue.browse())
                    .toIterable()
                    .hasSize(2)
                    .allSatisfy((ManageableMailQueue.MailQueueItemView item) -> {
                        assertThat(item.getMail().getRecipients()).doesNotContain(deletedRecipientMailAddress);
                    });
            }
        }
    }

    @Nested
    class ClearMail {

        @Test
        void clearMailQueueShouldReturnNotFoundWhenMailQueueDoesNotExist() {
            mailQueueFactory.createQueue(FIRST_QUEUE);

            when()
                .delete(SECOND_QUEUE.asString() + "/mails")
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void clearMailQueueShouldCompleteWhenNoQueryParameters() {
            mailQueueFactory.createQueue(FIRST_QUEUE);

            String taskId = with()
                .delete(FIRST_QUEUE.asString() + "/mails")
                .jsonPath()
                .getString("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"));
        }

        @Test
        public void clearMailQueueShouldHaveDetailsWhenNoQueryParameters() throws Exception {
            MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_1)
                .build());

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_2)
                .build());

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_3)
                .build());

            String taskId = with()
                .delete(FIRST_QUEUE.asString() + "/mails")
                .jsonPath()
                .getString("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"))
                .body("taskId", is(notNullValue()))
                .body("type", is(ClearMailQueueTask.TYPE.asString()))
                .body("additionalInformation.mailQueueName", is(FIRST_QUEUE.asString()))
                .body("additionalInformation.initialCount", is(3))
                .body("additionalInformation.remainingCount", is(0))
                .body("startedDate", is(notNullValue()))
                .body("submitDate", is(notNullValue()))
                .body("completedDate", is(notNullValue()));
        }

        @Test
        void clearMailQueueShouldDeleteAllMailsInQueueWhenNoQueryParameters() throws Exception {
            MemoryMailQueueFactory.MemoryCacheableMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_1)
                .build());

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_2)
                .build());

            queue.enQueue(FakeMail.builder()
                .name(FAKE_MAIL_NAME_3)
                .build());

            String taskId = with()
                .delete(FIRST_QUEUE.asString() + "/mails")
                .jsonPath()
                .getString("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await")
            .then()
                .body("status", is("completed"));

            assertThat(queue.getSize()).isEqualTo(0);
        }
    }
}
