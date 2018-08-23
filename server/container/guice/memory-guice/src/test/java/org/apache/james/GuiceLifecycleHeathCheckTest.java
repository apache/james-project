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

import org.apache.james.task.Task;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import com.google.inject.multibindings.Multibinder;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

public class GuiceLifecycleHeathCheckTest {
    private void configureRequestSpecification() {
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
                .setPort(webAdminGuiceProbe.getWebAdminPort().getValue())
                .build();
    }

    @Rule
    public MemoryJmapTestRule memoryJmap = new MemoryJmapTestRule();
    private GuiceJamesServer guiceJamesServer;

    @After
    public void cleanUp() {
        if (guiceJamesServer != null) {
            guiceJamesServer.stop();
        }
    }

    @Test
    public void startedJamesServerShouldBeHealthy() throws Exception {
        guiceJamesServer = memoryJmap.jmapServer()
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                .toInstance(WebAdminConfiguration.TEST_CONFIGURATION));


        guiceJamesServer.start();
        configureRequestSpecification();

        when()
            .get("/healthcheck")
        .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    public void stoppingJamesServerShouldBeUnhealthy() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CleanupTasksPerformer.CleanupTask awaitCleanupTask = () -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return Task.Result.COMPLETED;
        };
        CompletableFuture<Void> stopCompletedFuture = CompletableFuture.completedFuture(null);

        try {
            guiceJamesServer = memoryJmap.jmapServer(
                binder -> Multibinder.newSetBinder(binder, CleanupTasksPerformer.CleanupTask.class)
                    .addBinding()
                    .toInstance(awaitCleanupTask))
                .overrideWith(binder -> binder.bind(WebAdminConfiguration.class)
                    .toInstance(WebAdminConfiguration.TEST_CONFIGURATION));

            guiceJamesServer.start();
            configureRequestSpecification();

            stopCompletedFuture = CompletableFuture.runAsync(() -> guiceJamesServer.stop());

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
