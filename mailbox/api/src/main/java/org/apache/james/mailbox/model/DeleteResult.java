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

package org.apache.james.mailbox.model;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public class DeleteResult {
    public static class Builder {
        private final ImmutableSet.Builder<MessageId> destroyed;
        private final ImmutableSet.Builder<MessageId> notFound;

        public Builder() {
            destroyed = ImmutableSet.builder();
            notFound = ImmutableSet.builder();
        }

        public Builder addDestroyed(Collection<MessageId> messageIds) {
            destroyed.addAll(messageIds);
            return this;
        }

        public Builder addNotFound(Collection<MessageId> messageIds) {
            notFound.addAll(messageIds);
            return this;
        }

        public Builder addDestroyed(MessageId messageId) {
            destroyed.add(messageId);
            return this;
        }

        public Builder addNotFound(MessageId messageId) {
            notFound.add(messageId);
            return this;
        }

        public DeleteResult build() {
            return new DeleteResult(
                destroyed.build(),
                notFound.build());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DeleteResult destroyed(MessageId messageId) {
        return new Builder()
            .addDestroyed(messageId)
            .build();
    }

    public static DeleteResult notFound(MessageId messageId) {
        return new Builder()
            .addNotFound(messageId)
            .build();
    }

    private final Set<MessageId> destroyed;
    private final Set<MessageId> notFound;

    public DeleteResult(Set<MessageId> destroyed, Set<MessageId> notFound) {
        this.destroyed = destroyed;
        this.notFound = notFound;
    }

    public Set<MessageId> getDestroyed() {
        return destroyed;
    }

    public Set<MessageId> getNotFound() {
        return notFound;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DeleteResult) {
            DeleteResult result = (DeleteResult) o;

            return Objects.equals(this.destroyed, result.destroyed)
                && Objects.equals(this.notFound, result.notFound);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(destroyed, notFound);
    }

}
