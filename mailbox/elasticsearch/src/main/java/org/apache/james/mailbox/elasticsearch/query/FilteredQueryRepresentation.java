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

package org.apache.james.mailbox.elasticsearch.query;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

public  class FilteredQueryRepresentation {

    public static FilteredQueryRepresentation fromQuery(QueryBuilder query) {
        return new FilteredQueryRepresentation(Optional.of(query), Optional.empty());
    }

    public static FilteredQueryRepresentation fromFilter(FilterBuilder filter) {
        return new FilteredQueryRepresentation(Optional.empty(), Optional.of(filter));
    }

    public static FilteredQueryRepresentation empty() {
        return new FilteredQueryRepresentation(Optional.empty(), Optional.empty());
    }

    private final Optional<FilterBuilder> filter;
    private final Optional<QueryBuilder> query;

    private FilteredQueryRepresentation(Optional<QueryBuilder> query, Optional<FilterBuilder> filter) {
        this.query = query;
        this.filter = filter;
    }

    public Optional<FilterBuilder> getFilter() {
        return filter;
    }

    public Optional<QueryBuilder> getQuery() {
        return query;
    }

    public FilteredQueryRepresentation combine(SearchQuery.Conjunction type, FilteredQueryRepresentation collected) {
        switch (type) {
        case OR:
            return applyOr(collected);
        case AND:
            return applyAnd(collected);
        case NOR:
            return new FilteredQueryRepresentation(
                applyNorOnQuery(collected),
                applyNorOnFilter(collected));
        }
        return this;
    }
    
    private FilteredQueryRepresentation applyAnd(FilteredQueryRepresentation collected) {
        return new FilteredQueryRepresentation(
                applyOnQuery(
                    (x, y) -> x.must(y),
                    collected.getQuery(),
                    (x) -> QueryBuilders.boolQuery().must(x)),
                applyOnFilter(
                    (x, y) -> x.must(y),
                    collected.getFilter(),
                    (x) -> FilterBuilders.boolFilter().must(x)));
    }

    private FilteredQueryRepresentation applyOr(FilteredQueryRepresentation collected) {
        return new FilteredQueryRepresentation(
            applyOnQuery(
                (x, y) -> x.should(y),
                collected.getQuery(),
                (x) -> QueryBuilders.boolQuery().should(x)),
            applyOnFilter(
                (x, y) -> x.should(y),
                collected.getFilter(),
                (x) -> FilterBuilders.boolFilter().should(x)));
    }

    private Optional<QueryBuilder> applyOnQuery(BiFunction<BoolQueryBuilder, QueryBuilder, QueryBuilder> function, Optional<QueryBuilder> input, Function<QueryBuilder, BoolQueryBuilder> s) {
        return genericApply(ensureBoolQuery(function, s), query, input);
    }

    private BiFunction<QueryBuilder, QueryBuilder, QueryBuilder> 
        ensureBoolQuery(BiFunction<BoolQueryBuilder, QueryBuilder, QueryBuilder> f, Function<QueryBuilder, BoolQueryBuilder> s) {
        return (x, y) -> f.apply(s.apply(x), y);
    }
    
    private Optional<FilterBuilder> applyOnFilter(BiFunction<BoolFilterBuilder, FilterBuilder, FilterBuilder> function, Optional<FilterBuilder> input, Function<FilterBuilder, BoolFilterBuilder> s) {
        return genericApply(ensureBoolFilter(function, s), filter, input);
    }

    private BiFunction<FilterBuilder, FilterBuilder, FilterBuilder> 
        ensureBoolFilter(BiFunction<BoolFilterBuilder, FilterBuilder, FilterBuilder> f, Function<FilterBuilder, BoolFilterBuilder> s) {
        return (x, y) -> f.apply(s.apply(x), y);
    }
    
    private <T> Optional<T> genericApply(BiFunction<T, T, T> function, Optional<T> lhs, Optional<T> rhs) {
        if (rhs.isPresent()) {
            if (lhs.isPresent()) {
                return Optional.of(function.apply(rhs.get(), lhs.get()));
            } else {
                return rhs;
            }
        } else {
            return lhs;
        }
    }

    private Optional<FilterBuilder> applyNorOnFilter(FilteredQueryRepresentation collected) {
        // The cast is necessary for determining types ( in other cases : Optional<BoolFilterBuilder> is incompatible with Optional<FilterBuilder>
        return collected.getFilter().map(
            (collectedFilter) -> filter.map(
                (innerFilter) -> Optional.of((FilterBuilder) FilterBuilders.boolFilter().must(innerFilter).mustNot(collectedFilter)))
                    .orElse(Optional.of(FilterBuilders.boolFilter().mustNot(collectedFilter)))
        ).orElse(filter);
    }

    private Optional<QueryBuilder> applyNorOnQuery(FilteredQueryRepresentation collected) {
        // The cast is necessary for determining types ( in other cases : Optional<BoolQueryBuilder> is incompatible with Optional<QueryBuilder>
        return collected.getQuery().map(
            (collectedQuery) -> query.map(
                (innerQuery) -> Optional.of((QueryBuilder)QueryBuilders.boolQuery().must(innerQuery).mustNot(collected.getQuery().get())))
                    .orElse(Optional.of(QueryBuilders.boolQuery().mustNot(collectedQuery)))
        ).orElse(query);
    }

}
