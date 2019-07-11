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

package org.apache.james.vault.metadata;

import org.apache.james.vault.dto.DeletedMessageWithStorageInformationConverter;
import org.apache.james.vault.dto.DeletedMessageWithStorageInformationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import reactor.core.publisher.Mono;

class MetadataSerializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataSerializer.class);


    private final ObjectMapper objectMapper;
    private final DeletedMessageWithStorageInformationConverter dtoConverter;

    MetadataSerializer(DeletedMessageWithStorageInformationConverter dtoConverter) {
        this.dtoConverter = dtoConverter;
        this.objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
    }

    Mono<DeletedMessageWithStorageInformation> deserialize(String payload) {
        return Mono.just(payload)
            .flatMap(string -> Mono.fromCallable(() -> objectMapper.readValue(string, DeletedMessageWithStorageInformationDTO.class))
                .onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error deserializing JSON metadata", e))))
            .flatMap(dto -> Mono.fromCallable(() -> dtoConverter.toDomainObject(dto))
                .onErrorResume(e -> Mono.fromRunnable(() -> LOGGER.error("Error deserializing DTO", e))));

    }

    String serialize(DeletedMessageWithStorageInformation message) {
        DeletedMessageWithStorageInformationDTO dto = DeletedMessageWithStorageInformationDTO.toDTO(message);

        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
