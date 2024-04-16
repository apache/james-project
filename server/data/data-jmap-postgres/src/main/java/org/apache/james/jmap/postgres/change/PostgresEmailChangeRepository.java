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

package org.apache.james.jmap.postgres.change;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.EmailChanges;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresEmailChangeRepository implements EmailChangeRepository {
    public static final String LIMIT_NAME = "emailChangeDefaultLimit";

    private final PostgresExecutor.Factory executorFactory;
    private final Limit defaultLimit;

    @Inject
    public PostgresEmailChangeRepository(PostgresExecutor.Factory executorFactory, @Named(LIMIT_NAME) Limit defaultLimit) {
        this.executorFactory = executorFactory;
        this.defaultLimit = defaultLimit;
    }

    @Override
    public Mono<Void> save(EmailChange change) {
        PostgresEmailChangeDAO emailChangeDAO = createPostgresEmailChangeDAO(change.getAccountId());
        return emailChangeDAO.insert(change);
    }

    @Override
    public Mono<EmailChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        PostgresEmailChangeDAO emailChangeDAO = createPostgresEmailChangeDAO(accountId);
        if (state.equals(State.INITIAL)) {
            return emailChangeDAO.getAllChanges(accountId)
                .filter(change -> !change.isShared())
                .collect(new EmailChanges.Builder.EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return emailChangeDAO.getChangesSince(accountId, state)
            .switchIfEmpty(Flux.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .filter(change -> !change.isShared())
            .filter(change -> !change.getState().equals(state))
            .collect(new EmailChanges.Builder.EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<EmailChanges> getSinceStateWithDelegation(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        PostgresEmailChangeDAO emailChangeDAO = createPostgresEmailChangeDAO(accountId);
        if (state.equals(State.INITIAL)) {
            return emailChangeDAO.getAllChanges(accountId)
                .collect(new EmailChanges.Builder.EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return emailChangeDAO.getChangesSince(accountId, state)
            .switchIfEmpty(Flux.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .filter(change -> !change.getState().equals(state))
            .collect(new EmailChanges.Builder.EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        PostgresEmailChangeDAO emailChangeDAO = createPostgresEmailChangeDAO(accountId);
        return emailChangeDAO.latestStateNotDelegated(accountId)
            .switchIfEmpty(Mono.just(State.INITIAL));
    }

    @Override
    public Mono<State> getLatestStateWithDelegation(AccountId accountId) {
        PostgresEmailChangeDAO emailChangeDAO = createPostgresEmailChangeDAO(accountId);
        return emailChangeDAO.latestState(accountId)
            .switchIfEmpty(Mono.just(State.INITIAL));
    }

    private PostgresEmailChangeDAO createPostgresEmailChangeDAO(AccountId accountId) {
        return new PostgresEmailChangeDAO(executorFactory.create(Username.of(accountId.getIdentifier()).getDomainPart()));
    }
}
