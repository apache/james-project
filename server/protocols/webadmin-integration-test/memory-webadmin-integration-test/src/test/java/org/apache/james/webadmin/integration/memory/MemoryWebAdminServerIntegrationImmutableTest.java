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

package org.apache.james.webadmin.integration.memory;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.JamesServerExtension.Lifecycle.PER_CLASS;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.MemoryJamesConfiguration;
import org.apache.james.MemoryJamesServerMain;
import org.apache.james.webadmin.integration.WebAdminServerIntegrationImmutableTest;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MemoryWebAdminServerIntegrationImmutableTest extends WebAdminServerIntegrationImmutableTest {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
        MemoryJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(MemoryJamesServerMain::createServer)
        .lifeCycle(PER_CLASS)
        .build();

    @Test
    void healthCheckComponentsList() {
        List<String> listComponentNames =
            when()
                .get(HealthCheckRoutes.HEALTHCHECK)
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .jsonPath()
                .getList("checks.componentName", String.class);

        assertThat(listComponentNames).containsOnly("Guice application lifecycle", "MailReceptionCheck",
            "EventDeadLettersHealthCheck", "EmptyErrorMailRepository", "MessageFastViewProjection");
    }

    @Test
    void testImapRestart() {
        given()
            .queryParam("restart")
            .post("imap")
        .then()
            .statusCode(200);
    }

    @Test
    void testImapRestartShouldBeIndempotent() {
        with()
            .queryParam("restart")
            .post("imap");

        given()
            .queryParam("restart")
            .post("imap")
        .then()
            .statusCode(200);
    }
}