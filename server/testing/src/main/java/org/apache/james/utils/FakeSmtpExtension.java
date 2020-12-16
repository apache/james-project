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

import org.apache.james.util.docker.Images;
import org.apache.james.util.docker.RateLimiters;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

import com.github.dockerjava.api.model.ContainerNetwork;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;


public class FakeSmtpExtension implements
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

    private static final int SMTP_PORT = 25;

    public static FakeSmtpExtension withSmtpPort(Integer smtpPort) {
        GenericContainer<?> container = fakeSmtpContainer()
                .withExposedPorts(smtpPort)
                .withCommand("node", "cli", "--listen", "80", "--smtp", smtpPort.toString());

        return new FakeSmtpExtension(container);
    }

    public static FakeSmtpExtension withDefaultPort() {
        return withSmtpPort(SMTP_PORT);
    }

    private static GenericContainer<?> fakeSmtpContainer() {
        return new GenericContainer<>(Images.FAKE_SMTP)
            .waitingFor(new HostPortWaitStrategy()
                .withRateLimiter(RateLimiters.TWENTIES_PER_SECOND)
                .withStartupTimeout(Duration.ofMinutes(1))
            );
    }

    private final GenericContainer<?> container;
    private final FakeSmtpExtension.FakeSmtp fakeSmtp;

    private FakeSmtpExtension(GenericContainer<?> container) {
        this.container = container;
        this.fakeSmtp = new FakeSmtpExtension.FakeSmtp(container);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        container.start();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        container.stop();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType().isAssignableFrom(FakeSmtp.class));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return fakeSmtp;
    }

    public static class FakeSmtp {
        public static final ResponseSpecification RESPONSE_SPECIFICATION = new ResponseSpecBuilder().build();

        private final GenericContainer<?> container;

        public FakeSmtp(GenericContainer<?> container) {
            this.container = container;
        }

        public String getContainerIp() {
            return container.getContainerInfo()
                    .getNetworkSettings()
                    .getNetworks()
                    .values()
                    .stream()
                    .map(ContainerNetwork::getIpAddress)
                    .findFirst()
                    .orElseThrow(IllegalStateException::new);
        }

        private String getHostIp() {
            return container.getContainerIpAddress();
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
                    .setBaseUri("http://" + getContainerIp())
                    .build();
        }

        public void clean() {
            clean(requestSpecification());
        }

        private static void clean(RequestSpecification requestSpecification) {
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

        public GenericContainer<?> getContainer() {
            return container;
        }
    }
}
