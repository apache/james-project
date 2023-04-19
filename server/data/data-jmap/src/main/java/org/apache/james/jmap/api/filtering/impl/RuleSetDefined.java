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

import java.util.Objects;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.jmap.api.filtering.Rule;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class RuleSetDefined implements Event {

    private final FilteringAggregateId aggregateId;
    private final EventId eventId;
    private final ImmutableList<Rule> rules;

    public RuleSetDefined(FilteringAggregateId aggregateId, EventId eventId, ImmutableList<Rule> rules) {
        this.aggregateId = aggregateId;
        this.eventId = eventId;
        this.rules = rules;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public AggregateId getAggregateId() {
        return aggregateId;
    }

    public ImmutableList<Rule> getRules() {
        return rules;
    }

    @Override
    public boolean isASnapshot() {
        return true;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RuleSetDefined that = (RuleSetDefined) o;
        return Objects.equals(aggregateId, that.aggregateId) &&
            Objects.equals(eventId, that.eventId) &&
            Objects.equals(rules, that.rules);
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
