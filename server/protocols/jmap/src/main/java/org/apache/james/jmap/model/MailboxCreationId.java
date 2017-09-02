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

package org.apache.james.jmap.model;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.MoreObjects;

public class MailboxCreationId {

    public static MailboxCreationId of(String creationId) {
        return new MailboxCreationId(creationId);
    }

    private final String creationId;

    private MailboxCreationId(String creationId) {
        this.creationId = creationId;
    }

    @JsonValue
    public String getCreationId() {
        return creationId;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxCreationId) {
            return Objects.equals(creationId, ((MailboxCreationId) obj).creationId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(creationId);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("creationId", creationId)
            .toString();
    }
}
