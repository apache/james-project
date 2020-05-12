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

package org.apache.james.utils;

import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

import org.apache.james.util.docker.DockerContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;

public class FakeSmtp implements TestRule {
    public static void clean(RequestSpecification requestSpecification) {
        given(requestSpecification, RESPONSE_SPECIFICATION)
            .get("/api/email")
            .jsonPath()
            .getList("id", String.class)
            .stream()
            .mapToInt(Integer::valueOf)
            .max()
            .ifPresent(id -> given(requestSpecification, RESPONSE_SPECIFICATION)
                .get("/api/email/purge/" + id));
    }

    public static FakeSmtp withSmtpPort(Integer smtpPort) {
        DockerContainer container = fakeSmtpContainer()
            .withExposedPorts(smtpPort)
            .withCommands("node", "cli", "--listen", "80", "--smtp", smtpPort.toString());

        return new FakeSmtp(container);
    }

    public static FakeSmtp withDefaultPort() {
        return withSmtpPort(SMTP_PORT);
    }

    private static DockerContainer fakeSmtpContainer() {
        return DockerContainer.fromName(Images.FAKE_SMTP)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy()
                .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND)
                .withStartupTimeout(Duration.ofMinutes(1))
            );
    }

    private static final int SMTP_PORT = 25;
    public static final ResponseSpecification RESPONSE_SPECIFICATION = new ResponseSpecBuilder().build();
    private final DockerContainer container;

    private FakeSmtp(DockerContainer container) {
        this.container = container;
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return container.apply(statement, description);
    }

    public void assertEmailReceived(Consumer<ValidatableResponse> expectations) {
        expectations.accept(
            given(requestSpecification(), RESPONSE_SPECIFICATION)
                .get("/api/email")
            .then()
                .statusCode(200));
    }

    private RequestSpecification requestSpecification() {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(80)
            .setBaseUri("http://" + container.getContainerIp())
            .build();
    }

    public DockerContainer getContainer() {
        return container;
    }

    public void clean() {
        clean(requestSpecification());
    }
}
