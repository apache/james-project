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

package org.apache.james.mailbox.events;

import java.util.Objects;
import java.util.UUID;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventDeadLetters {

    class InsertionId {
        public static InsertionId of(UUID uuid) {
            return new InsertionId(uuid);
        }

        public static InsertionId random() {
            return new InsertionId(UUID.randomUUID());
        }

        public static InsertionId of(String serialized) {
            return of(UUID.fromString(serialized));
        }

        private final UUID id;

        private InsertionId(UUID id) {
            Preconditions.checkNotNull(id);
            this.id = id;
        }

        public UUID getId() {
            return id;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof InsertionId) {
                InsertionId insertionId = (InsertionId) o;

                return Objects.equals(this.id, insertionId.id);
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

        public String asString() {
            return id.toString();
        }
    }


    String REGISTERED_GROUP_CANNOT_BE_NULL = "registeredGroup cannot be null";
    String FAIL_DELIVERED_EVENT_CANNOT_BE_NULL = "failDeliveredEvent cannot be null";
    String FAIL_DELIVERED_ID_INSERTION_CANNOT_BE_NULL = "failDeliveredInsertionId cannot be null";

    Mono<InsertionId> store(Group registeredGroup, Event failDeliveredEvent);

    Mono<Void> remove(Group registeredGroup, InsertionId failDeliveredInsertionId);

    Mono<Event> failedEvent(Group registeredGroup, InsertionId failDeliveredInsertionId);

    Flux<InsertionId> failedIds(Group registeredGroup);

    Flux<Group> groupsWithFailedEvents();
}
