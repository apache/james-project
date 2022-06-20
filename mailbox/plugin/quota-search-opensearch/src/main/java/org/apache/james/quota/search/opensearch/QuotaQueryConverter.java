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

package org.apache.james.quota.search.opensearch;

import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.QUOTA_RATIO;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.james.quota.search.QuotaClause;
import org.apache.james.quota.search.QuotaClause.And;
import org.apache.james.quota.search.QuotaClause.HasDomain;
import org.apache.james.quota.search.QuotaClause.LessThan;
import org.apache.james.quota.search.QuotaClause.MoreThan;
import org.apache.james.quota.search.QuotaQuery;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.json.JsonData;

class QuotaQueryConverter {
    private final Map<Class<? extends QuotaClause>, Function<QuotaClause, Query>> clauseConverter;

    QuotaQueryConverter() {
        Builder<Class<? extends QuotaClause>, Function<QuotaClause, Query>> builder = ImmutableMap.builder();
        
        builder.put(HasDomain.class, this::convertHasDomain);
        builder.put(And.class, this::disableNestedAnd);
        builder.put(MoreThan.class, this::convertMoreThan);
        builder.put(LessThan.class, this::convertLessThan);

        clauseConverter = builder.build();
    }

    Query from(QuotaQuery query) {
        List<QuotaClause> clauses = query.getClause().getClauses();
        if (clauses.isEmpty()) {
            return new MatchAllQuery.Builder().build()._toQuery();
        }
        if (clauses.size() == 1) {
            return singleClauseAsESQuery(clauses.get(0));
        }
        
        return clausesAsAndESQuery(clauses);
    }

    private Query clausesAsAndESQuery(List<QuotaClause> clauses) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        clauses.stream()
            .map(this::singleClauseAsESQuery)
            .forEach(boolQueryBuilder::must);
        return boolQueryBuilder.build()._toQuery();
    }

    private Query disableNestedAnd(QuotaClause clause) {
        throw new IllegalArgumentException("Nested \"And\" clauses are not supported");
    }

    private Query convertHasDomain(QuotaClause clause) {
        HasDomain hasDomain = (HasDomain) clause;
        return new TermQuery.Builder()
            .field(DOMAIN)
            .value(hasDomain.getDomain().asString())
            .build()
            ._toQuery();
    }

    private Query convertMoreThan(QuotaClause clause) {
        MoreThan moreThan = (MoreThan) clause;
        return new RangeQuery.Builder()
            .field(QUOTA_RATIO)
            .gte(JsonData.of(moreThan.getQuotaBoundary().getRatio()))
            .build()
            ._toQuery();
    }

    private Query convertLessThan(QuotaClause clause) {
        LessThan lessThan = (LessThan) clause;
        return new RangeQuery.Builder()
            .field(QUOTA_RATIO)
            .lte(JsonData.of(lessThan.getQuotaBoundary().getRatio()))
            .build()
            ._toQuery();
    }

    private Query singleClauseAsESQuery(QuotaClause clause) {
        return clauseConverter.get(clause.getClass()).apply(clause);
    }

}
