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

package org.apache.james.vault.dto.query;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.vault.search.Combinator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(as = QueryDTO.class)
public class QueryDTO implements QueryElement {

    @VisibleForTesting
    static QueryDTO and(QueryElement... queryElements) {
        return new QueryDTO(Combinator.AND.getValue(), ImmutableList.copyOf(queryElements), Optional.empty());
    }

    @VisibleForTesting
    static QueryDTO and(Long limit, QueryElement... queryElements) {
        return new QueryDTO(Combinator.AND.getValue(), ImmutableList.copyOf(queryElements), Optional.ofNullable(limit));
    }

    private final String combinator;
    private final List<QueryElement> criteria;
    private final Optional<Long> limit;

    @JsonCreator
    public QueryDTO(@JsonProperty("combinator") String combinator,
                    @JsonProperty("criteria") List<QueryElement> criteria,
                    @JsonProperty("limit") Optional<Long> limit) {
        this.combinator = combinator;
        this.criteria = criteria;
        this.limit = Optional.ofNullable(limit).orElse(Optional.empty());
    }

    public String getCombinator() {
        return combinator;
    }

    public List<QueryElement> getCriteria() {
        return criteria;
    }

    public Optional<Long> getLimit() {
        return limit;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QueryDTO) {
            QueryDTO queryDTO = (QueryDTO) o;

            return Objects.equals(this.combinator, queryDTO.getCombinator())
                && Objects.equals(this.criteria, queryDTO.getCriteria())
                && Objects.equals(this.limit, queryDTO.getLimit());
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(combinator, criteria, limit);
    }
}
