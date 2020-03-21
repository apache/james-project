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

package org.apache.james.mock.smtp.server;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static io.netty.handler.codec.http.HttpResponseStatus.NO_CONTENT;
import static org.apache.james.mock.smtp.server.Fixture.JSON_BEHAVIORS;
import static org.apache.james.mock.smtp.server.Fixture.JSON_MAIL;
import static org.apache.james.mock.smtp.server.Fixture.JSON_MAILS_LIST;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;

import org.apache.james.mock.smtp.server.Fixture.MailsFixutre;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;

class HTTPConfigurationServerTest {
    private HTTPConfigurationServer.RunningStage server;

    @Nested
    class SMTPBehaviorsTest {
        @BeforeEach
        void setUp() {
            server = HTTPConfigurationServer.onRandomPort(new SMTPBehaviorRepository(), new ReceivedMailRepository())
                .start();

            RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getPort().getValue())
                .setBasePath("/smtpBehaviors")
                .build();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void getShouldReturnEmptyByDefault() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }

        @Test
        void getShouldReturnPreviouslyStoredData() {
            with().body(JSON_BEHAVIORS).put();

            String response = when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .extract()
                    .asString();

            assertThatJson(response)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER))
                .isEqualTo(JSON_BEHAVIORS);
        }

        @Test
        void getShouldReturnEmptyAfterDelete() {
            with().body(JSON_BEHAVIORS).put();

            with().delete();

            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }

        @Test
        void putShouldReturnNoContent() {
            given()
                .body(JSON_BEHAVIORS)
            .when()
                .put()
            .then()
                .statusCode(NO_CONTENT.code());
        }

        @Test
        void putShouldBeIdempotent() {
            with().body(JSON_BEHAVIORS).put();

            given()
                .body(JSON_BEHAVIORS)
            .when()
                .put()
            .then()
                .statusCode(NO_CONTENT.code());
        }

        @Test
        void deleteShouldReturnNoContent() {
            when()
                .delete()
            .then()
                .statusCode(NO_CONTENT.code());
        }
    }

    @Nested
    class SMTPMailsTest {
        private ReceivedMailRepository mailRepository;

        @BeforeEach
        void setUp() {
            mailRepository = new ReceivedMailRepository();

            server = HTTPConfigurationServer.onRandomPort(new SMTPBehaviorRepository(), mailRepository)
                .start();

            RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(server.getPort().getValue())
                .setBasePath("/smtpMails")
                .build();
        }

        @AfterEach
        void tearDown() {
            server.stop();
        }

        @Test
        void getShouldReturnEmptyByDefault() {
            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }

        @Test
        void getShouldReturnPreviouslyStoredData() {
            mailRepository.store(MailsFixutre.MAIL_1);

            String response = when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .extract()
                    .asString();

            assertThatJson(response)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER))
                .isEqualTo(JSON_MAIL);
        }

        @Test
        void getShouldReturnMultipleEmails() {
            mailRepository.store(MailsFixutre.MAIL_1);
            mailRepository.store(MailsFixutre.MAIL_2);

            String response = when()
                    .get()
                .then()
                    .contentType(ContentType.JSON)
                    .extract()
                    .asString();

            assertThatJson(response)
                .withOptions(new Options(Option.TREATING_NULL_AS_ABSENT, Option.IGNORING_ARRAY_ORDER))
                .isEqualTo(JSON_MAILS_LIST);
        }

        @Test
        void getShouldNotReturnClearedEmails() {
            mailRepository.store(MailsFixutre.MAIL_1);
            mailRepository.store(MailsFixutre.MAIL_2);

            with()
                .delete();

            when()
                .get()
            .then()
                .body(".", hasSize(0));
        }

        @Test
        void deleteShouldReturnNoContent() {
            when()
                .delete()
            .then()
                .statusCode(NO_CONTENT.code());
        }

        @Test
        void getShouldReturnEmptyAfterClear() {
            mailRepository.store(MailsFixutre.MAIL_1);

            mailRepository.clear();

            when()
                .get()
            .then()
                .contentType(ContentType.JSON)
                .body(".", hasSize(0));
        }
    }
}