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

package org.apache.james.jmap.api.filtering.impl;

import java.util.List;

import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class DefineRulesCommandHandler implements CommandHandler<DefineRulesCommand> {
    private final EventStore eventStore;

    public DefineRulesCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public Class<DefineRulesCommand> handledClass() {
        return DefineRulesCommand.class;
    }

    @Override
    public Publisher<List<EventWithState>> handle(DefineRulesCommand storeCommand) {
        FilteringAggregateId aggregateId = new FilteringAggregateId(storeCommand.getUsername());

        return Mono.from(eventStore.getEventsOfAggregate(aggregateId))
            .map(history -> FilteringAggregate.load(aggregateId, history).defineRules(storeCommand));
    }

}
