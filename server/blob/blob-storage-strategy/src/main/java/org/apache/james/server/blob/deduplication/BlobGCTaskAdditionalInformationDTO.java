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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlobGCTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<BlobGCTask.AdditionalInformation, BlobGCTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(BlobGCTask.AdditionalInformation.class)
            .convertToDTO(BlobGCTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto ->
                new BlobGCTask.AdditionalInformation(
                    dto.referenceSourceCount,
                    dto.blobCount,
                    dto.gcedBlobCount,
                    dto.errorCount,
                    dto.bloomFilterExpectedBlobCount,
                    dto.bloomFilterAssociatedProbability,
                    dto.timestamp,
                    dto.deletionWindowSize.orElse(BlobGCTask.Builder.DEFAULT_DELETION_WINDOW_SIZE)))
            .toDTOConverter((domain, type) ->
                new BlobGCTaskAdditionalInformationDTO(
                    type,
                    domain.getTimestamp(),
                    domain.getReferenceSourceCount(),
                    domain.getBlobCount(),
                    domain.getGcedBlobCount(),
                    domain.getErrorCount(),
                    domain.getBloomFilterExpectedBlobCount(),
                    domain.getBloomFilterAssociatedProbability(),
                    Optional.of(domain.getDeletionWindowSize())
                ))
            .typeName(BlobGCTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Instant timestamp;
    private final long referenceSourceCount;
    private final long blobCount;
    private final long gcedBlobCount;
    private final long errorCount;
    private final long bloomFilterExpectedBlobCount;
    private final double bloomFilterAssociatedProbability;
    private final Optional<Integer> deletionWindowSize;

    public BlobGCTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                              @JsonProperty("timestamp") Instant timestamp,
                                              @JsonProperty("referenceSourceCount") long referenceSourceCount,
                                              @JsonProperty("blobCount") long blobCount,
                                              @JsonProperty("gcedBlobCount") long gcedBlobCount,
                                              @JsonProperty("errorCount") long errorCount,
                                              @JsonProperty("bloomFilterExpectedBlobCount") long bloomFilterExpectedBlobCount,
                                              @JsonProperty("bloomFilterAssociatedProbability") double bloomFilterAssociatedProbability,
                                              @JsonProperty("deletionWindowSize") Optional<Integer> deletionWindowSize) {
        this.type = type;
        this.timestamp = timestamp;
        this.referenceSourceCount = referenceSourceCount;
        this.blobCount = blobCount;
        this.gcedBlobCount = gcedBlobCount;
        this.errorCount = errorCount;
        this.bloomFilterExpectedBlobCount = bloomFilterExpectedBlobCount;
        this.bloomFilterAssociatedProbability = bloomFilterAssociatedProbability;
        this.deletionWindowSize = deletionWindowSize;
    }


    @Override
    public String getType() {
        return type;
    }

    @Override
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

    public Optional<Integer> getDeletionWindowSize() {
        return deletionWindowSize;
    }
}
