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

package org.apache.james.mailbox.quota.mailing.commands;

import java.util.List;

import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.aggregates.UserQuotaThresholds;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class DetectThresholdCrossingHandler implements CommandHandler<DetectThresholdCrossing> {

    private final EventStore eventStore;
    private final QuotaMailingListenerConfiguration quotaMailingListenerConfiguration;
    private final String listenerName;

    public DetectThresholdCrossingHandler(EventStore eventStore, QuotaMailingListenerConfiguration quotaMailingListenerConfiguration) {
        this.eventStore = eventStore;
        this.quotaMailingListenerConfiguration = quotaMailingListenerConfiguration;
        this.listenerName = quotaMailingListenerConfiguration.getName();
    }

    @Override
    public Publisher<List<EventWithState>> handle(DetectThresholdCrossing command) {
        return loadAggregate(command)
            .map(aggregate -> aggregate.detectThresholdCrossing(quotaMailingListenerConfiguration, command));
    }

    private Mono<UserQuotaThresholds> loadAggregate(DetectThresholdCrossing command) {
        UserQuotaThresholds.Id aggregateId = UserQuotaThresholds.Id.from(command.getUsername(), listenerName);
        return Mono.from(eventStore.getEventsOfAggregate(aggregateId))
            .map(history -> UserQuotaThresholds.fromEvents(aggregateId, history));
    }

    @Override
    public Class<DetectThresholdCrossing> handledClass() {
        return DetectThresholdCrossing.class;
    }
}
