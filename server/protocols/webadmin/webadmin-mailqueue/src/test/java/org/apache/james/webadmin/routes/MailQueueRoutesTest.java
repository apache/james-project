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

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.RestAssured.with;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.Mails;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory.MemoryMailQueue;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.apache.mailet.base.test.FakeMail;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.steveash.guavate.Guavate;
import com.jayway.restassured.RestAssured;
import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;

public class MailQueueRoutesTest {

    static final String FIRST_QUEUE = "first one";
    static final String SECOND_QUEUE = "second one";
    static final String THIRD_QUEUE = "third one";
    static final String FOURTH_QUEUE = "fourth one";
    WebAdminServer webAdminServer;
    MemoryMailQueueFactory mailQueueFactory;


    WebAdminServer createServer(MemoryMailQueueFactory mailQueueFactory) throws Exception {
        WebAdminServer server = WebAdminUtils.createWebAdminServer(
            new NoopMetricFactory(),
            new MailQueueRoutes(mailQueueFactory, new JsonTransformer()));
        server.configure(NO_CONFIGURATION);
        server.await();
        return server;
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

    @Before
    public void setUp() throws Exception {
        mailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        webAdminServer = createServer(mailQueueFactory);
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @After
    public void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    public void listAllMailQueuesShouldReturnEmptyWhenNone() {
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
    public void listAllMailQueuesShouldReturnSingleElementListWhenOnlyOneMailQueue() {
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

        assertThat(actual).containsOnly(FIRST_QUEUE);
    }

    @Test
    public void listAllMailQueuesShouldReturnListWhenSeveralMailQueues() {
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

        assertThat(actual).containsOnly(FIRST_QUEUE, SECOND_QUEUE, THIRD_QUEUE, FOURTH_QUEUE);
    }

    @Test
    public void getMailQueueShouldReturnTheMailQueueDataWhenMailQueueExists() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        queue.enQueue(Mails.defaultMail().build());

        when()
            .get(FIRST_QUEUE)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("name", equalTo(FIRST_QUEUE))
            .body("size", equalTo(1));
    }

    @Test
    public void getMailQueueShouldReturnNotFoundWhenMailQueueDoesntExist() {
        when()
            .get(FIRST_QUEUE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void listMailsShouldReturnEmptyListWhenNoMails() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", empty());
    }

    @Test
    public void listMailsShouldReturnMailsWhenSome() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        queue.enQueue(Mails.defaultMail().build());
        queue.enQueue(Mails.defaultMail().build());

        when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(2));
    }

    @Test
    public void listMailsShouldReturnMailDetailsWhenSome() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail);

        String firstMail = "[0]";
        List<String> expectedRecipients = mail.getRecipients().stream()
                .map(MailAddress::asString)
                .collect(Guavate.toImmutableList());

