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
import static io.restassured.RestAssured.with;
import static org.apache.james.JamesServerExtension.Lifecycle.PER_CLASS;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsMapWithSize.anEmptyMap;

import org.apache.james.CassandraExtension;
import org.apache.james.CassandraRabbitMQJamesConfiguration;
import org.apache.james.CassandraRabbitMQJamesServerMain;
import org.apache.james.DockerOpenSearchExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.probe.DataProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.CassandraMappingsRoutes;
import org.apache.james.webadmin.routes.MailQueueRoutes;
import org.apache.james.webadmin.routes.MailRepositoriesRoutes;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.vault.routes.DeletedMessagesVaultRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

@Tag(BasicFeature.TAG)
class RabbitMQWebAdminServerTaskSerializationIntegrationImmutableTest {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<CassandraRabbitMQJamesConfiguration>(tmpDir ->
        CassandraRabbitMQJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                    .s3()
                    .disableCache()
                    .deduplication()
                    .noCryptoConfig())
            .vaultConfiguration(VaultConfiguration.ENABLED_DEFAULT)
            .searchConfiguration(SearchConfiguration.openSearch())
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new RabbitMQExtension())
        .server(CassandraRabbitMQJamesServerMain::createServer)
        .lifeCycle(PER_CLASS)
        .build();

    private static final String DOMAIN = "domain";
    private static final String USERNAME = "username@" + DOMAIN;

    @BeforeAll
    static void setUp(GuiceJamesServer guiceJamesServer) throws Exception {
        DataProbe dataProbe = guiceJamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        WebAdminGuiceProbe webAdminGuiceProbe = guiceJamesServer.getProbe(WebAdminGuiceProbe.class);

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setUrlEncodingEnabled(false) // no further automatically encoding by Rest Assured client. rf: https://issues.apache.org/jira/projects/JAMES/issues/JAMES-3936
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void fullReindexingShouldCompleteWhenNoMail() {
        String taskId = with()
            .post("/mailboxes?task=reIndex")
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is("full-reindexing"))
            .body("additionalInformation.successfullyReprocessedMailCount", is(0))
            .body("additionalInformation.failedReprocessedMailCount", is(0))
            .body("additionalInformation.messageFailures", is(anEmptyMap()));
    }

    @Test
    void clearMailQueueShouldCompleteWhenNoQueryParameters() {
        String firstMailQueue = with()
                .basePath(MailQueueRoutes.BASE_URL)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0]");

        String taskId = with()
                .basePath(MailQueueRoutes.BASE_URL)
            .delete(firstMailQueue + "/mails")
                .jsonPath()
                .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(Matchers.notNullValue()))
            .body("type", is("clear-mail-queue"))
            .body("additionalInformation.mailQueueName", is(notNullValue()))
            .body("additionalInformation.initialCount", is(0))
            .body("additionalInformation.remainingCount", is(0));
    }

    @Test
    void blobStoreVaultGarbageCollectionShouldComplete() {
        String taskId =
            with()
                .basePath(DeletedMessagesVaultRoutes.ROOT_PATH)
                .queryParam("scope", "expired")
            .delete()
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("deleted-messages-blob-store-based-garbage-collection"))
            .body("additionalInformation.beginningOfRetentionPeriod", is(notNullValue()))
            .body("additionalInformation.deletedBuckets", is(empty()));
    }

    @Test
    void clearMailRepositoryShouldComplete() {
        String escapedRepositoryPath = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .get()
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("[0].path");

        String taskId = with()
                .basePath(MailRepositoriesRoutes.MAIL_REPOSITORIES)
            .delete(escapedRepositoryPath + "/mails")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("clear-mail-repository"))
            .body("additionalInformation.repositoryPath", is(notNullValue()))
            .body("additionalInformation.initialCount", is(0))
            .body("additionalInformation.remainingCount", is(0));
    }

    @Test
    void cassandraMigrationShouldComplete() {
        SchemaVersion toVersion = CassandraSchemaVersionManager.MAX_VERSION;
        String taskId = with()
                .body(String.valueOf(toVersion.getValue()))
            .post("cassandra/version/upgrade")
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("cassandra-migration"))
            .body("additionalInformation.toVersion", is(toVersion.getValue()));
    }

    @Test
    void cassandraMappingsSolveInconsistenciesShouldComplete() {
        String taskId = with()
                .basePath(CassandraMappingsRoutes.ROOT_PATH)
                .queryParam("action", "SolveInconsistencies")
            .post()
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("cassandra-mappings-solve-inconsistencies"))
            .body("additionalInformation.successfulMappingsCount", is(0))
            .body("additionalInformation.errorMappingsCount", is(0));
    }

    @Test
    void recomputeMailboxCountersShouldComplete() {
        String taskId = with()
                .basePath("/mailboxes")
                .queryParam("task", "RecomputeMailboxCounters")
            .post()
                .jsonPath()
                .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("recompute-mailbox-counters"))
            .body("additionalInformation.processedMailboxes", is(0));
    }

    @Test
    void recomputeCurrentQuotasShouldComplete() {
        String taskId = with()
            .basePath("/quota/users")
            .queryParam("task", "RecomputeCurrentQuotas")
        .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("recompute-current-quotas"))
            .body("additionalInformation.processedQuotaRoots", is(0))
            .body("additionalInformation.failedQuotaRoots", empty());
    }

    @Test
    void republishNotProcessedMailsOnSpoolShouldComplete() {
        String taskId = with()
            .basePath("/mailQueues/spool")
            .queryParam("action", "RepublishNotProcessedMails")
            .queryParam("olderThan", "2d")
        .post()
            .jsonPath()
        .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("republish-not-processed-mails"))
            .body("additionalInformation.nbRequeuedMails", is(0));
    }


    @Test
    void tasksCleanupShouldComplete(){
        String taskId = with()
            .basePath("/tasks")
            .queryParam("olderThan", "15day")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("tasks-cleanup"))
            .body("additionalInformation.olderThan", is(notNullValue()))
            .body("additionalInformation.processedTaskCount", is(0))
            .body("additionalInformation.removedTaskCount", is(0));
    }
}
