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

package org.apache.james.dlp.eventsourcing.events;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.eventsourcing.aggregates.DLPAggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class ConfigurationItemsAdded implements Event {
    private final DLPAggregateId aggregateId;
    private final EventId eventId;
    private final List<DLPConfigurationItem> rules;

    public ConfigurationItemsAdded(DLPAggregateId aggregateId, EventId eventId, Collection<DLPConfigurationItem> rules) {
        Preconditions.checkNotNull(aggregateId);
        Preconditions.checkNotNull(eventId);
        Preconditions.checkNotNull(rules);
        Preconditions.checkArgument(!rules.isEmpty());

        this.aggregateId = aggregateId;
        this.eventId = eventId;
        this.rules = ImmutableList.copyOf(rules);
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    public DLPAggregateId getAggregateId() {
        return aggregateId;
    }

    public List<DLPConfigurationItem> getRules() {
        return rules;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ConfigurationItemsAdded) {
            ConfigurationItemsAdded that = (ConfigurationItemsAdded) o;

            return Objects.equals(this.aggregateId, that.aggregateId)
                && Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.rules, that.rules);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(aggregateId, eventId, rules);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("aggregateId", aggregateId)
            .add("eventId", eventId)
            .add("rules", rules)
            .toString();
    }
}
