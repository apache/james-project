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
package org.apache.james.user.memory;

import org.apache.james.core.Username;
import org.apache.james.user.api.DelegationStore;
import org.reactivestreams.Publisher;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryDelegationStore implements DelegationStore {
    private final Multimap<Username, Username> delegations;

    public MemoryDelegationStore() {
        delegations = Multimaps.synchronizedSetMultimap(HashMultimap.create());
    }

    @Override
    public Publisher<Username> authorizedUsers(Username baseUser) {
        return Flux.fromIterable(delegations.get(baseUser))
            .distinct();
    }

    @Override
    public Publisher<Void> addAuthorizedUser(Username baseUser, Username userWithAccess) {
        return Mono.fromRunnable(() -> delegations.put(baseUser, userWithAccess));
    }

    @Override
    public Publisher<Void> removeAuthorizedUser(Username baseUser, Username userWithAccess) {
        return Mono.fromRunnable(() -> delegations.remove(baseUser, userWithAccess));
    }

    @Override
    public Publisher<Void> clear(Username baseUser) {
        return Mono.fromRunnable(() -> delegations.removeAll(baseUser));
    }
}
