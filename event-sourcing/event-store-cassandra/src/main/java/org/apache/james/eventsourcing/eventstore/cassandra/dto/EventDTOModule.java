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

package org.apache.james.eventsourcing.eventstore.cassandra.dto;

import org.apache.james.eventsourcing.Event;

public class EventDTOModule<T extends Event, U extends EventDTO> {

    public interface EventDTOConverter<T extends Event, U extends EventDTO> {
        U convert(T event, String typeName);
    }

    public static <U extends Event> Builder<U> forEvent(Class<U> eventType) {
        return new Builder<>(eventType);
    }

    public static class Builder<T extends Event> {

        private final Class<T> eventType;

        private Builder(Class<T> eventType) {
            this.eventType = eventType;
        }

        public <U extends EventDTO> RequireConversionFunctionBuilder<U> convertToDTO(Class<U> dtoType) {
            return new RequireConversionFunctionBuilder<>(dtoType);
        }

        public class RequireConversionFunctionBuilder<U extends EventDTO> {

            private final Class<U> dtoType;

            private RequireConversionFunctionBuilder(Class<U> dtoType) {
                this.dtoType = dtoType;
            }

            public RequireTypeNameBuilder convertWith(EventDTOConverter<T, U> converter) {
                return new RequireTypeNameBuilder(converter);
            }

            public class RequireTypeNameBuilder {
                private final EventDTOConverter<T, U> converter;

                private RequireTypeNameBuilder(EventDTOConverter<T, U> converter) {
                    this.converter = converter;
                }

                public EventDTOModule<T, U> typeName(String typeName) {
                    return new EventDTOModule<>(converter, eventType, dtoType, typeName);
                }
            }
        }

    }

    private final EventDTOConverter<T, U> converter;
    private final Class<T> eventType;
    private final Class<U> dtoType;
    private final String typeName;

    private EventDTOModule(EventDTOConverter<T, U> converter, Class<T> eventType, Class<U> dtoType, String typeName) {
        this.converter = converter;
        this.eventType = eventType;
        this.dtoType = dtoType;
        this.typeName = typeName;
    }

    public String getType() {
        return typeName;
    }

    public Class<U> getDTOClass() {
        return dtoType;
    }

    public Class<T> getEventClass() {
        return eventType;
    }

    public EventDTO toDTO(T event) {
        return converter.convert(event, typeName);
    }
}