        when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1))
            .body(firstMail + ".name", equalTo(mail.getName()))
            .body(firstMail + ".sender", equalTo(mail.getSender().asString()))
            .body(firstMail + ".recipients", equalTo(expectedRecipients));
    }

    @Test
    public void listMailsShouldReturnEmptyWhenNoDelayedMailsAndAskFor() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail);

        given()
            .param("delayed", "true")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", empty());
    }

    @Test
    public void listMailsShouldReturnCurrentMailsWhenMailsAndAskForNotDelayed() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail);

        given()
            .param("delayed", "false")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    public void listMailsShouldReturnDelayedMailsWhenAskFor() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail, 10, TimeUnit.MINUTES);

        given()
            .param("delayed", "true")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    public void listMailsShouldReturnOneMailWhenMailsAndAskForALimitOfOne() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail);
        queue.enQueue(mail);
        queue.enQueue(mail);

        given()
            .param("limit", "1")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1));
    }

    @Test
    public void listMailsShouldReturnBadRequestWhenLimitIsLessThanZero() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("limit", "-1")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void listMailsShouldReturnBadRequestWhenLimitEqualsToZero() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("limit", "0")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void listMailsShouldReturnBadRequestWhenLimitIsInvalid() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("limit", "abc")
        .when()
            .get(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldReturnNotFoundWhenMailQueueDoesntExist() {
        when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void deleteMailsShouldReturnBadRequestWhenSenderIsInvalid() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("sender", "123")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldReturnBadRequestWhenRecipientIsInvalid() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("recipient", "123")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldReturnNoContentWhenSenderIsValid() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("sender", "sender@james.org")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deleteMailsShouldReturnNoContentWhenNameIsValid() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("name", "mailName")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deleteMailsShouldReturnNoContentWhenRecipientIsValid() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .param("recipient", "recipient@james.org")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void deleteMailsShouldReturnBadRequestWhenNoQueryParameters() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldReturnNoContent() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "true")
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
            .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    public void forcingDelayedMailsDeliveryForUnknownQueueShouldReturnNotFound() throws Exception {
        given()
            .queryParam("delayed", "true")
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch("unknown queue" + "/mails")
            .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    public void forcingDelayedMailsDeliveryRequiresDelayedParameter() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectFalseDelayedParam() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "false")
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectNonBooleanDelayedParam() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "wrong")
            .body("{\"delayed\": \"false\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectRequestWithoutBody() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "true")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldDeleteMailsWhenSenderIsGiven() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        String sender = "sender@james.org";
        queue.enQueue(Mails.defaultMail()
                .sender(sender)
                .build());

        given()
            .param("sender", sender)
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(queue.browse()).isEmpty();
    }

    @Test
    public void deleteMailsShouldDeleteMailsWhenNameIsGiven() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        String name = "mailName";
        queue.enQueue(Mails.defaultMail()
                .name(name)
                .build());

        given()
            .param("name", name)
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(queue.browse()).isEmpty();
    }

    @Test
    public void deleteMailsShouldDeleteMailsWhenRecipientIsGiven() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        String recipient = "recipient@james.org";
        queue.enQueue(Mails.defaultMail()
                .recipient(recipient)
                .build());

        given()
            .param("recipient", recipient)
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(queue.browse()).isEmpty();
    }

    @Test
    public void deleteMailsShouldReturnBadRequestWhenAllParametersAreGiven() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);
        given()
            .param("sender", "sender@james.org")
            .param("name", "mailName")
            .param("recipient", "recipient@james.org")
        .when()
            .delete(FIRST_QUEUE + "/mails")
         .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectRequestWithoutDelayedParameter() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "true")
            .body("{\"xx\": \"false\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldAcceptRequestWithUnknownFields() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "true")
            .body("{" +
                "\"xx\": \"false\"," +
                "\"delayed\": \"false\"" +
                "}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectMalformedJsonPayload() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "true")
            .body("{\"xx\":")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectTrueDelayedAttribute() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "false")
            .body("{\"delayed\": \"true\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldReturnBadRequestWhenTwoParametersAreGiven() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);
        given()
            .param("sender", "sender@james.org")
            .param("name", "mailName")
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldRejectStringDelayedAttribute() throws Exception {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        given()
            .queryParam("delayed", "false")
            .body("{\"delayed\": \"string\"}")
        .when()
            .patch(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    public void deleteMailsShouldDeleteMailsWhenTheyAreMatching() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        String recipient = "recipient@james.org";
        queue.enQueue(Mails.defaultMail()
                .recipient(recipient)
                .build());
        queue.enQueue(Mails.defaultMail().build());
        queue.enQueue(Mails.defaultMail().build());

        given()
            .param("recipient", recipient)
        .when()
            .delete(FIRST_QUEUE + "/mails")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        MailAddress deletedRecipientMailAddress = new MailAddress(recipient);
        assertThat(queue.browse())
            .hasSize(2)
            .allSatisfy((ManageableMailQueue.MailQueueItemView item) -> {
                assertThat(item.getMail().getRecipients()).doesNotContain(deletedRecipientMailAddress);
            });
    }

    @Test
    public void forcingDelayedMailsDeliveryShouldActuallyChangePropertyOnMails() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail, 10L, TimeUnit.MINUTES);
        queue.enQueue(mail, 10L, TimeUnit.MINUTES);
        queue.enQueue(mail);

        with()
            .queryParam("delayed", "true")
            .body("{\"delayed\": \"false\"}")
        .then()
            .patch(FIRST_QUEUE + "/mails");

        assertThat(queue.browse())
            .extracting(ManageableMailQueue.MailQueueItemView::getNextDelivery)
            .hasSize(3)
            .allSatisfy((delivery) -> {
                assertThat(delivery).isNotEmpty();
                assertThat(delivery.get()).isBefore(ZonedDateTime.now());
            });
    }
}
