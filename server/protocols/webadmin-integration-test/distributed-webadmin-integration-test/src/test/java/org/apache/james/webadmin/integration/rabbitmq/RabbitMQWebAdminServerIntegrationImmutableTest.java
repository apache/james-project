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

package org.apache.james.webadmin.integration.rabbitmq;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.apache.james.JamesServerExtension.Lifecycle.PER_CLASS;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.rabbitmq.MonitoredDeadLetterQueue;
import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.webadmin.integration.WebAdminServerIntegrationImmutableTest;
import org.apache.james.webadmin.routes.HealthCheckRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.google.inject.multibindings.Multibinder;

@Tag(BasicFeature.TAG)
class RabbitMQWebAdminServerIntegrationImmutableTest extends WebAdminServerIntegrationImmutableTest {
    public static class MonitoredRabbitMQProbe implements GuiceProbe {
        private final Set<MonitoredDeadLetterQueue> deadLetterQueues;
        private final Set<MonitoredRabbitMQConsumers> consumers;

        @Inject
        public MonitoredRabbitMQProbe(Set<MonitoredDeadLetterQueue> deadLetterQueues, Set<MonitoredRabbitMQConsumers> consumers) {
            this.deadLetterQueues = deadLetterQueues;
            this.consumers = consumers;
        }

        List<String> deadLetterQueues() {
            return deadLetterQueues.stream()
                .map(MonitoredDeadLetterQueue::queue)
                .toList();
        }

        List<String> consumerNames() {
            return consumers.stream()
                .map(MonitoredRabbitMQConsumers::name)
                .toList();
        }
    }

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .enableJMAP()
            .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(configuration -> CassandraRabbitMQJamesServerMain.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(MonitoredRabbitMQProbe.class)))
        .lifeCycle(PER_CLASS)
        .build();

    private static final String VERSION = "/cassandra/version";
    private static final String VERSION_LATEST = VERSION + "/latest";
    private static final String UPGRADE_VERSION = VERSION + "/upgrade";
    private static final String UPGRADE_TO_LATEST_VERSION = UPGRADE_VERSION + "/latest";

    @Test
    void getLatestVersionShouldReturnTheConfiguredLatestVersion() {
        when()
            .get(VERSION_LATEST)
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(JSON_CONTENT_TYPE)
            .body(is("{\"version\":" + CassandraSchemaVersionManager.MAX_VERSION.getValue() + "}"));
    }

    @Test
    void solveMessageInconsistenciesTasksShouldBeExposed() {
        String taskId = with().post(UPGRADE_TO_LATEST_VERSION)
            .jsonPath()
            .get("taskId");

        with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));

        taskId = with()
            .queryParam("task", "SolveInconsistencies")
            .post("/messages")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("type", is("solve-message-inconsistencies"))
            .body("additionalInformation.processedImapUidEntries", is(0))
            .body("additionalInformation.processedMessageIdEntries", is(0))
            .body("additionalInformation.addedMessageIdEntries", is(0))
            .body("additionalInformation.updatedMessageIdEntries", is(0))
            .body("additionalInformation.removedMessageIdEntries", is(0))
            .body("additionalInformation.removedImapUidEntries", is(0))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(100))
            .body("additionalInformation.runningOptions.cleanupEntriesWithoutContent", is(false))
            .body("additionalInformation.fixedInconsistencies", hasSize(0))
            .body("additionalInformation.errors", hasSize(0));
    }

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

        assertThat(listComponentNames).containsOnly("Guice application lifecycle", "EmptyErrorMailRepository",
            "RabbitMQ backend", "RabbitMQDeadLetterQueues", "MailReceptionCheck",
            "Cassandra backend", "EventDeadLettersHealthCheck", "MessageFastViewProjection",
            "RabbitMQMailQueue BrowseStart", "OpenSearch Backend", "ObjectStorage", "RabbitMQConsumers",
            "IMAPHealthCheck");
    }

    @Test
    void everyRabbitMQDeadLetterQueueShouldBeMonitored(GuiceJamesServer server) {
        assertThat(server.getProbe(MonitoredRabbitMQProbe.class).deadLetterQueues())
            .containsExactlyInAnyOrder("mailboxEvent-dead-letter-queue", "jmapEvent-dead-letter-queue", "contentDeletionEvent-dead-letter-queue",
                "JamesMailQueue-dead-letter-queue-spool");
    }

    @Test
    void everyRabbitMQConsumerShouldBeMonitored(GuiceJamesServer server) {
        assertThat(server.getProbe(MonitoredRabbitMQProbe.class).consumerNames())
            .containsExactlyInAnyOrder("mailboxEvent event bus", "jmapEvent event bus", "contentDeletionEvent event bus", "task manager", "mail queues");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RabbitMQDeadLetterQueues", "RabbitMQConsumers"})
    void rabbitMQHealthChecksShouldBeHealthy(String componentName) {
        when()
            .get(HealthCheckRoutes.HEALTHCHECK + "/checks/" + componentName)
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("healthy"));
    }
}
