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

package org.apache.james.queue.rabbitmq;

import java.util.Objects;
import java.util.UUID;

import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.Preconditions;

public class EnQueueId {

    public static EnQueueId generate() {
        return of(UUIDs.timeBased());
    }

    public static EnQueueId of(UUID uuid) {
        Preconditions.checkNotNull(uuid);
        return new EnQueueId(uuid);
    }

    public static EnQueueId ofSerialized(String serialized) {
        Preconditions.checkNotNull(serialized);
        return of(UUID.fromString(serialized));
    }

    private final UUID id;

    private EnQueueId(UUID id) {
        this.id = id;
    }

    public UUID asUUID() {
        return id;
    }

    public String serialize() {
        return id.toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof EnQueueId) {
            EnQueueId enQueueId = (EnQueueId) o;

            return Objects.equals(this.id, enQueueId.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
