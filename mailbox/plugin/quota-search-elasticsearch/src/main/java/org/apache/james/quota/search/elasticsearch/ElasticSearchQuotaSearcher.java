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

import static org.apache.james.quota.search.elasticsearch.json.JsonMessageConstants.USER;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.backends.es.AliasName;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.backends.es.ReadAliasName;
import org.apache.james.backends.es.search.ScrolledSearch;
import org.apache.james.core.User;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import com.github.steveash.guavate.Guavate;

public class ElasticSearchQuotaSearcher implements QuotaSearcher {
    private static final TimeValue TIMEOUT = TimeValue.timeValueMinutes(1);

    private final RestHighLevelClient client;
    private final AliasName readAlias;
    private final QuotaQueryConverter quotaQueryConverter;

    public ElasticSearchQuotaSearcher(RestHighLevelClient client, ReadAliasName readAlias) {
        this.client = client;
        this.readAlias = readAlias;
        this.quotaQueryConverter = new QuotaQueryConverter();
    }

    @Override
    public List<User> search(QuotaQuery query) {
        try {
            try (Stream<SearchHit> searchHits = searchHits(query)) {
                return searchHits
                    .map(SearchHit::getId)
                    .map(User::fromUsername)
                    .collect(Guavate.toImmutableList());
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected exception while executing " + query, e);
        }
    }

    private Stream<SearchHit> searchHits(QuotaQuery query) throws IOException {
        if (query.getLimit().isLimited()) {
            return executeSingleSearch(query);
        } else {
            return executeScrolledSearch(query);
        }
    }

    private Stream<SearchHit> executeSingleSearch(QuotaQuery query) throws IOException {
        SearchSourceBuilder searchSourceBuilder = searchSourceBuilder(query)
            .from(query.getOffset().getValue());
        query.getLimit().getValue()
            .ifPresent(searchSourceBuilder::size);

        SearchRequest searchRequest = new SearchRequest(readAlias.getValue())
            .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
            .source(searchSourceBuilder);

        return Arrays.stream(client.search(searchRequest, RequestOptions.DEFAULT)
            .getHits()
            .getHits());
    }

    private Stream<SearchHit> executeScrolledSearch(QuotaQuery query) {
        return new ScrolledSearch(client,
            new SearchRequest(readAlias.getValue())
                .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                .source(searchSourceBuilder(query))
                .scroll(TIMEOUT))
            .searchHits()
            .skip(query.getOffset().getValue());
    }

    private SearchSourceBuilder searchSourceBuilder(QuotaQuery query) {
        return new SearchSourceBuilder()
            .query(quotaQueryConverter.from(query))
            .sort(SortBuilders.fieldSort(USER).order(SortOrder.ASC));
    }
}