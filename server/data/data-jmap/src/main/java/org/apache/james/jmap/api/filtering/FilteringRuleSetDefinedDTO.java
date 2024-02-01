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

package org.apache.james.jmap.api.filtering;

import java.util.Objects;

import org.apache.james.eventsourcing.EventId;
import org.apache.james.eventsourcing.eventstore.dto.EventDTO;
import org.apache.james.jmap.api.filtering.RuleDTO;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.RuleSetDefined;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public class FilteringRuleSetDefinedDTO implements EventDTO {

    public static FilteringRuleSetDefinedDTO from(RuleSetDefined event, String type) {
        return new FilteringRuleSetDefinedDTO(
            type, event.eventId().serialize(),
            event.getAggregateId().asAggregateKey(),
            RuleDTO.from(event.getRules()));
    }

    public static FilteringRuleSetDefinedDTO from(RuleSetDefined event) {
        return from(event, FilteringRuleSetDefineDTOModules.TYPE);
    }

    private final String type;
    private final int eventId;
    private final String aggregateId;
    private final ImmutableList<RuleDTO> rules;

    @JsonCreator
    public FilteringRuleSetDefinedDTO(@JsonProperty("type") String type,
                                     @JsonProperty("eventId") int eventId,
                                     @JsonProperty("aggregateId") String aggregateId,
                                     @JsonProperty("rules") ImmutableList<RuleDTO> rules) {
        this.type = type;
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.rules = rules;
    }

    public String getType() {
        return type;
    }

    public int getEventId() {
        return eventId;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public ImmutableList<RuleDTO> getRules() {
        return rules;
    }

    @JsonIgnore
    public RuleSetDefined toEvent() {
        return new RuleSetDefined(
            FilteringAggregateId.parse(aggregateId),
            EventId.fromSerialized(eventId),
            RuleDTO.toRules(rules));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FilteringRuleSetDefinedDTO) {
            FilteringRuleSetDefinedDTO that = (FilteringRuleSetDefinedDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.aggregateId, that.aggregateId)
                && Objects.equals(this.rules, that.rules);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, eventId, aggregateId, rules);
    }
}
