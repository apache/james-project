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

package org.apache.james.quota.search.elasticsearch.v7;

import static org.apache.james.quota.search.elasticsearch.v7.json.JsonMessageConstants.DOMAIN;
import static org.apache.james.quota.search.elasticsearch.v7.json.JsonMessageConstants.QUOTA_RATIO;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.james.quota.search.QuotaClause;
import org.apache.james.quota.search.QuotaClause.And;
import org.apache.james.quota.search.QuotaClause.HasDomain;
import org.apache.james.quota.search.QuotaClause.LessThan;
import org.apache.james.quota.search.QuotaClause.MoreThan;
import org.apache.james.quota.search.QuotaQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

class QuotaQueryConverter {
    private final Map<Class<? extends QuotaClause>, Function<QuotaClause, QueryBuilder>> clauseConverter;

    QuotaQueryConverter() {
        Builder<Class<? extends QuotaClause>, Function<QuotaClause, QueryBuilder>> builder = ImmutableMap.builder();
        
        builder.put(HasDomain.class, this::convertHasDomain);
        builder.put(And.class, this::disableNestedAnd);
        builder.put(MoreThan.class, this::convertMoreThan);
        builder.put(LessThan.class, this::convertLessThan);

        clauseConverter = builder.build();
    }

    QueryBuilder from(QuotaQuery query) {
        List<QuotaClause> clauses = query.getClause().getClauses();
        if (clauses.isEmpty()) {
            return matchAllQuery();
        }
        if (clauses.size() == 1) {
            return singleClauseAsESQuery(clauses.get(0));
        }
        
        return clausesAsAndESQuery(clauses);
    }

    private BoolQueryBuilder clausesAsAndESQuery(List<QuotaClause> clauses) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        clauses.stream()
            .map(this::singleClauseAsESQuery)
            .forEach(boolQueryBuilder::must);
        return boolQueryBuilder;
    }

    private QueryBuilder disableNestedAnd(QuotaClause clause) {
        throw new IllegalArgumentException("Nested \"And\" clauses are not supported");
    }

    private TermQueryBuilder convertHasDomain(QuotaClause clause) {
        HasDomain hasDomain = (HasDomain) clause;
        return termQuery(DOMAIN, hasDomain.getDomain().asString());
    }

    private RangeQueryBuilder convertMoreThan(QuotaClause clause) {
        MoreThan moreThan = (MoreThan) clause;
        return rangeQuery(QUOTA_RATIO).gte(moreThan.getQuotaBoundary().getRatio());
    }

    private RangeQueryBuilder convertLessThan(QuotaClause clause) {
        LessThan lessThan = (LessThan) clause;
        return rangeQuery(QUOTA_RATIO).lte(lessThan.getQuotaBoundary().getRatio());
    }

    private QueryBuilder singleClauseAsESQuery(QuotaClause clause) {
        return clauseConverter.get(clause.getClass()).apply(clause);
    }

}
