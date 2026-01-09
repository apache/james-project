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

package org.apache.james.webadmin.routes;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.blob.deduplication.BlobGCTaskAdditionalInformationDTO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BlobRoutesTest {
    private static final String BASE_PATH = "/blobs";
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2015-10-30T16:12:00Z");
    private static final BucketName DEFAULT_BUCKET = BucketName.of("default");
    private static final GenerationAwareBlobId.Configuration GENERATION_AWARE_BLOB_ID_CONFIGURATION = GenerationAwareBlobId.Configuration.DEFAULT;
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await()
        .atMost(TEN_SECONDS);

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private UpdatableTickingClock clock;
    private BlobReferenceSource blobReferenceSource;
    private BlobStore blobStore;

    @BeforeEach
    void setUp() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        clock = new UpdatableTickingClock(TIMESTAMP.toInstant());
        blobReferenceSource = mock(BlobReferenceSource.class);
        when(blobReferenceSource.listReferencedBlobs()).thenReturn(Flux.empty());
        GenerationAwareBlobId.Factory generationAwareBlobIdFactory = new GenerationAwareBlobId.Factory(clock, BLOB_ID_FACTORY, GENERATION_AWARE_BLOB_ID_CONFIGURATION);

        BlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        blobStore = new DeDuplicationBlobStore(blobStoreDAO, generationAwareBlobIdFactory);
        JsonTransformer jsonTransformer = new JsonTransformer();
        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(BlobGCTaskAdditionalInformationDTO.SERIALIZATION_MODULE));
        BlobRoutes blobRoutes = new BlobRoutes(
            taskManager,
            jsonTransformer,
            clock,
            blobStoreDAO,
            ImmutableSet.of(blobReferenceSource),
            GENERATION_AWARE_BLOB_ID_CONFIGURATION,
            generationAwareBlobIdFactory);

        webAdminServer = WebAdminUtils.createWebAdminServer(blobRoutes, tasksRoutes).start();

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
    void deleteUnReferencedShouldReturnErrorWhenScopeInvalid() {
        given()
            .queryParam("scope", "invalid")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'scope' is missing or must be 'unreferenced'"));
    }

    @Test
    void deleteUnReferencedShouldReturnErrorWhenMissingScope() {
        given()
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'scope' is missing or must be 'unreferenced'"));
    }

    @Test
    void deleteUnReferencedShouldReturnTaskId() {
        given()
            .queryParam("scope", "unreferenced")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void gcTaskShouldReturnDetail() {
        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("taskId", is(notNullValue()))
            .body("type", is("BlobGCTask"))
            .body("startedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("additionalInformation.type", is("BlobGCTask"))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.referenceSourceCount", is(0))
            .body("additionalInformation.blobCount", is(0))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0))
            .body("additionalInformation.deletionWindowSize", is(1000))
            .body("additionalInformation.bloomFilterExpectedBlobCount", is(1_000_000))
            .body("additionalInformation.bloomFilterAssociatedProbability", is(0.01F));
    }

    @Test
    void deleteUnReferencedShouldAcceptBloomFilterExpectedBlobCountParam() {
        String taskId = given()
            .queryParam("scope", "unreferenced")
            .queryParam("expectedBlobCount", 99)
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("additionalInformation.bloomFilterExpectedBlobCount", is(99));
    }

    @Test
    void deleteUnReferencedShouldAcceptDeletionWindowSizeParam() {
        String taskId = given()
            .queryParam("scope", "unreferenced")
            .queryParam("deletionWindowSize", 99)
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("additionalInformation.deletionWindowSize", is(99));
    }

    @ParameterizedTest
    @MethodSource("expectedBlobCountParameters")
    void deleteUnReferencedShouldReturnErrorWhenExpectedBlobCountInvalid(Object expectedBlobCount) {
        given()
            .queryParam("scope", "unreferenced")
            .queryParam("expectedBlobCount", expectedBlobCount)
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", containsString("expectedBlobCount"));
    }

    private static Stream<Arguments> expectedBlobCountParameters() {
        return Stream.of(
            Arguments.of(-1),
            Arguments.of(0),
            Arguments.of("invalid")
        );
    }

    @Test
    void deleteUnReferencedShouldAcceptBloomFilterAssociatedProbabilityParam() {
        String taskId = given()
            .queryParam("scope", "unreferenced")
            .queryParam("associatedProbability", 0.2)
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("additionalInformation.bloomFilterAssociatedProbability", is(0.2F));
    }

    @ParameterizedTest
    @MethodSource("associatedProbabilityParameters")
    void deleteUnReferencedShouldReturnErrorWhenAssociatedProbabilityInvalid(Object associatedProbability) {
        given()
            .queryParam("scope", "unreferenced")
            .queryParam("associatedProbability", associatedProbability)
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", containsString("associatedProbability"));
    }

    private static Stream<Arguments> associatedProbabilityParameters() {
        return Stream.of(
            Arguments.of(-1),
            Arguments.of(-0.1F),
            Arguments.of(1.1),
            Arguments.of(1),
            Arguments.of(Integer.MAX_VALUE),
            Arguments.of("invalid"),
            Arguments.of("")
        );
    }

    @Test
    void gcTaskShouldRemoveOrphanBlob() {
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.referenceSourceCount", is(0))
            .body("additionalInformation.blobCount", is(1))
            .body("additionalInformation.gcedBlobCount", is(1))
            .body("additionalInformation.errorCount", is(0));

        assertThatThrownBy(() -> blobStore.read(DEFAULT_BUCKET, blobId))
            .isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    void gcTaskShouldNotRemoveUnExpireBlob() {
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.referenceSourceCount", is(0))
            .body("additionalInformation.blobCount", is(1))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0));

        assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
            .isNotNull();
    }

    @Test
    void gcTaskShouldNotRemoveReferencedBlob() {
        BlobId blobId = Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block();
        when(blobReferenceSource.listReferencedBlobs()).thenReturn(Flux.just(blobId));

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.referenceSourceCount", is(1))
            .body("additionalInformation.blobCount", is(1))
            .body("additionalInformation.gcedBlobCount", is(0))
            .body("additionalInformation.errorCount", is(0));

        assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
            .isNotNull();
    }

    @Test
    void gcTaskShouldSuccessWhenMixCase() {
        List<BlobId> referencedBlobIds = IntStream.range(0, 100)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());
        List<BlobId> orphanBlobIds = IntStream.range(0, 50)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());

        when(blobReferenceSource.listReferencedBlobs()).thenReturn(Flux.fromIterable(referencedBlobIds));
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        List<BlobId> unExpiredBlobIds = IntStream.range(0, 30)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());

        String taskId = given()
            .queryParam("scope", "unreferenced")
            .delete()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", is("completed"))
            .body("additionalInformation.referenceSourceCount", is(referencedBlobIds.size()))
            .body("additionalInformation.blobCount", is(referencedBlobIds.size() + orphanBlobIds.size() + unExpiredBlobIds.size()))
            .body("additionalInformation.gcedBlobCount", Matchers.lessThanOrEqualTo(orphanBlobIds.size()))
            .body("additionalInformation.errorCount", is(0));

        referencedBlobIds.forEach(blobId ->
            assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
                .isNotNull());

        unExpiredBlobIds.forEach(blobId ->
            assertThat(blobStore.read(DEFAULT_BUCKET, blobId))
                .isNotNull());
    }

    @Test
    void allOrphanBlobIdsShouldRemovedAfterMultipleCallDeleteUnreferenced() {
        List<BlobId> referencedBlobIds = IntStream.range(0, 100)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());
        List<BlobId> orphanBlobIds = IntStream.range(0, 50)
            .mapToObj(index -> Mono.from(blobStore.save(DEFAULT_BUCKET, UUID.randomUUID().toString(), BlobStore.StoragePolicy.HIGH_PERFORMANCE)).block())
            .collect(Collectors.toList());

        when(blobReferenceSource.listReferencedBlobs()).thenReturn(Flux.fromIterable(referencedBlobIds));
        clock.setInstant(TIMESTAMP.plusMonths(2).toInstant());

        CALMLY_AWAIT.untilAsserted(() -> {
            String taskId = given()
                .queryParam("scope", "unreferenced")
                .delete()
                .jsonPath()
                .get("taskId");

            given()
                .basePath(TasksRoutes.BASE)
            .when()
                .get(taskId + "/await");

            orphanBlobIds.forEach(blobId ->
                assertThatThrownBy(() -> blobStore.read(DEFAULT_BUCKET, blobId))
                    .isInstanceOf(ObjectNotFoundException.class));
        });
    }
}
