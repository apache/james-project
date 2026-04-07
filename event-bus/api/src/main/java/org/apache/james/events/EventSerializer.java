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

import java.nio.charset.StandardCharsets;
import java.util.Collection;

public interface EventSerializer {
    SerializationResult toJson(Event event);

    SerializationResult toJson(Collection<Event> event);

    default SerializationResult toJsonBytes(Event event) {
        return toJson(event);
    }

    default SerializationResult toJsonBytes(Collection<Event> event) {
        return toJson(event);
    }

    DeserializationResult asEvent(String serialized);

    DeserializationResult asEvents(String serialized);

    default DeserializationResult fromBytes(byte[] serialized) {
        return asEvent(new String(serialized, StandardCharsets.UTF_8));
    }

    default DeserializationResult asEventsFromBytes(byte[] serialized) {
        return asEvents(new String(serialized, StandardCharsets.UTF_8));
    }
}
