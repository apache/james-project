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

package org.apache.james.blob.compaction;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class InitialBlobCompactionTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("InitialBlobCompactionTask");
    private static final Logger LOGGER = LoggerFactory.getLogger(InitialBlobCompactionTask.class);

    public static class AdditionalInformation implements TaskExecutionDetails.AdditionalInformation {
        private final Instant timestamp;
        private final String bucketName;
        private final long generation;
        private final Optional<Integer> family;
        private final long packedBlobs;
        private final long packedBytes;
        private final long chunksWritten;
        private final long freedBytes;

        public AdditionalInformation(Instant timestamp,
                                     String bucketName,
                                     long generation,
                                     Optional<Integer> family,
                                     long packedBlobs,
                                     long packedBytes,
                                     long chunksWritten,
                                     long freedBytes) {
            this.timestamp = Preconditions.checkNotNull(timestamp, "'timestamp' must not be null");
            this.bucketName = Preconditions.checkNotNull(bucketName, "'bucketName' must not be null");
            this.generation = generation;
            this.family = Preconditions.checkNotNull(family, "'family' must not be null");
            this.packedBlobs = packedBlobs;
            this.packedBytes = packedBytes;
            this.chunksWritten = chunksWritten;
            this.freedBytes = freedBytes;
        }

        @Override
        public Instant timestamp() {
            return timestamp;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public String getBucketName() {
            return bucketName;
        }

        public long getGeneration() {
            return generation;
        }

        public Optional<Integer> getFamily() {
            return family;
        }

        public long getPackedBlobs() {
            return packedBlobs;
        }

        public long getPackedBytes() {
            return packedBytes;
        }

        public long getChunksWritten() {
            return chunksWritten;
        }

        public long getFreedBytes() {
            return freedBytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof AdditionalInformation that) {
                return generation == that.generation
                    && packedBlobs == that.packedBlobs
                    && packedBytes == that.packedBytes
                    && chunksWritten == that.chunksWritten
                    && freedBytes == that.freedBytes
                    && Objects.equals(timestamp, that.timestamp)
                    && Objects.equals(bucketName, that.bucketName)
                    && Objects.equals(family, that.family);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timestamp, bucketName, generation, family, packedBlobs, packedBytes, chunksWritten, freedBytes);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("timestamp", timestamp)
                .add("bucketName", bucketName)
                .add("generation", generation)
                .add("family", family)
                .add("packedBlobs", packedBlobs)
                .add("packedBytes", packedBytes)
                .add("chunksWritten", chunksWritten)
                .add("freedBytes", freedBytes)
                .toString();
        }
    }

    private final BlobCompactionAlgorithm algorithm;
    private final CompactionRequest request;
    private final Clock clock;
    private final AtomicReference<CompactionResult> currentResult;

    public InitialBlobCompactionTask(BlobCompactionAlgorithm algorithm, CompactionRequest request, Clock clock) {
        this.algorithm = Preconditions.checkNotNull(algorithm, "'algorithm' must not be null");
        this.request = Preconditions.checkNotNull(request, "'request' must not be null");
        this.clock = Preconditions.checkNotNull(clock, "'clock' must not be null");
        this.currentResult = new AtomicReference<>(CompactionResult.NONE);
    }

    @Override
    public Result run() {
        try {
            CompactionResult result = algorithm.initialCompact(request).block();
            if (result != null) {
                currentResult.set(result);
            }
            return Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.error("Error while running InitialBlobCompactionTask for generation {}", request.generation(), e);
            return Result.PARTIAL;
        }
    }

    @Override
    public TaskType type() {
        return TASK_TYPE;
    }

    @Override
    public Optional<TaskExecutionDetails.AdditionalInformation> details() {
        CompactionResult res = currentResult.get();
        return Optional.of(new AdditionalInformation(
            clock.instant(),
            request.bucketName().asString(),
            request.generation(),
            request.family(),
            res.packedBlobs(),
            res.packedBytes(),
            res.chunksWritten(),
            res.freedBytes()
        ));
    }

    public CompactionRequest getRequest() {
        return request;
    }

    public Clock getClock() {
        return clock;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof InitialBlobCompactionTask that) {
            return Objects.equals(request, that.request);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(request);
    }
}
