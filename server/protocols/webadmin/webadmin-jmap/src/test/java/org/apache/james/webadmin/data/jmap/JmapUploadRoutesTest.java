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

package org.apache.james.webadmin.data.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.james.jmap.cassandra.upload.CassandraUploadRepository.UPLOAD_BUCKET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.hamcrest.Matchers.notNullValue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.UploadId;
import org.apache.james.jmap.api.model.UploadMetaData;
import org.apache.james.jmap.api.model.UploadNotFoundException;
import org.apache.james.jmap.cassandra.upload.CassandraUploadRepository;
import org.apache.james.jmap.cassandra.upload.UploadDAO;
import org.apache.james.jmap.cassandra.upload.UploadModule;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.server.blob.deduplication.PassThroughBlobStore;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class JmapUploadRoutesTest {

    private static final String BASE_PATH = "/jmap/uploads";
    private static final ContentType CONTENT_TYPE = ContentType.of("text/html");
    private static final Username USERNAME = Username.of("Bob");
    private static final InputStream DATA = IOUtils.toInputStream("DATA 123", StandardCharsets.UTF_8);
    private static final ZonedDateTime TIMESTAMP  = ZonedDateTime.parse("2020-10-30T14:12:00Z");
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private BlobStore blobStore;
    private CassandraUploadRepository cassandraUploadRepository;
    private UpdatableTickingClock clock;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraModule.aggregateModules(
        UploadModule.MODULE));

    @BeforeEach
    void setUp() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        clock = new UpdatableTickingClock(TIMESTAMP.toInstant());
        blobStore = new PassThroughBlobStore(new MemoryBlobStoreDAO(),
            BucketName.of("default"),
            new HashBlobId.Factory());

        cassandraUploadRepository = new CassandraUploadRepository(new UploadDAO(cassandraCluster.getCassandraCluster().getConf(),
            new HashBlobId.Factory()), blobStore, clock);

        JsonTransformer jsonTransformer = new JsonTransformer();
        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(UploadCleanupTaskAdditionalInformationDTO.SERIALIZATION_MODULE));
        webAdminServer = WebAdminUtils.createWebAdminServer(new JmapUploadRoutes(cassandraUploadRepository, taskManager, jsonTransformer), tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void deleteUploadShouldReturnErrorWhenScopeInvalid() {
        Map<String, Object> errors = given()
            .queryParam("scope", "invalid")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", BAD_REQUEST_400)
            .containsEntry("type", "InvalidArgument")
            .containsEntry("message", "Invalid arguments supplied in the user request")
            .containsEntry("details", "'scope' is missing or invalid");
    }

    @Test
    void deleteUploadShouldReturnErrorWhenMissingScope() {
        Map<String, Object> errors = given()
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .extract()
            .body()
            .jsonPath()
            .getMap(".");

        assertThat(errors)
            .containsEntry("statusCode", BAD_REQUEST_400)
            .containsEntry("type", "InvalidArgument")
            .containsEntry("message", "Invalid arguments supplied in the user request")
            .containsEntry("details", "'scope' is missing or invalid");
    }

    @Test
    void deleteUploadShouldReturnTaskId() {
        given()
            .queryParam("scope", "expired")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void cleanUploadTaskShouldReturnDetail() {
        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", Matchers.is("completed"))
            .body("taskId", Matchers.is(notNullValue()))
            .body("type", Matchers.is(UploadRepositoryCleanupTask.TASK_TYPE.asString()))
            .body("startedDate", Matchers.is(notNullValue()))
            .body("submitDate", Matchers.is(notNullValue()))
            .body("completedDate", Matchers.is(notNullValue()))
            .body("additionalInformation.scope", Matchers.is("expired"));
    }

    @Test
    void cleanUploadTaskShouldRemoveExpiredBlob() {
        UploadMetaData uploadMetaData = Mono.from(cassandraUploadRepository.upload(DATA, CONTENT_TYPE, USERNAME)).block();

        clock.setInstant(TIMESTAMP.plusWeeks(3).toInstant());

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(Flux.from(blobStore.listBlobs(UPLOAD_BUCKET)).collectList().block())
            .doesNotContain(uploadMetaData.blobId());
    }

    @Test
    void cleanUploadTaskShouldNotRemoveUnExpiredBlob() {
        UploadMetaData upload = Mono.from(cassandraUploadRepository.upload(DATA, CONTENT_TYPE, USERNAME)).block();

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(Flux.from(blobStore.listBlobs(UPLOAD_BUCKET)).collectList().block())
            .containsOnly(upload.blobId());
    }

    @Test
    void cleanUploadTaskShouldNotRemoveUnExpiredUpload() {
        UploadMetaData upload = Mono.from(cassandraUploadRepository.upload(DATA, CONTENT_TYPE, USERNAME)).block();

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(cassandraUploadRepository.listUploads(USERNAME).collectList().block())
            .containsOnly(upload);
    }

    @Test
    void cleanUploadTaskShouldSuccessWhenMixCase() {
        UploadMetaData upload1 = Mono.from(cassandraUploadRepository.upload(IOUtils.toInputStream("DATA 1", StandardCharsets.UTF_8), CONTENT_TYPE, USERNAME)).block();

        clock.setInstant(TIMESTAMP.plusWeeks(1).toInstant());
        UploadMetaData upload2 = Mono.from(cassandraUploadRepository.upload(IOUtils.toInputStream("DATA 2", StandardCharsets.UTF_8), CONTENT_TYPE, USERNAME)).block();

        clock.setInstant(TIMESTAMP.plusWeeks(3).toInstant());
        UploadMetaData upload3 = Mono.from(cassandraUploadRepository.upload(IOUtils.toInputStream("DATA 3", StandardCharsets.UTF_8), CONTENT_TYPE, USERNAME)).block();

        clock.setInstant(TIMESTAMP.plusWeeks(4).toInstant());
        UploadMetaData upload4 = Mono.from(cassandraUploadRepository.upload(IOUtils.toInputStream("DATA 4", StandardCharsets.UTF_8), CONTENT_TYPE, USERNAME)).block();

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(cassandraUploadRepository.listUploads(USERNAME).collectList().block())
            .doesNotContain(upload1, upload2)
            .contains(upload3, upload4);

        assertThat(Flux.from(blobStore.listBlobs(UPLOAD_BUCKET)).collectList().block())
            .doesNotContain(upload1.blobId(), upload2.blobId())
            .contains(upload3.blobId(), upload4.blobId());
    }

    @Test
    void cleanUploadTaskShouldRemoveUploadEntriesInExpiredBucket() {
        UploadId uploadId = Mono.from(cassandraUploadRepository.upload(DATA, CONTENT_TYPE, USERNAME)).block().uploadId();

        clock.setInstant(TIMESTAMP.plusWeeks(3).toInstant());

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThatThrownBy(() -> Mono.from(cassandraUploadRepository.retrieve(uploadId, USERNAME)).block())
                .isInstanceOf(UploadNotFoundException.class));
    }

    @Test
    void cleanUploadTaskShouldNotRemoveUploadEntriesInUnExpiredBucket() {
        UploadId uploadId = Mono.from(cassandraUploadRepository.upload(DATA, CONTENT_TYPE, USERNAME)).block().uploadId();

        String taskId = given()
            .queryParam("scope", "expired")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(Mono.from(cassandraUploadRepository.retrieve(uploadId, USERNAME)).block())
            .isNotNull();
    }

}
