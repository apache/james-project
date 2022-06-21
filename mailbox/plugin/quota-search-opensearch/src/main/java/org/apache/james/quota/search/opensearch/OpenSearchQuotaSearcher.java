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

import static org.apache.james.quota.search.opensearch.json.JsonMessageConstants.USER;

import java.io.IOException;
import java.util.List;

import org.apache.james.backends.opensearch.AliasName;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.backends.opensearch.ReadAliasName;
import org.apache.james.backends.opensearch.search.ScrolledSearch;
import org.apache.james.core.Username;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.search.Hit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;

public class OpenSearchQuotaSearcher implements QuotaSearcher {
    private static final Time TIMEOUT = new Time.Builder().time("1m").build();

    private final ReactorElasticSearchClient client;
    private final AliasName readAlias;
    private final QuotaQueryConverter quotaQueryConverter;

    public OpenSearchQuotaSearcher(ReactorElasticSearchClient client, ReadAliasName readAlias) {
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

    private Flux<Hit<ObjectNode>> searchHits(QuotaQuery query) throws IOException {
        if (query.getLimit().isLimited()) {
            return executeSingleSearch(query);
        } else {
            return executeScrolledSearch(query);
        }
    }

    private Flux<Hit<ObjectNode>> executeSingleSearch(QuotaQuery query) throws IOException {
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