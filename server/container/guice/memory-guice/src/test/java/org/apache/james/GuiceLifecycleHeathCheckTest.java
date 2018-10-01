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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.store.search.PDFTextExtractor;
import org.apache.james.modules.TestJMAPServerModule;
import org.apache.james.task.Task;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class GuiceLifecycleHeathCheckTest {
    private static final int LIMIT_TO_10_MESSAGES = 10;

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
        JamesServerExtension jamesServerExtension = new JamesServerExtensionBuilder()
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION)))
            .build();

        @Test
        void startedJamesServerShouldBeHealthy(GuiceJamesServer server) {
            configureRequestSpecification(server);

            when()
                .get("/healthcheck")
                .then()
                .statusCode(HttpStatus.OK_200);
        }
    }

    @Nested
    class Unhealthy {
        CountDownLatch latch = new CountDownLatch(1);
        CleanupTasksPerformer.CleanupTask awaitCleanupTask = () -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Task.Result.COMPLETED;
        };

        @RegisterExtension
        JamesServerExtension jamesServerExtension = new JamesServerExtensionBuilder()
            .server(configuration -> GuiceJamesServer.forConfiguration(configuration)
                .combineWith(MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE)
                .overrideWith(new TestJMAPServerModule(LIMIT_TO_10_MESSAGES))
                .overrideWith(binder -> binder.bind(TextExtractor.class).to(PDFTextExtractor.class))
                .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION))
                .overrideWith(binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                    .addBinding()
                    .toInstance(awaitCleanupTask)))
            .build();

        @Test
        void stoppingJamesServerShouldBeUnhealthy(GuiceJamesServer server) {
            CompletableFuture<Void> stopCompletedFuture = CompletableFuture.completedFuture(null);

            try {
                configureRequestSpecification(server);

                stopCompletedFuture = CompletableFuture.runAsync(server::stop);

                when()
                    .get("/healthcheck")
                    .then()
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500);
            } finally {
                latch.countDown();
                stopCompletedFuture.join();
            }
        }
    }
}
