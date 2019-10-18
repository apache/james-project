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
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.jsontype.NamedType;
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

        public Optional<U> convert(T domainObject) {
            return Optional.ofNullable(domainClassToModule.get(domainObject.getClass()))
                .map(module -> module.toDTO(domainObject));
        }

        public Optional<T> convert(U dto) {
            String type = dto.getType();
            return Optional.ofNullable(typeToModule.get(type))
                .map(module -> module.getToDomainObjectConverter().convert(dto));
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
        modules.stream()
            .map(module -> new NamedType(module.getDTOClass(), module.getDomainObjectType()))
            .forEach(objectMapper::registerSubtypes);
        dtoConverter = new DTOConverter<>(modules);
    }

    public String serialize(T domainObject) throws JsonProcessingException {
        U dto = dtoConverter.convert(domainObject)
            .orElseThrow(() -> new UnknownTypeException("unknown type " + domainObject.getClass()));
        return objectMapper.writeValueAsString(dto);
    }


    public T deserialize(String value) throws IOException {
        U dto = jsonToDTO(value);
        return dtoConverter.convert(dto)
            .orElseThrow(() -> new UnknownTypeException("unknown type " + dto.getType()));
    }

    public U jsonToDTO(String value) throws IOException {
        try {
            JsonNode jsonTree = detectDuplicateProperty(value);
            return parseAsPolymorphicDTO(jsonTree);
        } catch (InvalidTypeIdException e) {
            String typeId = e.getTypeId();
            if (typeId == null) {
                throw new InvalidTypeException("Unable to deserialize the json document", e);
            } else {
                throw new UnknownTypeException("unknown type " + typeId);
            }
        } catch (MismatchedInputException e) {
            throw new InvalidTypeException("Unable to deserialize the json document", e);
        }
    }

    private JsonNode detectDuplicateProperty(String value) throws IOException {
        return objectMapper.readTree(value);
    }

    @SuppressWarnings("rawtypes")
    private U parseAsPolymorphicDTO(JsonNode jsonTree) throws IOException {
        return (U) objectMapper.readValue(objectMapper.treeAsTokens(jsonTree), DTO.class);
    }

}
