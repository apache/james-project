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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class JsonGenericSerializer<T, U extends DTO> {

    private static class DTOConverter<T, U extends DTO> {

        private final Map<String, DTOModule<T, U>> typeToModule;
        private final Map<Class<? extends T>, DTOModule<T, U>> domainClassToModule;

        public DTOConverter(Set<DTOModule<T, U>> modules) {
            typeToModule = modules.stream()
                .collect(Guavate.toImmutableMap(
                    DTOModule::getDomainObjectType,
                    Function.identity()));

            domainClassToModule = modules.stream()
                .collect(Guavate.toImmutableMap(
                    DTOModule::getDomainObjectClass,
                    Function.identity()));
        }

        public Optional<DTOModule<T, U>> findModule(T domainObject) {
            return Optional.ofNullable(domainClassToModule.get(domainObject.getClass()));
        }

        public Optional<DTOModule<T, U>> findModule(String type) {
            return Optional.ofNullable(typeToModule.get(type));
        }
    }

    public static class InvalidTypeException extends RuntimeException {
        public InvalidTypeException(String message) {
            super(message);
        }

        public InvalidTypeException(String message, MismatchedInputException exception) {
            super(message, exception);
        }
    }

    public static class UnknownTypeException extends RuntimeException {
        public UnknownTypeException(String message) {
            super(message);
        }
    }

    private final ObjectMapper objectMapper;
    private final DTOConverter<T, U> dtoConverter;

    @SafeVarargs
    public static <T, U extends DTO> JsonGenericSerializer<T, U> of(DTOModule<T, U>... modules) {
        return new JsonGenericSerializer<>(ImmutableSet.copyOf(modules));
    }

    public JsonGenericSerializer(Set<DTOModule<T, U>> modules) {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new GuavaModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
        dtoConverter = new DTOConverter<>(modules);
    }

    public String serialize(T domainObject) throws JsonProcessingException {
        U dto = dtoConverter.findModule(domainObject)
            .map(module -> module.toDTO(domainObject))
            .orElseThrow(() -> new UnknownTypeException("unknown type " + domainObject.getClass()));
        return objectMapper.writeValueAsString(dto);
    }


    public T deserialize(String value) throws IOException {
        try {
            JsonNode jsonNode = objectMapper.readTree(value);

            JsonNode typeNode = jsonNode.path("type");

            if (typeNode.isMissingNode()) {
                throw new InvalidTypeException("No \"type\" property found in the json document");
            }

            String type = typeNode.asText();
            DTOModule<T, U> dtoModule = dtoConverter.findModule(type)
                .orElseThrow(() -> new UnknownTypeException("unknown type " + type));
            U dto = objectMapper.readValue(objectMapper.treeAsTokens(jsonNode), dtoModule.getDTOClass());
            return dtoModule.getToDomainObjectConverter().convert(dto);
        } catch (MismatchedInputException e) {
            throw new InvalidTypeException("Unable to deserialize the json document", e);
        }
    }

}
