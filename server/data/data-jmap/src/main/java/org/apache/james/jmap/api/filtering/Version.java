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

package org.apache.james.jmap.api.filtering;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.eventsourcing.EventId;

import com.google.common.base.MoreObjects;

public class Version {
    public static final Version INITIAL = new Version(-1);

    public static Version from(Optional<EventId> eventId) {
        return eventId.map(EventId::value)
            .map(Version::new)
            .orElse(Version.INITIAL);
    }

    private final int version;

    public Version(int version) {
        this.version = version;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Version) {
            Version that = (Version) o;
            return Objects.equals(this.version, that.version);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("version", version)
            .toString();
    }

    public String asString() {
        return String.valueOf(version);
    }

    public int asInteger() {
        return version;
    }

    public Optional<EventId> asEventId() {
        if (version == -1) {
            return Optional.empty();
        }
        return Optional.of(EventId.apply(version));
    }
}
