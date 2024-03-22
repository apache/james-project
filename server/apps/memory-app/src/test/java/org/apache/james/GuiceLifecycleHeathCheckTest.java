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

package org.apache.james;

import static io.restassured.RestAssured.when;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminServer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

class GuiceLifecycleHeathCheckTest {
    private static JamesServerBuilder extensionBuilder() {
        return new JamesServerBuilder<MemoryJamesConfiguration>(tmpDir ->
            MemoryJamesConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .build())
            .server(configuration -> MemoryJamesServerMain.createServer(configuration)
                .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                    .toInstance(WebAdminConfiguration.TEST_CONFIGURATION)));
    }

    private static void configureRequestSpecification(GuiceJamesServer server) {
        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(webAdminGuiceProbe.getWebAdminPort().getValue())
                .build();
    }

    @Nested
    class Healthy {
        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder().build();

        @Test
        void startedJamesServerShouldBeHealthy(GuiceJamesServer server) {
            configureRequestSpecification(server);

            when()
                .get("/healthcheck")
                .then()
                .statusCode(HttpStatus.OK_200);
        }
    }

    static class DestroyedBeforeWebAdmin {
        WebAdminServer webAdminServer;
        CountDownLatch latch;

        @Inject
        DestroyedBeforeWebAdmin(WebAdminServer webAdminServer, CountDownLatch latch) {
            this.webAdminServer = webAdminServer;
            this.latch = latch;
        }

        @PreDestroy
        void cleanup() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Nested
    class Unhealthy {
        CountDownLatch latch = new CountDownLatch(1);

        @RegisterExtension
        JamesServerExtension jamesServerExtension = extensionBuilder()
            .overrideServerModule(binder -> binder.bind(CountDownLatch.class).toInstance(latch))
            .overrideServerModule(binder -> binder.bind(DestroyedBeforeWebAdmin.class).asEagerSingleton())
            .build();

        @Test
        void stoppingJamesServerShouldBeUnhealthy(GuiceJamesServer server) throws Exception {
            Sinks.Empty<Object> sink = Sinks.empty();
            try {
                configureRequestSpecification(server);

                Mono.fromRunnable(server::stop)
                    .publishOn(Schedulers.boundedElastic())
                    .subscribe(r -> {}, e -> {}, () -> sink.emitEmpty(FAIL_FAST));

                when()
                    .get("/healthcheck")
                    .then()
                    .statusCode(HttpStatus.SERVICE_UNAVAILABLE_503);
            } finally {
                latch.countDown();
                sink.asMono().block();
            }
        }
    }
}
