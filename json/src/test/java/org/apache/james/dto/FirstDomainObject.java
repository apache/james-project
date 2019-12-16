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

package org.apache.james.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

public class FirstDomainObject implements BaseType {
    private final Optional<Long> id;
    private final ZonedDateTime time;
    private final String payload;
    private final Optional<NestedType> child;

    public FirstDomainObject(Optional<Long> id, ZonedDateTime time, String payload, Optional<NestedType> child) {
        this.id = id;
        this.time = time;
        this.payload = payload;
        this.child = child;
    }

    public Optional<Long> getId() {
        return id;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<NestedType> getChild() {
        return child;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FirstDomainObject that = (FirstDomainObject) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(time, that.time) &&
                Objects.equals(child, that.child) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, time, child, payload);
    }
}
