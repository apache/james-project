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

package org.apache.james.jmap.cassandra.filtering;

import java.util.List;
import java.util.Objects;

import org.apache.james.jmap.api.filtering.Rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class RuleDTO {

    public static ImmutableList<Rule> toRules(List<RuleDTO> ruleDTOList) {
        Preconditions.checkNotNull(ruleDTOList);
        return ruleDTOList.stream()
                .map(dto -> Rule.of(dto.getId()))
                .collect(ImmutableList.toImmutableList());
    }

    public static ImmutableList<RuleDTO> from(List<Rule> rules) {
        Preconditions.checkNotNull(rules);
        return rules.stream()
            .map(RuleDTO::from)
            .collect(ImmutableList.toImmutableList());
    }

    public static RuleDTO from(Rule rule) {
        return new RuleDTO(rule.getId());
    }

    private final String id;

    @JsonCreator
    public RuleDTO(@JsonProperty("id") String id) {
        Preconditions.checkNotNull(id);

        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof RuleDTO) {
            RuleDTO ruleDTO = (RuleDTO) o;

            return Objects.equals(this.id, ruleDTO.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString();
    }
}
