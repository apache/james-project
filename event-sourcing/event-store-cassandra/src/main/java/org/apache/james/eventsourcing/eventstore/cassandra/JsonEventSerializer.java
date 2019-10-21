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
import java.util.Set;

import javax.inject.Inject;

import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.JsonGenericSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableSet;

public class JsonEventSerializer {

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
    public JsonEventSerializer(Set<EventDTOModule<?, ?>> modules) {
        //FIXME
        jsonGenericSerializer = new JsonGenericSerializer(modules, null);
    }
    
    public JsonEventSerializer(EventDTOModule<?, ?>... modules) {
        this(ImmutableSet.copyOf(modules));
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
