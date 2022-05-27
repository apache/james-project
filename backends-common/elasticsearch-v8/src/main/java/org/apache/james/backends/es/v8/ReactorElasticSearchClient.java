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

package org.apache.james.backends.es.v8;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.elasticsearch.client.RestClient;

import com.fasterxml.jackson.databind.node.ObjectNode;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ExistsAliasRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesRequest;
import co.elastic.clients.elasticsearch.indices.UpdateAliasesResponse;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

public class ReactorElasticSearchClient implements AutoCloseable {
    private final ElasticsearchAsyncClient client;
    private final RestClient lowLevelRestClient;

    public ReactorElasticSearchClient(ElasticsearchAsyncClient client, RestClient lowLevelRestClient) {
        this.client = client;
        this.lowLevelRestClient = lowLevelRestClient;
    }

    public Mono<BulkResponse> bulk(BulkRequest bulkRequest) {
        return toReactor(client.bulk(bulkRequest));
    }

    public Mono<ClearScrollResponse> clearScroll(ClearScrollRequest clearScrollRequest) {
        return toReactor(client.clearScroll(clearScrollRequest));
    }

    public Mono<DeleteResponse> delete(DeleteRequest deleteRequest) {
        return toReactor(client.delete(deleteRequest));
    }

    public Mono<DeleteByQueryResponse> deleteByQuery(DeleteByQueryRequest deleteRequest) {
        return toReactor(client.deleteByQuery(deleteRequest));
    }

    public RestClient getLowLevelClient() {
        return lowLevelRestClient;
    }

    public <T> Mono<IndexResponse> index(IndexRequest<T> indexRequest) {
        return toReactor(client.index(indexRequest));
    }

    public Mono<BooleanResponse> indexExists(ExistsRequest existsRequest) {
        return toReactor(client.indices().exists(existsRequest));
    }

    public Mono<BooleanResponse> aliasExists(ExistsAliasRequest existsAliasRequest) {
        return toReactor(client.indices().existsAlias(existsAliasRequest));
    }

    public Mono<CreateIndexResponse> createIndex(CreateIndexRequest indexRequest) {
        return toReactor(client.indices().create(indexRequest));
    }

    public Mono<UpdateAliasesResponse> updateAliases(UpdateAliasesRequest updateAliasesRequest) {
        return toReactor(client.indices().updateAliases(updateAliasesRequest));
    }

    public Mono<InfoResponse> info() {
        return toReactor(client.info());
    }

    public Mono<ScrollResponse<ObjectNode>> scroll(ScrollRequest scrollRequest) {
        return toReactor(client.scroll(scrollRequest, ObjectNode.class));
    }

    public Mono<SearchResponse<ObjectNode>> search(SearchRequest searchRequest) {
        return toReactor(client.search(searchRequest, ObjectNode.class));
    }

    public Mono<HealthResponse> health(HealthRequest request) {
        return toReactor(client.cluster().health(request));
    }

    public Mono<GetResponse<ObjectNode>> get(GetRequest getRequest) {
        return toReactor(client.get(getRequest, ObjectNode.class));
    }

    @Override
    public void close() throws IOException {
        lowLevelRestClient.close();
    }

    private static <T> Mono<T> toReactor(CompletableFuture<T> async) {
        return Mono.<T>create(sink -> async.whenComplete(getFuture(sink)))
            .publishOn(Schedulers.elastic());
    }

    private static <T> BiConsumer<? super T, ? super Throwable> getFuture(MonoSink<T> sink) {
        return (response, exception) -> {
            if (exception != null) {
                sink.error(exception);
            } else {
                sink.success(response);
            }
        };
    }
}
