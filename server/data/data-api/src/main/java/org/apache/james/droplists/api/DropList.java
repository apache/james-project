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
package org.apache.james.droplists.api;

import org.apache.james.core.MailAddress;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface for managing email droplists.
 */
public interface DropList {

    /**
     * Add an entry to the droplist.
     *
     * @param entry The entry to add.
     * @return A Mono representing the completion of the operation.
     */
    Mono<Void> add(DropListEntry entry);

    /**
     * Remove an entry from the droplist.
     *
     * @param entry The entry to remove.
     * @return A Mono representing the completion of the operation.
     */
    Mono<Void> remove(DropListEntry entry);

    /**
     * List all entries in the droplist for a specific owner.
     *
     * @param ownerScope The scope of the owner.
     * @param owner      The owner for which to list the entries.
     * @return A Flux emitting each entry in the droplist.
     */
    Flux<DropListEntry> list(OwnerScope ownerScope, String owner);

    enum Status {
        ALLOWED,
        BLOCKED
    }

    /**
     * Query the status of a sender's email address in the droplist.
     *
     * @param ownerScope The scope of the owner.
     * @param owner      The owner for which to query the status.
     * @param sender     The email address of the sender.
     * @return A Mono emitting the status of the sender's email address (ALLOWED or BLOCKED).
     */
    Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender);
}
