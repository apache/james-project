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

package org.apache.james.backends.es.v7;

import java.io.IOException;
import java.util.function.Consumer;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.storedscripts.DeleteStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptRequest;
import org.elasticsearch.action.admin.cluster.storedscripts.GetStoredScriptResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.explain.ExplainRequest;
import org.elasticsearch.action.explain.ExplainResponse;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesRequest;
import org.elasticsearch.action.fieldcaps.FieldCapabilitiesResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.index.rankeval.RankEvalRequest;
import org.elasticsearch.index.rankeval.RankEvalResponse;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.script.mustache.MultiSearchTemplateRequest;
import org.elasticsearch.script.mustache.MultiSearchTemplateResponse;
import org.elasticsearch.script.mustache.SearchTemplateRequest;
import org.elasticsearch.script.mustache.SearchTemplateResponse;

import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

public class ReactorElasticSearchClient implements AutoCloseable {
    private final RestHighLevelClient client;

    public ReactorElasticSearchClient(RestHighLevelClient client) {
        this.client = client;
    }

    public Mono<BulkResponse> bulk(BulkRequest bulkRequest, RequestOptions options) {
        return toReactor(listener -> client.bulkAsync(bulkRequest, options, listener));
    }

    public Mono<ClearScrollResponse> clearScroll(ClearScrollRequest clearScrollRequest, RequestOptions options) {
        return toReactor(listener -> client.clearScrollAsync(clearScrollRequest, options, listener));
    }

    public DeleteResponse delete(DeleteRequest deleteRequest, RequestOptions options) throws IOException {
        return client.delete(deleteRequest, options);
    }

    public Mono<BulkByScrollResponse> deleteByQuery(DeleteByQueryRequest deleteRequest, RequestOptions options) {
        return toReactor(listener -> client.deleteByQueryAsync(deleteRequest, options, listener));
    }

    public Mono<AcknowledgedResponse> deleteScript(DeleteStoredScriptRequest request, RequestOptions options) {
        return toReactor(listener -> client.deleteScriptAsync(request, options, listener));
    }

    public Mono<ExplainResponse> explain(ExplainRequest explainRequest, RequestOptions options) {
        return toReactor(listener -> client.explainAsync(explainRequest, options, listener));
    }

    public Mono<FieldCapabilitiesResponse> fieldCaps(FieldCapabilitiesRequest fieldCapabilitiesRequest, RequestOptions options) {
        return toReactor(listener -> client.fieldCapsAsync(fieldCapabilitiesRequest, options, listener));
    }

    public RestClient getLowLevelClient() {
        return client.getLowLevelClient();
    }

    public Mono<GetStoredScriptResponse> getScript(GetStoredScriptRequest request, RequestOptions options) {
        return toReactor(listener -> client.getScriptAsync(request, options, listener));
    }

    public Mono<IndexResponse> index(IndexRequest indexRequest, RequestOptions options) {
        return toReactor(listener -> client.indexAsync(indexRequest, options, listener));
    }

    public IndicesClient indices() {
        return client.indices();
    }

    public MainResponse info(RequestOptions options) throws IOException {
        return client.info(options);
    }

    public Mono<MultiSearchResponse> msearch(MultiSearchRequest multiSearchRequest, RequestOptions options) {
        return toReactor(listener -> client.msearchAsync(multiSearchRequest, options, listener));
    }

    public Mono<MultiSearchTemplateResponse> msearchTemplate(MultiSearchTemplateRequest multiSearchTemplateRequest, RequestOptions options) {
        return toReactor(listener -> client.msearchTemplateAsync(multiSearchTemplateRequest, options, listener));
    }

    public Mono<RankEvalResponse> rankEval(RankEvalRequest rankEvalRequest, RequestOptions options) {
        return toReactor(listener -> client.rankEvalAsync(rankEvalRequest, options, listener));
    }

    public Mono<SearchResponse> scroll(SearchScrollRequest searchScrollRequest, RequestOptions options) {
        return toReactor(listener -> client.scrollAsync(searchScrollRequest, options, listener));
    }

    public Mono<SearchResponse> search(SearchRequest searchRequest, RequestOptions options) {
        return toReactor(listener -> client.searchAsync(searchRequest, options, listener));
    }

    public Mono<ClusterHealthResponse> health(ClusterHealthRequest request) {
        return toReactor(listener -> client.cluster()
            .healthAsync(request, RequestOptions.DEFAULT, listener));
    }

    public Mono<SearchTemplateResponse> searchTemplate(SearchTemplateRequest searchTemplateRequest, RequestOptions options) {
        return toReactor(listener -> client.searchTemplateAsync(searchTemplateRequest, options, listener));
    }

    public Mono<GetResponse> get(GetRequest getRequest, RequestOptions options) {
        return toReactor(listener -> client.getAsync(getRequest, options, listener));
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    private static <T> Mono<T> toReactor(Consumer<ActionListener<T>> async) {
        return Mono.<T>create(sink -> async.accept(getListener(sink)))
            .publishOn(Schedulers.elastic());
    }

    private static <T> ActionListener<T> getListener(MonoSink<T> sink) {
        return new ActionListener<T>() {
            @Override
            public void onResponse(T t) {
                sink.success(t);
            }

            @Override
            public void onFailure(Exception e) {
                sink.error(e);
            }
        };
    }
}
