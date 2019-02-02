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

package org.apache.james.eventsourcing;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class EventId implements Comparable<EventId> {

    public static EventId fromSerialized(int value) {
        return new EventId(value);
    }

    public static EventId first() {
        return new EventId(0);
    }

    private final int value;

    private EventId(int value) {
        Preconditions.checkArgument(value >= 0, "EventId can not be negative");
        this.value = value;
    }

    public EventId next() {
        return new EventId(value + 1);
    }

    public Optional<EventId> previous() {
        if (value > 0) {
            return Optional.of(new EventId(value - 1));
        }
        return Optional.empty();
    }

    @Override
    public int compareTo(EventId o) {
        return Long.compare(value, o.value);
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EventId) {
            EventId eventId = (EventId) o;

            return Objects.equals(this.value, eventId.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }

    public int serialize() {
        return value;
    }
}
