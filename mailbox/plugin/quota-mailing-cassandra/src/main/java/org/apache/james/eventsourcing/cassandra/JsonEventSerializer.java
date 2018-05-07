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

package org.apache.james.eventsourcing.cassandra;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.cassandra.dto.EventDTOModule;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class JsonEventSerializer {
    public static class UnknownEventException extends RuntimeException {
        public UnknownEventException(String message) {
            super(message);
        }
    }

    private final Map<Class<? extends Event>, EventDTOModule> eventClassToModule;
    private final Map<String, EventDTOModule> typeToModule;
    private final ObjectMapper objectMapper;

    @Inject
    public JsonEventSerializer(Set<EventDTOModule> modules) {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT);

        typeToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                EventDTOModule::getType,
                Function.identity()));

        eventClassToModule = modules.stream()
            .collect(Guavate.toImmutableMap(
                EventDTOModule::getEventClass,
                Function.identity()));
    }
    
    public JsonEventSerializer(EventDTOModule... modules) {
        this(ImmutableSet.copyOf(modules));
    }

    public String serialize(Event event) throws JsonProcessingException {
        Object dto = Optional.ofNullable(eventClassToModule.get(event.getClass()))
            .orElseThrow(() -> new UnknownEventException("unknown event class " + event.getClass()))
            .toDTO(event);
        return objectMapper.writeValueAsString(dto);
    }

    public Event deserialize(String value) throws IOException {
        JsonNode jsonNode = objectMapper.readTree(value);

        String type = jsonNode.path("type").asText();

        EventDTO dto = objectMapper.readValue(
            objectMapper.treeAsTokens(jsonNode),
            retrieveDTOClass(type));
        return dto.toEvent();
    }

    public Class<? extends EventDTO> retrieveDTOClass(String type) {
        return Optional.ofNullable(typeToModule.get(type))
            .map(EventDTOModule::getDTOClass)
            .orElseThrow(() -> new UnknownEventException("unknown event type " + type));
    }

}
