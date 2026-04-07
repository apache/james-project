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

import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableList;

public sealed interface DeserializationResult permits DeserializationResult.Success, DeserializationResult.SuccessList, DeserializationResult.Failure {
    static DeserializationResult of(Optional<Event> maybeEvent, String throwingMessage) {
        return maybeEvent.<DeserializationResult>map(Success::new)
            .orElse(new Failure(throwingMessage));
    }

    static DeserializationResult ofList(Optional<List<Event>> maybeEvents, String throwingMessage) {
        return maybeEvents.<DeserializationResult>map(SuccessList::new)
            .orElse(new Failure(throwingMessage));
    }

    Event event();

    List<Event> events();

    default boolean isSuccess() {
        return this instanceof Success || this instanceof SuccessList;
    }

    record Success(Event event) implements DeserializationResult {
        @Override
        public Event event() {
            return event;
        }

        @Override
        public List<Event> events() {
            return ImmutableList.of(event);
        }
    }

    record SuccessList(List<Event> events) implements DeserializationResult {
        @Override
        public Event event() {
            if (events.size() != 1) {
                throw new IllegalStateException("Expected exactly one event but got " + events.size());
            }
            return events.getFirst();
        }

        @Override
        public List<Event> events() {
            return events;
        }
    }

    record Failure(String throwingMessage) implements DeserializationResult {
        @Override
        public Event event() {
            throw new RuntimeException(throwingMessage);
        }

        @Override
        public List<Event> events() {
            throw new RuntimeException(throwingMessage);
        }
    }
}
