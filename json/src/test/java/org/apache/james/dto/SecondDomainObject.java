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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SecondDomainObject implements BaseType {
    private final UUID id;
    private final String payload;
    private final Optional<NestedType> child;

    public SecondDomainObject(UUID id, String payload, Optional<NestedType> child) {
        this.id = id;
        this.payload = payload;
        this.child = child;
    }

    public UUID getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<NestedType> getChild() {
        return child;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SecondDomainObject that = (SecondDomainObject) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(child, that.child) &&
                Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, child, payload);
    }
}
