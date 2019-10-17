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

package org.apache.james.server.task.json.dto;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.task.MemoryReferenceWithCounterTask;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MemoryReferenceWithCounterTaskAdditionalInformationDTO implements AdditionalInformationDTO {

   public static final AdditionalInformationDTOModule<MemoryReferenceWithCounterTask.AdditionalInformation, MemoryReferenceWithCounterTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(MemoryReferenceWithCounterTask.AdditionalInformation.class)
            .convertToDTO(MemoryReferenceWithCounterTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto -> new MemoryReferenceWithCounterTask.AdditionalInformation(
                dto.count, dto.timestamp
            ))
            .toDTOConverter((details, type) -> new MemoryReferenceWithCounterTaskAdditionalInformationDTO(
                type, details.getCount(), details.timestamp()))
            .typeName(MemoryReferenceWithCounterTask.TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final long count;
    private final Instant timestamp;

    public MemoryReferenceWithCounterTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                                  @JsonProperty("count") long count,
                                                                  @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.count = count;
        this.timestamp = timestamp;
    }

    public long getCount() {
        return count;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }
}
