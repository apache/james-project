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

import com.google.common.base.Preconditions;

public class InitialBlobCompactionTask implements Task {
    public static final TaskType TASK_TYPE = TaskType.of("InitialBlobCompactionTask");
    private static final Logger LOGGER = LoggerFactory.getLogger(InitialBlobCompactionTask.class);

    public record AdditionalInformation(
        Instant timestamp,
        String bucketName,
        long generation,
        Optional<Integer> family,
        long packedBlobs,
        long packedBytes,
        long chunksWritten,
        long freedBytes
    ) implements TaskExecutionDetails.AdditionalInformation {

        public AdditionalInformation {
            Preconditions.checkNotNull(timestamp, "'timestamp' must not be null");
            Preconditions.checkNotNull(bucketName, "'bucketName' must not be null");
            Preconditions.checkArgument(generation >= 0, "'generation' must not be negative");
            Preconditions.checkNotNull(family, "'family' must not be null");
            family.ifPresent(f -> Preconditions.checkArgument(f > 0, "'family' must be strictly positive"));
            Preconditions.checkArgument(packedBlobs >= 0, "'packedBlobs' must not be negative");
            Preconditions.checkArgument(packedBytes >= 0, "'packedBytes' must not be negative");
            Preconditions.checkArgument(chunksWritten >= 0, "'chunksWritten' must not be negative");
            Preconditions.checkArgument(freedBytes >= 0, "'freedBytes' must not be negative");
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
