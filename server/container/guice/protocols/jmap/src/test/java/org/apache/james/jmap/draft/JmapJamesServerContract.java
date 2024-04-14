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
package org.apache.james.jmap.draft;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;

import org.apache.james.GuiceJamesServer;
import org.apache.james.jmap.JmapGuiceProbe;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public interface JmapJamesServerContract {
    String JAMES_SERVER_HOST = "127.0.0.1";

    @Test
    default void connectJMAPServerShouldFailWhenUnAuthenticatedRequest(GuiceJamesServer server) {
        RestAssured.requestSpecification = requestSpec(server);
        given()
            .body("{\"badAttributeName\": \"value\"}")
        .when()
            .post("/jmap")
        .then()
            .statusCode(401);
    }

    static RequestSpecification requestSpec(GuiceJamesServer server) {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept("application/json; jmapVersion=rfc-8621")
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();
    }
}
