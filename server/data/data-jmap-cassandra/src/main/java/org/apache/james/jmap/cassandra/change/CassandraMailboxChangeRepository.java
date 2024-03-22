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

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.MailboxChanges;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraMailboxChangeRepository implements MailboxChangeRepository {
    public static final String LIMIT_NAME = "mailboxChangeDefaultLimit";

    private final MailboxChangeRepositoryDAO mailboxChangeRepositoryDAO;
    private final Limit defaultLimit;

    @Inject
    public CassandraMailboxChangeRepository(MailboxChangeRepositoryDAO mailboxChangeRepositoryDAO, @Named(LIMIT_NAME) Limit defaultLimit) {
        this.mailboxChangeRepositoryDAO = mailboxChangeRepositoryDAO;
        this.defaultLimit = defaultLimit;
    }

    @Override
    public Mono<Void> save(MailboxChange change) {
        return mailboxChangeRepositoryDAO.insert(change);
    }

    @Override
    public Mono<MailboxChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        if (state.equals(State.INITIAL)) {
            return mailboxChangeRepositoryDAO.getAllChanges(accountId)
                .filter(change -> !change.isShared())
                .collect(new MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return mailboxChangeRepositoryDAO.getChangesSince(accountId, state)
            .switchIfEmpty(Flux.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .filter(change -> !change.isShared())
            .filter(change -> !change.getState().equals(state))
            .collect(new MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<MailboxChanges> getSinceStateWithDelegation(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        if (state.equals(State.INITIAL)) {
            return mailboxChangeRepositoryDAO.getAllChanges(accountId)
                .collect(new MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return mailboxChangeRepositoryDAO.getChangesSince(accountId, state)
            .switchIfEmpty(Flux.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .filter(change -> !change.getState().equals(state))
            .collect(new MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        return mailboxChangeRepositoryDAO.latestStateNotDelegated(accountId)
            .switchIfEmpty(Mono.just(State.INITIAL));
    }

    @Override
    public Mono<State> getLatestStateWithDelegation(AccountId accountId) {
        return mailboxChangeRepositoryDAO.latestState(accountId)
            .switchIfEmpty(Mono.just(State.INITIAL));
    }
}
