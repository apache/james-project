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

package org.apache.james.jmap.postgres.filtering;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.ReactiveSubscriber;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.jmap.api.filtering.Version;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregate;
import org.reactivestreams.Publisher;

public class PostgresFilteringProjection implements EventSourcingFilteringManagement.ReadProjection, ReactiveSubscriber {
    private final PostgresFilteringProjectionDAO postgresFilteringProjectionDAO;

    @Inject
    public PostgresFilteringProjection(PostgresFilteringProjectionDAO postgresFilteringProjectionDAO) {
        this.postgresFilteringProjectionDAO = postgresFilteringProjectionDAO;
    }

    @Override
    public Publisher<Void> handleReactive(EventWithState eventWithState) {
        Event event = eventWithState.event();
        FilteringAggregate.FilterState state = (FilteringAggregate.FilterState) eventWithState.state().get();
        return postgresFilteringProjectionDAO.upsert(event.getAggregateId(), event.eventId(), state.getRules());
    }

    @Override
    public Publisher<Rules> listRulesForUser(Username username) {
        return postgresFilteringProjectionDAO.listRulesForUser(username);
    }

    @Override
    public Publisher<Version> getLatestVersion(Username username) {
        return postgresFilteringProjectionDAO.getVersion(username);
    }

    @Override
    public Optional<ReactiveSubscriber> subscriber() {
        return Optional.of(this);
    }
}
