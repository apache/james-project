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
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.jmap.draft.JmapGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.inject.Module;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

public interface JmapJamesServerContract {
    Module DOMAIN_LIST_CONFIGURATION_MODULE = binder -> binder.bind(DomainListConfiguration.class)
        .toInstance(DomainListConfiguration.builder()
            .autoDetect(true)
            .autoDetectIp(false)
            .build());
    String JAMES_SERVER_HOST = "127.0.0.1";

    @BeforeEach
    default void setup(GuiceJamesServer server) throws Exception {

        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(JmapGuiceProbe.class).getJmapPort().getValue())
            .build();
    }

    @Test
    default void connectJMAPServerShouldRespondBadRequest() {
        given()
            .body("{\"badAttributeName\": \"value\"}")
        .when()
            .post("/authentication")
        .then()
            .statusCode(400);
    }
}
