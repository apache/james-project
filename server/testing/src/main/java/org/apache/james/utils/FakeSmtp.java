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
import java.util.function.Function;

import org.apache.james.util.docker.DockerGenericContainer;
import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.awaitility.core.ConditionFactory;
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

    public static FakeSmtp withSmtpPort(Integer smtpPort) {
        DockerGenericContainer container = fakeSmtpContainer()
            .withCommands("node", "cli", "--listen", "80", "--smtp", smtpPort.toString());

        return new FakeSmtp(container, smtpPort);
    }

    private static DockerGenericContainer fakeSmtpContainer() {
        return new DockerGenericContainer(Images.FAKE_SMTP)
            .withAffinityToContainer()
            .waitingFor(new HostPortWaitStrategy()
            .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND));
    }

    private static final int SMTP_PORT = 25;
    private static final ResponseSpecification RESPONSE_SPECIFICATION = new ResponseSpecBuilder().build();
    private final DockerGenericContainer container;
    private final Integer smtpPort;

    public FakeSmtp() {
        this(fakeSmtpContainer().withExposedPorts(SMTP_PORT), SMTP_PORT);
    }

    private FakeSmtp(DockerGenericContainer container, Integer smtpPort) {
        this.smtpPort = smtpPort;
        this.container = container;
    }

    @Override
    public Statement apply(Statement statement, Description description) {
        return container.apply(statement, description);
    }

    public void awaitStarted(ConditionFactory calmyAwait) {
        calmyAwait.until(() -> container.tryConnect(smtpPort));
    }

    public void assertEmailReceived(Function<ValidatableResponse, ValidatableResponse> expectations) {
        expectations.apply(
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

    public DockerGenericContainer getContainer() {
        return container;
    }

    public void clean() {
        given(requestSpecification(), RESPONSE_SPECIFICATION)
            .get("/api/email")
            .jsonPath()
            .getList("id", String.class)
            .stream()
            .mapToInt(Integer::valueOf)
            .max()
            .ifPresent(id -> given(requestSpecification(), RESPONSE_SPECIFICATION)
                .get("/api/email/purge/" + id));
    }
}
