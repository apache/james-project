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
package org.apache.james.mailbox.cassandra.ids;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.apache.james.mailbox.model.MailboxId;

import com.datastax.driver.core.utils.UUIDs;
import com.google.common.base.MoreObjects;

public class CassandraId implements MailboxId, Serializable {

    public static class Factory implements MailboxId.Factory {
        @Override
        public CassandraId fromString(String serialized) {
            return of(UUID.fromString(serialized));
        }
    }
    
    private final UUID id;

    public static CassandraId timeBased() {
        return of(UUIDs.timeBased());
    }

    public static CassandraId of(UUID id) {
        return new CassandraId(id);
    }

    private CassandraId(UUID id) {
        this.id = id;
    }

    @Override
    public String serialize() {
        return id.toString();
    }

    public UUID asUuid() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CassandraId) {
            CassandraId other = (CassandraId) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
