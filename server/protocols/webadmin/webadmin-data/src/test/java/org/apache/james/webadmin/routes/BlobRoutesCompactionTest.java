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
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.compaction.BlobCompactionAlgorithm;
import org.apache.james.blob.compaction.BlobCompactionDTOModules;
import org.apache.james.blob.compaction.CompactionResult;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.json.DTOConverter;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BlobRoutesCompactionTest {
    private static final String BASE_PATH = "/blobs";
    private static final PlainBlobId.Factory BLOB_ID_FACTORY = new PlainBlobId.Factory();
    private static final ZonedDateTime TIMESTAMP = ZonedDateTime.parse("2024-01-01T00:00:00Z");
    private static final BucketName DEFAULT_BUCKET = BucketName.of("default");
    private static final GenerationAwareBlobId.Configuration GENERATION_AWARE_BLOB_ID_CONFIGURATION = GenerationAwareBlobId.Configuration.DEFAULT;

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private BlobCompactionAlgorithm compactionAlgorithm;

    @BeforeEach
    void setUp() {
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        UpdatableTickingClock clock = new UpdatableTickingClock(TIMESTAMP.toInstant());
        BlobReferenceSource blobReferenceSource = mock(BlobReferenceSource.class);
        when(blobReferenceSource.listReferencedBlobs()).thenReturn(Flux.empty());

        GenerationAwareBlobId.Factory generationAwareBlobIdFactory = new GenerationAwareBlobId.Factory(clock, BLOB_ID_FACTORY, GENERATION_AWARE_BLOB_ID_CONFIGURATION);
        BlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        JsonTransformer jsonTransformer = new JsonTransformer();
        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, DTOConverter.of(
            BlobCompactionDTOModules.additionalInformationModule(),
            BlobCompactionDTOModules.initialAdditionalInformationModule(),
            BlobCompactionDTOModules.gcAdditionalInformationModule()));

        compactionAlgorithm = mock(BlobCompactionAlgorithm.class);
        when(compactionAlgorithm.compact(any())).thenReturn(Mono.just(CompactionResult.NONE));
        when(compactionAlgorithm.initialCompact(any())).thenReturn(Mono.just(CompactionResult.NONE));
        when(compactionAlgorithm.gcCompact(any())).thenReturn(Mono.just(CompactionResult.NONE));

        BlobRoutes blobRoutes = new BlobRoutes(
            taskManager,
            jsonTransformer,
            clock,
            blobStoreDAO,
            DEFAULT_BUCKET,
            ImmutableSet.of(blobReferenceSource),
            GENERATION_AWARE_BLOB_ID_CONFIGURATION,
            generationAwareBlobIdFactory,
            Optional.of(compactionAlgorithm));

        webAdminServer = WebAdminUtils.createWebAdminServer(blobRoutes, tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void deleteInitialCompactionShouldReturnTaskIdWhenValidParameters() {
        given()
            .queryParam("scope", "initial-compaction")
            .queryParam("generation", "2")
            .queryParam("family", "1")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteInitialCompactionWithActionParameterShouldReturnTaskId() {
        given()
            .queryParam("action", "initial-compaction")
            .queryParam("generation", "2")
            .queryParam("family", "1")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteGCCompactionShouldReturnTaskIdWhenValidParameters() {
        given()
            .queryParam("scope", "gc-compaction")
            .queryParam("generation", "2")
            .queryParam("family", "1")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteReCompactionWithActionParameterShouldReturnTaskId() {
        given()
            .queryParam("action", "re-compaction")
            .queryParam("generation", "2")
            .queryParam("family", "1")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteCompactionShouldReturnTaskIdWhenValidParameters() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "2")
            .queryParam("family", "1")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteCompactionShouldWorkWithoutFamily() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "0")
            .delete()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void deleteCompactionShouldReturnErrorWhenMissingGeneration() {
        given()
            .queryParam("scope", "compaction")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("details", is("'generation' is compulsory"));
    }

    @Test
    void deleteCompactionShouldReturnErrorWhenInvalidGeneration() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "abc")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("details", is("Invalid 'generation': abc"));
    }

    @Test
    void deleteCompactionShouldReturnErrorWhenNegativeGeneration() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "-1")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("details", is("'generation' must not be negative"));
    }

    @Test
    void deleteCompactionShouldReturnErrorWhenInvalidFamily() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "1")
            .queryParam("family", "abc")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("details", is("Invalid 'family': abc"));
    }

    @Test
    void deleteCompactionShouldReturnErrorWhenNonPositiveFamily() {
        given()
            .queryParam("scope", "compaction")
            .queryParam("generation", "1")
            .queryParam("family", "0")
            .delete()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("details", is("'family' must be strictly positive"));
    }
}
