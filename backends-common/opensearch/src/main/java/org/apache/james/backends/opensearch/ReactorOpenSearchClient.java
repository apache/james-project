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

package org.apache.james.backends.opensearch;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.ClearScrollRequest;
import org.opensearch.client.opensearch.core.ClearScrollResponse;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.opensearch.client.opensearch.indices.ExistsAliasRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesResponse;
import org.opensearch.client.transport.endpoints.BooleanResponse;

import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ReactorOpenSearchClient implements AutoCloseable {
    private final OpenSearchAsyncClient client;
    private final RestClient lowLevelRestClient;

    public ReactorOpenSearchClient(OpenSearchAsyncClient client, RestClient lowLevelRestClient) {
        this.client = client;
        this.lowLevelRestClient = lowLevelRestClient;
    }

    public Mono<BulkResponse> bulk(BulkRequest bulkRequest) throws IOException {
        return toReactor(client.bulk(bulkRequest));
    }

    public Mono<ClearScrollResponse> clearScroll(ClearScrollRequest clearScrollRequest) throws IOException {
        return toReactor(client.clearScroll(clearScrollRequest));
    }

    public Mono<DeleteResponse> delete(DeleteRequest deleteRequest) throws IOException {
        return toReactor(client.delete(deleteRequest));
    }

    public Mono<DeleteByQueryResponse> deleteByQuery(DeleteByQueryRequest deleteRequest) throws IOException {
        return toReactor(client.deleteByQuery(deleteRequest));
    }

    public <T> Mono<IndexResponse> index(IndexRequest<T> indexRequest) throws IOException {
        return toReactor(client.index(indexRequest));
    }

    public Mono<BooleanResponse> indexExists(ExistsRequest existsRequest) throws IOException {
        return toReactor(client.indices().exists(existsRequest));
    }

    public Mono<BooleanResponse> aliasExists(ExistsAliasRequest existsAliasRequest) throws IOException {
        return toReactor(client.indices().existsAlias(existsAliasRequest));
    }

    public Mono<CreateIndexResponse> createIndex(CreateIndexRequest indexRequest) throws IOException {
        return toReactor(client.indices().create(indexRequest));
    }

    public Mono<UpdateAliasesResponse> updateAliases(UpdateAliasesRequest updateAliasesRequest) throws IOException {
        return toReactor(client.indices().updateAliases(updateAliasesRequest));
    }

    public Mono<InfoResponse> info() throws IOException {
        return toReactor(client.info());
    }

    public Mono<ScrollResponse<ObjectNode>> scroll(ScrollRequest scrollRequest) throws IOException {
        return toReactor(client.scroll(scrollRequest, ObjectNode.class));
    }

    public Mono<SearchResponse<ObjectNode>> search(SearchRequest searchRequest) throws IOException {
        return toReactor(client.search(searchRequest, ObjectNode.class));
    }

    public Mono<HealthResponse> health(HealthRequest request) throws IOException {
        return toReactor(client.cluster().health(request));
    }

    public Mono<GetResponse<ObjectNode>> get(GetRequest getRequest) throws IOException {
        return toReactor(client.get(getRequest, ObjectNode.class));
    }

    @Override
    public void close() throws IOException {
        lowLevelRestClient.close();
    }

    private static <T> Mono<T> toReactor(CompletableFuture<T> async) {
        return Mono.fromFuture(async)
            .publishOn(Schedulers.boundedElastic());
    }
}
