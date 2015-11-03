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

import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.common.lang3.tuple.Pair;
import org.elasticsearch.index.query.QueryBuilder;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.elasticsearch.index.query.FilterBuilders.termFilter;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.filteredQuery;

public class QueryConverter implements Function<Pair<SearchQuery, String>, QueryBuilder> {


    private final CriterionConverter criterionConverter;

    @Inject
    public QueryConverter(CriterionConverter criterionConverter) {
        this.criterionConverter = criterionConverter;
    }

    @Override
    public QueryBuilder apply(Pair<SearchQuery, String> pair) {
        return from(pair.getLeft(), pair.getRight());
    }

    public QueryBuilder from(SearchQuery searchQuery, String mailboxUUID) {
        return Stream.of(generateQueryBuilder(searchQuery))
            .map((rep) -> addMailboxFilters(rep, mailboxUUID))
            .map(this::getFinalQuery)
            .findAny()
            .get();
    }

    private FilteredQueryRepresentation generateQueryBuilder(SearchQuery searchQuery) {
        List<SearchQuery.Criterion> criteria = searchQuery.getCriterias();
        if (criteria.isEmpty()) {
            return criterionConverter.convertCriterion(SearchQuery.all());
        } else if (criteria.size() == 1) {
            return criterionConverter.convertCriterion(criteria.get(0));
        } else {
            return criterionConverter.convertCriterion(new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.AND, criteria));
        }
    }

    private FilteredQueryRepresentation addMailboxFilters(FilteredQueryRepresentation elasticsearchQueryRepresentation, String mailboxUUID) {
        return Stream.of(elasticsearchQueryRepresentation,
            FilteredQueryRepresentation.fromFilter(termFilter(JsonMessageConstants.MAILBOX_ID, mailboxUUID)))
            .collect(FilteredQueryCollector.collector(SearchQuery.Conjunction.AND));
    }

    private QueryBuilder getFinalQuery(FilteredQueryRepresentation filteredQueryRepresentation) {
        QueryBuilder query = filteredQueryRepresentation.getQuery().orElse(matchAllQuery());
        if (!filteredQueryRepresentation.getFilter().isPresent()) {
            return query;
        }
        return filteredQuery(query, filteredQueryRepresentation.getFilter().get());
    }

}
