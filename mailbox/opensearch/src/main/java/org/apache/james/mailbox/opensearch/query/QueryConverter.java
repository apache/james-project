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

package org.apache.james.mailbox.opensearch.query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermsQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermsQueryField;

import com.google.common.collect.ImmutableList;

public class QueryConverter {
    private final CriterionConverter criterionConverter;

    @Inject
    public QueryConverter(CriterionConverter criterionConverter) {
        this.criterionConverter = criterionConverter;
    }

    public Query from(Collection<MailboxId> mailboxIds, SearchQuery query) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder()
            .must(generateQueryBuilder(query));

        mailboxesQuery(mailboxIds).map(boolQueryBuilder::filter);
        return boolQueryBuilder.build()._toQuery();
    }

    private Query generateQueryBuilder(SearchQuery searchQuery) {
        List<SearchQuery.Criterion> criteria = searchQuery.getCriteria();
        if (criteria.isEmpty()) {
            return criterionConverter.convertCriterion(SearchQuery.all());
        } else if (criteria.size() == 1) {
            return criterionConverter.convertCriterion(criteria.get(0));
        } else {
            return criterionConverter.convertCriterion(new SearchQuery.ConjunctionCriterion(SearchQuery.Conjunction.AND, criteria));
        }
    }

    private Optional<Query> mailboxesQuery(Collection<MailboxId> mailboxIds) {
        if (mailboxIds.isEmpty()) {
            return Optional.empty();
        }
        ImmutableList<FieldValue> ids = mailboxIds.stream()
            .map(MailboxId::serialize)
            .map(id -> new FieldValue.Builder().stringValue(id).build())
            .collect(ImmutableList.toImmutableList());
        return Optional.of(new TermsQuery.Builder()
            .field(JsonMessageConstants.MAILBOX_ID)
            .terms(new TermsQueryField.Builder()
                .value(ids)
                .build())
            .build()
            ._toQuery());
    }

}
