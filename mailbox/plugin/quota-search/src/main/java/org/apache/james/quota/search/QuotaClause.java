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

import java.util.List;
import java.util.Objects;

import org.apache.james.core.Domain;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public interface QuotaClause {

    class LessThan implements QuotaClause {

        private final QuotaBoundary quotaBoundary;

        private LessThan(QuotaBoundary quotaBoundary) {
            Preconditions.checkNotNull(quotaBoundary, "'quotaBoundary' is mandatory");
            this.quotaBoundary = quotaBoundary;
        }

        public QuotaBoundary getQuotaBoundary() {
            return quotaBoundary;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof LessThan) {
                LessThan lessThan = (LessThan) o;

                return Objects.equals(this.quotaBoundary, lessThan.quotaBoundary);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaBoundary);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaBoundary", quotaBoundary)
                .toString();
        }
    }

    class MoreThan implements QuotaClause {

        private final QuotaBoundary quotaBoundary;

        private MoreThan(QuotaBoundary quotaBoundary) {
            Preconditions.checkNotNull(quotaBoundary, "'quotaBoundary' is mandatory");
            this.quotaBoundary = quotaBoundary;
        }

        public QuotaBoundary getQuotaBoundary() {
            return quotaBoundary;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MoreThan) {
                MoreThan moreThan = (MoreThan) o;

                return Objects.equals(this.quotaBoundary, moreThan.quotaBoundary);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaBoundary);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaRatio", quotaBoundary)
                .toString();
        }
    }

    class HasDomain implements QuotaClause {

        private final Domain domain;

        private HasDomain(Domain domain) {
            Preconditions.checkNotNull(domain, "'domain' is mandatory");
            this.domain = domain;
        }

        public Domain getDomain() {
            return domain;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof HasDomain) {
                HasDomain hasDomain = (HasDomain) o;

                return Objects.equals(this.domain, hasDomain.domain);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(domain);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("domain", domain)
                .toString();
        }
    }

    class And implements QuotaClause {

        private final ImmutableList<QuotaClause> clauses;

        private And(List<QuotaClause> clauses) {
            Preconditions.checkNotNull(clauses, "'clauses' is mandatory");
            Preconditions.checkArgument(doesNotContainAnd(clauses), "Nested And clauses are not supported");
            this.clauses = ImmutableList.copyOf(clauses);
        }

        private boolean doesNotContainAnd(List<QuotaClause> clauses) {
            return clauses.stream()
                .noneMatch(clause -> clause instanceof And);
        }

        public List<QuotaClause> getClauses() {
            return clauses;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof And) {
                And and = (And) o;

                return Objects.equals(this.clauses, and.clauses);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(clauses);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("clauses", clauses)
                .toString();
        }
    }

    static LessThan lessThan(QuotaBoundary quotaBoundary) {
        return new LessThan(quotaBoundary);
    }

    static MoreThan moreThan(QuotaBoundary quotaBoundary) {
        return new MoreThan(quotaBoundary);
    }

    static HasDomain hasDomain(Domain domain) {
        return new HasDomain(domain);
    }

    static And and(List<QuotaClause> clauses) {
        return new And(clauses);
    }

    static And and(QuotaClause... clauses) {
        return new And(ImmutableList.copyOf(clauses));
    }
}
