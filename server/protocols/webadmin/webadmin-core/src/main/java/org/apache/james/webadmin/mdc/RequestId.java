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

package org.apache.james.webadmin.mdc;

import java.util.Objects;
import java.util.UUID;

import com.google.common.base.Preconditions;

public class RequestId {
    public static RequestId random() {
        return of(UUID.randomUUID());
    }

    public static RequestId of(UUID uuid) {
        Preconditions.checkNotNull(uuid, "'uuid' can not be null");

        return new RequestId(uuid);
    }

    public static RequestId of(String uuid) {
        Preconditions.checkNotNull(uuid, "'uuid' can not be null");

        return new RequestId(UUID.fromString(uuid));
    }

    private final UUID uuid;

    private RequestId(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String asString() {
        return uuid.toString();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RequestId) {
            RequestId requestId = (RequestId) o;

            return Objects.equals(this.uuid, requestId.uuid);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(uuid);
    }
}
