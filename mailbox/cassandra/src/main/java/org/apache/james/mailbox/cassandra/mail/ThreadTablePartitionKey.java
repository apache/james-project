/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.mailbox.cassandra.mail;

import java.util.Objects;
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.mailbox.store.mail.model.MimeMessageId;

public class ThreadTablePartitionKey {
    private final Username username;
    private final Set<Integer> mimeMessageIds;

    public ThreadTablePartitionKey(Username username, Set<Integer> mimeMessageIds) {
        this.username = username;
        this.mimeMessageIds = mimeMessageIds;
    }

    public Username getUsername() {
        return username;
    }

    public Set<Integer> getMimeMessageIds() {
        return mimeMessageIds;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ThreadTablePartitionKey) {
            ThreadTablePartitionKey that = (ThreadTablePartitionKey) o;
            return Objects.equals(this.username, that.username)
                && Objects.equals(this.mimeMessageIds, that.mimeMessageIds);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, mimeMessageIds);
    }
}
