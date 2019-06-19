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

import com.google.common.base.Preconditions;

public class EnqueueId {

    public static EnqueueId generate() {
        return of(UUID.randomUUID());
    }

    public static EnqueueId of(UUID uuid) {
        Preconditions.checkNotNull(uuid);
        return new EnqueueId(uuid);
    }

    public static EnqueueId ofSerialized(String serialized) {
        Preconditions.checkNotNull(serialized);
        return of(UUID.fromString(serialized));
    }

    private final UUID id;

    private EnqueueId(UUID id) {
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
        if (o instanceof EnqueueId) {
            EnqueueId enqueueId = (EnqueueId) o;

            return Objects.equals(this.id, enqueueId.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }
}
