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

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GCBlobCompactionTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<GCBlobCompactionTask.AdditionalInformation, GCBlobCompactionTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(GCBlobCompactionTask.AdditionalInformation.class)
            .convertToDTO(GCBlobCompactionTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto ->
                new GCBlobCompactionTask.AdditionalInformation(
                    dto.timestamp,
                    dto.bucketName,
                    dto.generation,
                    dto.family,
                    dto.deadPurged,
                    dto.mergedChunks,
                    dto.freedBytes))
            .toDTOConverter((domain, type) ->
                new GCBlobCompactionTaskAdditionalInformationDTO(
                    type,
                    domain.getTimestamp(),
                    domain.getBucketName(),
                    domain.getGeneration(),
                    domain.getFamily(),
                    domain.getDeadPurged(),
                    domain.getMergedChunks(),
                    domain.getFreedBytes()))
            .typeName(GCBlobCompactionTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Instant timestamp;
    private final String bucketName;
    private final long generation;
    private final Optional<Integer> family;
    private final long deadPurged;
    private final long mergedChunks;
    private final long freedBytes;

    public GCBlobCompactionTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                        @JsonProperty("timestamp") Instant timestamp,
                                                        @JsonProperty("bucketName") String bucketName,
                                                        @JsonProperty("generation") long generation,
                                                        @JsonProperty("family") Optional<Integer> family,
                                                        @JsonProperty("deadPurged") long deadPurged,
                                                        @JsonProperty("mergedChunks") long mergedChunks,
                                                        @JsonProperty("freedBytes") long freedBytes) {
        this.type = type;
        this.timestamp = timestamp;
        this.bucketName = bucketName;
        this.generation = generation;
        this.family = family;
        this.deadPurged = deadPurged;
        this.mergedChunks = mergedChunks;
        this.freedBytes = freedBytes;
    }

    @Override
    public String getType() {
        return type;
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

    public long getDeadPurged() {
        return deadPurged;
    }

    public long getMergedChunks() {
        return mergedChunks;
    }

    public long getFreedBytes() {
        return freedBytes;
    }
}
