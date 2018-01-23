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

import static com.jayway.restassured.RestAssured.when;
import static com.jayway.restassured.config.EncoderConfig.encoderConfig;
import static com.jayway.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.webadmin.WebAdminServer.NO_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.apache.james.queue.api.Mails;
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
            .setPort(server.getPort().get().getValue())
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
    public void listMessagesShouldReturnEmptyListWhenNoMessages() {
        mailQueueFactory.createQueue(FIRST_QUEUE);

        when()
            .get(FIRST_QUEUE + "/messages")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", empty());
    }

    @Test
    public void listMessagesShouldReturnMessagesWhenSome() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        queue.enQueue(Mails.defaultMail().build());
        queue.enQueue(Mails.defaultMail().build());

        when()
            .get(FIRST_QUEUE + "/messages")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(2));
    }

    @Test
    public void listMessagesShouldReturnMessageDetailWhenSome() throws Exception {
        MemoryMailQueue queue = mailQueueFactory.createQueue(FIRST_QUEUE);
        FakeMail mail = Mails.defaultMail().build();
        queue.enQueue(mail);

        String firstMessage = "[0]";
        List<String> expectedRecipients = mail.getRecipients().stream()
                .map(MailAddress::asPrettyString)
                .collect(Guavate.toImmutableList());

        when()
            .get(FIRST_QUEUE + "/messages")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(".", hasSize(1))
            .body(firstMessage + ".name", equalTo(mail.getName()))
            .body(firstMessage + ".sender", equalTo(mail.getSender().asPrettyString()))
            .body(firstMessage + ".recipients", equalTo(expectedRecipients))
            .body(firstMessage + ".delayed", equalTo(false));
    }
}
