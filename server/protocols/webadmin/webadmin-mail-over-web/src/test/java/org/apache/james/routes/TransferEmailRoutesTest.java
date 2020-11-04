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

package org.apache.james.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.apache.james.queue.memory.MemoryMailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TransferEmailRoutes;
import org.apache.mailet.Mail;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Flux;

public class TransferEmailRoutesTest {

    private WebAdminServer webAdminServer;

    private MailQueue mailQueue;

    private WebAdminServer createServer() {
        MailQueueFactory mailQueueFactory = new MemoryMailQueueFactory(new RawMailQueueItemDecoratorFactory());
        WebAdminServer webAdminServer = WebAdminUtils.createWebAdminServer(new TransferEmailRoutes(mailQueueFactory)).start();
        mailQueue = ((MailQueue) mailQueueFactory.getQueue(MailQueueFactory.SPOOL).get());
        return webAdminServer;
    }

    @BeforeEach
    void setup() {
        webAdminServer = createServer();
        RestAssured.requestSpecification = buildRequestSpecification(webAdminServer);
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    RequestSpecification buildRequestSpecification(WebAdminServer server) {
        return new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setBasePath(TransferEmailRoutes.BASE_URL)
                .setPort(server.getPort().getValue())
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .build();
    }


    @Test
    public void whenToIsMissingInRequestThenRequestFails() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message-without-tos.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);

        Assertions.assertThrows(IllegalStateException.class,
                () -> {
                    MailQueue.MailQueueItem mailQueueItem = ((Flux<MailQueue.MailQueueItem>) mailQueue.deQueue()).blockFirst(Duration.of(100, ChronoUnit.MILLIS));
                });
    }

    @Test
    public void statusCode201ReturnedWhenSendingMailWithAllRequiredFields() {
        given()
                .body(ClassLoaderUtils.getSystemResourceAsString("message/rfc822/message.eml"))
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(201);

        MailQueue.MailQueueItem mailQueueItem = ((Flux<MailQueue.MailQueueItem>) mailQueue.deQueue()).blockFirst(Duration.of(100, ChronoUnit.MILLIS));
        Mail mail = mailQueueItem.getMail();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Throwing.supplier(() -> mail.getMessage().getMessageID()).get()).isEqualTo("<20050430192829.0489.mlemos@acm.org>");
            softly.assertThat(Throwing.supplier(() -> mail.getSender().asPrettyString()).get()).isEqualTo("<mlemos@acm.org>");
            softly.assertThat(Throwing.supplier(() -> mail.getMessage().getHeader("X-Mailer")).get()[0]).isEqualTo("http://www.phpclasses.org/mimemessage $Revision: 1.63 $ (mail)");
        });
    }

    @Test
    public void requestFailsOnEmptyBody() {
        given()
                .body("")
                .when()
                .post()
                .then()
                .assertThat()
                .statusCode(500);

        Assertions.assertThrows(IllegalStateException.class,
                () -> {
                    MailQueue.MailQueueItem mailQueueItem = ((Flux<MailQueue.MailQueueItem>) mailQueue.deQueue()).blockFirst(Duration.of(100, ChronoUnit.MILLIS));
                });
    }
}
