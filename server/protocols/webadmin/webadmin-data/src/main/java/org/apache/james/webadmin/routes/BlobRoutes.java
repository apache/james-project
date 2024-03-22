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

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.server.blob.deduplication.BlobGCTask;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.tasks.TaskFromRequest;
import org.apache.james.webadmin.utils.JsonTransformer;

import com.google.common.base.Preconditions;

import spark.Request;
import spark.Service;

public class BlobRoutes implements Routes {

    public static final String BASE_PATH = "/blobs";
    public static final int EXPECTED_BLOB_COUNT_DEFAULT = 1_000_000;
    public static final double ASSOCIATED_PROBABILITY_DEFAULT = 0.01;

    private final TaskManager taskManager;
    private final JsonTransformer jsonTransformer;
    private final Clock clock;
    private final BlobStoreDAO blobStoreDAO;
    private final BucketName bucketName;
    private final Set<BlobReferenceSource> blobReferenceSources;
    private final GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
    private final GenerationAwareBlobId.Factory generationAwareBlobIdFactory;

    @Inject
    public BlobRoutes(TaskManager taskManager,
                      JsonTransformer jsonTransformer,
                      Clock clock,
                      BlobStoreDAO blobStoreDAO,
                      @Named(BlobStore.DEFAULT_BUCKET_NAME_QUALIFIER) BucketName defaultBucketName,
                      Set<BlobReferenceSource> blobReferenceSources,
                      GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                      GenerationAwareBlobId.Factory generationAwareBlobIdFactory) {
        this.taskManager = taskManager;
        this.jsonTransformer = jsonTransformer;
        this.clock = clock;
        this.blobStoreDAO = blobStoreDAO;
        this.bucketName = defaultBucketName;
        this.blobReferenceSources = blobReferenceSources;
        this.generationAwareBlobIdConfiguration = generationAwareBlobIdConfiguration;
        this.generationAwareBlobIdFactory = generationAwareBlobIdFactory;
    }

    @Override
    public String getBasePath() {
        return BASE_PATH;
    }

    @Override
    public void define(Service service) {
        TaskFromRequest gcUnreferencedTaskRequest = this::gcUnreferenced;
        service.delete(BASE_PATH, gcUnreferencedTaskRequest.asRoute(taskManager), jsonTransformer);
    }

    public Task gcUnreferenced(Request request) {
        Preconditions.checkArgument(Optional.ofNullable(request.queryParams("scope"))
            .filter("unreferenced"::equals)
            .isPresent(),
            "'scope' is missing or must be 'unreferenced'");

        int expectedBlobCount = getExpectedBlobCount(request).orElse(EXPECTED_BLOB_COUNT_DEFAULT);
        Optional<Integer> deletionWindowSize = getDeletionWindowSize(request);
        double associatedProbability = getAssociatedProbability(request).orElse(ASSOCIATED_PROBABILITY_DEFAULT);

        return BlobGCTask.builder()
            .blobStoreDAO(blobStoreDAO)
            .generationAwareBlobIdFactory(generationAwareBlobIdFactory)
            .generationAwareBlobIdConfiguration(generationAwareBlobIdConfiguration)
            .blobReferenceSource(blobReferenceSources)
            .bucketName(bucketName)
            .clock(clock)
            .expectedBlobCount(expectedBlobCount)
            .associatedProbability(associatedProbability)
            .deletionWindowSize(deletionWindowSize)
            .build();
    }

    private static Optional<Integer> getExpectedBlobCount(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("expectedBlobCount"))
                .map(Integer::parseInt)
                .map(expectedBlobCount -> {
                    Preconditions.checkArgument(expectedBlobCount > 0,
                        "'expectedBlobCount' must be strictly positive");
                    return expectedBlobCount;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'expectedBlobCount' must be numeric");
        }
    }

    private static Optional<Integer> getDeletionWindowSize(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("deletionWindowSize"))
                .map(Integer::parseInt)
                .map(expectedBlobCount -> {
                    Preconditions.checkArgument(expectedBlobCount > 0,
                        "'deletionWindowSize' must be strictly positive");
                    return expectedBlobCount;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'deletionWindowSize' must be numeric");
        }
    }

    private static Optional<Double> getAssociatedProbability(Request req) {
        try {
            return Optional.ofNullable(req.queryParams("associatedProbability"))
                .map(Double::parseDouble)
                .map(associatedProbability -> {
                    Preconditions.checkArgument(associatedProbability > 0 && associatedProbability < 1,
                        "'associatedProbability' must be greater than 0.0 and smaller than 1.0");
                    return associatedProbability;
                });
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("'associatedProbability' must be numeric");
        }
    }
}
