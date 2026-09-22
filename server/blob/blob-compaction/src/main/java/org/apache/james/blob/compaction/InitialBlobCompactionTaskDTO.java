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
import java.util.Optional;

import org.apache.james.blob.api.BucketName;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InitialBlobCompactionTaskDTO implements TaskDTO {

    private final String type;
    private final String bucketName;
    private final long generation;
    private final Optional<Integer> family;
    private final Optional<Long> chunkTargetSize;
    private final Optional<Long> maxPackableSize;

    public InitialBlobCompactionTaskDTO(@JsonProperty("type") String type,
                                        @JsonProperty("bucketName") String bucketName,
                                        @JsonProperty("generation") long generation,
                                        @JsonProperty("family") Optional<Integer> family,
                                        @JsonProperty("chunkTargetSize") Optional<Long> chunkTargetSize,
                                        @JsonProperty("maxPackableSize") Optional<Long> maxPackableSize) {
        this.type = type;
        this.bucketName = bucketName;
        this.generation = generation;
        this.family = family;
        this.chunkTargetSize = chunkTargetSize;
        this.maxPackableSize = maxPackableSize;
    }

    public static TaskDTOModule<InitialBlobCompactionTask, InitialBlobCompactionTaskDTO> module(BlobCompactionAlgorithm algorithm, Clock clock) {
        return DTOModule.forDomainObject(InitialBlobCompactionTask.class)
            .convertToDTO(InitialBlobCompactionTaskDTO.class)
            .toDomainObjectConverter(dto -> {
                CompactionConfiguration.Builder configBuilder = CompactionConfiguration.builder();
                dto.getChunkTargetSize().ifPresent(configBuilder::chunkTargetSize);
                dto.getMaxPackableSize().ifPresent(configBuilder::maxPackableSize);

                CompactionRequest.Builder requestBuilder = CompactionRequest.builder()
                    .bucketName(BucketName.of(dto.getBucketName()))
                    .generation(dto.getGeneration())
                    .configuration(configBuilder.build());

                dto.getFamily().ifPresent(requestBuilder::family);

                return new InitialBlobCompactionTask(algorithm, requestBuilder.build(), clock);
            })
            .toDTOConverter((domain, type) -> {
                CompactionRequest req = domain.getRequest();
                CompactionConfiguration conf = req.configuration();
                return new InitialBlobCompactionTaskDTO(
                    type,
                    req.bucketName().asString(),
                    req.generation(),
                    req.family(),
                    Optional.of(conf.chunkTargetSize()),
                    Optional.of(conf.maxPackableSize())
                );
            })
            .typeName(InitialBlobCompactionTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
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

    public Optional<Long> getChunkTargetSize() {
        return chunkTargetSize;
    }

    public Optional<Long> getMaxPackableSize() {
        return maxPackableSize;
    }
}
