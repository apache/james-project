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

package org.apache.james.mailbox.quota.mailing.aggregates;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.EventWithState;
import org.apache.james.eventsourcing.eventstore.History;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.quota.mailing.QuotaMailingListenerConfiguration;
import org.apache.james.mailbox.quota.mailing.commands.DetectThresholdCrossing;
import org.apache.james.mailbox.quota.mailing.events.QuotaThresholdChangedEvent;
import org.apache.james.mailbox.quota.model.HistoryEvolution;
import org.apache.james.mailbox.quota.model.QuotaThresholdChange;
import org.apache.james.mailbox.quota.model.QuotaThresholdHistory;
import org.apache.james.mailbox.quota.model.QuotaThresholds;

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

public class UserQuotaThresholds {

    public static class Id implements AggregateId {

        private static final int PREFIX_INDEX = 0;
        private static final int NAME_INDEX = 1;
        private static final int USER_INDEX = 2;
        private static final String SEPARATOR = "/";
        private static final String PREFIX = "QuotaThreasholdEvents";

        public static Id fromKey(String key) {
            List<String> keyParts = Splitter.on(SEPARATOR).splitToList(key);
            if (keyParts.size() != 3 || !keyParts.get(PREFIX_INDEX).equals(PREFIX)) {
                throw new IllegalArgumentException();
            }
            return new Id(Username.of(keyParts.get(USER_INDEX)), keyParts.get(NAME_INDEX));
        }

        public static Id from(Username username, String name) {
            return new Id(username, name);
        }

        private final Username username;
        private final String name;

        private Id(Username username, String name) {
            Preconditions.checkArgument(!username.asString().contains(SEPARATOR));
            Preconditions.checkArgument(!name.contains(SEPARATOR));
            this.username = username;
            this.name = name;
        }

        public Username getUsername() {
            return username;
        }

        @Override
        public String asAggregateKey() {
            return Joiner.on(SEPARATOR).join(PREFIX, name, username.asString());
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof Id) {
                Id id = (Id) o;

                return Objects.equals(this.username, id.username)
                    && Objects.equals(this.name, id.name);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(username, name);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("user", username)
                .add("name", name)
                .toString();
        }
    }

    public static UserQuotaThresholds fromEvents(Id aggregateId, History history) {
        return new UserQuotaThresholds(aggregateId, history);
    }

    private final Id aggregateId;
    private final History history;
    private final List<QuotaThresholdChangedEvent> events;

    private UserQuotaThresholds(Id aggregateId, History history) {
        this.aggregateId = aggregateId;
        this.history = history;
        this.events = history.getEventsJava()
            .stream()
            .map(QuotaThresholdChangedEvent.class::cast)
            .collect(Collectors.toList());
    }

    public List<EventWithState> detectThresholdCrossing(QuotaMailingListenerConfiguration configuration,
                                                        DetectThresholdCrossing command) {

        List<QuotaThresholdChangedEvent> events = generateEvents(
            configuration.getThresholds(),
            configuration.getGracePeriod(),
            command.getCountQuota(),
            command.getSizeQuota(),
            command.getInstant());
        events.forEach(this::apply);
        return events.stream()
            .map(EventWithState::noState)
            .collect(ImmutableList.toImmutableList());
    }

    private List<QuotaThresholdChangedEvent> generateEvents(QuotaThresholds configuration, Duration gracePeriod, Quota<QuotaCountLimit, QuotaCountUsage> countQuota, Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota, Instant now) {
        QuotaThresholdChange countThresholdChange = new QuotaThresholdChange(configuration.highestExceededThreshold(countQuota), now);
        QuotaThresholdChange sizeThresholdChange = new QuotaThresholdChange(configuration.highestExceededThreshold(sizeQuota), now);

        HistoryEvolution countHistoryEvolution = computeCountHistory()
            .compareWithCurrentThreshold(countThresholdChange, gracePeriod);
        HistoryEvolution sizeHistoryEvolution = computeSizeHistory()
            .compareWithCurrentThreshold(sizeThresholdChange, gracePeriod);

        return generateEvents(countHistoryEvolution, sizeHistoryEvolution, countQuota, sizeQuota);
    }

    private QuotaThresholdHistory computeSizeHistory() {
        return new QuotaThresholdHistory(
            events.stream()
                .map(QuotaThresholdChangedEvent::getSizeHistoryEvolution)
                .map(HistoryEvolution::getThresholdChange)
                .flatMap(Optional::stream)
                .collect(ImmutableList.toImmutableList()));
    }

    private QuotaThresholdHistory computeCountHistory() {
        return new QuotaThresholdHistory(
            events.stream()
                .map(QuotaThresholdChangedEvent::getCountHistoryEvolution)
                .map(HistoryEvolution::getThresholdChange)
                .flatMap(Optional::stream)
                .collect(ImmutableList.toImmutableList()));
    }

    private List<QuotaThresholdChangedEvent> generateEvents(HistoryEvolution countHistoryEvolution, HistoryEvolution sizeHistoryEvolution, Quota<QuotaCountLimit, QuotaCountUsage> countQuota, Quota<QuotaSizeLimit, QuotaSizeUsage> sizeQuota) {
        if (countHistoryEvolution.isChange() || sizeHistoryEvolution.isChange()) {
            return ImmutableList.of(
                new QuotaThresholdChangedEvent(
                    history.getNextEventId(),
                    sizeHistoryEvolution,
                    countHistoryEvolution,
                    sizeQuota,
                    countQuota,
                    aggregateId));
        }

        return ImmutableList.of();
    }

    private void apply(QuotaThresholdChangedEvent event) {
        events.add(event);
    }

}
