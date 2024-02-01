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
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.RuleDTO;
import org.apache.james.jmap.api.filtering.impl.FilteringAggregateId;
import org.apache.james.jmap.api.filtering.impl.IncrementalRuleChange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class FilteringIncrementalRuleChangeDTO implements EventDTO {

    public static FilteringIncrementalRuleChangeDTO from(IncrementalRuleChange event, String type) {
        return new FilteringIncrementalRuleChangeDTO(
            type, event.eventId().serialize(),
            event.getAggregateId().asAggregateKey(),
            RuleDTO.from(event.getRulesPrepended()),
            RuleDTO.from(event.getRulesPostPended()),
            RuleDTO.from(event.getRulesUpdated()),
            event.getRulesDeleted().stream()
                .map(id -> id.asString())
                .collect(ImmutableSet.toImmutableSet()));
    }

    public static FilteringIncrementalRuleChangeDTO from(IncrementalRuleChange event) {
        return from(event, FilteringRuleSetDefineDTOModules.TYPE_INCREMENTAL);
    }

    private final String type;
    private final int eventId;
    private final String aggregateId;
    private final ImmutableList<RuleDTO> prepended;
    private final ImmutableList<RuleDTO> postpended;
    private final ImmutableSet<String> deleted;
    private final ImmutableList<RuleDTO> updated;


    @JsonCreator
    public FilteringIncrementalRuleChangeDTO(@JsonProperty("type") String type,
                                             @JsonProperty("eventId") int eventId,
                                             @JsonProperty("aggregateId") String aggregateId,
                                             @JsonProperty("prepended") ImmutableList<RuleDTO> prepended,
                                             @JsonProperty("postpended") ImmutableList<RuleDTO> postpended,
                                             @JsonProperty("updated") ImmutableList<RuleDTO> updated,
                                             @JsonProperty("deleted") ImmutableSet<String> deleted) {
        this.type = type;
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.prepended = prepended;
        this.postpended = postpended;
        this.updated = updated;
        this.deleted = deleted;
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

    public ImmutableList<RuleDTO> getPrepended() {
        return prepended;
    }

    public ImmutableList<RuleDTO> getPostpended() {
        return postpended;
    }

    public ImmutableSet<String> getDeleted() {
        return deleted;
    }

    public ImmutableList<RuleDTO> getUpdated() {
        return updated;
    }

    @JsonIgnore
    public IncrementalRuleChange toEvent() {
        return new IncrementalRuleChange(
            FilteringAggregateId.parse(aggregateId),
            EventId.fromSerialized(eventId),
            RuleDTO.toRules(prepended),
            RuleDTO.toRules(postpended),
            deleted.stream()
                .map(Rule.Id::of)
                .collect(ImmutableSet.toImmutableSet()),
            RuleDTO.toRules(updated));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FilteringIncrementalRuleChangeDTO) {
            FilteringIncrementalRuleChangeDTO that = (FilteringIncrementalRuleChangeDTO) o;

            return Objects.equals(this.eventId, that.eventId)
                && Objects.equals(this.type, that.type)
                && Objects.equals(this.aggregateId, that.aggregateId)
                && Objects.equals(this.prepended, that.prepended)
                && Objects.equals(this.postpended, that.postpended)
                && Objects.equals(this.updated, that.updated)
                && Objects.equals(this.deleted, that.deleted);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(type, eventId, aggregateId, prepended, postpended, deleted, updated);
    }
}
