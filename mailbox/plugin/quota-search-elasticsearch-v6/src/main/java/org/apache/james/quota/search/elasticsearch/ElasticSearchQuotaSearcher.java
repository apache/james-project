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

package org.apache.james.quota.search.elasticsearch;

import static org.apache.james.quota.search.elasticsearch.QuotaRatioElasticSearchConstants.QUOTA_RATIO_TYPE;
import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.USER;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.search.ScrollIterable;
import org.apache.james.core.User;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.github.steveash.guavate.Guavate;

public class ElasticSearchQuotaSearcher implements QuotaSearcher {
    private static final TimeValue TIMEOUT = new TimeValue(60000);

    private final Client client;
    private final AliasName readAlias;
    private final QuotaQueryConverter quotaQueryConverter;

    public ElasticSearchQuotaSearcher(Client client, ReadAliasName readAlias) {
        this.client = client;
        this.readAlias = readAlias;
        this.quotaQueryConverter = new QuotaQueryConverter();
    }

    @Override
    public List<User> search(QuotaQuery query) {
        Stream<User> results = new ScrollIterable(client, prepareSearch(query))
            .stream()
            .flatMap(searchResponse -> Arrays.stream(searchResponse.getHits()
                .getHits()))
            .map(hit -> hit.field(USER))
            .map(field -> (String) field.getValue())
            .map(User::fromUsername)
            .skip(query.getOffset().getValue());

        return query.getLimit().getValue()
            .map(results::limit)
            .orElse(results)
            .collect(Guavate.toImmutableList());
    }

    public SearchRequestBuilder prepareSearch(QuotaQuery query) {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(readAlias.getValue())
            .setTypes(QUOTA_RATIO_TYPE.getValue())
            .setScroll(TIMEOUT)
            .addFields(USER)
            .setQuery(quotaQueryConverter.from(query));

        query.getLimit()
            .getValue()
            .ifPresent(searchRequestBuilder::setSize);

        searchRequestBuilder.addSort(
            SortBuilders.fieldSort(USER)
                .order(SortOrder.ASC));

        return searchRequestBuilder;
    }

}