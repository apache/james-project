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

package org.apache.james.events;

import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EventDeserializers {
    private final Set<EventSerializer> allEventSerializers;

    @Inject
    public EventDeserializers(Set<EventSerializer> allEventSerializers) {
        this.allEventSerializers = allEventSerializers;
    }

    public Event deserialize(String json) {
        return allEventSerializers.stream()
            .map(eventSerializer -> deserialize(json, eventSerializer))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Could not deserialize event: " + json));
    }

    private Optional<Event> deserialize(String json, EventSerializer eventSerializer) {
        try {
            return Optional.of(eventSerializer.asEvent(json));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
