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

package org.apache.james.jmap.cassandra.change;

import java.util.Optional;

import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.EmailChanges;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;

import reactor.core.publisher.Mono;

public class CassandraEmailChangeRepository implements EmailChangeRepository {

    @Override
    public Mono<Void> save(EmailChange change) {
        return Mono.empty();
    }

    @Override
    public Mono<EmailChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxIdsToReturn) {
        return Mono.empty();
    }

    @Override
    public Mono<EmailChanges> getSinceStateWithDelegation(AccountId accountId, State state, Optional<Limit> maxIdsToReturn) {
        return Mono.empty();
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        return Mono.empty();
    }

    @Override
    public Mono<State> getLatestStateWithDelegation(AccountId accountId) {
        return Mono.empty();
    }
}
