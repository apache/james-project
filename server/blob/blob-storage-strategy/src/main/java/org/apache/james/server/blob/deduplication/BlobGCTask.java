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

import org.apache.james.blob.api.BlobId;
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

        private static AdditionalInformation from(Context context, int deletionWindowSize) {
            Context.Snapshot snapshot = context.snapshot();
            return new AdditionalInformation(
                snapshot.getReferenceSourceCount(),
                snapshot.getBlobCount(),
                snapshot.getGcedBlobCount(),
                snapshot.getErrorCount(),
                snapshot.getBloomFilterExpectedBlobCount(),
                snapshot.getBloomFilterAssociatedProbability(),
                Clock.systemUTC().instant(), deletionWindowSize);
        }

        private final Instant timestamp;
        private final long referenceSourceCount;
        private final long blobCount;
        private final long gcedBlobCount;
        private final long errorCount;
        private final long bloomFilterExpectedBlobCount;
        private final double bloomFilterAssociatedProbability;
        private final int deletionWindowSize;

        AdditionalInformation(long referenceSourceCount,
                              long blobCount,
                              long gcedBlobCount,
                              long errorCount,
                              long bloomFilterExpectedBlobCount,
                              double bloomFilterAssociatedProbability,
                              Instant timestamp,
                              int deletionWindowSize) {
            this.referenceSourceCount = referenceSourceCount;
            this.blobCount = blobCount;
            this.gcedBlobCount = gcedBlobCount;
            this.errorCount = errorCount;
            this.bloomFilterExpectedBlobCount = bloomFilterExpectedBlobCount;
            this.bloomFilterAssociatedProbability = bloomFilterAssociatedProbability;
            this.timestamp = timestamp;
            this.deletionWindowSize = deletionWindowSize;
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

        public int getDeletionWindowSize() {
            return deletionWindowSize;
        }
    }

    public static class Builder {

        public static final int DEFAULT_DELETION_WINDOW_SIZE = 1000;

        @FunctionalInterface
        public interface RequireAssociatedProbability {
            Builder associatedProbability(double associatedProbability);
        }

        @FunctionalInterface
        public interface RequireExpectedBlobCount {
            RequireAssociatedProbability expectedBlobCount(int expectedBlobCount);
        }

        @FunctionalInterface
        public interface RequireClock {
            RequireExpectedBlobCount clock(Clock clock);
        }

        @FunctionalInterface
        public interface RequireBucketName {
            RequireClock bucketName(BucketName bucketName);
        }

        @FunctionalInterface
        public interface RequireBlobReferenceSources {
            RequireBucketName blobReferenceSource(Set<BlobReferenceSource> blobReferenceSources);
        }

        @FunctionalInterface
        public interface RequireGenerationAwareBlobIdConfiguration {
            RequireBlobReferenceSources generationAwareBlobIdConfiguration(GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration);
        }

        @FunctionalInterface
        public interface RequireGenerationAwareBlobIdFactory {
            RequireGenerationAwareBlobIdConfiguration generationAwareBlobIdFactory(BlobId.Factory generationAwareBlobIdFactory);
        }

        @FunctionalInterface
        public interface RequireBlobStoreDAO {
            RequireGenerationAwareBlobIdFactory blobStoreDAO(BlobStoreDAO blobStoreDAO);
        }

        private final BlobStoreDAO blobStoreDAO;
        private final BlobId.Factory generationAwareBlobIdFactory;
        private final GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
        private final Set<BlobReferenceSource> blobReferenceSources;
        private final Clock clock;
        private final BucketName bucketName;
        private final int expectedBlobCount;
        private final double associatedProbability;
        private Optional<Integer> deletionWindowSize;

        public Builder(BlobStoreDAO blobStoreDAO, BlobId.Factory generationAwareBlobIdFactory,
                       GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                       Set<BlobReferenceSource> blobReferenceSources, Clock clock, BucketName bucketName,
                       int expectedBlobCount, double associatedProbability) {
            this.blobStoreDAO = blobStoreDAO;
            this.generationAwareBlobIdFactory = generationAwareBlobIdFactory;
            this.generationAwareBlobIdConfiguration = generationAwareBlobIdConfiguration;
            this.blobReferenceSources = blobReferenceSources;
            this.clock = clock;
            this.bucketName = bucketName;
            this.expectedBlobCount = expectedBlobCount;
            this.deletionWindowSize = Optional.empty();
            this.associatedProbability = associatedProbability;
        }

        public Builder deletionWindowSize(int deletionWindowSize) {
            this.deletionWindowSize = Optional.of(deletionWindowSize);
            return this;
        }

        public Builder deletionWindowSize(Optional<Integer> deletionWindowSize) {
            this.deletionWindowSize = deletionWindowSize;
            return this;
        }

        public BlobGCTask build() {
            return new BlobGCTask(
                blobStoreDAO,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                bucketName,
                clock,
                expectedBlobCount,
                deletionWindowSize.orElse(DEFAULT_DELETION_WINDOW_SIZE),
                associatedProbability);
        }
    }

    public static Builder.RequireBlobStoreDAO builder() {
        return blobStoreDao -> generationAwareBlobIdFactory -> generationAwareBlobIdConfiguration
            -> blobReferenceSources -> bucketName -> clock -> expectedBlobCount
            -> associatedProbability -> new Builder(
                blobStoreDao,
                generationAwareBlobIdFactory,
                generationAwareBlobIdConfiguration,
                blobReferenceSources,
                clock,
                bucketName,
                expectedBlobCount,
                associatedProbability);
    }


    private final BlobStoreDAO blobStoreDAO;
    private final BlobId.Factory generationAwareBlobIdFactory;
    private final GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration;
    private final Set<BlobReferenceSource> blobReferenceSources;
    private final Clock clock;
    private final BucketName bucketName;
    private final int expectedBlobCount;
    private final int deletionWindowSize;
    private final double associatedProbability;
    private final Context context;


    public BlobGCTask(BlobStoreDAO blobStoreDAO,
                      BlobId.Factory generationAwareBlobIdFactory,
                      GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                      Set<BlobReferenceSource> blobReferenceSources,
                      BucketName bucketName,
                      Clock clock,
                      int expectedBlobCount,
                      int deletionWindowSize, double associatedProbability) {
        this.blobStoreDAO = blobStoreDAO;
        this.generationAwareBlobIdFactory = generationAwareBlobIdFactory;
        this.generationAwareBlobIdConfiguration = generationAwareBlobIdConfiguration;
        this.blobReferenceSources = blobReferenceSources;
        this.clock = clock;
        this.bucketName = bucketName;
        this.expectedBlobCount = expectedBlobCount;
        this.deletionWindowSize = deletionWindowSize;
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

        return gcAlgorithm.gc(expectedBlobCount, deletionWindowSize, associatedProbability, bucketName, context)
            .block();
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        return Optional.of(AdditionalInformation.from(context, deletionWindowSize));
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

    public int getDeletionWindowSize() {
        return deletionWindowSize;
    }
}
