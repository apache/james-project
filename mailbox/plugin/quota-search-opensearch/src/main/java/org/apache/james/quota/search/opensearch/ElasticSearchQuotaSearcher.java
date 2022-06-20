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

import static org.apache.james.quota.search.elasticsearch.v8.json.JsonMessageConstants.USER;

import java.util.List;

import org.apache.james.backends.es.v8.AliasName;
import org.apache.james.backends.es.v8.ReactorElasticSearchClient;
import org.apache.james.backends.es.v8.ReadAliasName;
import org.apache.james.backends.es.v8.search.ScrolledSearch;
import org.apache.james.core.Username;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import reactor.core.publisher.Flux;

public class ElasticSearchQuotaSearcher implements QuotaSearcher {
    private static final Time TIMEOUT = new Time.Builder().time("1m").build();

    private final ReactorElasticSearchClient client;
    private final AliasName readAlias;
    private final QuotaQueryConverter quotaQueryConverter;

    public ElasticSearchQuotaSearcher(ReactorElasticSearchClient client, ReadAliasName readAlias) {
        this.client = client;
        this.readAlias = readAlias;
        this.quotaQueryConverter = new QuotaQueryConverter();
    }

    @Override
    public List<Username> search(QuotaQuery query) {
        try {
            return searchHits(query)
                .map(Hit::id)
                .map(Username::of)
                .collect(ImmutableList.toImmutableList())
                .block();
        } catch (Exception e) {
            throw new RuntimeException("Unexpected exception while executing " + query, e);
        }
    }

    private Flux<Hit<ObjectNode>> searchHits(QuotaQuery query) {
        if (query.getLimit().isLimited()) {
            return executeSingleSearch(query);
        } else {
            return executeScrolledSearch(query);
        }
    }

    private Flux<Hit<ObjectNode>> executeSingleSearch(QuotaQuery query) {
        SearchRequest.Builder searchRequest = searchRequestBuilder(query)
            .index(readAlias.getValue())
            .from(query.getOffset().getValue());

        query.getLimit().getValue()
            .ifPresent(searchRequest::size);

        return client.search(searchRequest.build())
            .flatMapIterable(searchResponse -> ImmutableList.copyOf(searchResponse.hits().hits()));
    }

    private Flux<Hit<ObjectNode>> executeScrolledSearch(QuotaQuery query) {
        return new ScrolledSearch(client,
            searchRequestBuilder(query)
                .index(readAlias.getValue())
                .scroll(TIMEOUT)
                .build())
            .searchHits()
            .skip(query.getOffset().getValue());
    }

    private SearchRequest.Builder searchRequestBuilder(QuotaQuery query) {
        return new SearchRequest.Builder()
            .query(quotaQueryConverter.from(query))
            .sort(new SortOptions.Builder()
                .field(new FieldSort.Builder()
                    .field(USER)
                    .order(SortOrder.Asc)
                    .build())
                .build());
    }
}