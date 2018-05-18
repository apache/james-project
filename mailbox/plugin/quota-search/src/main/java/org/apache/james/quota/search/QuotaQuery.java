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

package org.apache.james.quota.search;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.Domain;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class QuotaQuery {

    public static class Builder {
        private ImmutableList.Builder<QuotaClause> clauses;
        private Optional<Limit> limit;
        private Optional<Offset> offset;

        private Builder() {
            clauses = ImmutableList.builder();
            limit = Optional.empty();
            offset = Optional.empty();
        }

        public Builder moreThan(QuotaBoundary quotaBoundary) {
            clauses.add(QuotaClause.moreThan(quotaBoundary));
            return this;
        }

        public Builder lessThan(QuotaBoundary quotaBoundary) {
            clauses.add(QuotaClause.lessThan(quotaBoundary));
            return this;
        }

        public Builder hasDomain(Domain domain) {
            clauses.add(QuotaClause.hasDomain(domain));
            return this;
        }

        public Builder withLimit(Limit limit) {
            this.limit = Optional.of(limit);
            return this;
        }

        public Builder withOffset(Offset offset) {
            this.offset = Optional.of(offset);
            return this;
        }

        public QuotaQuery build() {
            return new QuotaQuery(
                QuotaClause.and(clauses.build()),
                limit.orElse(Limit.unlimited()),
                offset.orElse(Offset.none()));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final QuotaClause.And clause;
    private final Limit limit;
    private final Offset offset;

    private QuotaQuery(QuotaClause.And clause, Limit limit, Offset offset) {
        this.clause = clause;
        this.limit = limit;
        this.offset = offset;
    }

    public QuotaClause.And getClause() {
        return clause;
    }

    public Limit getLimit() {
        return limit;
    }

    public Offset getOffset() {
        return offset;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof QuotaQuery) {
            QuotaQuery that = (QuotaQuery) o;

            return Objects.equals(this.clause, that.clause)
                && Objects.equals(this.limit, that.limit)
                && Objects.equals(this.offset, that.offset);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(clause, limit, offset);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("clause", clause)
            .add("limit", limit)
            .add("offset", offset)
            .toString();
    }
}
