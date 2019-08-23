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
import static org.apache.james.mock.smtp.server.Fixture.JSON_BEHAVIORS;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;

import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;

class HTTPConfigurationServerTest {
    private HTTPConfigurationServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HTTPConfigurationServer.onRandomPort(new SMTPBehaviorRepository());
        server.start();

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getPort().getValue())
            .setBasePath("/")
            .setBasePath("/smtpBehaviors")
            .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @Test
    void getShouldReturnEmptyByDefault() {
        when()
            .get()
        .then()
            .body(".", hasSize(0));
    }

    @Test
    void getShouldReturnPreviouslyStoredData() {
        with().body(JSON_BEHAVIORS).put();

        String response = when()
            .get()
            .then()
            .extract().asString();

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
            .body(".", hasSize(0));
    }

    @Test
    void putShouldReturnNoContent() {
        given()
            .body(JSON_BEHAVIORS)
        .when()
            .put()
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void putShouldBeIdempotent() {
        with().body(JSON_BEHAVIORS).put();

        given()
            .body(JSON_BEHAVIORS)
        .when()
            .put()
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void deleteShouldReturnNoContent() {
        when()
            .delete()
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }
}