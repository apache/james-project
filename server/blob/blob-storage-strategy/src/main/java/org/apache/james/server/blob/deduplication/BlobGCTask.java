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

package org.apache.james.server.blob.deduplication;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.server.blob.deduplication.BloomFilterGCAlgorithm.Context;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;

public class BlobGCTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("BlobGCTask");

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {

        private static AdditionalInformation from(Context context) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(
                snapshot.getReferenceSourceCount(),
                snapshot.getBlobCount(),
                snapshot.getGcedBlobCount(),
                snapshot.getErrorCount(),
                snapshot.getBloomFilterExpectedBlobCount(),
                snapshot.getBloomFilterAssociatedProbability(),
                Clock.systemUTC().instant());
        }

        private final Instant timestamp;
        private final long referenceSourceCount;
        private final long blobCount;
        private final long gcedBlobCount;
        private final long errorCount;
        private final long bloomFilterExpectedBlobCount;
        private final double bloomFilterAssociatedProbability;

        AdditionalInformation(long referenceSourceCount,
                              long blobCount,
                              long gcedBlobCount,
                              long errorCount,
                              long bloomFilterExpectedBlobCount,
                              double bloomFilterAssociatedProbability,
                              Instant timestamp) {
            this.referenceSourceCount = referenceSourceCount;
            this.blobCount = blobCount;
            this.gcedBlobCount = gcedBlobCount;
            this.errorCount = errorCount;
            this.bloomFilterExpectedBlobCount = bloomFilterExpectedBlobCount;
            this.bloomFilterAssociatedProbability = bloomFilterAssociatedProbability;
            this.timestamp = timestamp;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public long getReferenceSourceCount() {
            return referenceSourceCount;
        }

        public long getBlobCount() {
            return blobCount;
        }

        public long getGcedBlobCount() {
            return gcedBlobCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public long getBloomFilterExpectedBlobCount() {
            return bloomFilterExpectedBlobCount;
        }

        public double getBloomFilterAssociatedProbability() {
            return bloomFilterAssociatedProbability;
        }
    }

    interface Builder {

        @FunctionalInterface
        interface RequireAssociatedProbability {
            BlobGCTask associatedProbability(double associatedProbability);
        }

        @FunctionalInterface
        interface RequireExpectedBlobCount {
            RequireAssociatedProbability expectedBlobCount(int expectedBlobCount);
        }

        @FunctionalInterface
        interface RequireClock {
            RequireExpectedBlobCount clock(Clock clock);
        }

        @FunctionalInterface
        interface RequireBucketName {
            RequireClock bucketName(BucketName bucketName);
        }

        @FunctionalInterface
        interface RequireBlobReferenceSources {
            RequireBucketName blobReferenceSource(Set<BlobReferenceSource> blobReferenceSources);
        }

        @FunctionalInterface
        interface RequireGenerationAwareBlobIdConfiguration {
            RequireBlobReferenceSources generationAwareBlobIdConfiguration(GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration);
        }

        @FunctionalInterface
        interface RequireGenerationAwareBlobIdFactory {
            RequireGenerationAwareBlobIdConfiguration generationAwareBlobIdFactory(GenerationAwareBlobId.Factory generationAwareBlobIdFactory);
        }

        @FunctionalInterface
        interface RequireBlobStoreDAO {
            RequireGenerationAwareBlobIdFactory blobStoreDAO(BlobStoreDAO blobStoreDAO);
        }
    }

    public static Builder.RequireBlobStoreDAO builder() {
        return blobStoreDao -> generationAwareBlobIdFactory -> generationAwareBlobIdConfiguration
            -> blobReferenceSources -> bucketName -> clock -> expectedBlobCount
            -> associatedProbability
            -> new BlobGCTask(
            blobStoreDao,
            generationAwareBlobIdFactory,
            generationAwareBlobIdConfiguration,
            blobReferenceSources,
            bucketName,
            clock,
            expectedBlobCount,
            associatedProbability);
    }


    private final BlobStoreDAO blobStoreDAO;
    private final GenerationAwareBlobId.Factory generationAwareBlobIdFactory;
    private final GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
    private final Set<BlobReferenceSource> blobReferenceSources;
    private final Clock clock;
    private final BucketName bucketName;
    private final int expectedBlobCount;
    private final double associatedProbability;
    private final Context context;


    public BlobGCTask(BlobStoreDAO blobStoreDAO,
                      GenerationAwareBlobId.Factory generationAwareBlobIdFactory,
                      GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                      Set<BlobReferenceSource> blobReferenceSources,
                      BucketName bucketName,
                      Clock clock,
                      int expectedBlobCount,
                      double associatedProbability) {
        this.blobStoreDAO = blobStoreDAO;
        this.generationAwareBlobIdFactory = generationAwareBlobIdFactory;
        this.generationAwareBlobIdConfiguration = generationAwareBlobIdConfiguration;
        this.blobReferenceSources = blobReferenceSources;
        this.clock = clock;
        this.bucketName = bucketName;
        this.expectedBlobCount = expectedBlobCount;
        this.associatedProbability = associatedProbability;
        this.context = new Context(expectedBlobCount, associatedProbability);
    }

    @Override
    public Result run() {
        BloomFilterGCAlgorithm gcAlgorithm = new BloomFilterGCAlgorithm(
            BlobReferenceAggregate.aggregate(blobReferenceSources),
            blobStoreDAO,
            generationAwareBlobIdFactory,
            generationAwareBlobIdConfiguration,
            clock);

        return gcAlgorithm.gc(expectedBlobCount, associatedProbability, bucketName, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(context));
    }

    public Clock getClock() {
        return clock;
    }

    public BucketName getBucketName() {
        return bucketName;
    }

    public int getExpectedBlobCount() {
        return expectedBlobCount;
    }

    public double getAssociatedProbability() {
        return associatedProbability;
    }
}
