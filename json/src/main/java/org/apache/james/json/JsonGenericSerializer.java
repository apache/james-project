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

package org.apache.james.json;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class JsonGenericSerializer<T, U extends DTO<T>> {
    public static class UnknownTypeException extends RuntimeException {
        public UnknownTypeException(String message) {
            super(message);
        }
    }

    private final Map<Class<? extends T>, DTOModule<T, U>> domainClassToModule;
    private final Map<String, DTOModule<T, U>> typeToModule;
    private final ObjectMapper objectMapper;

    @SafeVarargs
    public static <T, U extends DTO<T>> JsonGenericSerializer of(DTOModule<T, U>... modules) {
        return new JsonGenericSerializer<>(ImmutableSet.copyOf(modules));
    }

    public JsonGenericSerializer(Set<DTOModule<T, U>> modules) {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new GuavaModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

        typeToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                DTOModule::getDomainObjectType,
                Function.identity()));

        domainClassToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                DTOModule::getDomainObjectClass,
                Function.identity()));
    }

    public String serialize(T domainObject) throws JsonProcessingException {
        U dto = Optional.ofNullable(domainClassToModule.get(domainObject.getClass()))
            .orElseThrow(() -> new UnknownTypeException("unknown type " + domainObject.getClass()))
            .toDTO(domainObject);
        return objectMapper.writeValueAsString(dto);
    }

    public T deserialize(String value) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(value);

        String type = jsonNode.path("type").asText();

        U dto = objectMapper.readValue(
            objectMapper.treeAsTokens(jsonNode),
            retrieveDTOClass(type));
        return dto.toDomainObject();
    }

    private Class<? extends U> retrieveDTOClass(String type) {
        return Optional.ofNullable(typeToModule.get(type))
            .map(DTOModule::getDTOClass)
            .orElseThrow(() -> new UnknownTypeException("unknown type " + type));
    }

}
