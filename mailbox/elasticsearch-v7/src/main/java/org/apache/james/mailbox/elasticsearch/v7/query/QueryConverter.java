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

package org.apache.james.mailbox.elasticsearch.v7.query;

import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;

public class QueryConverter {


    private final CriterionConverter criterionConverter;

    @Inject
    public QueryConverter(CriterionConverter criterionConverter) {
        this.criterionConverter = criterionConverter;
    }

    public QueryBuilder from(Collection<MailboxId> mailboxIds, SearchQuery query) {
        BoolQueryBuilder boolQueryBuilder = boolQuery()
            .must(generateQueryBuilder(query));

        mailboxesQuery(mailboxIds).map(boolQueryBuilder::filter);
        return boolQueryBuilder;
    }

    private QueryBuilder generateQueryBuilder(SearchQuery searchQuery) {
        List<SearchQuery.Criterion> criteria = searchQuery.getCriteria();
        if (criteria.isEmpty()) {
            return criterionConverter.convertCriterion(SearchQuery.all());
        } else if (criteria.size() == 1) {
            return criterionConverter.convertCriterion(criteria.get(0));
        } else {
            return criterionConverter.convertCriterion(new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.AND, criteria));
        }
    }

    private Optional<QueryBuilder> mailboxesQuery(Collection<MailboxId> mailboxIds) {
        if (mailboxIds.isEmpty()) {
            return Optional.empty();
        }
        ImmutableList<String> ids = mailboxIds.stream()
                .map(MailboxId::serialize)
                .collect(Guavate.toImmutableList());
        return Optional.of(termsQuery(JsonMessageConstants.MAILBOX_ID, ids));
    }

}
