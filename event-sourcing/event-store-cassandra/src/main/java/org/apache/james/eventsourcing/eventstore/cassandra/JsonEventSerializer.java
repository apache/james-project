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

package org.apache.james.eventsourcing.eventstore.cassandra;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTOModule;
import org.apache.james.json.JsonGenericSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

public class JsonEventSerializer {

    public static final String EVENT_NESTED_TYPES_INJECTION_NAME = "EventNestedTypes";

    public static RequireNestedConfiguration forModules(Set<? extends EventDTOModule<?, ?>> modules) {
        return nestedTypesModules -> {
            ImmutableSet<EventDTOModule<?, ?>> dtoModules = ImmutableSet.copyOf(modules);
            return new JsonEventSerializer(dtoModules, ImmutableSet.copyOf(nestedTypesModules));
        };
    }

    @SafeVarargs
    public static RequireNestedConfiguration forModules(EventDTOModule<?, ?>... modules) {
        return forModules(ImmutableSet.copyOf(modules));
    }

    public interface RequireNestedConfiguration {
        JsonEventSerializer withNestedTypeModules(Set<DTOModule<?, ?>> modules);

        default JsonEventSerializer withNestedTypeModules(DTOModule<?, ?>... modules) {
            return withNestedTypeModules(ImmutableSet.copyOf(modules));
        }

        default JsonEventSerializer withNestedTypeModules(Set<DTOModule<?, ?>>... modules) {
            return withNestedTypeModules(Arrays.stream(modules).flatMap(Collection::stream).collect(Guavate.toImmutableSet()));
        }

        default JsonEventSerializer withoutNestedType() {
            return withNestedTypeModules(ImmutableSet.of());
        }
    }

    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(JsonGenericSerializer.InvalidTypeException original) {
            super(original);
        }
    }

    public static class UnknownEventException extends RuntimeException {
        public UnknownEventException(JsonGenericSerializer.UnknownTypeException original) {
            super(original);
        }
    }

    private JsonGenericSerializer<Event, EventDTO> jsonGenericSerializer;

    @Inject
    private JsonEventSerializer(Set<EventDTOModule<?, ?>> modules, @Named(EVENT_NESTED_TYPES_INJECTION_NAME) Set<DTOModule<?, ?>> nestedTypesModules) {
        jsonGenericSerializer = JsonGenericSerializer.forModules(modules).withNestedTypeModules(nestedTypesModules);
    }
    
    public String serialize(Event event) throws JsonProcessingException {
        try {
            return jsonGenericSerializer.serialize(event);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownEventException(e);
        }
    }

    public Event deserialize(String value) throws IOException {
        try {
            return jsonGenericSerializer.deserialize(value);
        } catch (JsonGenericSerializer.UnknownTypeException e) {
            throw new UnknownEventException(e);
        } catch (JsonGenericSerializer.InvalidTypeException e) {
            throw new InvalidEventException(e);
        }
    }

}
