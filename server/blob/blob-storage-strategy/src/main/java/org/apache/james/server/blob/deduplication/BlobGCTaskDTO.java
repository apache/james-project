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
import java.util.Optional;
import java.util.Set;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobReferenceSource;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlobGCTaskDTO implements TaskDTO {

    private final String bucketName;
    private final int expectedBlobCount;
    private final Optional<Integer> deletionWindowSize;
    private final double associatedProbability;
    private final String type;

    public BlobGCTaskDTO(@JsonProperty("bucketName") String bucketName,
                         @JsonProperty("expectedBlobCount") int expectedBlobCount,
                         @JsonProperty("deletionWindowSize") Optional<Integer> deletionWindowSize,
                         @JsonProperty("associatedProbability") double associatedProbability,
                         @JsonProperty("type") String type) {
        this.bucketName = bucketName;
        this.expectedBlobCount = expectedBlobCount;
        this.deletionWindowSize = deletionWindowSize;
        this.associatedProbability = associatedProbability;
        this.type = type;
    }

    public static TaskDTOModule<BlobGCTask, BlobGCTaskDTO> module(BlobStoreDAO blobStoreDAO,
                                                                  BlobId.Factory generationAwareBlobIdFactory,
                                                                  GenerationAwareBlobId.Configuration generationAwareBlobIdConfiguration,
                                                                  Set<BlobReferenceSource> blobReferenceSources,
                                                                  Clock clock) {
        return DTOModule.forDomainObject(BlobGCTask.class)
            .convertToDTO(BlobGCTaskDTO.class)
            .toDomainObjectConverter(dto ->
                BlobGCTask.builder()
                    .blobStoreDAO(blobStoreDAO)
                    .generationAwareBlobIdFactory(generationAwareBlobIdFactory)
                    .generationAwareBlobIdConfiguration(generationAwareBlobIdConfiguration)
                    .blobReferenceSource(blobReferenceSources)
                    .bucketName(BucketName.of(dto.bucketName))
                    .clock(clock)
                    .expectedBlobCount(dto.expectedBlobCount)
                    .associatedProbability(dto.associatedProbability)
                    .deletionWindowSize(dto.deletionWindowSize)
                    .build())
            .toDTOConverter((domain, type) ->
                new BlobGCTaskDTO(
                    domain.getBucketName().asString(),
                    domain.getExpectedBlobCount(),
                    Optional.of(domain.getDeletionWindowSize()),
                    domain.getAssociatedProbability(),
                    type))
            .typeName(BlobGCTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }

    public String getBucketName() {
        return bucketName;
    }

    public int getExpectedBlobCount() {
        return expectedBlobCount;
    }

    public double getAssociatedProbability() {
        return associatedProbability;
    }

    public Optional<Integer> getDeletionWindowSize() {
        return deletionWindowSize;
    }
}
