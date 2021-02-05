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

package org.apache.james.backends.es;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import org.apache.james.backends.es.search.ScrolledSearch;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class DeleteByQueryPerformer {
    private static final TimeValue TIMEOUT = new TimeValue(60000);

    private final ReactorElasticSearchClient client;
    private final int batchSize;
    private final WriteAliasName aliasName;

    @VisibleForTesting
    DeleteByQueryPerformer(ReactorElasticSearchClient client, int batchSize, WriteAliasName aliasName) {
        this.client = client;
        this.batchSize = batchSize;
        this.aliasName = aliasName;
    }

    public Mono<Void> perform(QueryBuilder queryBuilder, RoutingKey routingKey) {
        SearchRequest searchRequest = prepareSearch(queryBuilder, routingKey);

        return new ScrolledSearch(client, searchRequest).searchResponses()
            .filter(searchResponse -> searchResponse.getHits().getHits().length > 0)
            .flatMap(searchResponse -> deleteRetrievedIds(searchResponse, routingKey), DEFAULT_CONCURRENCY)
            .then();
    }

    private SearchRequest prepareSearch(QueryBuilder queryBuilder, RoutingKey routingKey) {
        return new SearchRequest(aliasName.getValue())
            .types(NodeMappingFactory.DEFAULT_MAPPING_NAME)
            .scroll(TIMEOUT)
            .source(searchSourceBuilder(queryBuilder))
            .routing(routingKey.asString());
    }

    private SearchSourceBuilder searchSourceBuilder(QueryBuilder queryBuilder) {
        return new SearchSourceBuilder()
            .query(queryBuilder)
            .size(batchSize);
    }

    private Mono<BulkResponse> deleteRetrievedIds(SearchResponse searchResponse, RoutingKey routingKey) {
        BulkRequest request = new BulkRequest();

        for (SearchHit hit : searchResponse.getHits()) {
            request.add(
                new DeleteRequest(aliasName.getValue())
                    .type(NodeMappingFactory.DEFAULT_MAPPING_NAME)
                    .id(hit.getId())
                    .routing(routingKey.asString()));
        }

        return client.bulk(request, RequestOptions.DEFAULT);
    }
}
